# Treishvaam Finance API

The Treishvaam Finance API is an enterprise-grade backend service built with Spring Boot 3.4 and Java 21. It serves as the core processing unit for the Treishvaam Finance Platform, handling market data aggregation, content management, and user authentication.

## System Overview

* **Framework**: Spring Boot 3.4
* **Language**: Java 21 (Eclipse Temurin)
* **Database**: MariaDB 10.6
* **Caching**: Redis (Cluster-ready)
* **Search Engine**: Elasticsearch
* **Object Storage**: MinIO (S3 Compatible)
* **Identity Management**: Keycloak (OAuth2 / OIDC)
* **Secret Management**: Infisical (Zero-Trust)

## Quick Start Guide

### Prerequisites
* Java Development Kit (JDK) 21
* Docker and Docker Compose
* Maven 3.9+
* Infisical CLI

### Configuration
This project adheres to strict security standards and does not use local configuration files for sensitive data. All secrets are managed via Infisical.

1.  **Install CLI**: Install the Infisical CLI tool.
2.  **Authenticate**: Run `infisical login` and authenticate using your organization credentials.
3.  **Link Project**: Link your local repository to the 'Treishvaam Finance' workspace.

### Building the Application
To compile the project and run unit tests:
```bash
mvn clean package
```

### Running Locally
To start the application with injected secrets:
```bash
infisical run -- java -jar target/finance-api.war
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

## Deployment
Deployment is fully automated using a GitOps workflow. Changes pushed to the `main` branch trigger the self-hosted runner to build and deploy the latest Docker containers.

For detailed deployment procedures, refer to:
`docs/09-DEPLOYMENT-OPS.md`

## Documentation
* **Architecture**: `docs/01-ARCHITECTURE.md`
* **API Reference**: `docs/03-BACKEND-API.md`
* **Database Schema**: `docs/05-DATABASE-SCHEMA.md`
* **Security Protocols**: `SECRETS.md`