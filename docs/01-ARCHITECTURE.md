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

## Architecture Diagram
```mermaid
graph TD
    CF[Cloudflare Tunnel] --> NG[Nginx (Reverse Proxy + WAF)]
    NG --> API[Spring Boot Backend]
    API --> DB[MariaDB]
    API --> RD[Redis]
    API --> S3[MinIO Storage]
    NG --> KC[Keycloak (Auth)]
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
```

## Request Flow
1. **External Request**: A client request arrives at `backend.treishvaamgroup.com` and is routed through the Cloudflare Tunnel.
2. **Nginx Gateway**: The request hits Nginx, which acts as a reverse proxy and applies ModSecurity WAF rules for security.
3. **Routing**:
   - Requests to `/api/` are proxied to the Spring Boot backend.
   - Auth-related requests are routed to Keycloak.
   - Static assets or other endpoints are handled as configured.
4. **Backend Processing**: The Spring Boot application processes the request, interacting with MariaDB (data), Redis (cache), and MinIO (file storage) as needed.
5. **Observability**: Metrics, logs, and traces are collected by Prometheus, Loki, Tempo, and Alloy, and visualized in Grafana.
6. **Response**: The backend response is sent back through Nginx and Cloudflare to the client.

## Nginx as Reverse Proxy
Nginx serves as the secure entry point for all HTTP(S) traffic. For `/api/` requests:
- Nginx forwards the request to the Spring Boot backend on port 8080.
- It enforces security policies via ModSecurity (WAF).
- It can perform SSL termination, load balancing, and rate limiting as needed.
- Nginx also routes authentication requests to Keycloak and can serve static content or error pages.

---
This architecture ensures secure, scalable, and observable operations for Treishvaam Finance.