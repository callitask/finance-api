# 01-ARCHITECTURE.md

## System Overview
Treishvaam Finance is deployed on an Ubuntu Server (VirtualBox) using Docker Compose to orchestrate all core services. The system is designed for high availability, security, and observability, leveraging modern open-source technologies.

## Infrastructure & Services
- **Backend**: Spring Boot 3.4 (Java 21) — Port 8080
- **Database**: MariaDB — Port 3306
- **Cache**: Redis — Port 6379 (Configured for Resilience/Fail-Open)
- **Storage**: MinIO (S3 Compatible) — Port 9000
- **Identity**: Keycloak — Port 8080/auth
- **Gateway**: Nginx (with ModSecurity WAF) — Ports 80/443
- **Tunnel**: Cloudflare Tunnel (cloudflared) — Exposes Nginx to `backend.treishvaamgroup.com` securely via Token authentication.
- **Observability**: Grafana, Prometheus, Loki, Tempo, Alloy (LGTM Stack).
- **SEO Edge Logic**: Cloudflare Worker handles SEO, robots.txt, sitemaps, and meta/schema injection at the edge.
- **Messaging**: RabbitMQ with Dead Letter Exchange (DLX) for message reliability.
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
**External Request:** A client request arrives at `backend.treishvaamgroup.com` and is routed securely through the Cloudflare Tunnel.

**Nginx Gateway:** The request hits Nginx, which acts as a reverse proxy, handles CORS negotiation explicitly, and applies ModSecurity WAF rules.

**Routing:**
- Requests to `/api/` are proxied to the Spring Boot backend.
- Auth-related requests are routed to Keycloak.
- Static assets (Images/Sitemaps) are proxied directly from storage paths.

**Backend Processing:** The Spring Boot application processes the request, interacting with MariaDB (data), Redis (cache), MinIO (files), and RabbitMQ.

**Resilience (Fail-Open):**
- **Rate Limiting:** The API uses a Redis-backed rate limiter. It is configured to "Fail Open," meaning if Redis encounters disk/permission errors, the site **remains online** and allows traffic instead of crashing.

**Observability:** All services emit metrics, logs, and traces to the LGTM stack.

**Backup:** The Backup Service container performs automated encrypted backups to MinIO every 24 hours.

---

## Nginx as Reverse Proxy & Gateway
Nginx serves as the secure entry point for all HTTP(S) traffic.

**1. Gateway-Level CORS:**
Nginx is configured to explicitly handle Cross-Origin Resource Sharing (CORS).
- It intercepts `OPTIONS` (Pre-flight) requests and responds immediately with `Access-Control-Allow-Origin`.
- It injects permission headers into all backend responses.
- **Why?** This ensures that even if the Backend throws a 403 or 500 error (which might strip headers), the Browser still receives the "Permission" to view that error, preventing generic "Network Error" messages in the frontend.

**2. Web Application Firewall (ModSecurity):**
- Enforces OWASP Core Rules to block attacks (SQLi, XSS).
- **Whitelisting:** Specific endpoints like `/api/v1/monitoring/ingest` (Faro logs) and `/api/v1/posts/admin` are whitelisted to allow complex JSON payloads without triggering false positives.

---

## SEO Edge Logic (Cloudflare Worker)
The Cloudflare Worker acts as an edge layer for SEO and high-availability crawling:
- Intercepts requests for `/category/`, `/robots.txt`, `/sitemap.xml`, and `/feed.xml`.
- **Bot Detection:** Injects meta/schema tags dynamically if the visitor is a bot (Googlebot, Twitterbot).
- **High Availability:** Serves a fallback `robots.txt` even if the backend is offline, ensuring crawlers are never blocked by downtime.

See `08-SEO-EDGE.md` for full details.