# 09-DEPLOYMENT-OPS.md

## 1. Deployment Pipeline (GitOps Automation)
The platform utilizes a fully automated **GitOps** deployment strategy. We do not manually copy files to the server. All deployments are triggered by Git commits.

### The Workflow
1.  **Trigger**: A push to the `main` branch on GitHub.
2.  **Runner Execution**: The self-hosted Git Runner on the Ubuntu Server detects the change.
3.  **Build Phase**:
    * **Java**: Maven builds the Spring Boot JAR (`mvn clean package`).
    * **Docker**: The `Dockerfile` builds the image, installing Python dependencies and the **Infisical CLI**.
4.  **Deployment Phase**:
    * The runner executes `docker-compose up -d --build --remove-orphans`.
    * Containers are recreated with the new code.
    * **Zero-Downtime Goal**: Nginx handles traffic while the backend restarts (approx 15-20s startup).

### Manual Deployment (Emergency Only)
If the runner fails or you need to force a restart manually:
```bash
cd /opt/treishvaam
git pull origin main
docker-compose up -d --build backend
```

---

## 2. Secret Management (Infisical)
**Status**: ✅ Active (Enterprise Grade)
**Policy**: **Zero-Secrets-on-Disk**

We strictly adhere to a security model where no sensitive data (DB passwords, API keys) is stored in file systems.

### Architecture
* **Storage**: Secrets are encrypted and stored in **Infisical Cloud**.
* **Injection**: The `Dockerfile` entrypoint wraps the Java application with the Infisical CLI (`infisical run -- java ...`).
* **Runtime**: Secrets are fetched into **RAM** at startup and injected as Environment Variables to the process.

### Server Configuration (`.env`)
The server contains only **one** configuration file at `/opt/treishvaam/.env`. It holds **only** the authentication tokens required for the machine to prove its identity to Infisical.

| Variable | Description |
|----------|-------------|
| `INFISICAL_URL` | The auth endpoint (e.g., `https://app.infisical.com`). |
| `INFISICAL_PROJECT_ID` | The Treishvaam Finance Project ID. |
| `INFISICAL_CLIENT_ID` | The Machine Identity (Robot) ID. |
| `INFISICAL_CLIENT_SECRET` | The Robot's Secret Key (Generated once). |

**Note:** If you need to rotate a database password, do it in the Infisical Dashboard, then restart the backend container.

---

## 3. Startup Order & Healthchecks
To prevent deployment loops and service failures:

1.  **Backend Dependency**: The backend waits for `keycloak`, `mariadb`, and `redis` to be healthy before starting (`condition: service_healthy`).
2.  **Nginx Resilience**: Nginx uses a dynamic Docker DNS resolver (`127.0.0.11`) to prevent crashing if the backend container is momentarily missing during a rebuild.
3.  **Infisical Validation**: If the Infisical authentication fails (e.g., bad token), the container will exit immediately with an error code, preventing the app from starting with missing config.

---

## 4. Observability (LGTM Stack)
We use the "Zero-CLI" debugging model via the Grafana LGTM Stack.

### Mission Control
- **URL**: `http://<YOUR_SERVER_IP>:3001` (Grafana)
- **Login**: `admin` / `%getrichsoon1954`
- **Dashboard**: "Treishvaam Mission Control"

### Debugging with Loki (Logs)
Instead of SSH-ing into the server to `tail` logs, use Loki in Grafana:
1.  Go to **Explore**.
2.  Select source **Loki**.
3.  **Query (Errors)**: `{app="finance-api"} |= "ERROR"`
4.  **Query (Infisical)**: `{app="finance-api"} |= "Infisical"` (To debug secret injection).

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
- **CORS**: Explicitly injects `Access-Control-Allow-Origin` headers to ensure frontend clients can always read error responses (403/500), preventing generic network errors.

### Cloudflare Tunnel
- **Security**: The server exposes **zero** open ports (80/443) to the public internet.
- **Access**: Traffic is routed exclusively via the Cloudflare Tunnel (`cloudflared`), authenticated via `TUNNEL_TOKEN`.

---

## 7. Resilience Strategies
| Scenario | Behavior |
|----------|----------|
| **Secrets Rotation** | Change secret in Infisical -> Restart Backend. Zero code changes required. |
| **Redis Failure** | **Fail-Open:** Rate limiters disable themselves; site remains online. |
| **Backend Down** | **Worker Failover:** Cloudflare Worker serves fallback `robots.txt` and cached pages (Stale-While-Revalidate). |
| **Vault Failure** | **Resolved:** Legacy Vault dependency removed to eliminate startup hangs. |