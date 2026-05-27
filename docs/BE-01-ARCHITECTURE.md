# 01 — System Architecture

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized)

---

## Overview

The Treishvaam Group Ecosystem is an **Enterprise-Grade Multi-Tenant Platform** deployed on an Ubuntu Server (VirtualBox) using Docker Compose. A **single shared Java Spring Boot backend** securely powers multiple decoupled Next.js frontend applications (Finance, Agro, Parent) deployed on Cloudflare Pages.

The system implements a **Hybrid Static Site Generation (SSG)** architecture fortified by a **Strict Zero-Trust Network**, an **Intelligent Cloudflare Edge**, and the **AEGIS Security Framework** (Adaptive Entropic Guardian Intelligence System).

**Key Architectural Security Feature:**
Zero internal ports are exposed to the host machine or public internet. The database, cache, search engine, object storage, ZKP microservice, and messaging broker are **invisible outside the internal Docker network** (`treish_net`), accessible only by authorized containers.

---

## System Components

### 1. Application Layer

#### Backend API — Spring Boot 3.4 (Java 21)
- **Internal Port:** 8080 (proxied exclusively by OpenResty — never exposed to host)
- **Role:** Core business logic, Multi-Tenant data aggregation, OAuth2 Resource Server, AEGIS filter execution
- **Tenant Isolation:** `TenantInterceptor` reads the `X-Tenant-ID` header injected by Edge Workers and locks all DB queries and service logic to the specific tenant context via `TenantContext` (ThreadLocal)
- **Security Engine:** Runs the AEGIS Byzantine Consensus Security Mesh (L8-BCSM) and Behavioral Intelligence Engine (L5-BIE) using Java 21 Virtual Threads to avoid blocking HTTP workers during parallel security validation
- **Key Services:**
  - `HtmlMaterializerService` — Generates static HTML files (Hybrid SSG) asynchronously on post publication for 100% SEO availability
  - `GeoOptimizationService` — Produces HMAC-signed AI-readable semantic payloads (`llms.txt`, `ai-feed.md`, `ontology.json`)
  - `SitemapService` — Generates multi-tenant, paginated XML sitemaps (designed for 10M+ URLs); includes GEO endpoint URLs at priority 1.0
  - `MarketDataService` — Hybrid Java + Python market data aggregation (Strategy Pattern over multiple providers)
  - Native Analytics Engine — Internal audience tracking via `audience_visits` table and GA4 BigQuery integration

#### Edge Workers — Cloudflare Workers
- **Finance:** `treishfin-seo-worker` — Route: `treishvaamfinance.com/*`
- **Agro:** `treishvaamagro-seo-worker` — Routes: `treishvaamagro.com/*`, `www.treishvaamagro.com/*`
- **Role:** Intelligent Edge Routers, Zero-Trust API Proxies, L4-ADA Deception Checkpoints, GEO bot interceptors
- **AEGIS Edge:** Validates requests via `HMAC-SHA-512` Edge Signature (`X-Aegis-Edge-Signature`) before forwarding to backend. Reads `aegis:mtd:manifest` from KV for Moving Target Defense path translation. Blocks known malicious IPs/JA3 hashes from KV without hitting the origin
- **SEO:** Injects E-E-A-T JSON-LD schemas via `HTMLRewriter`. Intercepts LLM crawlers (GPTBot, ClaudeBot, DeepSeek, etc.) and serves GEO payloads directly from KV cache

#### Frontends — Next.js 14 App Router (Cloudflare Pages)
- **Finance:** `treishvaam-finance-frontend` → `treishvaamfinance.com`
- **Agro:** `treishvaam-agro-frontend` → `treishvaamagro.com` (in development)
- **Parent:** `treishvaamgroup-frontend` → `treishvaamgroup.com`
- **Framework:** Next.js 14 App Router (`app/` directory). Migrated from Create React App (CRA). Legacy `src/pages/*.js` components are imported by `app/*/page.tsx` wrappers — they are NOT URL routes
- **Runtime:** Edge Runtime (`export const runtime = 'edge'` in `app/layout.tsx`)
- **Security:** Per-request cryptographic CSP nonce via `middleware.ts` (uses `btoa(crypto.randomUUID())` — Edge-safe, no `Buffer`)
- **Telemetry:** `AegisTelemetry.tsx` (L5-BIE biometric hashing), `WebVitalsTracker.tsx` (Core Web Vitals), Grafana Faro RUM

---

### 2. Data Layer — Zero Exposed Ports

| Service | Image | Role | Internal Network Only |
| :--- | :--- | :--- | :--- |
| **MariaDB** | `mariadb:10.6` | Primary relational DB | Yes — port 3306 not bound to host |
| **Redis** | `redis:7-alpine` | Read-through cache + AEGIS temporal path registry | Yes — port 6379 not bound to host |
| **Elasticsearch** | `elasticsearch:8.17.0` | Full-text search (`PostDocument`) | Yes — port 9200 not bound to host |
| **MinIO** | `minio/minio` | S3-compatible object storage (media + materialized HTML) | Yes — ports 9000/9001 not bound to host |
| **RabbitMQ** | `rabbitmq:3.12-management` | Async event bus (threat telemetry, sitemap triggers, DLX) | Yes — ports 5672/15672 not bound to host |

**MariaDB specifics:**
- Transparent Data Encryption (TDE) via `config/mariadb/encryption.cnf`
- JDBC Batching enabled (`batch_size=50`) for bulk write performance
- Optimistic Locking enforced on `blog_posts` (version column, V40 migration)
- All JDBC queries intercepted and HMAC-signed by `AegisQueryInterceptor` (SHA3-256)

**Redis specifics:**
- Read-through caching for market data widgets
- AEGIS temporal path registry (daily rotating manifests)
- `@Cacheable` is deliberately NOT applied to `Optional<T>` returning repository methods (Jackson deserialization crash prevention)

---

### 3. Security Layer

| Service | Image | Role |
| :--- | :--- | :--- |
| **Keycloak** | `keycloak:25.0.0` | Centralized SSO / Identity Provider — internal only, exposed via OpenResty |
| **aegis-zkp-service** | `treishvaam/aegis-zkp-service:latest` (compiled Go, distroless/scratch) | L3-ZKA Zero-Knowledge Proof verification on gRPC port 9090 (internal only). Strict 256M memory limit |
| **OpenResty** | `openresty/openresty:alpine` | **Only container with exposed ports (80/443).** WAF, TLS JA3 fingerprinting via Lua (`aegis_ja3.lua`), SSL termination, rate limiting, ModSecurity OWASP CRS |
| **cloudflared** | `cloudflare/cloudflared` | Secure ingress tunnel — no firewall ports opened |
| **aegis-canary-server** | `thinkst/canarytokens:latest` | Canary token management for L4-ADA deception |
| **wazuh-agent** | `wazuh/wazuh-agent:4.7.3` | HIDS — subscribes to RabbitMQ threat events |
| **Envoy** | `envoyproxy/envoy:v1.29-latest` | Internal L7 proxy (gRPC routing to ZKP service) |

---

### 4. Observability Layer — SSH-Tunnel Access Only

| Service | Image | Access Method |
| :--- | :--- | :--- |
| **Grafana** | `grafana/grafana:latest` | SSH tunnel → `localhost:3001` |
| **Prometheus** | `prom/prometheus:latest` | Internal scrape of Spring Boot actuator |
| **Loki** | `grafana/loki:2.9.2` | Log aggregation via Docker driver. Query label: `{job="varlogs"}` |
| **Promtail** | `grafana/promtail:2.9.2` | Log shipping to Loki |
| **Tempo** | `grafana/tempo:latest` | Distributed tracing (Zipkin-compatible endpoint) |

**ZERO-TRUST ACCESS:** Grafana and Prometheus UIs are never exposed to `0.0.0.0`. Access is strictly via SSH Local Port Forwarding.

---

## Architecture Data Flow

```
Client/Bot Request
      │
      ▼
Cloudflare DNS + WAF (DDoS, Bot Score, IP Reputation)
      │
      ├── www.* → 301 Cloudflare Bulk Redirect → apex domain
      │
      ▼
Cloudflare Edge Worker (treishfin-seo-worker)
      │
      ├── Known malicious IP/JA3 → KV tarpit marker → BLOCK or DECEPTION payload
      ├── AI/LLM crawler (GPTBot, ClaudeBot, DeepSeek, etc.) → KV GEO payload (/ai-feed.md, /llms.txt)
      ├── Sitemap/SEO request → KV TREISHFIN_SEO_CACHE (3-tier: CDN Cache → KV → Backend)
      │
      ├── Standard request → inject X-Tenant-ID header + HMAC-SHA-512 Edge Signature
      │       │
      │       ▼
      │   Cloudflare Pages (Next.js 14 Edge SSR)
      │       │
      │       └── API calls → Worker proxy → Cloudflare Tunnel → OpenResty (443)
      │                                                                │
      │                                                                ▼
      │                                             AegisEdgeValidationFilter (verify HMAC-SHA-512)
      │                                                                │
      │                                             AegisMainFilter (L8-BCSM Byzantine Consensus)
      │                                                                │
      │                                             Spring Security (OAuth2 Resource Server)
      │                                                                │
      │                                             Business Logic → MariaDB / Redis / Elasticsearch / MinIO
      │
      └── RabbitMQ (async) → CloudflareEdgeSyncService → Cloudflare KV (threat intel sync)
```

---

## Multi-Tenant Architecture Summary

One backend, multiple brands:

| Tenant ID | Frontend | Domain |
| :--- | :--- | :--- |
| `finance` | `treishvaam-finance-frontend` | `treishvaamfinance.com` |
| `agro` | `treishvaam-agro-frontend` | `treishvaamagro.com` |
| `public` (default fallback) | `treishvaamgroup-frontend` | `treishvaamgroup.com` |

The `X-Tenant-ID` header is injected by each Edge Worker and validated by `TenantInterceptor`. All DB queries, sitemaps, and service behavior are scoped to the tenant context.

---

## Cloudflare Edge Routing Rules (Managed in Dashboard — Not in Code)

**Rule 1 — www → apex 301 (Cloudflare Dynamic Redirect):**
```
Expression: (http.host in {"www.treishvaamfinance.com" "www.treishvaamgroup.com" "www.treishvaamagro.com"})
Target: concat("https://", substring(http.host, 4), http.request.uri.path)
Status: 301 | Preserve query string: ON
```

**Rule 1.5 — Legacy subdomain migration (treishvaamgroup.com zone):**
```
Expression: (http.host eq "treishfin.treishvaamgroup.com")
Target: concat("https://treishvaamfinance.com", http.request.uri.path)
Status: 301 | Preserve query string: ON
```

**Rule 2 — Bulk Redirect (Account-level list: `previewurl`):**
Only non-Worker-proxied frontends. Finance and Agro are EXCLUDED (Worker fetches their `.pages.dev` origin internally; a bulk redirect would create an infinite loop).
```
treishvaamgroup-frontend.pages.dev/ → https://treishvaamgroup.com/
Status: 301 | Include subdomains: ON | Subpath matching: ON | Preserve path suffix: ON
```

**ABSOLUTE RULE:** Canonicalization is handled exclusively at Cloudflare Edge. It must never be implemented in `_redirects`, `next.config.mjs`, `worker.js`, or backend code.
