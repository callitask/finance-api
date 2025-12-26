# 09-DEPLOYMENT-OPS.md

## 1. Multi-Branch Deployment Strategy
We utilize a sophisticated 3-branch strategy to balance rapid development with enterprise stability.

### The Branches
1.  **`develop` (Active Work)**
    * **Purpose**: Daily development, experimental features, and rapid iteration.
    * **Behavior**: Code pushed here is immediately picked up by the automation engine.
    * **Risk**: Moderate. Used for testing new integrations.
2.  **`staging` (Stable / Restore Point)**
    * **Purpose**: A clean, stable snapshot of the codebase. Features are only merged here when they are "Done Done".
    * **Use Case**: If `develop` becomes unstable or corrupt, we can immediately switch the server to `staging` to restore service.
3.  **`main` (Production)**
    * **Purpose**: The locked, public-facing release history.

---

## 2. Dual-Engine Automation Architecture
Our deployment process is split into two distinct engines to separate **Build Logic** from **Server State Logic**.

### Engine A: The Builder (GitHub Actions)
* **File**: `.github/workflows/deploy.yml`
* **Responsibility**:
    1.  Detects push to `develop`, `staging`, or `main`.
    2.  Sets up Java 21 environment.
    3.  Compiles the Backend Code (`mvn clean package`).
    4.  **Transfers the Artifact**: Copies the generated `WAR` file to the Ubuntu Server.
    5.  Restarts the Backend Docker Container.

### Engine B: The Watchdog (Auto-Deploy Script)
* **File**: `scripts/auto_deploy.sh`
* **Location**: Runs locally on the Ubuntu Server (via Cron/Runner).
* **Responsibility**: "Self-Healing" and Infrastructure Sync.
* **Logic**:
    1.  **Polls Git**: Checks timestamps of `origin/develop`, `origin/staging`, and `origin/main`.
    2.  **Winner Takes All**: Determines which branch has the latest activity.
    3.  **Syncs Infrastructure**: Updates local server files that *aren't* in the WAR (e.g., Nginx configs, Cloudflare Worker scripts, Python updaters).
    4.  **Restarts Services**: If Nginx or Config files changed, it restarts those specific containers.

---

## 3. Secret Management (Infisical)
**Status**: ✅ Active (Enterprise Orchestrator Mode)
**Policy**: **Zero-Secrets-on-Disk**

We use **Host-Level Injection**. The `docker-compose.yml` file maps environment variables (e.g., `${PROD_DB_PASSWORD}`) to the containers. These variables are populated by the `infisical run` wrapper command on the host.

### Server Configuration (`.env`)
The server contains only **one** configuration file at `/opt/treishvaam/.env`. It holds **only** the Machine Identity tokens.

| Variable | Description |
|----------|-------------|
| `INFISICAL_PROJECT_ID` | The Treishvaam Finance Project ID. |
| `INFISICAL_CLIENT_ID` | The Machine Identity (Robot) ID. |
| `INFISICAL_CLIENT_SECRET` | The Robot's Secret Key. |

---

## 4. Disaster Recovery (Restore Points)

### Scenario: `develop` is broken/corrupted
If a bad commit on `develop` crashes the server:
1.  **Manual Override**: You can force the server to switch to the stable `staging` branch.
    ```bash
    # On the Ubuntu Server
    cd /opt/treishvaam
    git checkout staging
    git reset --hard origin/staging
    ./scripts/auto_deploy.sh
    ```
2.  **Automatic Override**: Simply push a new commit to `staging` from your local machine. The Watchdog script will see `staging` has a newer timestamp than `develop` and automatically switch the server to it.

### Database Recovery
Data safety is guaranteed via an isolated Backup Service container.
- **Frequency**: Automated daily backups (24h interval).
- **Destination**: MinIO Bucket (`treishvaam-backups`).
- **Restore Procedure**:
  ```bash
  # 1. List backups
  docker exec -it treishvaam-minio ls /data/treishvaam-backups
  
  # 2. Run restore script
  docker exec -it treishvaam-backup ./restore.sh <backup_filename.sql.gz>
  ```

---

## 5. Observability (LGTM Stack)
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