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
* **Edge Worker**: Cloudflare Worker
    * **Role**: Global Edge Logic for **Edge-Side Hydration**, SEO injection, security headers, and bot mitigation.

### 2. Data Layer (The "Vault" - No Exposed Ports)
* **Database**: MariaDB 10.6
    * **Networking**: Accessible ONLY by `backend` and `keycloak`. Port 3306 is removed from host binding.
* **Cache**: Redis (Alpine)
    * **Networking**: Accessible ONLY by `backend`. Port 6379 is removed.
* **Search Engine**: Elasticsearch 8.17
    * **Networking**: Accessible ONLY by `backend`. Port 9200 is removed.
* **Object Storage**: MinIO (S3 Compatible)
    * **Networking**: Accessible ONLY by `backend` and `backup-service`. Ports 9000/9001 are removed.
* **Messaging**: RabbitMQ
    * **Networking**: Internal Event Bus. Ports 5672/15672 are removed.

### 3. Security Layer (The "Shield")
* **Identity Provider**: Keycloak 23
    * **Role**: Centralized Auth (SSO). Running internally, exposed only via Nginx Gateway.
* **Gateway**: Nginx + ModSecurity (OWASP CRS)
    * **Role**: The **ONLY** container with exposed ports (80/443). Handles WAF, Rate Limiting, and SSL Termination.
* **Tunnel**: Cloudflare Tunnel (`cloudflared`)
    * **Role**: Secure ingress for Admin Dashboards (Grafana, MinIO Console) without opening firewall ports.
* **Secrets Management**: Infisical
    * **Role**: Runtime injection of secrets into the `.env` file during deployment (Flash & Wipe strategy).

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
    API --> RD
    API --> ES
    API --> S3
    API --> MQ
    
    API -- "Logs/Metrics" --> Log
```

## Request Flow

**1. Edge Processing (Cloudflare Worker - Phase 2 Optimization):**
A client request hits the Cloudflare Worker first.
- **Edge Hydration (Zero Latency)**: For blog posts and market data pages, the Worker actively fetches the API data from the backend and injects it into the HTML head as `window.__PRELOADED_STATE__`. This eliminates the need for the browser to make a second API call, resulting in instant rendering.
- **Security**: Injects HSTS, X-Content-Type-Options, and strictly defined Content-Security-Policy (CSP) headers.
- **SEO**: Dynamic JSON-LD Schema injection for rich snippets.
- **Routing**: Traffic is routed through the Cloudflare Tunnel to the origin server.

**2. Zero Trust Gateway (Nginx):**
The request emerges from the Tunnel and hits Nginx container (listening on port 80/443).
- **WAF**: ModSecurity inspects the payload for SQL Injection or XSS attacks.
- **Proxy**: Nginx forwards valid requests to the Backend container via the internal `treish_net` network.

**3. Backend Processing (Enterprise I/O Strategy):**
The Spring Boot application processes the request.
- **Enterprise I/O Separation (Phase 1)**: 
    - **Network I/O**: Heavy operations like Image Uploads (to MinIO) are performed **outside** the database transaction boundary.
    - **Transaction**: The database transaction is opened *only* to persist metadata (URLs) after the upload succeeds ("Plan First, Commit Later").
    - **Concurrency**: Image resizing and processing are handled by **Java 21 Virtual Threads**, ensuring the main thread pool is never blocked by CPU-intensive tasks.
- **Rate Limiting**: The `RateLimitingFilter` checks the client IP against Redis buckets.
- **Service Mesh**: The app talks to Redis, MariaDB, and ElasticSearch using container hostnames.

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