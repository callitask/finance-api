# BE-06 — Infrastructure, DevOps & CI/CD

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized — No Credentials, No Server IPs)
**Last Verified:** 2026-05-29 — All claims verified against `docker-compose.yml`, `deploy.yml`, `auto_deploy.sh`, `load_secrets.sh`, `rotate_secrets.sh`, `backup/backup.sh`, `backup/restore.sh`, `ansible/setup-server.yml`, `config/prometheus.yml`, `config/grafana-alerting.yml`, `config/rabbitmq/definitions.json`, `config/envoy.yaml`, `nginx/conf.d/default.conf`, `nginx/lua/aegis_ja3.lua`

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

**Network:** `treish_net` (bridge driver) — all containers communicate by service-name DNS.

### 2.1. Data Layer

| Service | Container Name | Image | Volume | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **MariaDB** (App DB) | `treishvaam-db` | `mariadb:10.6` | `./data/mariadb` | TDE via `config/mariadb/encryption.cnf`. Docker secret: `mariadb_encryption_key`. Healthcheck: `mysqladmin ping` |
| **MariaDB** (Keycloak DB) | `treishvaam-keycloak-db` | `mariadb:10.6` | `./data/keycloak-db` | Isolated DB for Keycloak SSO data |
| **Redis** | `treishvaam-redis` | `redis:7-alpine` | `./data/redis` | Password auth required. FLUSHALL/FLUSHDB/CONFIG/DEBUG/MONITOR commands renamed to `""` (disabled). `save "60" "1"` persistence. `stop-writes-on-bgsave-error no` |
| **MinIO** | `treishvaam-minio` | `minio/minio` | `./data/minio` | Console on internal port 9001. Stores media files and materialized HTML |
| **Elasticsearch** | `treishvaam-elastic` | `elasticsearch:8.17.0` | `./data/elastic` | Single-node, xpack security enabled, SSL disabled (internal-only traffic). JVM: `-Xms512m -Xmx512m` |

### 2.2. Identity & Messaging

| Service | Container Name | Image | Notes |
| :--- | :--- | :--- | :--- |
| **Keycloak** | `treishvaam-keycloak` | `quay.io/keycloak/keycloak:25.0.0` | MariaDB backend. Realm auto-imported from `config/keycloak/realm-export.json`. `start-dev --import-realm`. HTTP enabled (TLS terminated by OpenResty). Metrics and health enabled |
| **RabbitMQ** | `treishvaam-rabbitmq` | `rabbitmq:3.12-management` | Management UI: `127.0.0.1:15672` (SSH tunnel only). Definitions loaded via `RABBITMQ_SERVER_DEFINITION_FILE`. Healthcheck: `rabbitmq-diagnostics -q check_running`. Backend boot depends on `service_healthy` |

### 2.3. Application Layer

| Service | Container Name | Image | Notes |
| :--- | :--- | :--- | :--- |
| **Backend** | (dynamic — 2 replicas) | `ghcr.io/callitask/finance-api:latest` + `build: .` fallback | JVM: `-Xmx768m -Xms512m`. 2 replicas. Hardware entropy: `/dev/random`, `/dev/urandom` mapped. Healthcheck: Python3 TCP socket check on port 8080. Start period: 160s. Depends on: MariaDB, Redis, Elasticsearch, RabbitMQ, Tempo, Keycloak — all `service_healthy` |
| **OpenResty** | `treishvaam-nginx` | `openresty/openresty:alpine` | **Only container exposing ports 80/443 to host.** Mounts Lua WAF scripts and Nginx config. Healthcheck: `curl /robots.txt` |

### 2.4. Security Layer

| Service | Container Name | Image | Notes |
| :--- | :--- | :--- | :--- |
| **Cloudflare Tunnel** | `treishvaam-tunnel` | `cloudflare/cloudflared` | Outbound tunnel only. No inbound ports opened. Token: `CLOUDFLARE_TUNNEL_TOKEN` |
| **AEGIS ZKP Service** | `aegis-zkp-service` | `treishvaam/aegis-zkp-service:latest` (built from `./aegis/zkp-service`) | Compiled Go distroless binary. gRPC port 9090 bound to `127.0.0.1` only. **Strict 256MB memory limit** via `deploy.resources`. TCP netcat healthcheck on 9090 |
| **Envoy Sidecar** | `treishvaam-envoy` | `envoyproxy/envoy:v1.29-latest` | Internal L7 proxy for gRPC routing to ZKP service. Config: `config/envoy.yaml` |
| **AEGIS Canary Server** | `aegis-canary-server` | `thinkst/canarytokens:latest` | L4-ADA deception token management. `127.0.0.1:8089` |
| **Wazuh Agent** | `wazuh-agent` | `wazuh/wazuh-agent:4.7.3` | HIDS. `privileged: true`, `pid: host`. Signals feed `HidsIntegrityValidator` in BCSM |

### 2.5. Observability Layer

| Service | Container Name | Image | Notes |
| :--- | :--- | :--- | :--- |
| **Grafana** | `treishvaam-grafana` | `grafana/grafana:latest` | Port `127.0.0.1:3001:3000` — SSH tunnel only. Provisioned datasources + dashboards + alerting via YAML |
| **Prometheus** | `treishvaam-prometheus` | `prom/prometheus:latest` | Scrapes Spring Boot actuator metrics at `/actuator/prometheus`. Config: `config/prometheus.yml` |
| **Loki** | `treishvaam-loki` | `grafana/loki:2.9.2` | Log aggregation. Config: `config/loki-config.yml`. Query label: `{job="varlogs"}` |
| **Promtail** | `treishvaam-promtail` | `grafana/promtail:2.9.2` | Ships logs from `./logs` to Loki. Config: `config/promtail-config.yml` |
| **Tempo** | `treishvaam-tempo` | `grafana/tempo:latest` | Distributed tracing (Zipkin endpoint). Config: `config/tempo.yaml`. Backend traces to `http://treishvaam-tempo:9411/api/v2/spans` |

### 2.6. Utility Services

| Service | Container Name | Notes |
| :--- | :--- | :--- |
| **Backup Service** | `treishvaam-backup` | Built from `./backup/`. AES-encrypts MariaDB dumps + MinIO data with `BACKUP_ENCRYPTION_KEY` |

### 2.7. Docker Compose Critical Rules (Non-Negotiable)

1. **YAML Array Syntax:** Any `command:` entry with passwords or special characters (`!`, `$`, `~`) MUST use YAML array syntax — shell string syntax causes expansion corruption
2. **`.env` Quote Rule:** Values must NEVER be wrapped in single or double quotes — HikariCP receives literal quote → fatal `Failed to determine suitable jdbc url` crash → never wrapping is PERMANENT
3. **`$$` Escaping in Healthchecks:** Use `$$VARIABLE` (double-dollar) in `test:` arrays to prevent Docker Compose consuming variables before command executes
4. **Docker Secret:** `mariadb_encryption_key` sourced exclusively from `MARIADB_ENCRYPTION_KEY` environment variable — not a file
5. **Local Build Fallback:** `backend` has `build: .` as fallback for `docker compose up --build` — bypasses ghcr.io registry auth after cache prune

---

## 3. OpenResty / Nginx Configuration

**Config:** `nginx/conf.d/default.conf`
**Lua scripts:** `nginx/lua/` (critically: `aegis_ja3.lua`)
**ModSecurity:** `nginx/modsecurity/whitelist.conf`

OpenResty replaces plain Nginx to enable Lua script execution at the WAF layer. Standard Nginx directives that conflict with Lua execution are **prohibited**.

### 3.1. AEGIS JA3 Fingerprinting (`nginx/lua/aegis_ja3.lua`)

- Captures TLS ClientHello JA3 fingerprint of every connection at the Lua layer
- Resolves Cloudflare real IP from `CF-Connecting-IP` header
- Injects `X-JA3-Fingerprint: <hash>` header into upstream request to Tomcat
- Sets `X-Real-IP` to true client IP (not Cloudflare proxy IP)
- Backend `AegisMainFilter` and `BehavioralValidator` use this header for threat classification

### 3.2. Routing Logic

- All HTTP (port 80) → HTTPS (port 443) redirect
- HTTPS → Spring Boot backend at `treishvaam-backend:8080` (internal — never host-exposed)
- `/auth/**` → Keycloak at `treishvaam-keycloak:8080`
- Static uploads served from `./uploads` volume (bypasses Java I/O)
- Static sitemaps served from `./sitemaps` volume

### 3.3. ModSecurity (OWASP CRS)

- Custom whitelist at `nginx/modsecurity/whitelist.conf` allows legitimate API patterns incorrectly flagged by OWASP CRS
- Protects against SQLi, XSS, and protocol-level attacks before requests reach Tomcat

---

## 4. Scripts Reference (Complete)

All scripts are in `scripts/` and `backup/` directories.

| Script | Purpose | Run Location |
| :--- | :--- | :--- |
| `auto_deploy.sh` | Watchdog: compares branch timestamps, pulls changes, calls `load_secrets.sh`, runs OS memory recovery, runs `docker compose up -d` | Ubuntu VM (cron/git runner) |
| `init_automation.sh` | One-time initialization of the automated deployment loop | Ubuntu VM |
| `load_secrets.sh` | Authenticates with Infisical (Universal Auth), exports secrets to temp `.env` via `sed "s/['\"]//g"` (HikariCP crash prevention), wipes after container startup | Ubuntu VM (called by `auto_deploy.sh`) |
| `rotate_secrets.sh` | Zero-downtime key rotation: generates new JWT + internal API keys via `openssl rand`, updates `.env`, Docker Compose rolling restart | Ubuntu VM |
| `backup/backup.sh` | MariaDB dump + MinIO `docker cp` + Redis BGSAVE. AES-encrypts with `BACKUP_ENCRYPTION_KEY` | Ubuntu VM (backup-service container) |
| `backup/restore.sh` | Pulls from S3, decrypts, restores MariaDB + MinIO + Redis | Ubuntu VM |
| `scripts/market_data_updater.py` | Python market data processor. Invoked via Java `ProcessBuilder`. Uses `decimal.Decimal` for financial precision. Credentials injected via environment (never CLI args) | Inside backend container (via ProcessBuilder) |
| `verify_seo.sh` | Validates sitemap structure, canonical tags, and robots.txt correctness | Ubuntu VM |
| `sanitize_for_sale.sh` | Strips all credentials, keys, and PII before packaging codebase for external sharing | Developer Windows machine only |

---

## 5. Ansible Provisioning

**File:** `ansible/setup-server.yml`

Automates Ubuntu VM initial provisioning:
- Installs Docker Engine and Docker Compose plugin
- Configures UFW firewall (only ports 22, 80, 443 open)
- Sets up automatic OS security updates (`unattended-upgrades`)
- Creates application directory structure
- Configures cron entries for the auto-deploy watchdog

---

## 6. CI/CD Pipeline (`deploy.yml`)

**File:** `.github/workflows/deploy.yml`
**Triggers:** Push to `develop`, `staging`, or `main`

### Synchronous Build + Deploy Job

```
1. Checkout code
2. Set up Java 21 (Temurin) + Maven cache
3. Gitleaks secret scan              ← BLOCKS on any credential detection
4. git verify-commit HEAD            ← BLOCKS on unsigned commits
5. mvn test                          ← Unit tests
6. mvn clean package                 ← Build WAR artifact
7. mvn spotless:check                ← Code formatting gate
8. SCP artifact to Ubuntu VM
9. Signal Watchdog (auto_deploy.sh)
```

### Separate OWASP Cron Job (`dependency-security-scan`)

- Runs on a **separate, isolated, cron-scheduled job**
- Heavy OWASP Dependency Check (15+ minutes) is **intentionally decoupled** from the deployment speed path
- Never blocks the synchronous `build-and-deploy` job — this is a hard architectural constraint

### Key CI/CD Constraints

| Constraint | Enforcement |
| :--- | :--- |
| GPG commit signing | `git verify-commit HEAD` — unsigned commits → pipeline blocked |
| Secret scanning | Gitleaks on every push |
| Code formatting | `mvn spotless:check` — unformatted code → pipeline blocked |
| `package-lock.json` sync | Must always be committed alongside `package.json` — CF Pages uses `npm ci`, desync crashes Edge build |
| OWASP check | Isolated cron job only — never in synchronous deploy path |

### `.gitleaks.toml`

Custom Gitleaks configuration extending the default ruleset with Treishvaam-specific patterns: Infisical token formats, Cloudflare API token patterns, and AEGIS secret naming conventions.

---

## 7. Observability Configuration Files (Verified)

| File | Purpose |
| :--- | :--- |
| `config/prometheus.yml` | Scrape configs: Spring Boot Actuator, Node Exporter, Redis exporter |
| `config/grafana-datasources.yml` | Provisioned datasources: Prometheus, Loki, Tempo |
| `config/grafana-dashboards.yml` | Dashboard provider config pointing to `./dashboards/` |
| `config/grafana-alerting.yml` | Alert rules: `HighBackendErrorRate`, `SlowAPIResponse`, `SecretKeyRotationDue` |
| `config/loki-config.yml` | Loki storage config (local filesystem) |
| `config/promtail-config.yml` | Tails `./logs/*.log`, attaches `job="varlogs"` label |
| `config/tempo.yaml` | Tempo receiver config (Zipkin protocol on port 9411) |
| `config/envoy.yaml` | Envoy listener + cluster config for gRPC routing to `aegis-zkp-service` |
| `config/rabbitmq/definitions.json` | RabbitMQ exchanges, queues, bindings, vhosts — loaded via `RABBITMQ_SERVER_DEFINITION_FILE` |

### Grafana Access (Zero-Trust SSH Tunnel — Mandatory)

**NEVER expose Grafana or Prometheus to `0.0.0.0`.** Access strictly via:

```powershell
ssh -L 3001:localhost:3001 -L 15672:localhost:15672 vboxuser@<SERVER_IP>
```

Then access locally:
- **Grafana:** `http://localhost:3001`
- **RabbitMQ:** `http://localhost:15672`
- **Loki query label:** `{job="varlogs"}` | Tenant-filtered: `{job="varlogs"} | json | tenantId="finance"`

---

## 8. Data Volume Map (Verified)

```
./data/
    ├── mariadb/        ← App database (TDE encrypted via encryption.cnf)
    ├── keycloak-db/    ← Keycloak SSO database (isolated)
    ├── redis/          ← Redis AOF/RDB persistence
    ├── minio/          ← Object storage (media + materialized HTML)
    ├── elastic/        ← Elasticsearch index data
    ├── grafana/        ← Grafana dashboards + alert state
    └── tempo/          ← Distributed trace data

./logs/                 ← Spring Boot logs (tailed by Promtail → Loki)
./sitemaps/             ← Generated XML sitemap files (served by OpenResty static)
./uploads/              ← Uploaded media files (served by OpenResty static)
./scripts/              ← market_data_updater.py and operational scripts
./config/               ← All service config files (mounted read-only)
./nginx/                ← OpenResty config + Lua scripts + ModSecurity whitelist
./aegis/zkp-service/    ← Go source for ZKP microservice (compiled into container)
./backup/               ← backup.sh, restore.sh, encrypted backup outputs
```

---

## 9. Cloudflare Tunnel

**Container:** `treishvaam-tunnel` (`cloudflare/cloudflared`)

- Secure ingress from Cloudflare's network to internal Docker network
- Eliminates need to open any firewall ports on the Ubuntu VM for application traffic
- Tunnel terminates inside `treish_net`, routing to `treishvaam-nginx` on port 80/443
- Token: `CLOUDFLARE_TUNNEL_TOKEN` (environment variable from Infisical)
- All TLS terminated at Cloudflare edge — tunnel traffic to OpenResty is plain HTTP internally

---

## 10. Secret Management — Infisical Flash & Wipe Flow

```
Infisical Cloud (source of truth for all backend secrets)
      │
      ▼ (auto_deploy.sh on each deployment)
load_secrets.sh
   └── infisical login --method=universal-auth (Machine Identity Token)
   └── infisical export | sed "s/['\"]//g" > .env   ← strips literal quotes (HikariCP fix)
      │
      ▼
OS Memory Recovery (before docker compose up)
   └── sudo sync && sudo sh -c "echo 3 > /proc/sys/vm/drop_caches"
   └── docker builder prune -f
      │
      ▼
docker compose up -d (reads .env via env_file: + environment: mapping)
      │
      ▼
.env sanitized — high-value secret values wiped from disk
Containers retain secrets exclusively in-memory
```

**Secret routing rules:**
- **Backend secrets** (DB keys, encryption keys, API tokens) → **Infisical** → `.env` → Docker Compose `environment:`
- **Frontend public vars** (Analytics IDs, API URLs) → **Cloudflare Pages Environment Variables**
- **Edge secrets** (Worker signing key, backend origin) → **Cloudflare Worker Secrets** (`npx wrangler secret put`)

---

## 11. SaltStack & Packer (Configuration Management & Image Baking)

**SaltStack** (`saltstack/states/`) — Configuration management for the Ubuntu VM. Manages package installations, service configurations, and system state.

**Packer** (`packer/server-image.pkr.hcl`) — Server image baking for reproducible VM provisioning. Produces a pre-configured Ubuntu image with Docker Engine and required tooling pre-installed.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **VERIFIED + UPDATED (2026-05-29 — Enterprise Documentation Generation):**
  - All 20 containers in `docker-compose.yml` verified with exact image names, versions, port bindings, and health check configurations.
  - All 9 scripts verified against actual script files.
  - **ADDED:** `verify_seo.sh` and `sanitize_for_sale.sh` to Scripts Reference — both existed in `scripts/` directory but were absent from the scripts table in previous documentation.
  - **ADDED:** OS Memory Recovery sequence to Secret Management flow (Section 10) — `sync + drop_caches + builder prune` is an immutable step in `auto_deploy.sh`, critical for preventing Exit 137 OOM kills on backend replica startup.
  - **ADDED:** SaltStack and Packer section (Section 11) — both exist in codebase but were undocumented.
  - **CONFIRMED:** `sed "s/['\"]//g"` in `load_secrets.sh` is present and intentional — documented as permanent HikariCP crash prevention fix.
  - No existing architectural claims changed.