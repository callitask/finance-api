# 01-ARCHITECTURE.md

## System Overview
Treishvaam Finance is deployed on an Ubuntu Server (VirtualBox) using Docker Compose to orchestrate all core services. The system is designed for high availability, security, and observability, leveraging modern open-source technologies.

## Infrastructure & Services
- **Backend**: Spring Boot 3.4 (Java 21) — Port 8080
- **Database**: MariaDB — Port 3306
- **Cache**: Redis — Port 6379
- **Storage**: MinIO (S3 Compatible) — Port 9000
- **Identity**: Keycloak — Port 8080/auth
- **Gateway**: Nginx (with ModSecurity WAF) — Ports 80/443
- **Tunnel**: Cloudflare Tunnel (cloudflared) — Exposes Nginx to `backend.treishvaamgroup.com`
- **Observability**: Grafana, Prometheus, Loki, Tempo, Alloy
- **SEO Edge Logic**: Cloudflare Worker handles SEO, robots.txt, sitemaps, and meta/schema injection at the edge for high-availability crawling and rendering.
- **Messaging**: RabbitMQ with Dead Letter Exchange (DLX) for message reliability and failure handling.
- **Backup Service**: Dedicated container for automated encrypted database backups and PITR.

## Architecture Diagram
```mermaid
graph TD
    CF[Cloudflare Tunnel] --> NG[Nginx (Reverse Proxy + WAF/ModSecurity)]
    NG --> API[Spring Boot Backend]
    API --> DB[MariaDB]
    API --> RD[Redis]
    API --> S3[MinIO Storage]
    NG --> KC[Keycloak (Auth)]
    API --> MQ[RabbitMQ]
    MQ --> DLX[DLX (Dead Letter Exchange)]
    subgraph Observability
        G[Grafana]
        P[Prometheus]
        L[Loki]
        T[Tempo]
        A[Alloy]
    end
    API --> P
    API --> L
    API --> T
    API --> A
    G --> P
    G --> L
    G --> T
    G --> A
    API --> BU[Backup Service]
```

### Request Flow
**External Request:** A client request arrives at backend.treishvaamgroup.com and is routed through the Cloudflare Tunnel.

**Nginx Gateway:** The request hits Nginx, which acts as a reverse proxy and applies ModSecurity WAF rules for security.

**Routing:**
- Requests to `/api/` are proxied to the Spring Boot backend.
- Auth-related requests are routed to Keycloak.
- Static assets or other endpoints are handled as configured.

**Backend Processing:** The Spring Boot application processes the request, interacting with MariaDB (data), Redis (cache), MinIO (file storage), and RabbitMQ (messaging with DLX for reliability) as needed.

**Observability:** All services emit metrics, logs, and traces to the PLG+T stack (Grafana, Prometheus, Loki, Tempo, Alloy).

**Backup:** The Backup Service container performs automated encrypted backups and supports PITR.

---

## Nginx as Reverse Proxy
Nginx serves as the secure entry point for all HTTP(S) traffic. For `/api/` requests:
- Nginx forwards the request to the Spring Boot backend on port 8080.
- It enforces security policies via ModSecurity (WAF).

**CORS Handling:** Nginx acts as a Transparent Proxy for CORS. It does not inject `Access-Control-Allow-Origin` headers itself. Instead, it allows the Spring Boot Security configuration to handle the handshake. This prevents "Double Header" conflicts and ensures complex headers (like `x-faro-session-id`) are correctly validated by the application logic.

Nginx routes authentication requests to Keycloak and supports `X-Forwarded-*` headers for correct IP resolution.

---

## SEO Edge Logic (Cloudflare Worker)
The Cloudflare Worker acts as an edge layer for SEO and high-availability crawling:
- Intercepts requests for `/category/`, `/robots.txt`, `/sitemap.xml`, and injects meta/schema tags or serves/proxies critical SEO files.
- Ensures search engines and social media always receive valid metadata, sitemaps, and robots.txt, even if the backend is down.

See `08-SEO-EDGE.md` for full details.

---

This architecture ensures secure, scalable, observable, and SEO-optimized operations for Treishvaam Finance.