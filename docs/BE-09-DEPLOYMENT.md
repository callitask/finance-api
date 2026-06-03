# BE-09 — Deployment & Operations Manual

**Stable Version:** `tfin-financeapi-Develop.0.0.0.9`
**Classification:** Internal Reference (Sanitized — No Server IPs, No Credentials)
**Last Verified:** 2026-06-03 — Verified against `deploy.yml`, `auto_deploy.sh`, `rotate_secrets.sh`, `backup.sh`, `restore.sh`, `docker-compose.yml`

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

Two independent engines handle deployments. They are intentionally decoupled and operate in strict sequence — CI compiles, then signals CD; CD orchestrates containers.

### Engine A — GitHub Actions Builder (`deploy.yml`)

**Trigger:** Push to `develop`, `staging`, or `main` (or manual `workflow_dispatch`)

**Global env:** `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true` — forces all marketplace actions onto Node 24 runtime, bypassing GitHub's Node 20 deprecation lockouts.

**Concurrency gate:** `group: production-deployment, cancel-in-progress: true` — a newer push automatically cancels any in-progress older pipeline run.

**Pipeline steps (verified against `deploy.yml`):**
1. **Checkout** (`fetch-depth: 0` — full history required for GPG verification)
2. **GPG Commit Signature Verification** — imports developer's public key via `$GPG_PUBLIC_KEY` secret; runs `git verify-commit HEAD`; unsigned commits → pipeline blocked, deployment denied
3. **Pre-Flight Self-Healing Cleanup** — `sudo rm -f /tmp/gitleaks.tmp || true`; prevents stale temp-file crash on self-hosted runners (which retain filesystem state between runs)
4. **Gitleaks Secret Scan** — blocks pipeline on any credential detection
5. **Set Up JDK 21** (Temurin) + Maven dependency cache
6. **Log Deployment Branch** — records which branch triggered the run
7. **Maven Build** — `./mvnw clean package -DskipTests -B` with `MAVEN_OPTS="-Xmx1024m"` (single-threaded; `-T 1C` prohibited on this runner — OOM risk)
8. **Deploy to Application Folder** (self-hosted runner local copy — no SCP):
   - `cp target/finance-api.war /opt/treishvaam/backend-app.war`
   - `cp -r scripts/* /opt/treishvaam/scripts/` (syncs latest scripts to server path)
   - `cp docker-compose.yml /opt/treishvaam/` (syncs latest compose file)
   - `nohup ./scripts/auto_deploy.sh --force > /opt/treishvaam/deploy_pipeline.log 2>&1 &` (**Fire-and-forget async handoff** — runner detaches immediately)
9. **Guaranteed Flash & Wipe** (`if: always()`) — `cp /opt/treishvaam/.env.template /opt/treishvaam/.env`; executes even if the pipeline fails, ensuring secrets never persist after a crash

**OWASP Dependency Check:** Runs in a **separate, isolated cron-scheduled job** (`dependency-security-scan`) that only fires on `schedule` or `workflow_dispatch` events — NEVER blocks the synchronous `build-and-deploy` job. A 15-minute OWASP scan must not delay deployments.

**⚠️ Why async fire-and-forget (step 8):** Shutting down 17 enterprise containers simultaneously causes a VirtualBox I/O storm that temporarily locks the network adapter. A synchronous runner waiting for `docker compose up` to finish will have its TCP connection severed by this storm, causing a fatal `SIGKILL` mid-deployment that orphans containers (e.g., the database container gets removed but the backend never rebuilds). The `nohup` detachment insulates the CI pipeline entirely from the CD infrastructure shock. Monitor progress via: `sudo tail -f /opt/treishvaam/deploy_pipeline.log`

### Engine B — Server Orchestration (`auto_deploy.sh`)

**Trigger:** Invoked **exclusively** by GitHub Actions Engine A via `nohup ./scripts/auto_deploy.sh --force &`. **Cron has been permanently abolished.** The `scripts/init_automation.sh` script that registered the cron job is now deprecated — do not execute it.

**Runner Service:** The GitHub Actions self-hosted runner (`TREISHVAAM-PROD-RUNNER`) runs as a permanent `systemd` service on the Ubuntu VM. It auto-starts on reboot and receives the Engine A trigger signal over the established runner connection.

**Logic flow (verified against `auto_deploy.sh`):**

1. **Concurrency lock** — PID-verified lockfile at `/tmp/treishvaam_deploy.lock`; if a deployment is already running, exits immediately (safe for rapid successive pushes)
2. **Force flag** — `--force` argument bypasses the Git timestamp diff check; used by GitHub Actions to guarantee execution even if no code files changed (e.g., script-only updates)
3. **Pre-flight permission repair** — `sudo chown -R $(id -u):$(id -g) .git scripts docker-compose.yml` (host-native OS command — never Docker-based; Docker alpine spawn caused storage daemon deadlocks under heavy I/O)
4. **Branch intelligence** — `git fetch --all`, compares timestamps of `origin/main`, `origin/staging`, `origin/develop`; most recent commit's branch wins; `git reset --hard origin/$TARGET_BRANCH`
5. **Flash & Wipe secret injection** (inline — does not call `load_secrets.sh`):
   - Authenticates with Infisical via Machine Identity Token (Universal Auth): `infisical login --method=universal-auth`
   - Exports secrets: `infisical export --projectId ... --env prod --format dotenv`
   - **Double-pass boundary quote extractor** (not global strip): `sed 's/\r//g' | sed -E "s/='(.*)'$/=\1/" | sed -E 's/="(.*)"$/=\1/'` — removes only surrounding Infisical-added quotes; preserves internal `#`, `$`, special chars in passwords; global stripping previously caused Redis/MariaDB NOAUTH crashes
   - OS shadow-kill: `unset` all template variable names from active shell to prevent runner environment from overriding `.env` values
6. **Remove dead containers** — `docker rm -f $(docker ps -f "status=dead" -q)` — surgical cleanup only
7. **OS memory recovery** — `sudo sync && echo 3 | sudo tee /proc/sys/vm/drop_caches` — evicts Linux page cache to free RAM for JVM startup
8. **IPv6 disable** — `sudo sysctl -w net.ipv6.conf.all.disable_ipv6=1` — prevents Docker registry pull timeouts on the VM's unreliable IPv6 stack
9. **Swap management** — provisions persistent 4 GB `/swapfile` if absent (`dd`, `mkswap`, `swapon`, `/etc/fstab`); activates existing swap otherwise; provides RAM safety net for dual-replica JVM boot
10. **Rolling builder cache prune** — `docker builder prune --filter until=168h -f` — rolling 7-day retention; avoids destroying useful cache while reclaiming stale layers
11. **Surgical deadlock breaker** — `docker rm -f $(docker ps -q -f "status=restarting" -f "status=dead" -f "status=exited")` — removes crashed containers blocking the dependency tree without mass-shutting the healthy stack
12. **Smart efficient global rebuild** — `docker compose up -d --build --remove-orphans`; Docker hashes current containers against new artifacts and only restarts what changed; stateful services (MariaDB, Redis, Keycloak) remain untouched; `--build` recompiles non-Java auxiliary containers (ZKP Go binary, backup service) only if their source changed
13. **Surgical application restart** — `docker compose restart backend nginx`; ensures the backend picks up the freshly mounted `.war` artifact and OpenResty reloads its config
14. **Stabilization wait** — `sleep 10` — allows container healthchecks to pass before secret wipe
15. **Security wipe** — `cp "$TEMPLATE_FILE" "$ENV_FILE"` — `.env` restored to credential-free template; high-value secrets removed from disk; containers retain secrets exclusively in-memory
16. **Image layer prune** — `docker image prune -f`

**Why double-pass sed (not global strip):**
The original `sed "s/['\"]//g"` approach stripped ALL quote characters from the entire `.env`, including quotes that were part of passwords containing `#` or `$`. Docker Compose's YAML parser then interpreted the unquoted `#` as a comment terminator, silently truncating `REDIS_PASSWORD` and causing widespread `NOAUTH` crash loops. The boundary-only approach (`sed -E "s/='(.*)'$/=\1/"`) surgically removes only the surrounding Infisical-added wrapper quotes while leaving internal password content completely intact.

---

## 3.5. Self-Hosted Runner — TREISHVAAM-PROD-RUNNER (systemd)

The GitHub Actions self-hosted runner is the physical bridge between the GitHub-hosted CI environment and the Ubuntu VM's CD environment. It must be healthy for any deployment to succeed.

### Runner Identity

| Property | Value |
| :--- | :--- |
| **Runner Name** | `TREISHVAAM-PROD-RUNNER` |
| **Host** | Ubuntu VirtualBox VM |
| **Run Mode** | `systemd` service (auto-starts on VM reboot) |
| **Location** | `/opt/treishvaam/actions-runner/` (or equivalent runner install path) |

### Runner Health Checks

```bash
# SSH to Ubuntu VM, then:

# Check if runner service is active
sudo ./svc.sh status
# or: systemctl status actions.runner.*.service

# Check runner logs
journalctl -u actions.runner.*.service -n 100 --no-pager
```

### Runner Recovery (If Runner Goes Dead)

```bash
# On Ubuntu VM:
# Stop the zombie service
sudo ./svc.sh stop

# Clear stale credentials/config (only if authentication is broken)
rm -f .runner .credentials .credentials_rsaparams

# Re-register with GitHub (requires a fresh registration token from GitHub → Settings → Actions → Runners → New self-hosted runner)
./config.sh --url https://github.com/callitask/finance-api --token <REGISTRATION_TOKEN> --name TREISHVAAM-PROD-RUNNER

# Re-install and start as systemd service
sudo ./svc.sh install
sudo ./svc.sh start
```

**Clock drift warning:** If the runner consistently fails authentication, the VM clock may have drifted. Fix before re-registering:
```bash
# Force NTP sync
sudo timedatectl set-ntp true
sudo systemctl restart systemd-timesyncd
timedatectl status  # confirm clock is accurate
```

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

- **EDITED (2026-06-03 — CI/CD Architectural Overhaul Documentation):**
  - **Section 3 Engine A — Completely rewritten** to match actual `deploy.yml`: correct step order (GPG first, then Gitleaks, then build), removed phantom `mvn test` and `mvn spotless:check` steps from the pipeline (spotless is developer-side pre-commit only, not in CI), corrected "SCP" to local file copy (self-hosted runner operates on same machine), documented async fire-and-forget nohup handoff, documented guaranteed Flash & Wipe (`if: always()`), documented Node 24 global env and concurrency gate, documented pre-flight self-healing cleanup.
  - **Section 3 Engine B — Completely rewritten**: Changed trigger from "cron every minute" to "event-driven via GitHub Actions" (cron was permanently abolished). Expanded logic flow from 6 simplified steps to the full 16-step verified sequence: concurrency lock, force flag, permission repair (host-native chown), branch intelligence, inline Flash & Wipe with double-pass boundary sed, OS shadow-kill, dead container removal, memory recovery (drop_caches), IPv6 disable, swap management (4GB), rolling builder cache prune, surgical deadlock breaker, smart global rebuild (`docker compose up -d --build --remove-orphans`), surgical application restart (`docker compose restart backend nginx`), stabilization wait, security wipe, image prune.
  - **Section 3 — Corrected sed explanation**: Old `sed "s/['\"]//g"` (global strip) was replaced by double-pass boundary extractor in `auto_deploy.sh`. Documented why the global strip caused Redis/MariaDB NOAUTH crashes via Docker Compose YAML parser misinterpretation of unquoted `#` as comment terminators.
  - **Section 3.5 ADDED — TREISHVAAM-PROD-RUNNER**: New section documenting the systemd-installed self-hosted runner, health check commands, recovery procedure, and clock drift warning.
  - **Header version updated** to 0.0.0.9.
  - **Strategic Pivot Note:** `scripts/init_automation.sh` is now DEPRECATED. It still installs the cron job that was deliberately abolished. Future AI must never instruct the user to run `init_automation.sh` — it will re-install the broken polling model. The script is retained in the repo as historical reference only.