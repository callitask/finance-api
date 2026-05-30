# BE-09 — Deployment & Operations Manual

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized — No Server IPs, No Credentials)
**Last Verified:** 2026-05-29 — Verified against `deploy.yml`, `auto_deploy.sh`, `rotate_secrets.sh`, `backup.sh`, `restore.sh`, `docker-compose.yml`

---

## 1. Infrastructure Overview

| Environment | Machine | Role |
| :--- | :--- | :--- |
| **Codebase** | Developer Windows Host | All `git`, `mvn`, `npm`, `npx wrangler` commands |
| **Runtime** | Ubuntu VirtualBox VM | All Docker containers, databases, reverse proxies |

**Non-negotiable rule:** Never SSH into the Ubuntu VM to manually pull code or restart containers. All deployments flow through `git push` → GitHub Actions → auto-deploy watchdog. The only legitimate reason to SSH is to diagnose an emergency or access Grafana/RabbitMQ via SSH tunnel.

---

## 2. Git Branching Strategy

| Branch | Role | Auto-Deploy Target |
| :--- | :--- | :--- |
| `develop` | Active development — daily work | Backend dev environment |
| `staging` | Release candidate — feature-complete, stable restore point | Staging |
| `main` | Production — locked, public-facing release | Production containers |

**Commit signing is mandatory.** All commits reaching `develop`, `staging`, or `main` must be GPG-signed. The CI pipeline (`deploy.yml`) executes `git verify-commit` and rejects unsigned commits.

---

## 3. Dual-Engine Deployment Architecture

Two independent engines handle deployments. They are intentionally decoupled.

### Engine A — GitHub Actions Builder (`deploy.yml`)

**Trigger:** Push to `develop`, `staging`, or `main`

**Pipeline steps (verified):**
1. Java 21 (Temurin) setup + Maven dependency cache
2. **Gitleaks secret scan** — blocks pipeline on credential leakage detection
3. **GPG commit verification** (`git verify-commit`) — unsigned commits are rejected
4. Unit tests (`mvn test`)
5. Code formatting check (`mvn spotless:check`)
6. Build (`mvn clean package`)
7. SCP artifact to Ubuntu VM
8. Signal server watchdog to restart

**OWASP Dependency Check:** Runs in a **separate, isolated cron-scheduled job** (`dependency-security-scan`) — NEVER blocks the synchronous `build-and-deploy` job. A 15-minute OWASP scan must not delay deployments.

### Engine B — Ubuntu VM Watchdog (`auto_deploy.sh`)

**Trigger:** Runs every minute via cron on the Ubuntu VM

**Logic flow (verified from `auto_deploy.sh`):**
1. Compares timestamps of `origin/develop`, `origin/staging`, `origin/main` — most recent commit wins
2. Pulls changes to non-compiled files (OpenResty/Nginx configs, Python market engine scripts, Docker configs)
3. Executes **Flash & Wipe** secret injection via Infisical:
   - Authenticates with Infisical via Machine Identity Token (Universal Auth)
   - `infisical export` piped through `sed "s/['\"]//g"` (strips literal quotes — prevents HikariCP JDBC URL crash)
   - Writes secrets to transient `.env` file
4. **OS Memory Recovery** (critical — prevents Exit 137 OOM kills):
   - `sudo sync && sudo sh -c "echo 3 > /proc/sys/vm/drop_caches"` — evicts Linux page cache
   - `docker builder prune -f` — reclaims build cache RAM
5. `docker compose up -d` — zero-downtime rolling update (pulls latest images, rebuilds, restarts without dropping connections)
6. `.env` sanitised after startup — high-value secrets wiped from disk

**Why the sed strip:** Infisical wraps `.env` values in literal single quotes. HikariCP JDBC URL parser receives the literal quote character → `IllegalStateException: LINKEDIN_TOKEN_ENCRYPTION_KEY missing or empty` → crash loop. The `sed` strip is a permanent fix. Do not remove it.

**Why OS Memory Recovery:** When scaling to 2 backend replicas, both Spring Boot JVMs boot simultaneously. The instantaneous RAM spike exhausted the VirtualBox VM's memory → OOM Kill (Exit Code 137). Evicting the Linux page cache guarantees maximum available RAM for container initialization.

---

## 4. Deploy Command Sequences

### Backend Repository

```powershell
# Windows Host — F:\BACKEND PROJECG\finance-api\finance-api
mvn spotless:apply
git checkout develop
git add .
git commit -m "feat: <description>"
git push origin develop
```

### Finance Frontend (Next.js Only — No Worker Change)

```powershell
# Windows Host — C:\Users\7303150607\OneDrive\Desktop\PrOJEct\treishvaam-finance-frontend
git checkout main
git add .
git commit -m "feat: <description>"
git push origin main
```

### Finance Frontend + Worker (When `worker.js` or `wrangler.toml` Changed)

```powershell
# Step 1: Deploy Worker first
cd "C:\Users\7303150607\OneDrive\Desktop\PrOJEct\treishvaam-finance-frontend\worker"
npx wrangler deploy

# Step 2: Commit and push frontend
cd ..
git checkout main
git add .
git commit -m "feat: <description>"
git push origin main
```

**⚠️ Worker Deployment Rule:** Only run `npx wrangler deploy` if `worker/worker.js` or `worker/wrangler.toml` was actually modified. Unnecessary Worker deployments add risk.

### Agro Frontend + Worker

```powershell
# F:\treishvaamgroup\treishvaam-agro-frontend\treishvaam-agro-frontend\worker
npx wrangler deploy

cd ..
git checkout develop
git add .
git commit -m "feat: <description>"
git push origin develop
```

### Parent Frontend

```powershell
# F:\treishvaamgroup\treishvaamgroup-frontend
git checkout production-deploy
git add .
git commit -m "feat: <description>"
git push origin production-deploy
```

---

## 5. Flash & Wipe Secret Management

Secrets never persist unencrypted on disk beyond container startup:

```
auto_deploy.sh execution:
1. infisical login --method=universal-auth (Machine Identity)
2. infisical export | sed "s/['\"]//g" > .env
3. docker compose up -d  (reads .env via env_file: directive)
4. Immediate .env sanitisation — high-value secret values wiped
5. Containers retain secrets in-memory only
```

**Never commit `.env` to git.** Listed in `.gitignore`. **Never quote `.env` values** — HikariCP crash.

---

## 6. Backup Procedures (Verified Commands — Do Not Modify)

All backup commands verified on **2026-05-03 (pre-P0 session)**.

**Run all on the Ubuntu VM via SSH.**

### MariaDB Backup

```bash
docker exec -i treishvaam-db mysqldump -u root -p --all-databases \
  --single-transaction --routines --triggers | gzip | \
  sudo tee /backup/mariadb_$(date +%Y%m%d_%H%M%S).sql.gz > /dev/null

# Verify
sudo ls -lh /backup/mariadb*.sql.gz
```

### Redis Backup

```bash
docker exec -i treishvaam-redis redis-cli BGSAVE
sudo docker cp treishvaam-redis:/data/dump.rdb /backup/redis_$(date +%Y%m%d).rdb
```

### MinIO Backup (docker cp ONLY — tar method permanently fails)

```bash
sudo docker cp treishvaam-minio:/data /backup/minio_data_$(date +%Y%m%d)
```

**⚠️ CRITICAL:** The `tar` method fails — `minio-data` directory does not exist on host. Only `docker cp` is verified working. Do not attempt `tar` or `mc` — the Ubuntu VM has no `mc` binary installed.

### Git Tag Checkpoint (Windows Host)

```powershell
git tag -a backup-pre-p0 -m "Pre-change P0 backup checkpoint"
git push origin --tags
```

### Last Verified Backup State (Reference)

| Backup | Path | Size | Date |
| :--- | :--- | :--- | :--- |
| MariaDB | `/backup/mariadb_20260503_183454.sql.gz` | 3.8 MB | 2026-05-03 |
| Redis | `/backup/redis_20260503.rdb` | 2.05 kB | 2026-05-03 |
| MinIO | `/backup/minio_data_20260503` | 7.27 GB | 2026-05-03 |
| Git Tag | `backup-pre-p0` | — | 2026-05-03 |

---

## 7. Restore Procedures

### MariaDB Restore

```bash
docker exec -i treishvaam-db mysql -u root -p < <(zcat /backup/mariadb_YYYYMMDD_HHMMSS.sql.gz)
```

### Redis Restore

```bash
docker compose stop treishvaam-redis
sudo cp /backup/redis_YYYYMMDD.rdb /opt/treishvaam/data/redis/dump.rdb
sudo chown 999:999 /opt/treishvaam/data/redis/dump.rdb
docker compose start treishvaam-redis
```

### MinIO Restore

```bash
docker compose stop treishvaam-minio
sudo rm -rf /opt/treishvaam/data/minio
sudo cp -r /backup/minio_data_YYYYMMDD /opt/treishvaam/data/minio
docker compose start treishvaam-minio
```

---

## 8. Secret Rotation Procedures

### JWT Key Rotation (Every 90 Days)

**Impact: ALL active user sessions invalidated. Users must re-login.**

```bash
# On Ubuntu VM
cd /opt/treishvaam
bash scripts/rotate_secrets.sh
# Type "ROTATE" when prompted
```

Script actions (verified from `rotate_secrets.sh`):
1. Backs up `.env` to `/opt/treishvaam/env_backups/.env.backup.TIMESTAMP`
2. Generates `JWT_SECRET_KEY` (512-bit, `openssl rand -base64 64 | tr -d '\n' | tr -d '/'`)
3. Generates `INTERNAL_API_SECRET_KEY` (256-bit, `openssl rand -base64 32 | tr -d '\n' | tr -d '/'`)
4. Replaces values in `.env` in-place via `sed -i`
5. Logs rotation event to `/opt/treishvaam/key_rotation.log`
6. Zero-downtime backend restart: `docker compose stop backend` → `docker compose rm -f -s -v backend` → `docker compose up -d --force-recreate --no-deps backend`

### Cloudflare API Token Rotation (Before 2026-08-26)

1. Cloudflare Dashboard → Profile → API Tokens → Create Token (same scopes + IP fencing)
2. Update `CLOUDFLARE_API_TOKEN` in Infisical
3. Trigger deployment: `git push origin develop` (trivial commit)
4. Verify `CloudflareEdgeSyncService` logs: `[CloudflareEdgeSyncService] Synced X threats to Edge KV`
5. Revoke old token in Cloudflare Dashboard

---

## 9. Zero-Downtime Rolling Update Pattern

For individual service restart without full `docker compose up -d`:

```bash
# Backend (single service restart)
docker compose stop backend
docker compose rm -f -s -v backend
docker compose up -d --force-recreate --no-deps backend

# OpenResty config reload (non-destructive)
docker exec treishvaam-nginx openresty -s reload

# Full stack restart (emergency only)
docker compose down
docker compose up -d
```

**Never** use `docker compose restart` for the backend — it does not guarantee rolling behavior with 2 replicas.

---

## 10. CI/CD Security Enforcements

| Check | Mechanism | Failure Action |
| :--- | :--- | :--- |
| Secret scanning | Gitleaks in `deploy.yml` | Pipeline blocked, push rejected |
| Commit signing | `git verify-commit` in `deploy.yml` | Pipeline blocked |
| Code formatting | `mvn spotless:check` | Pipeline blocked |
| Dependency vulnerabilities | OWASP Dependency Check (separate cron job) | Alert only — does not block main pipeline |
| Docker config validation | `docker compose config` | Must pass before any compose operation |

**Shell safety rule:** All complex command strings in `docker-compose.yml` use YAML array syntax (`command: ["bash", "-c", "..."]`) to prevent `!` and `$` character corruption by the shell when passwords contain special characters.

---

## 11. Observability Access

Access Grafana and RabbitMQ exclusively via SSH local port forwarding. **Never expose these to `0.0.0.0`.**

```powershell
# Windows Host
ssh -L 3001:localhost:3001 -L 15672:localhost:15672 vboxuser@192.168.29.111
```

- **Grafana:** http://localhost:3001
- **RabbitMQ Management:** http://localhost:15672

### Prometheus Metrics Query Examples

```
# Backend request rate
rate(http_server_requests_seconds_count[5m])

# P99 latency
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# JVM memory
jvm_memory_used_bytes{area="heap"}
```

### Loki Log Query

```
{job="varlogs"}
{job="varlogs"} |= "AEGIS"
{job="varlogs"} |= "ERROR"
```

---

## 12. Planned: OCI Hybrid-Cloud Migration (Status: Pending)

When triggered, the OCI setup will:
1. Provision Oracle Cloud Always-Free instance via Terraform/Ansible IaC
2. Establish MariaDB GTID replication between OCI (primary) and local VBox (replica)
3. Implement sync-before-serve gate: local node serves API traffic only when 100% synced with OCI master
4. Set up automated backups from OCI → S3/OCI Object Storage
5. Zero-State Ignition script: `docker compose up` → Liquibase → S3 restore → Cloudflare Tunnel re-establish

When OCI migration is verified and operational, this section must be moved from Planned to Active and the `BE-01-ARCHITECTURE.md` updated accordingly.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **VERIFIED (2026-05-29 — Enterprise Documentation Generation):**
  - All deploy command sequences verified against `deploy.yml`, `auto_deploy.sh`, `rotate_secrets.sh`.
  - Backup commands verified as working from 2026-05-03 pre-P0 session.
  - Added `sed "s/['\"]//g"` explanation — IMMUTABLE, critical for HikariCP crash prevention.
  - Added OS Memory Recovery sequence and explanation — IMMUTABLE, critical for Exit 137 OOM prevention.
  - Added `docker compose config` validation requirement.
  - Added rotate_secrets.sh step-by-step breakdown.
  - Confirmed `docker cp` as the only verified MinIO backup method (tar permanently fails).
  - Added Cloudflare API Token rotation steps with verified expiry date (2026-08-26).