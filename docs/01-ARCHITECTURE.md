# System Architecture

## System Overview
Treishvaam Finance is deployed on an Ubuntu Server (VirtualBox) using Docker Compose to orchestrate all core services. The architecture implements a **Zero Trust Network**, ensuring that no database, cache, or internal service is directly accessible from the public internet.

## System Components
* **Application Layer**:
    * **Backend**: Spring Boot 3.4 (Java 21) — Internal Port 8080 (Proxied by Nginx).
    * **Worker**: Cloudflare Worker (Edge Logic for SEO/Routing/Security).
* **Data Layer** (Internal Access Only):
    * **Database**: MariaDB — Port 3306 (Closed to Internet).
    * **Cache**: Redis — Port 6379 (Closed to Internet).
    * **Search**: Elasticsearch 8.17 — Port 9200 (Closed to Internet).
    * **Storage**: MinIO — Port 9000 (API) / 9001 (Console) (Closed to Internet).
    * **Messaging**: RabbitMQ — Port 5672 (AMQP) / 15672 (Mgmt) (Closed to Internet).
* **Security Layer**:
    * **Identity**: Keycloak — Port 8080/auth (Internal).
    * **WAF**: Nginx + ModSecurity (OWASP Rules).
    * **Tunnel**: Cloudflare Tunnel (Secure Admin Access).
    * **Secrets**: Infisical (Machine Identity Injection).
* **Automation Layer**:
    * **Build**: GitHub Actions (Runner).
    * **Watchdog**: Bash Script (`auto_deploy.sh`) for branch monitoring, self-healing, and "Flash & Wipe" secret injection.

## Architecture Diagram
```mermaid
graph TD
    subgraph Public_Internet
        Client[Browser/App]
    end

    subgraph Edge_Layer
        CF[Cloudflare Network] --> Worker[CF Worker (Security + SEO)]
        Worker --> Tunnel[Cloudflare Tunnel]
    end

    subgraph Internal_Docker_Network ["Docker Network (treish_net)"]
        Tunnel --> NG[Nginx (Gateway + WAF)]
        NG --> API[Spring Boot Backend]
        API --> DB[MariaDB]
        API --> RD[Redis]
        API --> ES[Elasticsearch]
        API --> S3[MinIO Storage]
        NG --> KC[Keycloak (Auth)]
        API --> MQ[RabbitMQ]
    end

    Client -- HTTPS (443) --> CF
```

## Request Flow
**1. Edge Processing (Cloudflare Worker):**
A client request hits the Cloudflare Worker first.
- **Security**: The Worker injects HSTS, X-Frame-Options, and Content-Security-Policy headers.
- **SEO**: If the visitor is a bot, the Worker fetches metadata and injects it into the HTML.
- **Routing**: Traffic is routed through the Cloudflare Tunnel to the origin server.

**2. Zero Trust Gateway (Nginx):**
The request emerges from the Tunnel and hits Nginx container (listening on port 80/443).
- **WAF**: ModSecurity inspects the payload for SQL Injection or XSS attacks.
- **Proxy**: Nginx forwards valid requests to the Backend container via the internal `treish_net` network.

**3. Backend Processing:**
The Spring Boot application processes the request. It talks to Redis, MariaDB, and ElasticSearch using their **container hostnames** (e.g., `treishvaam-redis`).
- **Isolation**: Since `ports` are removed in `docker-compose.yml`, these services are completely invisible to port scanners on the public internet.

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
See `docs/08-SEO-EDGE.md` for full details on how the Cloudflare Worker handles Bot Detection and Meta Injection.