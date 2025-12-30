# System Architecture

## System Overview
Treishvaam Finance is an Enterprise-Grade Financial Intelligence Platform deployed on an Ubuntu Server (VirtualBox) using Docker Compose. The system implements a **Strict Zero Trust Network** architecture fortified by the **Fort Knox Security Suite**.

**Key Architectural Security Feature:**
Unlike standard deployments, this system exposes **zero** internal ports. The database, cache, search engine, and storage services are **invisible** to the host machine and the public internet, accessible *only* via the internal Docker network (`treish_net`).

## System Components

### 1. Application Layer (The "Face")
* **Backend API**: Spring Boot 3.4 (Java 21)
    * **Port**: 8080 (Internal Only - Proxied by Nginx).
    * **Role**: Core business logic, OAuth2 resource server, data aggregation.
    * **Concurrency**: Utilizes **Java 21 Virtual Threads** for high-throughput, non-blocking image processing and parallel tasks.
    * **Resilience**: Integrated **Resilience4j Circuit Breakers** to handle external API failures gracefully.
* **Edge Worker**: Cloudflare Worker
    * **Role**: Global Edge Logic for **Edge-Side Hydration**, SEO injection, security headers, and bot mitigation.

### 2. Data Layer (The "Vault" - No Exposed Ports)
* **Database**: MariaDB 10.6
    * **Networking**: Accessible ONLY by `backend` and `keycloak`. Port 3306 is removed from host binding.
    * **Optimization**: Enabled **JDBC Batching** (`batch_size=50`) for high-performance bulk writes.
    * **Integrity**: Enforces **Optimistic Locking** using `@Version` columns to prevent lost updates.
* **Cache**: Redis (Alpine)
    * **Networking**: Accessible ONLY by `backend`. Port 6379 is removed.
    * **Strategy**: **Read-Through Caching**. The service layer transparently serves read-heavy data (Articles, Market Widgets) from Redis, hitting the DB only on misses.
* **Search Engine**: Elasticsearch 8.17
    * **Networking**: Accessible ONLY by `backend`. Port 9200 is removed.
* **Object Storage**: MinIO (S3 Compatible)
    * **Networking**: Accessible ONLY by `backend` and `nginx`. Ports 9000/9001 are removed.
* **Messaging**: RabbitMQ
    * **Networking**: Internal Event Bus. Ports 5672/15672 are removed.

### 3. Security Layer (The "Shield")
* **Identity Provider**: Keycloak 23
    * **Role**: Centralized Auth (SSO). Running internally, exposed only via Nginx Gateway.
* **Gateway**: Nginx + ModSecurity (OWASP CRS)
    * **Role**: The **ONLY** container with exposed ports (80/443). Handles WAF, Rate Limiting, SSL Termination, and **Static Asset Offloading**.
* **Tunnel**: Cloudflare Tunnel (`cloudflared`)
    * **Role**: Secure ingress for Admin Dashboards (Grafana, MinIO Console) without opening firewall ports.
* **Secrets Management**: Environment Injection
    * **Role**: Runtime injection of secrets into the `.env` file during deployment (Flash & Wipe strategy), completely removing hardcoded credentials from the codebase.

### 4. Observability Layer (The "Eyes")
* **Loki**: Log Aggregation (Internal).
* **Tempo**: Distributed Tracing (Internal).
* **Prometheus**: Metrics Collection (Internal).
* **Grafana**: Visualization Dashboard (Accessed via Cloudflare Tunnel).

## Fort Knox Security Protocols

**1. "Dark Mode" Networking (Port Elimination)**
We do not rely on firewalls alone. We rely on Docker's network isolation.
* **Config**: In `docker-compose.yml`, the `ports:` directive is commented out for all data services.
* **Effect**: Even if the UFW firewall is disabled, the databases remain inaccessible from the internet.

**2. Hardcoded Secret Elimination**
* **Strategy**: All sensitive credentials (DB passwords, API Keys, MinIO Secrets) are replaced with Environment Variables (`${...}`) in `application-prod.properties`.
* **Injection**: Variables are passed explicitly to containers via the `environment` block in Docker Compose.

**3. IP Defense Strategy**
* **Layer 1 (Edge)**: Cloudflare (DDoS Protection, Bot Fight Mode).
* **Layer 2 (Gateway)**: Nginx ModSecurity (SQLi/XSS Blocking).
* **Layer 3 (App)**: Spring Boot `RateLimitingFilter` (Bucket4j) blocks abusive IPs before they reach business logic.

## Architecture Diagram
```mermaid
graph TD
    subgraph Public_Internet
        Client[Client (Browser/Mobile)]
        Admin[Admin User]
    end

    subgraph Edge_Layer
        CF[Cloudflare Network]
        Worker[CF Worker (Edge Hydration & SEO)]
        Tunnel[Cloudflare Tunnel]
    end

    subgraph Host_Server_Ubuntu ["Ubuntu Server (Docker Host)"]
        subgraph Exposed_Services
            NG[Nginx Gateway (Port 80/443)]
        end

        subgraph Internal_Treish_Net ["Docker Network (treish_net) - NO EXTERNAL ACCESS"]
            API[Spring Boot Backend]
            KC[Keycloak (Auth)]
            
            DB[(MariaDB)]
            RD[(Redis)]
            ES[(Elasticsearch)]
            S3[(MinIO Storage)]
            MQ[(RabbitMQ)]
            
            Log[Loki/Prometheus]
        end
    end

    Client --> CF --> Worker --> Tunnel --> NG
    Admin -- "Zero Trust Access" --> CF --> Tunnel --> Grafana/MinIO_Console
    
    NG --> API
    NG --> KC
    
    API --> DB
    API -- "Read-Through Cache" --> RD
    API --> ES
    API -- "Writes (S3 Protocol)" --> S3
    NG -- "Reads (Static Offload)" --> S3
    API --> MQ
    
    API -- "Logs/Metrics" --> Log
```

## Request Flow

**1. Edge Processing (Cloudflare Worker - Phase 3 Optimization):**
A client request hits the Cloudflare Worker first.
- **Edge Hydration (Zero Latency)**: For blog posts and market data pages, the Worker actively fetches the API data from the backend and injects it into the HTML head as `window.__PRELOADED_STATE__`. This eliminates the need for the browser to make a second API call, resulting in instant rendering.
- **Security**: Injects HSTS, X-Content-Type-Options, and strictly defined Content-Security-Policy (CSP) headers.
- **SEO**: Dynamic JSON-LD Schema injection for rich snippets.
- **Routing**: Traffic is routed through the Cloudflare Tunnel to the origin server.

**2. Zero Trust Gateway (Nginx):**
The request emerges from the Tunnel and hits Nginx container (listening on port 80/443).
- **Static Asset Offloading (Phase 1)**: READ requests for images (`/api/uploads/*`) are intercepted by Nginx and served **directly** from MinIO storage, bypassing the Java Backend entirely. This reduces JVM load and latency.
- **WAF**: ModSecurity inspects the payload for SQL Injection or XSS attacks.
- **Proxy**: Nginx forwards API requests to the Backend container via the internal `treish_net` network.

**3. Backend Processing (Enterprise I/O Strategy):**
The Spring Boot application processes the request using advanced patterns:

- **Secure Streaming I/O (Phase 1)**:
    - **Memory Safety**: Large file uploads are streamed directly to `Files.createTempFile` instead of loading into RAM (`byte[]`), preventing Out-Of-Memory (OOM) crashes under load.
    - **Security Validation**: **Apache Tika** analyzes the binary signature (magic numbers) of uploads to strictly enforce MIME types, rejecting spoofed extensions (e.g., `.exe` renamed to `.jpg`).
    - **Transaction Boundary**: Network I/O to MinIO happens **outside** the database transaction. The DB transaction opens *only* after a successful upload ("Plan First, Commit Later").

- **Concurrency & Precision (Phase 2 & 3)**:
    - **Optimistic Locking**: Implements a strict "Version Handshake". Updates must include the version number of the record being edited.
    - **Conflict Resolution**: If the version in the DB is newer than the client's version (indicating another admin saved changes), the request is rejected with **409 Conflict**, preventing "Lost Updates".
    - **Circuit Breakers**: External API calls (FMP, AlphaVantage) and the Python Market Engine are protected by **Resilience4j**. If a service is slow, the circuit opens to prevent thread pool exhaustion, serving fallback/stale data instead of crashing.
    - **Financial Precision**: All monetary calculations utilize `BigDecimal` (Java) and `decimal.Decimal` (Python) to ensure 8-decimal precision and prevent IEEE 754 floating-point errors.

- **High-Performance Database I/O (Phase 1)**:
    - **JDBC Batching**: Hibernate is configured to group INSERT/UPDATE statements into batches of 50. This prevents the "N+1 Select/Insert" problem during bulk operations like Sitemap generation or Market Data backfilling.
    - **Read-Through Cache**: API read operations hit Redis first. If the key exists, data is returned in **<5ms**.

**4. Admin Access (Grafana/MinIO):**
Admins access dashboards (Grafana, MinIO Console) via **Cloudflare Tunnel Public Hostnames** (e.g., `grafana.treishfin.treishvaamgroup.com`). This puts them behind Cloudflare Access (SSO), eliminating the need for open ports.

---

## Nginx as Reverse Proxy & Gateway
Nginx is the **only** container with exposed ports (80/443).

**1. Gateway-Level CORS:**
Nginx is configured to explicitly handle Cross-Origin Resource Sharing (CORS).
- It intercepts `OPTIONS` (Pre-flight) requests and responds immediately with `Access-Control-Allow-Origin`.

**2. Web Application Firewall (ModSecurity):**
- Enforces OWASP Core Rules to block attacks (SQLi, XSS).
- **Whitelisting**: Specific endpoints like `/api/v1/monitoring/ingest` (Faro logs) are whitelisted.

---

## SEO Edge Logic
See `docs/08-SEO-EDGE.md` for full details on how the Cloudflare Worker handles **Edge Hydration**, Bot Detection, and Meta Injection.