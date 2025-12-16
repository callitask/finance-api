# 09-DEPLOYMENT-OPS.md

## 1. Deployment Pipeline (Smart Auto-Pilot)
The platform utilizes a **Smart Auto-Pilot** deployment strategy (`scripts/auto_deploy.sh`). Unlike traditional scripts that blindly rebuild everything, this intelligent system detects exactly *which* files changed and only restarts the necessary services.

### The "Smart" Mechanism
- **Trigger**: A Cron job runs `scripts/auto_deploy.sh` every minute.
- **Detection**: Checks `git diff` between Local (`HEAD`) and Remote (`origin/main`).
- **Intelligent Action**:
    - **Backend Code (`src/`, `pom.xml`)**: Rebuilds the Java container (`docker-compose up -d --build backend`).
    - **Nginx Config (`nginx/`)**: Reloads the Proxy/WAF (`docker restart treishvaam-nginx`).
    - **Monitoring Config (`config/prometheus.yml`)**: Restarts Prometheus only.
    - **Backup Scripts (`backup/`)**: Rebuilds the Backup Service.

### Manual Trigger
If you need to force an update immediately without waiting for the cron job:
```bash
cd /opt/treishvaam
./scripts/auto_deploy.sh
```

---

## 2. Environment Variables & Secrets
Secrets are strictly categorized. **Never commit `.env` files to version control.**

### Runtime Secrets (Production `.env`)
| Category | Variable | Description |
|----------|----------|-------------|
| **Database** | `DB_HOST`, `DB_NAME` | Connection details. |
| | `DB_USER`, `PROD_DB_PASSWORD` | **Critical**: Root/User credentials. |
| **Messaging** | `RABBITMQ_HOST`, `RABBITMQ_PASS` | Broker credentials. |
| **Storage** | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | S3 Admin keys. |
| **IAM (Keycloak)** | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` | Super-admin credentials. |
| **Infrastructure** | `CLOUDFLARE_TUNNEL_TOKEN` | **Critical**: Auth token for Zero Trust Tunnel (Replaces legacy json file). |

---

## 3. Startup Order & Healthchecks
To prevent "White Screen of Death" and deployment loops:

1.  **Backend Dependency**: The backend depends on `keycloak` using **`condition: service_started`**.
2.  **Nginx Resilience**: Uses Dynamic DNS Resolution (`resolver 127.0.0.11`) to prevent Nginx from crashing if the backend container is momentarily down during a restart.

---

## 4. Observability & Debugging (Mission Control)
The platform uses a "Zero-CLI" debugging model via the **LGTM Stack**.

### Accessing the Dashboard
- **URL**: `http://<YOUR_UBUNTU_SERVER_IP>:3001`
- **User**: `admin`
- **Password**: `%getrichsoon1954`

### The "Mission Control" View
Located under **Dashboards > Treishvaam Mission Control**.
1.  **Traffic**: Real-time Requests Per Second (RPS).
2.  **Latency**: Request duration (Target: < 200ms).
3.  **Application Logs**: Live feed. Errors (stack traces) appear in **RED**.

### Advanced Debugging (Loki)
1.  Go to **Explore** (Compass Icon).
2.  Select **Loki**.
3.  **Find Errors:** `{job="varlogs"} |= "ERROR"`
4.  **Find Firewall Blocks:** `{job="varlogs"} |= "403"` (Useful for ModSecurity debugging).

---

## 5. Disaster Recovery (DR)
The system guarantees data safety via an isolated Backup Service.

- **Frequency**: Every 24 hours (Automated Alpine container).
- **Storage**: MinIO Bucket `treishvaam-backups`.
- **Method**: `mysqldump` with `--master-data=2` (Enables Point-in-Time Recovery).
- **Restore Command**:
  ```bash
  docker exec -it treishvaam-backup ./restore.sh <backup_file.sql.gz>
  ```

---

## 6. Gateway, WAF & Security
Responsibility is divided between the Origin (Nginx) and the Edge (Cloudflare).

### Nginx (Origin Gateway)
- **Role**: Load Balancer, Reverse Proxy, and WAF.
- **Gateway-Level CORS**:
    - **Update:** Nginx **explicitly handles** CORS (`Access-Control-Allow-Origin`).
    - **Why:** This ensures that even if the Backend crashes (500 Error) or ModSecurity blocks a request (403), the browser still receives the permission headers to display the error correctly, preventing generic "Network Errors".
- **WAF (ModSecurity)**:
    - Enforces OWASP Core Rules.
    - **Whitelisting:** Explicitly allows complex JSON payloads for:
        - `/api/v1/monitoring/ingest` (Faro Logs)
        - `/api/v1/posts/admin` (Blog Content)

### Cloudflare Tunnel
- **Security:** Uses `TUNNEL_TOKEN` from `.env`. No certificate files are stored on disk.
- **Access:** Acts as the *only* entry point into the server. No open ports (80/443) are exposed to the internet.

---

## 7. Resilience Strategies
| Scenario | Behavior |
|----------|----------|
| **Redis Failure** | **Fail-Open:** The API Rate Limiter detects the failure and allows traffic to pass instead of crashing the site. |
| **Backend Down** | **Worker Failover:** Cloudflare Worker serves `robots.txt` and cached HTML shells to keep SEO alive. |
| **Database Down** | Backend Circuit Breakers open; User sees a friendly 503 error page. |