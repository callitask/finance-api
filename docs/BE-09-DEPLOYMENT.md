# 09 — Deployment & Operations Manual

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized — No Server IPs, No Credentials)

---

## 1. Infrastructure Overview

The backend runs on an **Ubuntu Server (VirtualBox VM)** using Docker Compose. The application codebase and Git version control reside on the **developer's Windows host machine**. These are two distinct environments:

- **Windows Host:** All `git`, `mvn`, `npm`, and `npx wrangler` commands run here
- **Ubuntu VM:** All Docker containers, databases, and reverse proxies run here. No host-level CLI tools (mysqldump, redis-cli, mc) are installed — all data operations use `docker exec` or `docker cp` against running containers

---

## 2. Multi-Branch Git Strategy

| Branch | Role | Behavior |
| :--- | :--- | :--- |
| `develop` | Active development | Daily work; auto-deployed to dev environment immediately on push |
| `staging` | Release candidate | Feature-complete, tested "golden copy"; stable restore point |
| `main` | Production | Locked, public-facing stable release |

---

## 3. Dual-Engine Automation Architecture

Two separate engines handle deployment. They are intentionally decoupled.

### Engine A — The Builder (GitHub Actions)
- **File:** `.github/workflows/deploy.yml`
- **Triggers:** Push to `develop`, `staging`, or `main`
- **Responsibilities:**
  1. Java 21 setup + Maven dependency cache
  2. Gitleaks secret scan (blocks pipeline on credential leakage)
  3. GPG commit signature verification (`git verify-commit`) — unsigned commits are rejected
  4. Unit tests (`mvn test`)
  5. Build (`mvn clean package`)
  6. Spotless code formatting check (`mvn spotless:check`)
  7. OWASP Dependency Check (runs in a **separate, isolated cron-scheduled job** — never blocks the synchronous build job)
  8. SCP artifact to Ubuntu VM
  9. Signal server watchdog to restart

### Engine B — The Watchdog (Auto-Deploy Script)
- **File:** `scripts/auto_deploy.sh`
- **Location:** Runs on the Ubuntu VM via Git Runner/cron
- **Logic Flow:**
  1. Compares timestamps of `origin/develop`, `origin/staging`, `origin/main` — most recent commit wins
  2. Pulls changes to non-compiled files (Nginx/OpenResty configs, Python market engine scripts, Docker configs)
  3. Executes the **Flash & Wipe** secret injection sequence via Infisical
  4. Runs `docker compose up -d` — zero-downtime rolling update
  5. OS cache flush to reclaim RAM before JVM startup

**NEVER instruct the user to SSH into the server to pull code or restart containers.** All deployments flow exclusively through `git push` → GitHub Actions → Watchdog.

---

## 4. Flash & Wipe Secret Management

Secrets never persist on disk. The lifecycle per deployment:

1. `auto_deploy.sh` authenticates with Infisical using a Machine Identity Token (Universal Auth)
2. Infisical exports all secrets to a temporary `.env` file
3. `docker compose up -d` reads the `.env` file
4. **Immediately after container startup,** the `.env` file is sanitized — high-value secrets removed

**CRITICAL Docker Compose `.env` rule:** Values in `.env` files **must NEVER be wrapped in single quotes (`'`) or double quotes (`"`)**. Quoted values cause HikariCP to receive literal quote characters as part of the connection string, resulting in a fatal `Failed to determine suitable jdbc url` crash loop.

**CRITICAL YAML array syntax rule:** All `command:` entries in `docker-compose.yml` that contain passwords or special characters (`!`, `$`, `~`) must use YAML array syntax (not shell string syntax) to prevent shell expansion corruption.

---

## 5. Deployment Commands by Repository

### Backend Repository
**Local Path:** `F:\BACKEND PROJECG\finance-api\finance-api`
**Branch:** `develop`

```powershell
mvn spotless:apply
git checkout develop
git add .
git commit -m "commit-message"
git push origin develop
```

### Finance Frontend Repository (with Worker)
**Local Path:** `C:\Users\7303150607\OneDrive\Desktop\PrOJEct\treishvaam-finance-frontend`
**Branch:** `main`

**Only run the wrangler deploy steps if `worker/worker.js` or `wrangler.toml` was modified:**
```powershell
cd "C:\Users\7303150607\OneDrive\Desktop\PrOJEct\treishvaam-finance-frontend\worker"
npx wrangler deploy
cd ..
git checkout main
git add .
git commit -m "commit-message"
git push origin main
```

### Agro Frontend Repository (with Worker)
**Local Path:** `F:\treishvaamgroup\treishvaam-agro-frontend\treishvaam-agro-frontend`
**Branch:** `develop`

**Only run the wrangler deploy steps if `worker/worker.js` or `wrangler.toml` was modified:**
```powershell
cd "F:\treishvaamgroup\treishvaam-agro-frontend\treishvaam-agro-frontend\worker"
npx wrangler deploy
cd ..
git checkout develop
git add .
git commit -m "commit-message"
git push origin develop
```

### Parent Frontend Repository
**Local Path:** `F:\treishvaamgroup\treishvaamgroup-frontend`
**Branch:** `production-deploy`

```powershell
git checkout production-deploy
git add .
git commit -m "commit-message"
git push origin production-deploy
```

---

## 6. CI/CD Integrity Standards

| Standard | Enforcement |
| :--- | :--- |
| **GPG Commit Signing** | All commits to `main`/`develop` must be GPG-signed. CI rejects unsigned commits via `git verify-commit` |
| **Secret Scanning** | Gitleaks runs on every pipeline. Any credential detected = pipeline failure |
| **Spotless Formatting** | `mvn spotless:apply` must be run before every commit. CI runs `mvn spotless:check` |
| **OWASP Dependency Check** | Isolated cron job (never blocks synchronous build) |
| **npm ci** | Cloudflare Pages uses `npm ci` — `package-lock.json` must be committed alongside `package.json` always |
| **Content Integrity** | All published blog posts are HMAC-SHA256 signed; mismatches trigger critical alerts |

---

## 7. Verified Backup Commands

**These exact commands were verified on 2026-05-03. Do not modify.**

### MariaDB Backup
```bash
docker exec -i treishvaam-db mysqldump -u root -p --all-databases \
  --single-transaction --routines --triggers | gzip | \
  sudo tee /backup/mariadb_$(date +%Y%m%d_%H%M%S).sql.gz > /dev/null
```
Verify: `sudo ls -lh /backup/mariadb*.sql.gz`

### Redis Backup
```bash
docker exec -i treishvaam-redis redis-cli BGSAVE
sudo docker cp treishvaam-redis:/data/dump.rdb /backup/redis_$(date +%Y%m%d).rdb
```

### MinIO Backup
```bash
sudo docker cp treishvaam-minio:/data /backup/minio_data_$(date +%Y%m%d)
```
**NOTE:** The `tar` method fails — the minio-data directory does not exist on the host. `docker cp` is the only verified working approach.

### Git Tag Checkpoint (run on Windows Host inside backend repo)
```powershell
git tag -a backup-pre-change -m "Pre-change backup checkpoint"
git push origin --tags
```

---

## 8. Observability Access — SSH Tunnel (Zero-Trust)

Grafana and RabbitMQ UIs are **never exposed to the external network**. Access is strictly via SSH Local Port Forwarding:

```bash
ssh -L 3001:localhost:3001 -L 15672:localhost:15672 vboxuser@<SERVER_IP>
```

Once the tunnel is active:
- **Grafana Mission Control:** `http://localhost:3001`
- **RabbitMQ Management:** `http://localhost:15672`

**Log Query in Grafana/Loki:** `{job="varlogs"}` — all application logs are tagged with `tenantId` via MDC for per-tenant filtering.

---

## 9. Zero-Downtime Secret Rotation

Key rotation (e.g., JWT secret, encryption keys) is handled via `scripts/rotate_secrets.sh` which:
1. Updates secrets in Infisical
2. Triggers a Docker Compose rolling restart (`docker compose up -d --no-deps backend`)
3. Validates health endpoint before decommissioning old replica

**Cloudflare API Token rotation:** The token has a 90-day TTL. The `SecretKeyRotationDue` Grafana alert fires 7 days before expiry. On expiry trigger, regenerate via the Cloudflare dashboard and re-inject via `npx wrangler secret put AEGIS_EDGE_SECRET`.

---

## 10. Network Architecture — Docker Container Map

All containers communicate exclusively on the internal `treish_net` bridge network. The **only** container with ports exposed to the host is `treishvaam-nginx` (OpenResty) on 80/443. All other port bindings have been removed.

| Container | Image | External Access |
| :--- | :--- | :--- |
| `treishvaam-nginx` | `openresty/openresty:alpine` | ✅ 80/443 |
| `treishvaam-tunnel` | `cloudflare/cloudflared` | Outbound tunnel only |
| `treishvaam-grafana` | `grafana/grafana:latest` | SSH tunnel only (127.0.0.1:3001) |
| `treishvaam-rabbitmq-mgmt` | (RabbitMQ mgmt) | SSH tunnel only (127.0.0.1:15672) |
| All others | Various | ❌ Internal only |

---

## 11. Hybrid-Cloud Migration (Planned — Not Yet Active)

A future migration to Oracle Cloud Infrastructure (OCI) Always Free tier is planned as a Hybrid-Cloud Architecture:

- **OCI:** Primary Master Node (always-on)
- **VirtualBox:** Intermittent Replica (syncs via TLS-encrypted MariaDB GTID replication when powered on)
- **Sync Gate:** Local server must not serve traffic until 100% synced with OCI Master
- **IaC:** Terraform + Ansible for zero-state restoration
- **DR:** Automated backups to OCI Object Storage; single-script restoration via Liquibase + Docker Compose

This section will be moved from Planned to Active once the OCI instance is fully provisioned and synced.
