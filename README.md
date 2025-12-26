---
root: true
project: Treishvaam Finance API
stack: [Java 21, Spring Boot 3.4, React, Docker, Nginx]
entry_points: [Main Class, Docker Compose, Auto Deploy Script]
status: Active
---

# Treishvaam Finance API

The Treishvaam Finance API is an enterprise-grade backend service built with Spring Boot 3.4 and Java 21. It serves as the core processing unit for the Treishvaam Finance Platform, handling market data aggregation, content management, and user authentication.

## Development Status & Branching Strategy

The project follows a strict **GitFlow-inspired** branching strategy to ensure stability and rapid development.

| Branch | Role | Deployment Policy |
| :--- | :--- | :--- |
| **`main`** | **Production** | Protected. Represents the live, customer-facing code. Only stable releases are merged here. |
| **`staging`** | **Stable / Restore Point** | The "Golden Copy". Features are merged here when 100% complete. Acts as a fallback restore point if `develop` breaks. |
| **`develop`** | **Active Development** | Daily work happens here. Code is pushed frequently. The server automatically deploys this branch if it has the latest activity. |

## System Overview

* **Framework**: Spring Boot 3.4
* **Language**: Java 21 (Eclipse Temurin)
* **Database**: MariaDB 10.6
* **Caching**: Redis (Cluster-ready)
* **Search Engine**: Elasticsearch
* **Object Storage**: MinIO (S3 Compatible)
* **Identity Management**: Keycloak (OAuth2 / OIDC)
* **Secret Management**: Infisical (Zero-Trust / Orchestrator Injection)

## Quick Start Guide

### Prerequisites
* Java Development Kit (JDK) 21
* Docker and Docker Compose
* Maven 3.9+
* Infisical CLI (v0.154+ via official repository)

### Configuration
This project adheres to strict security standards and does not use local configuration files for sensitive data. All secrets are managed via Infisical.

1.  **Install CLI**: Install the Infisical CLI tool using the official artifacts repository.
2.  **Authenticate**: Run `infisical login` and authenticate using your organization credentials.
3.  **Link Project**: Link your local repository to the 'Treishvaam Finance' workspace.

### Building the Application
To compile the project and run unit tests:
```bash
mvn clean package
```

### Running Locally (Enterprise Mode)
We use **Orchestrator Injection**. You do not need to configure `.env` files for application secrets. Infisical injects them directly into the Docker process from the host.

```bash
# This fetches secrets and starts the entire stack (DB, Cache, Backend)
infisical run --env dev -- docker-compose up -d
```

## Architecture and Design
The system follows a microservices-ready monolithic architecture, designed for scalability and resilience.

### Core Modules
* **Market Data Engine**: Ingests and normalizes real-time financial data from multiple providers.
* **Content Management System**: Handles blog posts, editorial workflows, and media assets.
* **Analytics Engine**: Processes telemetry and user engagement metrics.
* **Security Layer**: Enforces authentication and authorization policies via Keycloak.

### Observability
The platform integrates with the LGTM stack (Loki, Grafana, Tempo, Mimir) for comprehensive monitoring.
* **Metrics**: Prometheus endpoints exposed at `/actuator/prometheus`.
* **Logs**: Structured JSON logging with correlation IDs.
* **Tracing**: Distributed tracing via Zipkin/Tempo protocols.

## CI/CD Automation (Dual-Engine)
We utilize a two-part automation engine to handle builds and deployments independently.

1.  **Build Engine (GitHub Actions)**:
    * **File**: `.github/workflows/deploy.yml`
    * **Role**: Compiles the Java code (`mvn package`), runs tests, and transfers the `WAR` file to the server.
    * **Triggers**: Pushes to `main`, `staging`, or `develop`.

2.  **State Engine (Watchdog Script)**:
    * **File**: `scripts/auto_deploy.sh`
    * **Role**: Runs on the Ubuntu Server. Watches for changes in infrastructure files (Nginx, Configs, Python Scripts).
    * **Intelligence**: It automatically detects which branch (`main`, `staging`, `develop`) has the latest code and updates the server's local Git repository to match.

For detailed deployment procedures, refer to:
`docs/09-DEPLOYMENT-OPS.md`

## Documentation
* **Architecture**: `docs/01-ARCHITECTURE.md`
* **API Reference**: `docs/03-BACKEND-API.md`
* **Database Schema**: `docs/05-DATABASE-SCHEMA.md`
* **Security Protocols**: `SECRETS.md`