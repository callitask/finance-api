---
root: true
project: Treishvaam Finance API
stack: [Java 21, Spring Boot 3.4, Docker, Nginx, Python 3, Redis, MinIO]
entry_points: [FinanceApiApplication.java, auto_deploy.sh, docker-compose.yml]
status: Active
---

# Treishvaam Finance API

The Treishvaam Finance API is a **High-Performance Enterprise Backend** built with Spring Boot 3.4 and Java 21. It serves as the "Financial Intelligence Engine" for the platform, engineered for **Zero Latency**, **Decimal Precision**, and **Zero Trust Security**.

Unlike standard CRUD apps, this system utilizes **Java 21 Virtual Threads**, **Off-Heap Streaming**, and a hybrid **Java/Python Architecture** to handle high-frequency market data and media processing without blocking.

## Development Status & Branching Strategy

The project follows a strict **GitFlow-inspired** branching strategy to ensure stability and rapid development.

| Branch | Role | Deployment Policy |
| :--- | :--- | :--- |
| **`main`** | **Production** | Protected. Represents the live, customer-facing code. Only stable releases are merged here. |
| **`staging`** | **Stable / Restore Point** | The "Golden Copy". Features are merged here when 100% complete. Acts as a fallback restore point if `develop` breaks. |
| **`develop`** | **Active Development** | Daily work happens here. Code is pushed frequently. The server automatically deploys this branch if it has the latest activity. |

## System Overview

### Core Technology Stack
* **Runtime**: Java 21 LTS (Temurin) with **Virtual Threads (Project Loom)** enabled.
* **Framework**: Spring Boot 3.4.0.
* **Database**: MariaDB 10.6 (Optimized with **JDBC Batching** `batch_size=50`).
* **Caching**: Redis 7 (Alpine) implementing **Read-Through** & **Cache-Aside** patterns with JSON Serialization.
* **Search**: Elasticsearch 8.17 (Real-time indexing via RabbitMQ).
* **Storage**: MinIO (S3 Protocol) with **Nginx Direct-Read Offloading**.
* **Market Engine**: Hybrid Java/Python 3 subsystem using `decimal.Decimal` for precision.
* **Security**: Keycloak 23 (OAuth2/OIDC) + Infisical (Secrets).

## Key Enterprise Features

This codebase implements advanced industry patterns to solve common scaling issues:

### 1. High-Performance I/O Architecture
* **Virtual Threads**: Uses `Executors.newVirtualThreadPerTaskExecutor()` for parallel image resizing and I/O-bound tasks, allowing thousands of concurrent operations without thread-pool exhaustion.
* **Secure Streaming (Zero-Allocation)**: File uploads are streamed directly to `Files.createTempFile` (Disk) via `InputStream`, bypassing RAM. This prevents `OutOfMemoryError` even under heavy load.
* **Transaction/IO Separation**: Implements the **"Plan First, Commit Later"** pattern. Network I/O (MinIO uploads) occurs *outside* the database transaction to prevent connection pool starvation.
* **Static Asset Offloading**: Nginx intercepts READ requests (`/api/uploads/**`) and serves files directly from MinIO with `immutable` caching headers. The Java backend is **never** blocked by serving static images.

### 2. Financial Data Precision & Integrity
* **IEEE 754 Compliance**: All monetary calculations utilize `BigDecimal` (Java) and `decimal.Decimal` (Python) with a 28-digit precision context to prevent floating-point errors.
* **Hybrid Engine**: Complex historical data analysis is offloaded to a secure Python subsystem (`market_data_updater.py`) injected with environment variables via `ProcessBuilder`.
* **Optimistic Locking**: Implements strict version control (`@Version`) on database entities. If two admins edit a post simultaneously, the second write is rejected with `409 Conflict`, preventing "Lost Updates".

### 3. Resilience & Stability
* **Circuit Breakers (Resilience4j)**:
    * **External APIs**: FMP/AlphaVantage calls have a 5s timeout and 50% failure threshold.
    * **Python Engine**: The market data script has a 120s timeout to prevent zombie processes.
* **Fail-Open Rate Limiting**: If Redis fails, the Rate Limiter (`Bucket4j`) is architected to "Fail Open", prioritizing application availability over restriction.
* **Database Batching**: Hibernate is configured to group `INSERT`/`UPDATE` statements into batches of 50, eliminating the "N+1" problem during bulk data syncs or sitemap generation.

### 4. Fort Knox Security
* **Zero-Trust Networking**: No internal ports (3306, 6379, 9200) are exposed to the host. All communication happens strictly within the `treish_net` Docker network.
* **Subprocess Security**: The Python Market Engine receives database credentials strictly via **Environment Variables**, ensuring passwords are never visible in the process table (`ps aux`).
* **MIME Validation**: **Apache Tika** analyzes binary signatures ("Magic Numbers") to reject spoofed file extensions (e.g., malware renamed as `.jpg`).
* **Secret Injection**: No hardcoded passwords. Secrets are injected into the process environment at runtime via Infisical.

### 5. Enterprise SEO & Edge Architecture
* **Materialized HTML (SSG)**: Implements "Publish-Time Static Generation". When an editor saves a post, the backend fetches the React shell, injects the full content body and JSON-LD schema, and uploads a static `.html` file to MinIO.
* **Edge Intelligence**: The Cloudflare Worker acts as a smart router, serving the pre-generated static HTML to bots and users (Strategy A) while falling back to API hydration (Strategy B) if necessary.
* **Zero-Flicker Hydration**: The React frontend detects server-injected content (`window.__PRELOADED_STATE__`) and uses `hydrateRoot` to attach event listeners without destroying the DOM.

## Quick Start Guide

### Prerequisites
* Java Development Kit (JDK) 21
* Docker & Docker Compose
* Maven 3.9+
* Infisical CLI (v0.154+)

### Configuration
This project follows the **12-Factor App** methodology.

1.  **Install CLI**: Install the Infisical CLI tool.
2.  **Link Project**:
    ```bash
    infisical login
    infisical init
    ```

### Building the Application
To compile the project and run unit tests:
```bash
mvn clean package -DskipTests=false
```

### Running Locally (Enterprise Mode)
We use **Orchestrator Injection**. Infisical injects secrets directly into the Docker process.

```bash
# This fetches secrets and starts the entire stack (DB, Cache, Backend, Monitor)
infisical run --env dev -- docker-compose up -d
```

## Architecture Modules

* **`com.treishvaam.financeapi.marketdata`**: The core financial engine. Handles the "Strategy Pattern" for data providers and manages the Python bridge with resilience.
* **`com.treishvaam.financeapi.service.BlogPostService`**: CMS logic implementing the "Plan First, Commit Later" transaction pattern and Optimistic Locking.
* **`com.treishvaam.financeapi.security`**: Custom Security Filters (`InternalSecretFilter`, `RateLimitingFilter`) and Keycloak integration.
* **`com.treishvaam.financeapi.config`**: Centralized configuration for Caching (Redis), Async Executors, and Web MVC.
* **`scripts/`**: DevOps automation (`auto_deploy.sh`) and the Python Market Engine (`market_data_updater.py`).

## CI/CD Automation (Dual-Engine)

We utilize a two-part automation engine to handle builds and deployments independently.

1.  **Build Engine (GitHub Actions)**:
    * **File**: `.github/workflows/deploy.yml`
    * **Role**: CI/CD Pipeline. Compiles Java code, runs tests, and securely transfers the `WAR` artifact.

2.  **State Engine (Watchdog Script)**:
    * **File**: `scripts/auto_deploy.sh`
    * **Role**: Runs on the Ubuntu Server. It is "Self-Healing"—automatically detecting branch changes (`main` vs `develop`) and syncing infrastructure configs (Nginx, Python) without manual intervention.

For detailed deployment procedures, refer to:
`docs/09-DEPLOYMENT-OPS.md`

## Documentation Index
* **Architecture**: `docs/01-ARCHITECTURE.md`
* **Core Logic**: `docs/02-BACKEND-CORE.md`
* **API Reference**: `docs/03-BACKEND-API.md`
* **Services Layer**: `docs/04-BACKEND-SERVICES.md`
* **Database Schema**: `docs/05-DATABASE-SCHEMA.md`
* **Edge & SEO**: `docs/08-SEO-EDGE.md`
* **Deployment Ops**: `docs/09-DEPLOYMENT-OPS.md`
* **Changelog**: `docs/10-CHANGELOG.md`
* **Security Protocols**: `SECRETS.md`