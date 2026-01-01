# System Architecture

## System Overview
Treishvaam Finance is an Enterprise-Grade Financial Intelligence Platform deployed on an Ubuntu Server (VirtualBox) using Docker Compose. The system implements a **Hybrid Static Site Generation (SSG)** architecture fortified by a **Strict Zero Trust Network** and the **Fort Knox Security Suite**.

**Key Architectural Security Feature:**
Unlike standard deployments, this system exposes **zero** internal ports. The database, cache, search engine, and storage services are **invisible** to the host machine and the public internet, accessible *only* via the internal Docker network (`treish_net`).

## System Components

### 1. Application Layer (The "Face")
* **Backend API**: Spring Boot 3.4 (Java 21)
    * **Port**: 8080 (Internal Only - Proxied by Nginx).
    * **Role**: Core business logic, OAuth2 resource server, data aggregation.
    * **Key Service**: **`HtmlMaterializerService`** - Generates static HTML files (Hybrid SSG) immediately upon post publication/update to ensure 100% SEO availability.
    * **Concurrency**: Utilizes **Java 21 Virtual Threads** for high-throughput, non-blocking image processing and parallel tasks.
    * **Resilience**: Integrated **Resilience4j Circuit Breakers** to handle external API failures gracefully.
* **Edge Worker**: Cloudflare Worker
    * **Role**: Intelligent **Edge Router** that decides between **Strategy A (Static HTML)** and **Strategy B (Dynamic Fallback)**. Handles security headers, `<base>` tag injection, and bot mitigation.

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
    * **Role**: Stores media uploads (images) AND **Materialized HTML** files for the SSG strategy.
* **Messaging**: RabbitMQ
    * **Networking**: Internal Event Bus. Ports 5672/15672 are removed.

### 3. Security Layer (The "Shield")
* **Identity Provider**: Keycloak 23
    * **Role**: Centralized Auth (SSO). Running internally, exposed only via Nginx Gateway.
* **Gateway**: Nginx + ModSecurity (OWASP CRS)
    * **Role**: The **ONLY** container with exposed ports (80/443). Handles WAF, Rate Limiting, SSL Termination, and **Static Asset Offloading** (for both Images and HTML).
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
        Google[GoogleBot]
    end

    subgraph Edge_Layer
        CF[Cloudflare Network]
        Worker[CF Worker (Router: Strategy A / B)]
        Tunnel[Cloudflare Tunnel]
    end

    subgraph Host_Server_Ubuntu ["Ubuntu Server (Docker Host)"]
        subgraph Exposed_Services
            NG[Nginx Gateway (Port 80/443)]
        end

        subgraph Internal_Treish_Net ["Docker Network (treish_net) - NO EXTERNAL ACCESS"]
            API[Spring Boot Backend]
            Materializer[HtmlMaterializerService]
            KC[Keycloak (Auth)]
            
            DB[(MariaDB)]
            RD[(Redis)]
            ES[(Elasticsearch)]
            S3[(MinIO Storage)]
            MQ[(RabbitMQ)]
            
            Log[Loki/Prometheus]
        end
    end

    Client --> CF --> Worker
    Google --> CF --> Worker
    Worker --> Tunnel --> NG
    Admin -- "Zero Trust Access" --> CF --> Tunnel --> Grafana/MinIO_Console
    
    NG --> API
    NG --> KC
    
    API --> Materializer
    Materializer -- "Upload Static HTML" --> S3
    
    API --> DB
    API -- "Read-Through Cache" --> RD
    API --> ES
    API -- "Writes (S3 Protocol)" --> S3
    NG -- "Reads (Static Offload: Images & HTML)" --> S3
    API --> MQ
    
    API -- "Logs/Metrics" --> Log
```

## Request Flow (Hybrid SSG Strategy)

**1. Edge Processing (Cloudflare Worker - The Router):**
A client request hits the Cloudflare Worker first. The Worker decides the serving strategy:

* **Strategy A: Materialized HTML (Primary)**
    * The Worker attempts to fetch the pre-generated HTML file from MinIO (via Nginx) at `/api/uploads/posts/{slug}.html`.
    * **HIT:** If found, it serves the static HTML immediately.
    * **Transformation:** It injects `<base href="/">` into the `<head>` to ensure relative assets (CSS/JS) load correctly even on deep URLs.
    * **Benefit:** Zero DB Load, Instant TTFB, 100% SEO Indexability.

* **Strategy B: Edge Hydration (Fallback)**
    * **MISS:** If the static file is missing (e.g., MinIO down, file not generated), the Worker calls the Spring Boot API (`/api/v1/posts/url/{id}`).
    * **Hydration:** It fetches the JSON data and injects it into `window.__PRELOADED_STATE__`.
    * **Benefit:** High Availability. The site works even if the static generation failed.

**2. Zero Trust Gateway (Nginx):**
The request emerges from the Tunnel and hits Nginx container (listening on port 80/443).
- **Static Asset Offloading**: READ requests for images OR static HTML (`/api/uploads/*`) are intercepted by Nginx and served **directly** from MinIO storage, bypassing the Java Backend entirely.
- **WAF**: ModSecurity inspects the payload for SQL Injection or XSS attacks.
- **Proxy**: API requests fall through to the Backend container.

**3. Backend Processing (Enterprise I/O Strategy):**
The Spring Boot application processes the request using advanced patterns:

-   **Publish-Time Materialization (Phase 6)**:
    -   When an admin clicks "Publish" or "Update", the `HtmlMaterializerService` activates.
    -   It fetches the React "Shell", injects the Article Content, Metadata, JSON-LD, and Preloaded State.
    -   It uploads this finalized `.html` file to MinIO. This is what makes Strategy A possible.

-   **Secure Streaming I/O**:
    -   **Memory Safety**: Large file uploads are streamed directly to `Files.createTempFile` preventing OOM crashes.
    -   **Security Validation**: **Apache Tika** enforces strict MIME type checking.

-   **Concurrency & Precision**:
    -   **Recursion Protection**: Entities (`BlogPost`, `PostThumbnail`) utilize `@JsonIgnoreProperties` to prevent infinite recursion during JSON serialization, ensuring API stability (preventing 500 errors).
    -   **Optimistic Locking**: Implements "Version Handshake" to prevent lost updates.
    -   **Circuit Breakers**: External API calls are protected by **Resilience4j**.

**4. Frontend Hydration (Phase 9 Fix):**
-   The React app uses `ReactDOM.createRoot` (instead of `hydrateRoot`) to handle the transition from the Static Server Content (`<div id="server-content">`) to the Interactive App (`<div id="root">`).
-   It automatically detects and removes the duplicate "Plain Text" content upon mounting to ensure a seamless visual experience.

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
See `docs/08-SEO-EDGE.md` for full details on how the Cloudflare Worker handles **Strategy A/B**, Bot Detection, and Meta Injection.