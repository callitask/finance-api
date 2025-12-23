# Secret Management Protocol

This project utilizes Infisical for enterprise-grade secret management, strictly adhering to a **Zero-Secrets-on-Disk** policy.

## 1. Security Policies
* **No .env Files**: Confidential configuration files must never be committed to version control or stored unencrypted on the disk (except for the strictly limited machine authentication tokens).
* **No Hardcoded Secrets**: Passwords, API keys, and tokens must never be embedded in source code, properties files, or Dockerfiles.
* **Least Privilege**: Application services use restricted Machine Identities (Robots) with read-only access to specific environments.

## 2. Architecture Overview: Orchestrator Injection
We utilize the **Host-Level Injection** pattern. The application containers (Backend, Database, Keycloak) are standard Docker images and contain **no secret-fetching logic**.

Instead, the Host (Orchestrator) is responsible for fetching secrets and passing them into the container runtime memory.

### Injection Workflow
1.  **Deployment Trigger**: The `auto_deploy.sh` script is triggered.
2.  **Host Authentication**: The host authenticates with Infisical using the Machine Identity tokens stored in `/opt/treishvaam/.env`.
3.  **Secure Expansion**: The command `infisical run -- docker-compose up` is executed.
4.  **Runtime Injection**:
    * Infisical fetches secrets into the Host's RAM.
    * It populates the environment variables for the `docker-compose` process.
    * Docker Compose maps these variables to the containers via the `environment` blocks in `docker-compose.yml` (e.g., `MYSQL_ROOT_PASSWORD: ${PROD_DB_PASSWORD}`).
5.  **Result**: Containers start with full access to secrets, but **no secrets ever touch the server's disk**.

## 3. Local Development Setup
Developers must install the Infisical CLI to run the backend locally. This ensures development environments mirror production security standards.

### Installation
* **MacOS**: `brew install infisical/tap/infisical`
* **Windows**: `winget install Infisical.Infisical`
* **Linux (Ubuntu/Debian)**:
    ```bash
    curl -1sLf '[https://artifacts-cli.infisical.com/setup.deb.sh](https://artifacts-cli.infisical.com/setup.deb.sh)' | sudo -E bash
    sudo apt-get update && sudo apt-get install -y infisical
    ```

### Running the Application
1.  **Authenticate**: Run `infisical login` in your terminal.
2.  **Select Project**: Choose 'Treishvaam Finance'.
3.  **Execute**:
    ```bash
    # Runs the full stack with injected secrets
    infisical run -- docker-compose up
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
2.  **Restart**: Run the secure restart command on the server:
    ```bash
    cd /opt/treishvaam
    # Load Auth
    export $(grep -v '^#' .env | xargs)
    # Restart
    infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- docker-compose up -d --force-recreate
    ```
3.  **Verify**: The application will fetch the new value immediately upon startup. No code changes or commits are required.