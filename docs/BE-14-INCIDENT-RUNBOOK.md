# BE-14 — Incident Response Runbook

**Classification:** Internal Reference (Sanitized — No Credentials)
**Verified Against:** `docker-compose.yml`, `backup.sh`, `restore.sh`, `auto_deploy.sh`, `rotate_secrets.sh`, `application-prod.properties`, `BE-09-DEPLOYMENT.md`
**Last Updated:** 2026-05-29

---

## Critical Rules Before Any Incident Action

1. **Never SSH into the Ubuntu VM and run git/docker commands manually** unless the automated watchdog has completely failed. All normal deployments flow through `git push`.
2. **All data operations require `docker exec` or `docker cp`** — the Ubuntu VM has no host-level CLI tools.
3. **The Cloudflare Edge Worker provides SEO and GEO continuity even when the backend is down.** The KV cache serves sitemaps and GEO payloads independently.
4. **Tag a git checkpoint before any emergency operation:**
   ```powershell
   git tag -a "backup-incident-$(date +%Y%m%d)" -m "Pre-incident backup checkpoint"
   git push origin --tags
   ```

---

## Incident 1 — Backend Containers Down / Not Starting

### Symptoms
- API calls return 502 or 503
- Cloudflare Worker logs show backend fetch failures
- Grafana (if accessible) shows no Spring Boot metrics

### Diagnosis

```bash
# SSH to Ubuntu VM (for emergency direct access)
ssh vboxuser@192.168.29.111

# Check container status
docker compose ps

# Check backend logs
docker compose logs --tail=100 backend

# Check if dependency services are healthy
docker compose ps treishvaam-db treishvaam-redis treishvaam-elastic treishvaam-rabbitmq
```

### Common Causes & Fixes

**A. OOM Kill (Exit Code 137)**
```bash
# Check system memory
free -h

# Drop OS page cache to reclaim RAM
sudo sync && sudo sh -c "echo 3 > /proc/sys/vm/drop_caches"

# Prune builder cache
docker builder prune -f

# Restart backend
docker compose up -d --force-recreate --no-deps backend
```

**B. Secret injection failure — missing environment variable**
```bash
# Check what variables the container has
docker exec <backend-container-id> env | grep -i "KEY\|SECRET\|PASSWORD"

# Re-run secret injection
cd /opt/treishvaam
bash scripts/load_secrets.sh
docker compose up -d --force-recreate --no-deps backend
```

**C. Dependency service not healthy (RabbitMQ most common)**
```bash
# Check RabbitMQ health
docker exec treishvaam-rabbitmq rabbitmq-diagnostics -q check_running

# Restart RabbitMQ if unhealthy
docker compose restart treishvaam-rabbitmq
# Wait 30s then restart backend
sleep 30 && docker compose restart backend
```

**D. Database migration failure (Liquibase)**
```bash
# Check backend logs for Liquibase errors
docker compose logs backend | grep -i "liquibase\|changeset\|lock"

# If Liquibase lock is stuck:
docker exec -it treishvaam-db mysql -u root -p finance_db
> DELETE FROM DATABASECHANGELOGLOCK;
> EXIT;
docker compose restart backend
```

**E. JVM startup crash (ClassNotFoundException, BeanCreationException)**
```bash
# Get full startup log
docker compose logs --tail=500 backend | grep -E "ERROR|WARN|Exception"

# Usually indicates a missing dependency or config issue
# Check application-prod.properties for the failing bean
```

---

## Incident 2 — Database Issues

### MariaDB Won't Start

```bash
# Check logs
docker compose logs treishvaam-db | tail -50

# Check data volume permissions
ls -la /opt/treishvaam/data/mariadb/

# Fix permissions if needed
sudo chown -R 999:999 /opt/treishvaam/data/mariadb/

# Restart
docker compose restart treishvaam-db
```

### Data Restore from Backup

```bash
# List available backups
sudo ls -lh /backup/mariadb*.sql.gz

# Restore (replace FILENAME with actual backup)
docker exec -i treishvaam-db mysql -u root -p < <(zcat /backup/mariadb_YYYYMMDD_HHMMSS.sql.gz)
```

### Redis Data Restore

```bash
# Stop Redis
docker compose stop treishvaam-redis

# Copy backup into container volume
sudo cp /backup/redis_YYYYMMDD.rdb /opt/treishvaam/data/redis/dump.rdb
sudo chown 999:999 /opt/treishvaam/data/redis/dump.rdb

# Restart
docker compose start treishvaam-redis
```

### MinIO Data Restore

```bash
# Stop MinIO
docker compose stop treishvaam-minio

# Restore data
sudo rm -rf /opt/treishvaam/data/minio
sudo cp -r /backup/minio_data_YYYYMMDD /opt/treishvaam/data/minio

# Restart
docker compose start treishvaam-minio
```

---

## Incident 3 — Cloudflare Worker Failure

### Symptoms
- All requests to `treishvaamfinance.com` return errors
- Worker logs in Cloudflare Dashboard show exceptions
- The Next.js frontend is unreachable

### Diagnosis

1. Open Cloudflare Dashboard → Workers & Pages → `treishfin-seo-worker` → Logs
2. Check if the error is in the Worker logic vs. the backend being unreachable

### Fix — Worker Logic Error

```powershell
# Windows Host — redeploy Worker
cd "C:\Users\7303150607\OneDrive\Desktop\PrOJEct\treishvaam-finance-frontend\worker"
npx wrangler deploy
```

### Fix — Worker Secret Missing/Expired

```powershell
# Re-inject Worker secrets
npx wrangler secret put AEGIS_EDGE_SECRET
npx wrangler secret put BACKEND_API_URL
```

### Fix — HMAC Signature Mismatch (403 from backend)

This occurs if `AEGIS_EDGE_SECRET` in the Worker doesn't match the backend's `AEGIS_EDGE_SECRET`.

1. Verify both secrets are identical (check Infisical vs. Cloudflare Worker secrets)
2. Re-inject the Worker secret if they differ:
   ```powershell
   npx wrangler secret put AEGIS_EDGE_SECRET
   # Paste the exact same value as stored in Infisical
   ```

### Fix — KV Cache Stale/Corrupt

```bash
# Clear sitemap KV entries via Cloudflare Dashboard
# Workers → KV → TREISHFIN_SEO_CACHE → delete sitemap:* keys
# The Worker will re-populate from backend on next request
```

---

## Incident 4 — SEO / Sitemap Emergency

### Symptoms
- Google Search Console shows sitemap errors
- `https://treishvaamfinance.com/sitemap.xml` returns 5xx or empty

### Diagnosis

```bash
# Check if backend sitemap endpoint works
curl -H "X-Tenant-ID: finance" http://localhost:8080/api/v1/sitemap/sitemap.xml

# Check KV cache state
# Cloudflare Dashboard → KV → TREISHFIN_SEO_CACHE → check sitemap:meta key
```

### Fix — Force Worker Sitemap Cache Rebuild

Trigger the Worker's cron manually via Cloudflare Dashboard:
- Workers & Pages → `treishfin-seo-worker` → Triggers → Run Now (if available)

Or wait up to 1 hour for the `0 * * * *` cron to run automatically.

### Fix — Backend Sitemap Generation Broken

```bash
# Check SitemapService logs
docker compose logs backend | grep -i "sitemap"

# Verify MinIO bucket accessible (sitemaps are also written to MinIO)
docker exec treishvaam-minio mc ls treishvaam-uploads/sitemaps/
```

---

## Incident 5 — Secret Expiry / Rotation Required

### JWT Key Rotation (Every 90 Days)

**Impact:** ALL active user sessions are invalidated. Users must re-login.

```bash
# On Ubuntu VM
cd /opt/treishvaam
bash scripts/rotate_secrets.sh
# Type ROTATE when prompted
```

This script:
1. Backs up `.env` to `/opt/treishvaam/env_backups/`
2. Generates new `JWT_SECRET_KEY` (512-bit) and `INTERNAL_API_SECRET_KEY` (256-bit) via `openssl rand`
3. Updates `.env` in place
4. Restarts the backend with zero-downtime rolling update

### Cloudflare API Token Rotation (Before 2026-08-26)

1. Log in to Cloudflare Dashboard → Profile → API Tokens
2. Create a new scoped token (same permissions and IP fencing as the existing one)
3. Update `CLOUDFLARE_API_TOKEN` in Infisical
4. Trigger `auto_deploy.sh` to inject new token: `git push origin develop` (trivial change)
5. Verify `CloudflareEdgeSyncService` is still syncing to KV: check backend logs for `[CloudflareEdgeSyncService]`
6. Revoke the old token in Cloudflare Dashboard

---

## Incident 6 — AEGIS ZKP Service Down

### Symptoms
- Admin endpoints (`/api/v1/admin/**`) return 503 or circuit breaker open errors
- Backend logs show gRPC connection failures to `aegis-zkp-service`

### Diagnosis

```bash
# Check ZKP service health
docker compose ps aegis-zkp-service
docker compose logs aegis-zkp-service | tail -20

# Test gRPC port
docker exec treishvaam-nginx nc -zv aegis-zkp-service 9090
```

### Fix

```bash
# Restart ZKP service
docker compose restart aegis-zkp-service

# Wait for TCP health check to pass (healthcheck: nc -z localhost 9090)
docker compose ps aegis-zkp-service
```

**Important:** The ZKP service has a **strict 256MB memory limit** (`deploy.resources.limits.memory: 256M`). Do NOT increase this limit without explicit approval — it will consume VirtualBox host RAM and trigger OOM kills on other services.

---

## Incident 7 — OpenResty (Nginx) Configuration Error

### Symptoms
- All traffic returns 502 or connection refused
- Cloudflare logs show origin connectivity issues

### Diagnosis

```bash
# Check OpenResty container
docker compose logs treishvaam-nginx | tail -50

# Test config syntax
docker exec treishvaam-nginx openresty -t
```

### Fix

```bash
# If config file was edited:
docker compose restart treishvaam-nginx

# If hard crash:
docker compose up -d --force-recreate --no-deps treishvaam-nginx
```

**Critical:** Do NOT replace `openresty/openresty:alpine` with standard `nginx:alpine`. The `aegis_ja3.lua` TLS fingerprinting module requires the Lua execution engine provided exclusively by OpenResty.

---

## Incident 8 — Observability Stack Issues

### Grafana Inaccessible

```bash
# Ensure SSH tunnel is active on Windows Host:
# ssh -L 3001:localhost:3001 -L 15672:localhost:15672 vboxuser@192.168.29.111

# Check Grafana container
docker compose ps treishvaam-grafana
docker compose logs treishvaam-grafana | tail -20
docker compose restart treishvaam-grafana
```

### Prometheus Not Scraping

```bash
# Check Prometheus targets
# Access via SSH tunnel → http://localhost:9090 (Prometheus default, if bound)
docker compose logs treishvaam-prometheus | tail -20

# Verify Spring Boot actuator is exposing metrics
curl http://backend-container:8080/actuator/prometheus
```

### Loki Not Receiving Logs

```bash
# Check Promtail is running
docker compose ps treishvaam-promtail
docker compose logs treishvaam-promtail | tail -20

# Query label in Grafana: {job="varlogs"}
```

---

## Last Verified Backup State (Reference)

| Backup | File | Size | Verified Date |
| :--- | :--- | :--- | :--- |
| MariaDB | `/backup/mariadb_20260503_183454.sql.gz` | 3.8 MB | 2026-05-03 |
| Redis | `/backup/redis_20260503.rdb` | 2.05 kB | 2026-05-03 |
| MinIO | `/backup/minio_data_20260503` | 7.27 GB | 2026-05-03 |
| Git Tag | `backup-pre-p0` | — | 2026-05-03 |

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **ADDED (2026-05-29 — Enterprise Documentation Generation):**
  - Created `BE-14-INCIDENT-RUNBOOK.md` from scratch.
  - Why: No consolidated incident response document existed. Operational procedures were embedded in `BE-09-DEPLOYMENT.md` and `BE-06-INFRA-DEVOPS.md` but not organized as a runbook. This document is a critical missing enterprise artifact for non-coder operational safety.
  - Source of truth: `docker-compose.yml`, `backup.sh`, `restore.sh`, `auto_deploy.sh`, `rotate_secrets.sh`, `application-prod.properties`, `SECRETS.md`, `BE-09-DEPLOYMENT.md`.
