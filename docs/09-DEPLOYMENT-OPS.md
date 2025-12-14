# 09-DEPLOYMENT-OPS.md

## 1. CI/CD Execution Model
The platform utilizes a **Zero-Touch Deployment** pipeline running on a **self-hosted GitHub Actions runner**.

### Why Self-Hosted?
- **Security**: Keeps build artifacts and database credentials within the private network firewall; no secrets leave the server.
- **Speed**: Persists the local Maven cache (`~/.m2`), reducing build times from ~10 mins to ~40s by avoiding redundant downloads.
- **Cache Persistence**: Uses a **Weekly Rolling Cache Key** (Year–Week) for NVD data to optimize dependency scanning.

### Pipeline Lifecycle (`deploy.yml`)
The pipeline strictly enforces the following Maven lifecycle phases:
1.  **Validate**: Runs `mvn spotless:check` (Google Java Style) and `checkstyle:check`. Builds fail immediately on style violations.
2.  **Verify**: Runs `mvn verify`. **`-DskipTests` is FORBIDDEN.**
    - Uses **Testcontainers** to spawn ephemeral Docker instances (MariaDB, Redis, RabbitMQ) for real integration testing.
3.  **Security Scan**: Runs **OWASP Dependency-Check**.
    - **NVD_API_KEY**: Injected via GitHub Secrets to bypass rate limits.
    - **OSS Index**: Explicitly **DISABLED** to prevent build failures caused by 429 Rate Limit errors from the commercial API.

---

## 2. Environment Variables & Secrets
Secrets are strictly categorized by their lifecycle stage. **Never commit `.env` files to version control.**

### Build-Time Secrets (GitHub Secrets)
| Secret Name | Purpose |
|-------------|---------|
| `NVD_API_KEY` | Required for OWASP Dependency-Check to download vulnerability data. |
| `GHCR_TOKEN` | (Optional) If pushing images to GitHub Container Registry. |

### Runtime Secrets (Production `.env`)
| Category | Variable | Description |
|----------|----------|-------------|
| **Database** | `DB_HOST`, `DB_NAME` | Connection details. |
| | `DB_USER`, `DB_PASS` | **Critical**: Root/User credentials. |
| **Messaging** | `RABBITMQ_HOST`, `RABBITMQ_PASS` | Broker credentials. |
| **Storage** | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | S3 Admin keys. |
| **IAM (Keycloak)** | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` | Super-admin credentials. |
| | `OAUTH2_ISSUER_URI` | `https://backend.treishvaamgroup.com/auth/realms/treishvaam` |
| | `OAUTH2_JWK_SET_URI` | `/protocol/openid-connect/certs` |
| | `OAUTH2_CLIENT_ID`, `OAUTH2_CLIENT_SECRET` | Backend Service Client credentials. |

---

## 3. Startup Order, Healthchecks & Deadlock Prevention
To prevent "White Screen of Death" and deployment loops (Phase 17 Patch), the stack uses a specific startup strategy.

### Deadlock Prevention Strategy
1.  **Backend Dependency**: The backend depends on `keycloak` using **`condition: service_started`**, NOT `service_healthy`.
    - **Reason**: Keycloak can take minutes to become "Healthy". If we wait for "Healthy", the backend build might time out. The backend is designed to retry connections until Keycloak is ready.
2.  **Keycloak Health**: Configured to check `/auth/health`.
3.  **Nginx "Crash-Proofing"**:
    - **Issue**: Nginx normally crashes if the `backend` host is unreachable during startup.
    - **Fix**: We use **Dynamic DNS Resolution** in `nginx.conf`:
      ```nginx
      resolver 127.0.0.11 valid=30s;
      set $backend_cluster http://backend:8080;
      proxy_pass $backend_cluster;
      ```
    - **Result**: Nginx starts successfully even if the backend is down, allowing it to serve "Maintenance" or static error pages.

---

## 4. Disaster Recovery (DR) & PITR
The system guarantees **Zero Data Loss** (RPO ≈ 0) using a multi-layer strategy. **Do not rely solely on daily backups.**

### Backup Architecture
- **Frequency**: Every 24 hours (Automated Alpine container).
- **Retention**: Rolling 7-day window; older files auto-deleted.
- **Binlogs**: MariaDB configured with `log_bin` enabled at `/opt/treishvaam/data/mariadb`.

### Point-in-Time Recovery (PITR)
1.  **Daily Backup**: The `backup.sh` script uses **`--master-data=2`**.
    - **Purpose**: This flag writes the exact **Binary Log Filename** and **Position** (coordinate) into the backup header. This tells us exactly where to start replaying logs.
2.  **Recovery Workflow**:
    - **Step 1**: Restore the last successful `.sql.gz` backup.
    - **Step 2**: Use `mysqlbinlog` to replay transactions from the coordinate found in Step 1 up to the exact timestamp of the crash.
    - **Command**: `mysqlbinlog --start-position=12345 mysql-bin.000001 | mysql -u root -p`

---

## 5. Gateway, WAF & Edge Responsibilities
Responsibility is strictly divided between the Origin (Nginx) and the Edge (Cloudflare).

### Nginx (Origin Gateway)
- **Role**: Load Balancer, Reverse Proxy, and Application Firewall.
- **WAF**: **OWASP ModSecurity CRS** (Iron Dome) runs here.
    - Blocks SQL Injection, XSS, and Malformed Payloads (Layer 7).
- **Bot Whitelisting**: Explicitly permits known good bots (Googlebot, Bingbot) to bypass WAF rules to prevent SEO indexing penalties.
- **CORS**: Enforces `Access-Control-Allow-Origin` for the frontend.

### Cloudflare Worker (Edge)
- **Role**: High-Availability SEO & Static Assets.
- **Robots.txt**: **Owned by Edge**. Served directly from the Worker (`worker.js`).
    - **Reason**: Ensures search engines can *always* find crawling instructions, even if the backend/Nginx is completely offline.
- **Schema Injection**: Intercepts `/category/` requests to inject JSON-LD (NewsArticle, VideoObject) before the HTML reaches the browser.

---

## 6. Failure Modes & Expected Behavior
| Failure Scenario | Expected Behavior |
|------------------|-------------------|
| **Backend Down** | Nginx serves 502 Bad Gateway (or custom error page). Frontend RUM logs error. |
| **Keycloak Down**| Users redirected to login see 502/Error. Active sessions may persist briefly (JWT). |
| **DB Down** | Backend throws JDBC Connection Exceptions. Circuit Breakers open. |
| **Entire Cluster Down** | **Edge Logic Persists**: Cloudflare Worker still serves `robots.txt` and cached HTML shells. |
