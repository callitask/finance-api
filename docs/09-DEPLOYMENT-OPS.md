/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - The definitive deployment, DevOps, and disaster recovery manual for the Treishvaam Group Backend (finance-api).
 *
 * Scope:
 * - Covers the Multi-Branch Git strategy, CI/CD automation, Secret Management (Infisical), Docker Compose networking, and Hybrid SSG operations.
 *
 * Critical Dependencies:
 * - GitHub Actions (CI/Build).
 * - `auto_deploy.sh` (Watchdog/State Management).
 * - Infisical (Secrets).
 * - Docker Compose (Runtime).
 *
 * Security Constraints:
 * - Flash & Wipe Secret Management: Secrets must never rest on disk.
 * - ZERO-PORT EXPOSURE: All internal services must remain within `treish_net`.
 * - CRITICAL DEVOPS RULE: Docker Compose `.env` files must NEVER contain single or double quotes around values.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED: Initial Deployment & Operations Manual.
 * - EDITED:
 * • Phase 3 Update: Added strict Docker Compose `.env` quoting rules based on live deployment crash diagnostics (HikariCP failures).
 * • Clarified Multi-Tenant deployment implications (ensure tenant DBs and schemas are ready before restarting).
 * - EDITED:
 * • Added boundary clarifications distinguishing Backend Automation from Cloudflare Pages/Worker deployments.
 * • Upgraded Observability section to include Tier 2 Application Telemetry (Native RUM and API Diagnostics).
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted,
 * truncated, rewritten, or regenerated.
 * Future AI must append only.
 */

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

### 2.1. Deployment Boundaries (Backend vs. Edge)
**CRITICAL CLARIFICATION**: This dual-engine architecture *strictly* governs the Java Backend, Database, and Docker infrastructure. 
* **Frontends** (React/Next.js) are decoupled and deploy automatically via Cloudflare Pages upon Git push.
* **Edge Workers** deploy via the `wrangler deploy` CLI command from developer workstations or frontend CI pipelines.

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
    3.  **Infrastructure Sync**: Pulls changes to non-compiled files (Nginx configs, Python Market Engine, Docker configs).
    4.  **Secret Injection**: Executes the "Flash & Wipe" sequence via Infisical.
    5.  **Smart Restart**: Rebuilds containers only if configurations have changed (`docker compose up -d`).

---

## 3. Secret Management & Docker Compose Traps

**Status**: ✅ Active (Fort Knox: Zero-Secrets-on-Disk)

We do not rely on static `.env` files for application secrets. Instead, we use a dynamic injection strategy orchestrated by `auto_deploy.sh`.

### The "Flash & Wipe" Sequence
1.  **State 0 (Resting)**: The `.env` file on disk contains **only** the Infisical Machine Identity tokens (`INFISICAL_CLIENT_ID`, etc.). No DB passwords or API keys are present.
2.  **State 1 (Flash)**: When deployment starts, the script authenticates with Infisical and exports the full production secret set, appending them to `.env`.
3.  **State 2 (Consumption)**: `docker compose up` is executed. The Docker daemon reads the secrets from the file and injects them into the container's RAM.
4.  **State 3 (Stabilization)**: The script waits 10 seconds to ensure containers have initialized.
5.  **State 4 (Wipe)**: The script immediately overwrites `.env` with a safe template, removing all sensitive data from the disk.

### ⚠️ CRITICAL DEVOPS RULE: The Docker Compose Quoting Trap
When managing secrets in Infisical or manually editing the `.env` file, **you must NEVER use single quotes (`'`) or double quotes (`"`) around values.**
* **The Problem:** Unlike Bash, Docker Compose reads `.env` files literally. If you write `PROD_DB_URL='jdbc:mariadb...'`, Docker passes the literal string including the quotes into the container.
* **The Crash:** Spring Boot's HikariCP database pool will fail to recognize the URL (expecting it to start with `jdbc:`), causing the backend to enter a fatal crash loop (`Failed to determine suitable jdbc url`).
* **The Fix:** Ensure all values in the `.env` file are raw strings: `PROD_DB_URL=jdbc:mariadb...`

---

## 4. Fort Knox Security Configuration (Defense in Depth)

We employ a "Defense in Depth" strategy starting at the Nginx Gateway and penetrating the Backend Logic.

### Layer 1: Gateway Headers (Hardened)
The following headers are strictly enforced in `nginx/conf.d/default.conf`.

| Header | Value | Purpose |
| :--- | :--- | :--- |
| **`Content-Security-Policy`** | `frame-ancestors 'self' https://treishfin.treishvaamgroup.com;` | **Critical Fix**: Whitelists the Frontend domains for Silent SSO (Keycloak iframe) while blocking clickjacking. |
| **`X-Content-Type-Options`** | `nosniff` | Prevents browsers from "guessing" MIME types. |
| **`X-XSS-Protection`** | `1; mode=block` | Enables the browser's built-in XSS filter. |

### Layer 2: Static Asset Offloading (Performance & Security)
* **Direct Read Path**: Requests to `/api/uploads/**` are intercepted by Nginx and proxied directly to MinIO.
* **Bypass**: This bypasses the Java application layer, preventing thread exhaustion from image downloads.
* **Write Security**: Nginx strictly denies `PUT`, `POST`, or `DELETE` on these paths. All writes MUST go through the authenticated Java Backend.

### Layer 3: Backend Validation (Zero Trust I/O)
* **MIME Validation (Apache Tika)**: The backend analyzes the **binary signature** (Magic Numbers) of every uploaded file to prevent malware execution.
* **OOM Protection (Zero-Allocation)**: Uploads are streamed directly to temporary disk storage (`Files.createTempFile`). This prevents Out-Of-Memory crashes even for 100MB files.
* **Subprocess Security (Python)**: The Market Data Engine receives credentials via **Environment Variables** (`ProcessBuilder.environment`), hiding passwords from the process table (`ps aux`).

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

## 6. Enterprise Observability Architecture

Observability is divided into two distinct tiers: Infrastructure (Hardware/Network) and Application (Business/User Logic).

### Tier 1: Infrastructure Telemetry (LGTM Stack)
We utilize the **Grafana LGTM Stack** (Loki, Grafana, Tempo, Mimir) for full-stack server observability.
* **Access**: Direct IP access to Port 3001 is disabled. Accessed via Cloudflare Tunnel at `https://grafana.treishvaamgroup.com`.
* **Debugging**: 
    * **Logs (Loki)**: Use `{container="backend"} |= "ERROR"` to find exceptions. Filter Multi-tenant logs by MDC tags (`tenantId="agro"`).
    * **Tracing (Tempo)**: Use Trace IDs from logs to visualize full request paths.

### Tier 2: Application Telemetry (Native Diagnostics)
To eliminate blind spots caused by third-party ad-blockers and external vendor outages, the backend runs native tracking engines:
* **Native RUM (Real User Monitoring)**: The `AnalyticsService` logs direct user traffic, OS, and geo-data to the `audience_visits` MariaDB table, providing GDPR-compliant reporting unaffected by GA4 script blockers.
* **API Diagnostic Health**: The `ApiStatusController` actively tracks the latency, HTTP response codes, and success rates of external market data providers (AlphaVantage, Finnhub). If market data stops syncing, check the `api_fetch_status` table first to rule out third-party vendor outages before debugging backend code.

---

## 7. Maintenance Procedures

### Cache Management
With **Read-Through Caching** enabled, Redis holds active content.

**Option A: Admin API (Preferred)**
Authenticated Admins can trigger a flush via the API:
```bash
POST /api/v1/admin/actions/cache/clear
Authorization: Bearer <ADMIN_TOKEN>
```

**Option B: CLI (Emergency)**
Access the Redis container directly:
```bash
docker exec -it treishvaam-redis redis-cli FLUSHALL
```
*Note: This will temporarily spike DB load as caches rebuild.*

---

## 8. Hybrid SSG Operations (SEO & Content)

### The Materialization Trigger & CSS Synchronization
Our **Hybrid SSG** architecture relies on pre-generated HTML files stored in MinIO/S3 to provide instant load times. These files contain hardcoded links to CSS and JS bundles.

**Scenario:** When you deploy a new version of the Frontend, the build tools (Webpack/Vite) generate new filenames for CSS bundles (e.g., `main.a1b2c3.css` becomes `main.x9y8z7.css`) to bust browser caches.

**The Problem:** Existing static HTML files in MinIO will still point to the *old* CSS filenames, causing the "Plain Text" / "MIME Type Error" visual glitch.

**Resolution Procedure (The "Update" Trigger):**
To align the static content with the new Frontend build, you must force a re-materialization:
1.  Log in to the **Admin Dashboard**.
2.  Navigate to **Manage Posts**.
3.  **Edit** the affected post.
4.  Click **Update** (you do not need to change any text).
5.  *Edge Worker Note:* If Cloudflare KV Caching (`TREISHFIN_SEO_CACHE`) is heavily utilized, allow up to 60 seconds for background `ctx.waitUntil` cache invalidation to propagate globally.