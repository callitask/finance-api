# Deployment & Operations Manual

**Stable Version:** `tfin-financeapi-Develop.0.0.0.1`

## 1. Multi-Branch Strategy

We utilize a sophisticated 3-branch strategy to balance rapid development with enterprise stability. The "Watchdog" script on the server intelligently switches between these branches based on the latest activity.

### The Branching Model
| Branch | Role | Automation Behavior |
| :--- | :--- | :--- |
| **`develop`** | **Active Development** | Daily work occurs here. Code pushed is immediately deployed to the dev environment. Used for integration testing. |
| **`staging`** | **Release Candidate** | A "Golden Copy" of the codebase. Features are merged here only when feature-complete and tested. Acts as a stable restore point. |
| **`main`** | **Production** | The locked, public-facing release history. Represents the currently live, stable version of the platform. |

---

## 2. Dual-Engine Automation Architecture

Our deployment process is decoupled into two distinct engines. This separates the **Build Logic** (Compiling Java) from the **State Logic** (Managing Containers & Secrets).

### Engine A: The Builder (GitHub Actions)
* **File**: `.github/workflows/deploy.yml`
* **Triggers**: Pushes to `develop`, `staging`, or `main`.
* **Responsibilities**:
    1.  **CI**: Sets up Java 21, caches dependencies, and runs Unit Tests (`mvn test`).
    2.  **Build**: Compiles the Spring Boot application into an executable WAR file (`mvn clean package`).
    3.  **Artifact Transfer**: Securely copies the `backend-app.war` to the Ubuntu Server using SSH/SCP.
    4.  **Trigger**: Signals the server to restart the Backend service.

### Engine B: The Watchdog (Auto-Deploy Script)
* **File**: `scripts/auto_deploy.sh`
* **Location**: Runs locally on the Ubuntu Server (via Cron/Git Runner).
* **Responsibilities**: "Self-Healing" and Infrastructure Sync.
* **Logic Flow**:
    1.  **Branch Intelligence**: Checks timestamps of `origin/develop`, `origin/staging`, and `origin/main`.
    2.  **Winner Takes All**: Automatically checks out the branch with the most recent commit.
    3.  **Infrastructure Sync**: Pulls changes to non-compiled files (Nginx configs, Python scripts, Docker configs).
    4.  **Secret Injection**: Executes the "Flash & Wipe" security sequence.
    5.  **Smart Restart**: Rebuilds containers only if configurations have changed.

---

## 3. Secret Management (Flash & Wipe)

**Status**: ✅ Active (Fort Knox: Zero-Secrets-on-Disk)

We do not rely on static `.env` files for application secrets. Instead, we use a dynamic injection strategy orchestrated by `auto_deploy.sh`.

### The "Flash & Wipe" Sequence
1.  **State 0 (Resting)**: The `.env` file on disk contains **only** the Infisical Machine Identity tokens (`INFISICAL_CLIENT_ID`, etc.). No DB passwords or API keys are present.
2.  **State 1 (Flash)**: When deployment starts, the script authenticates with Infisical and exports the full production secret set, appending them to `.env`.
3.  **State 2 (Consumption)**: `docker compose up` is executed. The Docker daemon reads the secrets from the file and injects them into the container's RAM.
4.  **State 3 (Stabilization)**: The script waits 10 seconds to ensure containers have initialized.
5.  **State 4 (Wipe)**: The script immediately overwrites `.env` with a safe template, removing all sensitive data from the disk.

### Required Secrets Reference (Infisical)
The following keys **must** exist in the Infisical Production Environment for the deployment to succeed.

| Key | Description | Service(s) |
| :--- | :--- | :--- |
| `PROD_DB_PASSWORD` | MariaDB Root Password | Database |
| `KEYCLOAK_DB_PASSWORD` | Keycloak DB Password | Keycloak |
| `MINIO_ROOT_PASSWORD` | MinIO Admin Secret | Storage, Backend |
| `RABBITMQ_DEFAULT_USER` | RabbitMQ User | Messaging, Backend |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ Password | Messaging, Backend |
| `JWT_SECRET_KEY` | Token Signing Key | Backend |
| `INTERNAL_API_SECRET_KEY` | Service-to-Service Lock | Backend |
| `CLOUDFLARE_TUNNEL_TOKEN` | Tunnel Auth Token | Cloudflared |

---

## 4. Fort Knox Security Configuration (Nginx & Edge)

We employ a "Defense in Depth" strategy starting at the Nginx Gateway.

### Security Headers (Hardened)
The following headers are strictly enforced in `nginx/conf.d/default.conf` to prevent common web attacks while enabling secure cross-origin authentication.

| Header | Value | Purpose |
| :--- | :--- | :--- |
| **`Content-Security-Policy`** | `frame-ancestors 'self' https://treishfin.treishvaamgroup.com;` | **Critical Fix**: Replaces `X-Frame-Options`. Explicitly whitelists the Frontend domain to allow Silent SSO (Keycloak iframe) while blocking all other clickjacking attempts. |
| **`X-Content-Type-Options`** | `nosniff` | Prevents browsers from "guessing" MIME types (e.g., treating text as executable scripts). |
| **`X-XSS-Protection`** | `1; mode=block` | Enables the browser's built-in Cross-Site Scripting (XSS) filter. |
| **`Server`** | *(Hidden)* | `server_tokens off;` prevents Nginx from broadcasting its version number to scanners. |

### Upload Security
* **Limits**: `client_max_body_size 100M` matches Spring Boot's limit.
* **Execution Prevention**: The `/uploads/` directory is served with `no-transform` headers to prevent execution of uploaded scripts.

---

## 5. Disaster Recovery (DR)

### Scenario A: Bad Code on `develop`
If a deployment to `develop` breaks the site:
1.  **Automatic Rollback**: Push a new commit to the `staging` branch (even an empty commit).
2.  **Watchdog Action**: The Watchdog will detect that `staging` has a newer timestamp than `develop`.
3.  **Resolution**: It will automatically check out `staging`, reset the codebase, and redeploy the stable version.

### Scenario B: Database Corruption
Database backups are automated via the dedicated `backup-service` container.
- **Schedule**: Every 24 hours.
- **Storage**: Encrypted and stored in the local MinIO `treishvaam-backups` bucket.

**Restore Procedure:**
```bash
# 1. List available backups in MinIO
docker exec -it treishvaam-backup ls -lh /data/

# 2. Execute Restore (WARNING: Overwrites current DB)
# Replace <timestamp>.sql.gz with the actual filename
docker exec -it treishvaam-backup ./restore.sh <timestamp>.sql.gz
```

---

## 6. Observability (LGTM Stack)

We utilize the **Grafana LGTM Stack** (Loki, Grafana, Tempo, Mimir) for full-stack observability.

### Zero Trust Access
Direct IP access to Grafana (Port 3001) has been **disabled** for security. Access is managed via Cloudflare Tunnel.

* **URL**: `https://grafana.treishvaamgroup.com` (Configured in Cloudflare Zero Trust Dashboard)
* **Authentication**: Protected via Cloudflare Access (SSO/Google Login).
* **Internal User**: `admin` (Password managed in Infisical via `GRAFANA_ADMIN_PASSWORD`).

### Debugging Workflows

#### 1. Viewing Logs (Loki)
Instead of `docker logs`, use Grafana Explore:
1.  Select Datasource: **Loki**.
2.  **Backend Logs**: `{container="backend"}`
3.  **Error Search**: `{container="backend"} |= "ERROR"`
4.  **Nginx Access Logs**: `{container="nginx"}`

#### 2. Performance Tracing (Tempo)
To trace a slow request:
1.  Select Datasource: **Tempo**.
2.  Find the `traceId` from the Loki logs.
3.  Paste it into Tempo to visualize the full request path (Nginx -> Backend -> Database/Redis).

#### 3. Infrastructure Health (Prometheus)
Check the **"Mission Control"** dashboard for:
* CPU/Memory usage of containers.
* RabbitMQ Queue depth.
* JVM Heap memory usage.
* Circuit Breaker states (Resilience4j).