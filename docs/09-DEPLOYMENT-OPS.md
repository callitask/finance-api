# Deployment & Operations Guide

## 1. Deployment Pipeline (GitOps Automation)
The platform utilizes a fully automated **GitOps** deployment strategy. Deployments are managed by a smart orchestration script that handles secret injection.

### The Workflow
1.  **Trigger**: A push to the `main` or `develop` branch on GitHub.
2.  **Runner Execution**: The self-hosted Git Runner on the Ubuntu Server detects the change.
3.  **Orchestration**: The runner invokes `scripts/auto_deploy.sh`.
    * This script checks which files changed (Backend, Infrastructure, or Nginx).
    * It intelligently decides which services to restart.
4.  **Secure Deployment Phase**:
    * The script executes `infisical run --env prod -- docker-compose up -d ...`.
    * Secrets are injected into the deployment process in real-time.
    * **Zero-Downtime**: Nginx remains active while the backend containers are recreated.

### Manual Deployment (Emergency Only)
If you need to force a restart manually:
```bash
cd /opt/treishvaam
./scripts/auto_deploy.sh
```

---

## 2. Secret Management (Infisical)
**Status**: Active (Enterprise Orchestrator Mode)
**Policy**: **Zero-Secrets-on-Disk**

We use **Host-Level Injection**. The `docker-compose.yml` file maps environment variables (e.g., `${PROD_DB_PASSWORD}`) to the containers. These variables are populated by the `infisical run` wrapper command on the host.

### Server Configuration (`.env`)
The server contains only **one** configuration file at `/opt/treishvaam/.env`. It holds **only** the Machine Identity tokens.

| Variable | Description |
|----------|-------------|
| `INFISICAL_PROJECT_ID` | The Treishvaam Finance Project ID. |
| `INFISICAL_CLIENT_ID` | The Machine Identity (Robot) ID. |
| `INFISICAL_CLIENT_SECRET` | The Robot's Secret Key. |

**Note:** The `CLOUDFLARE_TUNNEL_TOKEN` is also managed inside Infisical and injected via the wrapper. It should **not** be in the `.env` file.

---

## 3. Startup Order & Healthchecks
To prevent deployment loops and service failures:

1.  **Backend Dependency**: The backend waits for `keycloak`, `mariadb`, and `redis` to be healthy before starting (`condition: service_healthy`).
2.  **Keycloak Latency**: Keycloak takes ~30-60s to start. Nginx may return `502 Bad Gateway` during this window. This is normal behavior during a full restart.
3.  **Database URL**: The JDBC URL is constructed securely in Infisical (e.g., `jdbc:mariadb://treishvaam-db:3306/...`) to ensure correct internal routing.

---

## 4. Observability (LGTM Stack)
We use the "Zero-CLI" debugging model via the Grafana LGTM Stack.

### Mission Control
- **URL**: `http://<YOUR_SERVER_IP>:3001` (Grafana)
- **Login**: `admin` / (Password in Infisical)
- **Dashboard**: "Treishvaam Mission Control"

### Debugging with Loki (Logs)
Instead of SSH-ing into the server to `tail` logs, use Loki in Grafana:
1.  Go to **Explore**.
2.  Select source **Loki**.
3.  **Query (Errors)**: `{app="finance-api"} |= "ERROR"`
4.  **Query (Startup)**: `{container="treishvaam-backend-1"}` (To view boot logs).

---

## 5. Disaster Recovery (DR)
Data safety is guaranteed via an isolated Backup Service container.

- **Frequency**: Automated daily backups (24h interval).
- **Destination**: MinIO Bucket (`treishvaam-backups`).
- **Encryption**: Backups are encrypted at rest.
- **Restore Procedure**:
  ```bash
  # 1. List backups
  docker exec -it treishvaam-minio ls /data/treishvaam-backups
  
  # 2. Run restore script
  docker exec -it treishvaam-backup ./restore.sh <backup_filename.sql.gz>
  ```

---

## 6. Gateway & Security
Responsibility is divided between the Origin (Nginx) and the Edge (Cloudflare).

### Nginx (Origin Gateway)
- **Role**: Reverse Proxy, WAF, and CORS Handler.
- **WAF (ModSecurity)**: Blocks SQL Injection (SQLi) and XSS attacks using OWASP Core Rules.
- **CORS**: Explicitly injects `Access-Control-Allow-Origin` headers to ensure frontend clients can always read error responses.

### Cloudflare Tunnel
- **Security**: The server exposes **zero** open ports (80/443) to the public internet.
- **Access**: Traffic is routed exclusively via the Cloudflare Tunnel (`cloudflared`).
- **Authentication**: The Tunnel Token is injected securely via Infisical (`TUNNEL_TOKEN=${CLOUDFLARE_TUNNEL_TOKEN}`).

---

## 7. Resilience Strategies
| Scenario | Behavior |
|----------|----------|
| **Secrets Rotation** | Change secret in Infisical -> Run `auto_deploy.sh`. |
| **Redis Failure** | **Fail-Open:** Rate limiters disable themselves; site remains online. |
| **Backend Down** | **Worker Failover:** Cloudflare Worker serves fallback `robots.txt` and cached pages. |
| **Bad Config** | **Fail-Fast:** Containers exit immediately if secrets (like DB Password) are missing or invalid. |