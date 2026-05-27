# BE-06 — Infrastructure, DevOps & CI/CD

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized — No Credentials, No Server IPs)

---

## 1. Infrastructure Overview

The backend runs on a **Ubuntu Server (VirtualBox VM)** using **Docker Compose v3.8**. All services are isolated on a private bridge network (`treish_net`). The developer's Windows host machine hosts the codebase and runs all Git/Maven/npm commands.

```
Developer Windows Machine
    ├── git / mvn / npm / npx wrangler   ← All code commands run here
    └── SSH access (for observability tunnel only)

Ubuntu VirtualBox VM
    └── Docker Engine
        └── docker-compose.yml → treish_net (bridge)
            ├── OpenResty (nginx)    → PORTS 80:80, 443:443  [ONLY exposed container]
            ├── Grafana              → PORT 127.0.0.1:3001:3000
            ├── RabbitMQ Management  → PORT 127.0.0.1:15672:15672
            ├── ZKP Service          → PORT 127.0.0.1:9090:9090
            ├── Canary Server        → PORT 127.0.0.1:8089:80
            └── All other services   → Internal only (no host binding)
```

---

## 2. Docker Compose — Full Container Reference

**Network:** `treish_net` (bridge driver) — all containers communicate by service name DNS.

### 2.1 Data Layer

| Service | Container Name | Image | Volume | Notes |
|:---|:---|:---|:---|:---|
| **MariaDB** (App DB) | `treishvaam-db` | `mariadb:10.6` | `./data/mariadb` | TDE via `config/mariadb/encryption.cnf`. Docker secret: `mariadb_encryption_key`. Healthcheck via `mysqladmin ping`. FLUSHALL/FLUSHDB/DEBUG/MONITOR Redis commands renamed to `""` |
| **MariaDB** (Keycloak DB) | `treishvaam-keycloak-db` | `mariadb:10.6` | `./data/keycloak-db` | Separate isolated DB for Keycloak SSO data |
| **Redis** | `treishvaam-redis` | `redis:7-alpine` | `./data/redis` | Password auth required. FLUSHALL/FLUSHDB/CONFIG/DEBUG/MONITOR commands renamed (disabled) for Zero-Trust. `save "60" "1"` persistence. `stop-writes-on-bgsave-error no` |
| **MinIO** | `treishvaam-minio` | `minio/minio` | `./data/minio` | Console on internal port 9001. Stores media files and materialized HTML |
| **Elasticsearch** | `treishvaam-elastic` | `elasticsearch:8.17.0` | `./data/elastic` | Single-node, xpack security enabled, SSL disabled (internal-only traffic), JVM: `-Xms512m -Xmx512m` |

### 2.2 Identity & Messaging

| Service | Container Name | Image | Notes |
|:---|:---|:---|:---|
| **Keycloak** | `treishvaam-keycloak` | `quay.io/keycloak/keycloak:25.0.0` | MariaDB backend. Realm auto-imported from `config/keycloak/realm-export.json`. `start-dev --import-realm`. HTTP enabled (TLS terminated by OpenResty). Metrics and health enabled |
| **RabbitMQ** | `treishvaam-rabbitmq` | `rabbitmq:3.12-management` | Management UI: `127.0.0.1:15672` (SSH tunnel only). Definitions loaded via `RABBITMQ_SERVER_DEFINITION_FILE`. Healthcheck: `rabbitmq-diagnostics -q check_running`. Backend boot depends on `service_healthy` |

### 2.3 Application Layer

| Service | Container Name | Image | Notes |
|:---|:---|:---|:---|
| **Backend** | (dynamic — 2 replicas) | `ghcr.io/callitask/finance-api:latest` (also builds locally via `build: .`) | JVM: `-Xmx768m -Xms512m`. 2 replicas. Hardware entropy mapped: `/dev/random`, `/dev/urandom`. Healthcheck: Python3 TCP socket check on port 8080. Start period: 160s. Waits on: MariaDB, Redis, Elasticsearch, RabbitMQ (`service_healthy`), Tempo, Keycloak |
| **OpenResty** | `treishvaam-nginx` | `openresty/openresty:alpine` | **Only container exposing ports 80/443 to host.** Mounts Lua WAF scripts and Nginx config. Healthcheck: `curl /robots.txt` |

### 2.4 Security Layer

| Service | Container Name | Image | Notes |
|:---|:---|:---|:---|
| **Cloudflare Tunnel** | `treishvaam-tunnel` | `cloudflare/cloudflared` | Outbound tunnel only. No inbound ports opened. Token injected via env var |
| **AEGIS ZKP Service** | `aegis-zkp-service` | `treishvaam/aegis-zkp-service:latest` (local build: `./aegis/zkp-service`) | Compiled Go distroless binary. gRPC port 9090 bound to `127.0.0.1` only. **Strict 256MB memory limit** via `deploy.resources`. TCP netcat healthcheck on 9090 |
| **Envoy Sidecar** | `treishvaam-envoy` | `envoyproxy/envoy:v1.29-latest` | Internal L7 proxy for gRPC routing. Config: `config/envoy.yaml` |
| **AEGIS Canary Server** | `aegis-canary-server` | `thinkst/canarytokens:latest` | L4-ADA deception token management. Bound to `127.0.0.1:8089` |
| **Wazuh Agent** | `wazuh-agent` | `wazuh/wazuh-agent:4.7.3` | HIDS. Runs with `privileged: true` and `pid: host`. Signals feed `HidsIntegrityValidator` in BCSM |

### 2.5 Observability Layer

| Service | Container Name | Image | Notes |
|:---|:---|:---|:---|
| **Grafana** | `treishvaam-grafana` | `grafana/grafana:latest` | Port `127.0.0.1:3001:3000` — SSH tunnel only. Provisioned datasources + dashboards + alerting via mounted YAML files |
| **Prometheus** | `treishvaam-prometheus` | `prom/prometheus:latest` | Scrapes Spring Boot actuator metrics. Config: `config/prometheus.yml` |
| **Loki** | `treishvaam-loki` | `grafana/loki:2.9.2` | Log aggregation. Config: `config/loki-config.yml` |
| **Promtail** | `treishvaam-promtail` | `grafana/promtail:2.9.2` | Ships app logs from `./logs` to Loki. Config: `config/promtail-config.yml` |
| **Tempo** | `treishvaam-tempo` | `grafana/tempo:latest` | Distributed tracing (Zipkin endpoint). Config: `config/tempo.yaml`. Backend sends traces to `http://tempo:9411/api/v2/spans` |

### 2.6 Utility Services

| Service | Container Name | Notes |
|:---|:---|:---|
| **Backup Service** | `treishvaam-backup` | Built from `./backup/`. Encrypts and pushes MariaDB dumps + MinIO data to internal S3 bucket. Uses `BACKUP_ENCRYPTION_KEY` |

### 2.7 Docker Compose Critical Rules

1. **YAML Array Syntax (NON-NEGOTIABLE):** Any `command:` entry containing passwords or special characters (`!`, `$`, `~`) MUST use YAML array syntax. Shell string syntax causes expansion corruption
2. **`.env` Quote Rule:** Values in `.env` files must NEVER be wrapped in single or double quotes. Quoted values cause HikariCP to receive literal quote characters → fatal `Failed to determine suitable jdbc url` crash
3. **`$$` Escaping in Healthchecks:** Use `$$VARIABLE` (double-dollar) in `test:` arrays to prevent Docker Compose shell expansion from consuming variables before the command executes
4. **Docker Secret:** `mariadb_encryption_key` is sourced exclusively from the environment variable `MARIADB_ENCRYPTION_KEY` — not from a file
5. **Local Build Fallback:** `backend` service has `build: .` as a fallback for `docker compose up --build`, bypassing ghcr.io registry authentication after cache prune

---

## 3. OpenResty / Nginx Configuration

**Config file:** `nginx/conf.d/default.conf`
**Lua scripts:** `nginx/lua/`
**ModSecurity:** `nginx/modsecurity/whitelist.conf`

OpenResty replaces plain Nginx to enable Lua script execution at the WAF layer. Standard Nginx directives that conflict with Lua execution are prohibited.

### 3.1 AEGIS JA3 Fingerprinting (`nginx/lua/aegis_ja3.lua`)

- Captures TLS ClientHello JA3 fingerprint of every connection
- Resolves Cloudflare real IP from `CF-Connecting-IP` header
- Injects `X-JA3-Fingerprint: <hash>` header into the upstream request to Tomcat
- `X-Real-IP` is set to the true client IP (not Cloudflare's proxy IP)
- The backend `AegisMainFilter` and `BehavioralValidator` use this header for threat classification

### 3.2 Routing Logic

- All HTTP (port 80) traffic redirected to HTTPS (port 443)
- HTTPS traffic proxied to Spring Boot backend on internal `treishvaam-backend:8080` (never exposed)
- `/auth/**` proxied to Keycloak on `treishvaam-keycloak:8080`
- Static uploads served directly from the `./uploads` volume (bypasses Java I/O)
- Static sitemaps served directly from `./sitemaps` volume

### 3.3 ModSecurity (OWASP CRS)

- Custom whitelist at `nginx/modsecurity/whitelist.conf` allows legitimate API patterns that OWASP CRS would incorrectly flag
- Protects against SQLi, XSS, and protocol-level attacks at the edge before requests reach Tomcat

---

## 4. Scripts Reference

All scripts are located in `scripts/` and `backup/` directories.

| Script | Purpose | Run Location |
|:---|:---|:---|
| `auto_deploy.sh` | Watchdog: compares branch timestamps, pulls changes, calls `load_secrets.sh`, runs `docker compose up -d` | Ubuntu VM (cron/git runner) |
| `init_automation.sh` | One-time initialization of the automated deployment loop | Ubuntu VM |
| `load_secrets.sh` | Authenticates with Infisical (Universal Auth), exports secrets to temp `.env`, wipes after container startup | Ubuntu VM (called by auto_deploy.sh) |
| `rotate_secrets.sh` | Zero-downtime key rotation: updates Infisical → Docker Compose rolling restart (`--no-deps backend`) | Ubuntu VM |
| `backup/backup.sh` | Executes MariaDB dump + MinIO copy + Redis BGSAVE, encrypts with `BACKUP_ENCRYPTION_KEY`, pushes to S3 | Ubuntu VM (via backup-service container) |
| `backup/restore.sh` | Pulls from S3, decrypts, and restores MariaDB + MinIO + Redis | Ubuntu VM |
| `scripts/market_data_updater.py` | Python market data processor. Invoked via Java `ProcessBuilder`. Uses `decimal.Decimal` for financial precision | Inside backend container (via ProcessBuilder) |
| `verify_seo.sh` | Validates sitemap structure, canonical tags, and robots.txt | Ubuntu VM |
| `sanitize_for_sale.sh` | Strips all credentials, keys, and PII before packaging for external sharing | Developer machine only |

---

## 5. Ansible Provisioning

**File:** `ansible/setup-server.yml`

Automates Ubuntu VM initial provisioning:
- Installs Docker Engine and Docker Compose plugin
- Configures UFW firewall (only ports 22, 80, 443 open)
- Sets up automatic OS security updates
- Creates application directory structure
- Configures cron entries for the auto-deploy watchdog

---

## 6. CI/CD Pipeline (`deploy.yml`)

**File:** `.github/workflows/deploy.yml`
**Triggers:** Push to `develop`, `staging`, or `main`

### Pipeline Stages (Synchronous Build Job)

```
1. Checkout code
2. Set up Java 21 (Temurin) + Maven cache
3. Gitleaks secret scan  ← BLOCKS on any credential detection
4. GPG commit signature verification (git verify-commit HEAD)  ← BLOCKS on unsigned commits
5. mvn test  ← Unit tests
6. mvn clean package  ← Build WAR artifact
7. mvn spotless:check  ← Code formatting gate
8. SCP artifact to Ubuntu VM
9. Signal Watchdog (auto_deploy.sh)
```

### Separate Cron Job (OWASP Dependency Check)

- Runs on a **separate, isolated, cron-scheduled job**
- Never blocks the synchronous `build-and-deploy` job
- Heavy vulnerability scanning (15+ minutes) is intentionally decoupled from deployment speed

### Key CI/CD Constraints

| Constraint | Enforcement |
|:---|:---|
| GPG commit signing | `git verify-commit HEAD` — unsigned commits → pipeline failure |
| Secret scanning | Gitleaks on every push |
| Code formatting | `mvn spotless:check` — unformatted code → pipeline failure |
| `package-lock.json` | Must always be committed alongside `package.json` — Cloudflare Pages uses `npm ci` |
| OWASP check | Isolated cron job only — never in synchronous deploy path |

### `.gitleaks.toml`

Custom Gitleaks configuration that extends the default ruleset with Treishvaam-specific patterns, including Infisical token formats, Cloudflare API token patterns, and AEGIS secret naming conventions.

---

## 7. Observability Configuration Files

| File | Purpose |
|:---|:---|
| `config/prometheus.yml` | Scrape configs: Spring Boot Actuator (`/actuator/prometheus`), Node Exporter, Redis exporter |
| `config/grafana-datasources.yml` | Provisioned datasources: Prometheus, Loki, Tempo |
| `config/grafana-dashboards.yml` | Dashboard provider config pointing to `./dashboards/` directory |
| `config/grafana-alerting.yml` | Alert rules: `HighBackendErrorRate`, `SlowAPIResponse`, `SecretKeyRotationDue` |
| `config/loki-config.yml` | Loki storage config (local filesystem) |
| `config/promtail-config.yml` | Tail `./logs/*.log`, attach `job="varlogs"` label |
| `config/tempo.yaml` | Tempo receiver config (Zipkin protocol on port 9411) |
| `config/envoy.yaml` | Envoy listener + cluster config for gRPC routing to aegis-zkp-service |
| `config/rabbitmq/definitions.json` | RabbitMQ exchanges, queues, bindings, vhosts, users — loaded via `RABBITMQ_SERVER_DEFINITION_FILE` |

### Grafana Access (Zero-Trust SSH Tunnel)

**NEVER expose Grafana or Prometheus to `0.0.0.0`.** Access strictly via:

```bash
ssh -L 3001:localhost:3001 -L 15672:localhost:15672 vboxuser@<SERVER_IP>
```

Then access locally:
- Grafana: `http://localhost:3001`
- RabbitMQ: `http://localhost:15672`

**Loki query label for all application logs:** `{job="varlogs"}`

Logs are tagged with `tenantId` via MDC for per-tenant filtering.

---

## 8. Data Volume Map

All persistent data lives in the `./data/` subdirectory on the Ubuntu VM:

```
./data/
    ├── mariadb/        ← App database (TDE encrypted)
    ├── keycloak-db/    ← Keycloak SSO database
    ├── redis/          ← Redis AOF/RDB persistence
    ├── minio/          ← Object storage (media + materialized HTML)
    ├── elastic/        ← Elasticsearch index data
    ├── grafana/        ← Grafana dashboards + alert state
    └── tempo/          ← Distributed trace data

./logs/                 ← Spring Boot application logs (tailed by Promtail)
./sitemaps/             ← Generated XML sitemap files (served directly by OpenResty)
./uploads/              ← Uploaded media files (served directly by OpenResty)
./scripts/              ← market_data_updater.py and operational scripts
./config/               ← All service config files (mounted read-only)
./nginx/                ← OpenResty config + Lua scripts + ModSecurity whitelist
./aegis/zkp-service/    ← Go source for ZKP microservice (compiled into container)
```

---

## 9. Cloudflare Tunnel

**Container:** `treishvaam-tunnel` (`cloudflare/cloudflared`)

- Provides secure ingress from Cloudflare's network to the internal Docker network
- Eliminates the need to open any firewall ports on the Ubuntu VM for application traffic
- The tunnel terminates inside `treish_net`, routing to `treishvaam-nginx` (OpenResty) on port 80/443
- Token injected via `CLOUDFLARE_TUNNEL_TOKEN` environment variable
- All TLS is terminated at Cloudflare's edge — traffic inside the tunnel to OpenResty is HTTP internally

---

## 10. Secret Management — Infisical Flow

Secrets never persist unencrypted on disk. Full lifecycle:

```
Infisical Cloud (source of truth for all backend secrets)
      │
      ▼ (auto_deploy.sh runs on each deployment)
load_secrets.sh
   └── infisical login --method=universal-auth
   └── infisical export > .env   ← temporary, in-memory
      │
      ▼
docker compose up -d (reads .env via env_file + environment: mapping)
      │
      ▼
.env sanitized (high-value secrets removed from disk)
```

**NEVER commit `.env` to git.** `.env` is in `.gitignore`.

Secret routing rules:
- **Backend secrets** (DB keys, encryption keys, API tokens) → **Infisical** → `.env` → Docker Compose `environment:`
- **Frontend public vars** (Analytics IDs, API URLs, privacy toggle) → **Cloudflare Pages Environment Variables**
- **Edge secrets** (Worker signing key, backend origin URL) → **Cloudflare Worker Secrets** (`npx wrangler secret put`)
