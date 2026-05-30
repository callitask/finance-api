# BE-12 — Local Development Setup Guide

**Classification:** Internal Reference (Sanitized — No Credentials)
**Applies To:** Backend (`finance-api`) + Finance Frontend (`treishvaam-finance-frontend`)
**Verified Against:** `docker-compose.yml`, `auto_deploy.sh`, `load_secrets.sh`, `application-dev.properties`, `pom.xml`, `package.json`

---

## Architecture Split — Critical Understanding

This system operates across **two physically separate machines**. Confusing which commands run where is the most common source of operational errors.

```
Developer Windows Host Machine
├── Codebase + Git version control  (F:\BACKEND PROJECG\finance-api\finance-api)
├── Maven (mvn) commands            ← Run here
├── Git commands                    ← Run here ONLY
├── npm / npx wrangler commands     ← Run here
└── SSH tunnel (for observability)  ← Run here

Ubuntu VirtualBox VM (vboxuser@192.168.29.111 or 192.168.56.101)
├── Docker Engine
├── All containers (MariaDB, Redis, Elasticsearch, MinIO, RabbitMQ, etc.)
├── docker compose commands         ← Run here ONLY
└── auto_deploy.sh watchdog         ← Runs here automatically via cron
```

**RULE:** Never run `git` commands on the Ubuntu VM. Never run `docker` commands on the Windows Host unless Docker Desktop is installed separately for local dev only.

---

## Prerequisites

### Windows Host

| Tool | Purpose | Notes |
| :--- | :--- | :--- |
| **Java 21 (Temurin LTS)** | Maven build | Must be JDK, not JRE |
| **Maven 3.9+** | Build system | Or use `mvnw` wrapper |
| **Node.js 20 LTS** | Next.js frontend builds | Required for `npm ci`, `npx wrangler` |
| **Git (with GPG)** | Version control + signed commits | All commits to `develop`/`main` must be GPG-signed |
| **GnuPG** | Commit signing | `gpg --full-generate-key` required |
| **Infisical CLI** | Secret injection (for local dev) | Optional for dev; required for prod deploy |
| **Wrangler CLI** | Cloudflare Worker deploys | `npm install -g wrangler` or `npx wrangler` |

**GPG Setup (One-Time):**
```powershell
# Generate key
gpg --full-generate-key

# Configure git to sign all commits
git config --global commit.gpgsign true
git config --global user.signingkey <YOUR_KEY_ID>
```

### Ubuntu VM

| Tool | Purpose | Notes |
| :--- | :--- | :--- |
| **Docker Engine 24+** | Container runtime | Not Docker Desktop |
| **Docker Compose v2** | Service orchestration | Use `docker compose` (v2 syntax), NOT `docker-compose` (v1) |
| **Infisical CLI** | Secret injection at deploy time | Installed and authenticated via Machine Identity |
| **Python 3.x** | Market data updater | Used by `scripts/market_data_updater.py` |
| **Git** | Code pull by watchdog | Used by `auto_deploy.sh` only |

---

## Backend Local Development

### 1. Clone Repository

```powershell
# Windows Host only
cd "F:\BACKEND PROJECG\finance-api"
git clone https://github.com/callitask/finance-api.git finance-api
cd finance-api
git checkout develop
```

### 2. Configure Local Environment

Copy the example env file and populate local dev values:

```powershell
copy .env.dev.example .env
```

Edit `.env` with your local dev values. **Never wrap values in quotes** — HikariCP will crash with literal quotes in the JDBC URL.

Key dev properties are in `src/main/resources/application-dev.properties`. The active profile is set via:
```
SPRING_PROFILES_ACTIVE=dev
```

### 3. Build

```powershell
# Apply code formatting first (required before commit)
mvn spotless:apply

# Build (skip tests for speed during dev)
mvn clean package -DskipTests

# Full build with tests
mvn clean package
```

**Memory constraint (self-hosted runner):** If running Maven on the same machine as the CI runner, export `MAVEN_OPTS="-Xmx1024m"` to prevent OOM kills. Do not use `-T 1C` parallel builds unless the machine has >8GB RAM.

### 4. Run Locally

For local backend development, you need the data layer running. Start only the data services:

```bash
# On Ubuntu VM — start only data layer for local dev
docker compose up -d treishvaam-db treishvaam-redis treishvaam-elastic treishvaam-minio treishvaam-rabbitmq
```

Then run the Spring Boot app from the Windows Host using your IDE (IntelliJ / Eclipse) with the `dev` profile, or:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Finance Frontend Local Development

### 1. Clone Repository

```powershell
cd "C:\Users\7303150607\OneDrive\Desktop\PrOJEct"
git clone <repo-url> treishvaam-finance-frontend
cd treishvaam-finance-frontend
git checkout main
```

### 2. Install Dependencies

```powershell
# MUST use npm ci (not npm install) for reproducible builds — Cloudflare Pages uses npm ci
npm ci
```

**Critical:** Always commit `package-lock.json` alongside `package.json`. If they fall out of sync, the Cloudflare Pages Edge build will crash.

### 3. Configure Environment

Copy the example env and populate:

```powershell
copy .env.example .env
```

Minimum required vars for local dev:

```
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_GA_MEASUREMENT_ID=     # leave blank locally
NEXT_PUBLIC_ENFORCE_STRICT_PRIVACY=false
```

### 4. Run Dev Server

```powershell
npm run dev
# → http://localhost:3000
```

**Note:** The Next.js dev server runs in Node.js mode locally. The production build runs in Cloudflare Edge Runtime. Some edge-specific behaviors (e.g., `Buffer` not defined) only manifest in production builds. Test production builds locally with:

```powershell
npm run build
```

---

## Worker Local Testing

The Cloudflare Worker (`worker/worker.js`) runs in a Cloudflare V8 isolate, not Node.js. Local testing uses Wrangler's dev server:

```powershell
cd "C:\Users\7303150607\OneDrive\Desktop\PrOJEct\treishvaam-finance-frontend\worker"
npx wrangler dev
```

**Note:** Worker Secrets (`AEGIS_EDGE_SECRET`, `BACKEND_API_URL`) are not available locally unless you use `--local` mode with a `.dev.vars` file (which must never be committed to git).

---

## Deploying Changes

### Backend

```powershell
# Windows Host — BACKEND REPOSITORY
cd "F:\BACKEND PROJECG\finance-api\finance-api"
mvn spotless:apply
git checkout develop
git add .
git commit -m "feat: <description>"   # Commit will be GPG-signed automatically
git push origin develop
# → GitHub Actions triggers → self-hosted runner builds → auto_deploy.sh watchdog picks up → containers restart
```

### Finance Frontend (Next.js only — no Worker change)

```powershell
cd "C:\Users\7303150607\OneDrive\Desktop\PrOJEct\treishvaam-finance-frontend"
git checkout main
git add .
git commit -m "feat: <description>"
git push origin main
# → Cloudflare Pages auto-builds and deploys
```

### Finance Frontend + Worker (when worker.js changed)

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

**⚠️ Deploy the Worker ONLY if `worker/worker.js` or `worker/wrangler.toml` was modified.** Unnecessary Worker deploys add risk of misconfiguration.

---

## Observability Access (SSH Tunnel)

Grafana and RabbitMQ UIs are never publicly exposed. Access them via SSH local port forwarding from the Windows Host:

```powershell
ssh -L 3001:localhost:3001 -L 15672:localhost:15672 vboxuser@192.168.29.111
```

Once the tunnel is active:
- **Grafana:** http://localhost:3001
- **RabbitMQ Management:** http://localhost:15672

Credentials are in the `.env` file on the Ubuntu VM (Infisical-injected). Never expose these ports to `0.0.0.0`.

---

## Data Operations — Docker Exec Required

The Ubuntu VM has **no host-level CLI tools** (no `mysqldump`, no `redis-cli`, no `mc`). All data operations must use `docker exec` or `docker cp`:

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

### MinIO Backup (docker cp ONLY — tar method fails)
```bash
sudo docker cp treishvaam-minio:/data /backup/minio_data_$(date +%Y%m%d)
```

### Git Tag Checkpoint (Windows Host)
```powershell
git tag -a backup-pre-p0 -m "Pre-change P0 backup checkpoint"
git push origin --tags
```

---

## Pre-Flight Checklist Before Any Change

Before implementing any modification, verify internally:

- [ ] Did I read the relevant IMMUTABLE CHANGE HISTORY blocks?
- [ ] Does my change avoid any strategy listed as FAILED in AI-CONTEXT history?
- [ ] If I added a secret/env var — did I route it to the correct vault (Infisical / CF Worker Secrets / CF Pages Env Vars)?
- [ ] Is my code output 100% complete — no truncation, no `// ... rest of code` placeholders?
- [ ] Have I verified the proposed change against actual code files (not docs)?
- [ ] Will this change break sitemap generation, SEO routing, or Worker fallback behavior?
- [ ] Does this change respect the Zero-Trust architecture (no hardcoded origins, secrets, or URLs)?

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **ADDED (2026-05-29 — Enterprise Documentation Generation):**
  - Created `BE-12-LOCAL-SETUP.md` from scratch.
  - Why: This document was entirely absent from the repository. The setup procedures were scattered across `README.md`, `SECRETS.md`, `BE-06-INFRA-DEVOPS.md`, and `BE-09-DEPLOYMENT.md`. A single authoritative local setup guide is a critical enterprise gap for onboarding and AI session continuity.
  - Source of truth: `docker-compose.yml`, `auto_deploy.sh`, `load_secrets.sh`, `application-dev.properties`, `.env.dev.example`, `deploy.yml`, `package.json`, `wrangler.toml`.