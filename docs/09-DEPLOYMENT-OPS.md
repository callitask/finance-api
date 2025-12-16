# 09-DEPLOYMENT-OPS.md

## 1. Deployment Pipeline (Auto-Pilot)
The platform utilizes a **Resilient Auto-Pilot** deployment strategy (`scripts/auto_deploy.sh`) combined with **Multi-Stage Docker Builds**. This ensures the server automatically syncs with Git, compiles code in a controlled environment, and redeploys without manual intervention.

### The "Auto-Pilot" Mechanism
- **Trigger**: A Cron job runs `scripts/auto_deploy.sh` every minute.
- **Detection**: Checks `git rev-parse HEAD` against `origin/main`. If they differ, deployment starts.
- **Build Strategy**: **Multi-Stage Dockerfile**.
    - **Stage 1 (Builder)**: Uses `maven:3.9-eclipse-temurin-21` to compile the JAR/WAR. This guarantees the correct Java version regardless of the host OS.
    - **Stage 2 (Runtime)**: Copies the compiled artifact to a lightweight `eclipse-temurin:21-jdk-jammy` image.
- **Zero-Touch**: No manual SSH is required. Pushing to `main` triggers the update within 60 seconds.

### Pipeline Lifecycle
1.  **Fetch**: Server pulls latest commits.
2.  **Build**: Docker builds the backend image (Maven Compilation happens *inside* Docker).
3.  **Deploy**: `docker-compose up -d --build backend` replaces the container.
4.  **Config Reload**: `docker restart treishvaam-nginx` ensures Nginx loads new configs.

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
To prevent "White Screen of Death" and deployment loops, the stack uses a specific startup strategy.

### Deadlock Prevention Strategy
1.  **Backend Dependency**: The backend depends on `keycloak` using **`condition: service_started`**, NOT `service_healthy`.
2.  **Keycloak Health**: Configured to check `/auth/health`.
3.  **Nginx "Crash-Proofing"**:
    - **Issue**: Nginx normally crashes if the `backend` host is unreachable.
    - **Fix**: Dynamic DNS Resolution in `nginx.conf` (`resolver 127.0.0.11`).
    - **Result**: Nginx starts successfully even if the backend is down.

---

## 4. Observability & Debugging (Mission Control)
The platform uses a "Zero-CLI" debugging model.

### Accessing the Dashboard
- **URL**: `http://<YOUR_UBUNTU_SERVER_IP>:3001`
- **User**: `admin`
- **Password**: *(Refer to `GRAFANA_PASSWORD` in your `docker-compose.yml`)*

### The "Mission Control" View
Located under **Dashboards > Treishvaam Mission Control**.
1.  **Traffic**: Real-time Requests Per Second (RPS).
2.  **Latency**: Request duration (Target: < 200ms).
3.  **Application Logs**: Live feed highlighting `ERROR` or `WARN` in red.

### Advanced Debugging (Loki & Tempo)
- **Loki**: Use `{job="varlogs"} |= "ERROR"` to find stack traces.
- **Tempo**: Paste `traceId` (from logs) to visualize the request waterfall.

---

## 5. Disaster Recovery (DR) & PITR
The system guarantees **Zero Data Loss** (RPO ≈ 0).

### Backup Architecture
- **Frequency**: Every 24 hours (Automated Alpine container).
- **Retention**: Rolling 7-day window.
- **Binlogs**: Enabled at `/opt/treishvaam/data/mariadb`.

### Point-in-Time Recovery (PITR)
1.  **Daily Backup**: Contains Master Data coordinates (`--master-data=2`).
2.  **Recovery**: Restore `.sql.gz` + Replay `mysqlbinlog` to the exact crash timestamp.

---

## 6. Gateway, WAF & Edge Responsibilities
Responsibility is strictly divided between the Origin (Nginx) and the Edge (Cloudflare).

### Nginx (Origin Gateway)
- **Role**: Load Balancer, Reverse Proxy, and WAF.
- **WAF**: **OWASP ModSecurity CRS** (Iron Dome).
- **Transparent CORS**: Nginx **does not** set `Access-Control-Allow-Origin`. It passes all headers to Spring Boot, preventing "Double Header" errors.
- **Bot Whitelisting**: Permits known good bots (Googlebot) to bypass WAF.

### Cloudflare Worker (Edge)
- **Role**: High-Availability SEO.
- **Robots.txt**: Served directly from Edge.
- **Schema Injection**: Intercepts `/category/` requests to inject JSON-LD.

---

## 7. Failure Modes & Expected Behavior
| Failure Scenario | Expected Behavior |
|------------------|-------------------|
| **Backend Down** | Nginx serves 502 Bad Gateway. |
| **Keycloak Down**| Users redirected to login see 502/Error. |
| **DB Down** | Backend throws JDBC Exceptions. Circuit Breakers open. |
| **Entire Cluster Down** | Cloudflare Worker serves `robots.txt` and cached shells. |