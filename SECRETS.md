# Secret Management Protocol

This project utilizes Infisical for enterprise-grade secret management, strictly adhering to a **Zero-Secrets-on-Disk** policy via the **Flash & Wipe** strategy.

## 1. Security Policies
* **No .env Files**: Confidential configuration files must never be committed to version control. The `.env` file on disk must ONLY contain machine authentication tokens.
* **Flash & Wipe**: Actual application secrets (Database URLs, API Keys) exist on the disk only for the few seconds required to start Docker, after which they are aggressively wiped.
* **Least Privilege**: Application services use restricted Machine Identities (Robots) with read-only access to specific environments.

## 2. Architecture Overview: Flash & Wipe Injection
We utilize a dynamic injection pattern orchestrated by `auto_deploy.sh`.

### Injection Workflow
1.  **Trigger**: Deployment starts. The `.env` file currently contains only `INFISICAL_CLIENT_ID` and `SECRET`.
2.  **Flash (Inject)**: The script authenticates with Infisical and appends the production secrets (e.g., `PROD_DB_URL`, `JWT_SECRET`) to the `.env` file on disk.
3.  **Consumption**: `docker compose up` is executed. Docker reads the `.env` file and passes variables into the container runtime.
4.  **Wipe (Secure)**: Immediately after Docker initialization (and a 10s safety buffer), the script overwrites `.env` with `.env.template`.
5.  **Result**: The secrets are now in the RAM of the running containers, but the file on disk is clean. If the server is inspected 20 seconds later, no secrets are found.

## 3. Local Development & Debugging
Since secrets are not persistent, you must manually inject them if you need to run debug commands or restart specific containers manually.

### Manual Injection Script (`scripts/load_secrets.sh`)
We have created a dedicated utility for this.

**To Load Secrets:**
```bash
cd /opt/treishvaam
./scripts/load_secrets.sh
```
* This will restore auth keys and fetch live secrets into `.env`.
* **Warning**: Your `.env` file is now "Hot" (contains secrets).

**To Secure (Wipe) Secrets:**
After you finish your manual debugging (e.g., running `docker compose up`), you **MUST** wipe the file manually to maintain security standards.
```bash
cp .env.template .env
```

## 4. Production Configuration
The production environment uses a Machine Identity for authentication.

### Server-Side Configuration
* **File Location**: `/opt/treishvaam/.env`
* **Permissions**: `600` (Read/Write by Owner only)
* **Contents**: Only authentication tokens. No actual application secrets.

### Required Variables (Identity Only)
| Variable | Description |
| :--- | :--- |
| `INFISICAL_PROJECT_ID` | The unique identifier for the Treishvaam Finance project. |
| `INFISICAL_CLIENT_ID` | The Machine Identity Client ID. |
| `INFISICAL_CLIENT_SECRET` | The Machine Identity Client Secret. |

## 5. Secret Rotation Policy
To rotate a database password, API key, or internal secret:

1.  **Update**: Change the secret value in the Infisical Dashboard (Production Environment).
2.  **Restart**: Simply trigger the `auto_deploy.sh` script (or push a commit). The Flash & Wipe process will pick up the new values automatically during the next boot.