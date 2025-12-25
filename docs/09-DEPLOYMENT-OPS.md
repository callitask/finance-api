# Deployment & Operations Guide

## 1. Deployment Pipeline (GitOps Automation)
The platform utilizes a fully automated **GitOps** deployment strategy. Deployments are managed by a smart orchestration script that handles secret injection, service dependencies, and security wiping.

### The Workflow (`auto_deploy.sh`)
1.  **Trigger**: A push to the `main` or `develop` branch on GitHub.
2.  **Runner Execution**: The self-hosted Git Runner on the Ubuntu Server detects the change.
3.  **Branch Logic**: The script intelligently compares timestamps between `main` and `develop` to deploy the most recently active branch.
4.  **Secure Deployment Phase (Flash & Wipe)**:
    * **Inject**: Authenticates with Infisical and appends secrets to `.env`.
    * **Docker Up**: Executes `docker compose up -d --build --force-recreate`.
    * **Wait**: Pauses for **10 seconds** to ensure Docker has fully read the configuration.
    * **Wipe**: Overwrites `.env` with `.env.template`, removing all secrets from the disk.

### Manual Deployment (Emergency Only)
If you need to force a restart manually:
```bash
cd /opt/treishvaam
./scripts/auto_deploy.sh
```

---

## 2. Startup Order & Healthchecks (The "2-Minute Rule")
The Backend (Spring Boot) takes approximately **113 seconds** to initialize its connection pool and Elasticsearch client. To prevent "502 Bad Gateway" loops or manual restart requirements, we enforce the following:

1.  **Backend Start Period**: Configured with `start_period: 160s`. Docker will not mark the container as "Unhealthy" during this boot window.
2.  **Nginx Dependency**: Nginx is configured with `depends_on: backend: condition: service_healthy`.
    * **Effect**: When deployment starts, Nginx will stay in the `Created` state (Stopped) for about 2 minutes.
    * **Auto-Start**: Once the Backend logs "Started FinanceApiApplication", it becomes `healthy`. Docker then **automatically** starts Nginx.
    * **No Manual Action**: You do not need to manually `docker start nginx`. Just wait for the sequence to complete.

---

## 3. Secret Management (Infisical)
**Status**: Active (Flash & Wipe Mode)
**Policy**: **Zero-Secrets-on-Disk**

We use **Host-Level Injection**. The `docker-compose.yml` file maps environment variables (e.g., `${PROD_DB_PASSWORD}`) to the containers.

### Manual Debugging
If you need to run docker commands manually (e.g., to debug a specific container crash), you must manually inject the secrets first.

1.  **Load Secrets**:
    ```bash
    ./scripts/load_secrets.sh
    ```
2.  **Run Commands**:
    ```bash
    docker compose up -d ...
    ```
3.  **Wipe Secrets (Mandatory)**:
    ```bash
    cp .env.template .env
    ```

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