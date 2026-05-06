/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - The authoritative architectural blueprint for the Treishvaam Group Ecosystem.
 * - Details the transition from a single-app backend to a Zero-Trust Multi-Tenant Engine serving multiple Next.js frontends (Finance, Agro, Parent).
 *
 * Scope:
 * - Covers Network Topology, Edge Routing, Application Layers, Data Layers, and Security Protocols.
 *
 * Critical Dependencies:
 * - Cloudflare Edge (Workers + KV Cache + Pages).
 * - Java Spring Boot Backend (Multi-Tenant Context).
 * - Nginx ModSecurity Gateway.
 *
 * Security Constraints:
 * - STRICT PORT ELIMINATION: Internal Docker services (DB, Redis, ES, MinIO, RabbitMQ) must NEVER expose ports to the host or internet.
 * - TENANT ISOLATION: The backend must strictly validate the `X-Tenant-ID` header via `TenantInterceptor`.
 * - ENVIRONMENT SECRETS: Docker Compose `.env` files must NEVER use single quotes ('') or double quotes ("") to prevent HikariCP parse failures.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED: Initial Treishvaam Finance SSG Architecture.
 * - EDITED:
 * • Phase 3 Update: Expanded to Multi-Tenant Architecture.
 * • Added Edge Worker KV Caching and API Reverse Proxy flow.
 * • Added TenantInterceptor and contextual SitemapService routing.
 * • Documented SPA Fallback logic (404 -> 200 OK) at the Edge.
 * • Added strict Docker Compose .env quoting constraints based on deployment crash diagnostics.
 * - EDITED:
 * • Documented Native Analytics & API Diagnostics within the Application Layer.
 * • Added Zero-Trust Third-Party Tagging architecture to the Edge and Security scopes.
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted,
 * truncated, rewritten, or regenerated.
 * Future AI must append only.
 */

# System Architecture

## System Overview
The Treishvaam Group Ecosystem is an Enterprise-Grade Multi-Tenant Platform deployed on an Ubuntu Server (VirtualBox) using Docker Compose. A **single shared Java Spring Boot backend** securely powers multiple decoupled Next.js frontend applications (Finance, Agro, Parent) deployed on Cloudflare Pages.

The system implements a **Hybrid Static Site Generation (SSG)** architecture fortified by a **Strict Zero Trust Network**, an **Intelligent Cloudflare Edge**, and the **Fort Knox Security Suite**.

**Key Architectural Security Feature:**
Unlike standard deployments, this system exposes **zero** internal ports. The database, cache, search engine, and storage services are **invisible** to the host machine and the public internet, accessible *only* via the internal Docker network (`treish_net`).

## System Components

### 1. Application Layer (The "Engine" & The "Face")
* **Backend API**: Spring Boot 3.4 (Java 21)
    * **Port**: 8080 (Internal Only - Proxied by Nginx).
    * **Role**: Core business logic, Multi-Tenant data aggregation, OAuth2 resource server.
    * **Tenant Isolation**: Uses `TenantInterceptor` to intercept the `X-Tenant-ID` header injected by Edge Workers, locking database queries and services (like `SitemapService`) to the specific frontend context.
    * **Key Services**: 
        * **`HtmlMaterializerService`** - Generates static HTML files (Hybrid SSG) immediately upon post publication to ensure 100% SEO availability.
        * **Native Analytics Engine** - Implements Google Analytics 4 (GA4) integration for historical data and audience dashboard queries, with transactional safeguards and data enrichment.
        * **API Diagnostics** - Tracks external API health and latency using the `ApiFetchStatus` entity for logging and monitoring.
    * **Concurrency**: Utilizes **Java 21 Virtual Threads** for high-throughput, non-blocking image processing and parallel tasks. 
* **Edge Workers**: Cloudflare Workers (`treishvaamagro-seo-worker`, `treishfin-seo-worker`)
    * **Role**: Intelligent **Edge Routers** and **Zero-Trust API Proxies**. 
    * **SEO Payload**: Injects E-E-A-T JSON-LD schemas directly into HTML via `HTMLRewriter`.
    * **SPA Fallback**: Intercepts 404/403 errors on static routes and rewrites them to `200 OK` (delivering `index.html`) to eliminate GSC Soft 404s.
* **Frontends**: Next.js/React (Cloudflare Pages)
    * **Zero-Trust Tagging**: Frontends natively execute a 0ms TBT (Total Blocking Time) Idle Strategy for Analytics and Ads, relying entirely on dynamically injected Cloudflare Environment Variables (e.g., `REACT_APP_GA_MEASUREMENT_ID`).

### 2. Data Layer (The "Vault" - No Exposed Ports)
* **Database**: MariaDB 10.6
    * **Networking**: Accessible ONLY by `backend` and `keycloak`. Port 3306 is removed from host binding.
    * **Optimization**: Enabled **JDBC Batching** (`batch_size=50`) for high-performance bulk writes.
    * **Integrity**: Enforces **Optimistic Locking** using `@Version` columns.
* **Cache**: Redis (Alpine) & Cloudflare KV
    * **Internal (Redis)**: Read-Through Caching. Port 6379 removed.
    * **Edge (Cloudflare KV)**: The `TREISHFIN_SEO_CACHE` namespace serves dynamic `sitemap.xml` files instantly. Cache misses securely proxy to the backend and update asynchronously (`ctx.waitUntil`).
* **Search Engine**: Elasticsearch 8.17
    * **Networking**: Accessible ONLY by `backend`. Port 9200 removed.
* **Object Storage**: MinIO (S3 Compatible)
    * **Networking**: Accessible ONLY by `backend` and `nginx`. Ports 9000/9001 removed.
    * **Role**: Stores media uploads AND **Materialized HTML** files for the SSG strategy.
* **Messaging**: RabbitMQ
    * **Networking**: Internal Event Bus. Ports 5672/15672 removed.

### 3. Security Layer (The "Shield")
* **Identity Provider**: Keycloak 23
    * **Role**: Centralized Auth (SSO). Running internally, exposed only via Nginx Gateway.
* **Gateway**: Nginx + ModSecurity (OWASP CRS)
    * **Role**: The **ONLY** container with exposed ports (80/443). Handles WAF, Rate Limiting, SSL Termination, and **Static Asset Offloading**.
* **Tunnel**: Cloudflare Tunnel (`cloudflared`)
    * **Role**: Secure ingress for API traffic without opening firewall ports.
* **Secrets Management (DevOps Constraint)**:
    * **Injection**: Secrets are injected via Environment Variables. 
    * **CRITICAL TRAP**: Docker Compose `.env` files must **NEVER** use single quotes (`'`) or double quotes (`"`).

### 4. Observability Layer (The "Eyes")
* **Loki**: Log Aggregation (Internal). Logs explicitly tag `tenantId` via MDC.
* **Tempo**: Distributed Tracing (Internal).
* **Prometheus**: Metrics Collection (Internal).
* **Grafana**: Visualization Dashboard (Accessed via Cloudflare Tunnel).

## Architecture Diagram
```mermaid
graph TD
    subgraph Public_Internet
        Client[Client (Browser/Mobile)]
        Google[GoogleBot / Crawlers]
    end

    subgraph Cloudflare_Edge ["Cloudflare Edge (Zero-Trust)"]
        CF_DNS[Cloudflare DNS / Rules]
        
        subgraph Workers
            WorkerAgro[Agro SEO Worker]
            WorkerFin[Finance SEO Worker]
        end
        
        KV[(TREISHFIN_SEO_CACHE)]
        
        subgraph Pages
            PagesAgro[treishvaam-agro.pages.dev]
            PagesFin[treishvaam-finance.pages.dev]
        end
        
        Tunnel[Cloudflare Tunnel]
    end

    subgraph Host_Server_Ubuntu ["Ubuntu Server (Docker Host)"]
        subgraph Exposed_Services
            NG[Nginx Gateway (WAF / Port 80/443)]
        end

        subgraph Internal_Treish_Net ["Docker Network (treish_net) - NO EXTERNAL ACCESS"]
            API[Spring Boot Backend <br/> *TenantInterceptor*]
            Materializer[HtmlMaterializerService]
            KC[Keycloak (Auth)]
            
            DB[(MariaDB)]
            RD[(Redis)]
            ES[(Elasticsearch)]
            S3[(MinIO Storage)]
            MQ[(RabbitMQ)]
        end
    end

    %% Edge Flow
    Client --> CF_DNS
    Google --> CF_DNS
    CF_DNS --> WorkerAgro
    CF_DNS --> WorkerFin
    
    %% Worker Logic
    WorkerAgro -- "Hit" --> KV
    WorkerAgro -- "Proxy Frontend" --> PagesAgro
    WorkerAgro -- "Proxy API (X-Tenant-ID: agro)" --> Tunnel
    
    WorkerFin -- "Proxy API (X-Tenant-ID: finance)" --> Tunnel

    %% Backend Flow
    Tunnel --> NG
    NG --> API
    NG --> KC
    
    API --> Materializer
    Materializer -- "Upload Static HTML" --> S3
    
    API --> DB
    API -- "Read-Through Cache" --> RD
    API --> ES
    API -- "Writes (S3 Protocol)" --> S3
    NG -- "Reads (Static Offload)" --> S3
    API --> MQ
```

## Request Flow Profiles

### 1. The Multi-Tenant API Flow (Zero-Trust Proxy)
All frontends execute API calls strictly via relative paths (`/api/v1/...`).
1. **Interception**: The Edge Worker intercepts the request.
2. **Context Injection**: The Worker injects `X-Tenant-ID: <tenant_name>` and securely proxies the request to the `BACKEND_ORIGIN`.
3. **Validation**: The Spring Boot `TenantInterceptor` strips and validates the header, assigning the `TenantContext`.
4. **Execution**: Services dynamically alter logic based on the tenant, ensuring absolute data isolation.

### 2. Edge Sitemap Caching Flow (Free-Tier Optimized)
1. Request for `/sitemap.xml` hits the Edge Worker.
2. Worker checks `TREISHFIN_SEO_CACHE`.
3. **HIT**: Serves XML instantly.
4. **MISS**: Worker proxies request to backend with `X-Tenant-ID`. Backend generates the XML. Worker intercepts the response, serves it, and asynchronously updates the KV via `ctx.waitUntil`.

### 3. Hybrid SSG Strategy (Finance & Content)
* **Strategy A (Materialized HTML)**: The Worker attempts to fetch a pre-generated HTML file from MinIO (via Nginx). If found, it injects a `<base href="/">` tag and serves it instantly.
* **Strategy B (Edge Hydration)**: If the static file is missing, the Worker fetches raw JSON data, injects it into `window.__PRELOADED_STATE__`, and serves the React SPA shell for seamless hydration.

---

## Nginx as Reverse Proxy & Gateway
Nginx is the **only** container with exposed ports (80/443).

**1. Gateway-Level CORS:**
Nginx explicitly handles Cross-Origin Resource Sharing (CORS) across multi-tenant domains.

**2. Web Application Firewall (ModSecurity):**
- Enforces OWASP Core Rules to block attacks (SQLi, XSS).
- **Whitelisting**: Specific endpoints like `/api/v1/monitoring/ingest` (Faro logs) are whitelisted.
- **Static Asset Offloading**: Nginx directly intercepts and serves images/HTML from MinIO, bypassing the Java layer to conserve JVM memory.