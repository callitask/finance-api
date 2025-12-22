# Secret Management Protocol

This project utilizes Infisical for enterprise-grade secret management, strictly adhering to a **Zero-Secrets-on-Disk** policy.

## 1. Security Policies
* **No .env Files**: Confidential configuration files must never be committed to version control or stored unencrypted on the disk (except for the strictly limited machine authentication tokens).
* **No Hardcoded Secrets**: Passwords, API keys, and tokens must never be embedded in source code, properties files, or Dockerfiles.
* **Least Privilege**: Application services use restricted Machine Identities (Robots) with read-only access to specific environments.

## 2. Architecture Overview
The application configuration does not rely on static text files for sensitive data. Instead, the Docker container utilizes the **Infisical Command Line Interface (CLI)** to inject secrets directly into the process memory at runtime.

### Injection Workflow
1.  **Container Initialization**: The Docker entrypoint invokes the Infisical CLI wrapper.
2.  **Authentication**: The CLI authenticates with the Infisical Cloud using a Machine Identity (Client ID and Client Secret).
3.  **Injection**: Secrets are fetched securely over TLS and injected as environment variables available **only** to the Java process.
4.  **Execution**: The Spring Boot application starts with full access to the required configuration without writing data to the file system.

## 3. Local Development Setup
Developers must install the Infisical CLI to run the backend locally. This ensures development environments mirror production security standards.

### Installation
* **MacOS**: `brew install infisical/tap/infisical`
* **Windows**: `winget install Infisical.Infisical`
* **Linux**:
    ```bash
    curl -1sLf '[https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh](https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh)' | sudo -E bash
    sudo apt-get update && sudo apt-get install -y infisical
    ```

### Running the Application
1.  **Authenticate**: Run `infisical login` in your terminal.
2.  **Select Project**: Choose 'Treishvaam Finance'.
3.  **Execute**:
    ```bash
    infisical run -- mvn spring-boot:run
    ```

## 4. Production Configuration
The production environment uses a Machine Identity for authentication. The server requires a single configuration file containing authentication tokens to establish trust.

### Server-Side Configuration
* **File Location**: `/opt/treishvaam/.env`
* **Permissions**: `600` (Read/Write by Owner only)

### Required Variables
| Variable | Description |
| :--- | :--- |
| `INFISICAL_URL` | The URL of the secret management server (e.g., https://app.infisical.com). |
| `INFISICAL_PROJECT_ID` | The unique identifier for the Treishvaam Finance project. |
| `INFISICAL_CLIENT_ID` | The Machine Identity Client ID (Public identifier). |
| `INFISICAL_CLIENT_SECRET` | The Machine Identity Client Secret (Private key). |

## 5. Secret Rotation Policy
To rotate a database password, API key, or internal secret:

1.  **Update**: Change the secret value in the Infisical Dashboard (Production Environment).
2.  **Restart**: Restart the backend service using Docker Compose:
    ```bash
    cd /opt/treishvaam
    docker-compose restart backend
    ```
3.  **Verify**: The application will fetch the new value immediately upon startup. No code changes or commits are required.