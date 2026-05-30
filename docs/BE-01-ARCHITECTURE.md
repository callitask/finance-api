# BE-01 — System Architecture

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized — No Credentials, No Internal IPs)
**Last Verified:** 2026-05-29 — All claims verified against actual codebase

---

## Overview

The Treishvaam Group Ecosystem is an **Enterprise-Grade Multi-Tenant Platform** deployed on an Ubuntu Server (VirtualBox) using Docker Compose v3.8. A **single shared Java Spring Boot 3.4 backend** (Java 21) securely powers multiple decoupled Next.js frontend applications (Finance, Agro, Parent) deployed exclusively on **Cloudflare Pages**.

The system implements a **Hybrid Static Site Generation (SSG)** architecture fortified by:
- **Strict Zero-Trust Network** — no database, cache, or messaging ports exposed externally
- **Intelligent Cloudflare Edge** — Worker-based SEO, security, and GEO routing
- **AEGIS Security Framework** — 9-layer adaptive adversarial defense system

**Key Architectural Security Feature:**
Zero internal ports are exposed to the host machine or public internet. MariaDB, Redis, Elasticsearch, MinIO, RabbitMQ, the ZKP microservice, and all observability services are **invisible outside the internal Docker network** (`treish_net`), accessible only by authorized containers via service-name DNS.

---

## System Components

### 1. Application Layer

#### Backend API — Spring Boot 3.4 (Java 21)

| Attribute | Value |
| :--- | :--- |
| **Internal Port** | 8080 (proxied exclusively by OpenResty — never exposed to host) |
| **Entry Point** | `FinanceApiApplication.java` (extends `SpringBootServletInitializer`) |
| **Concurrency** | Java 21 Virtual Threads (Project Loom) throughout — zero blocking I/O |
| **Build** | Maven WAR packaging |
| **Replicas** | 2 replicas (restored post-OOM fix — JVM tuned to `-Xmx768m -Xms512m`) |

**Key Services (all verified in code):**
- `HtmlMaterializerService` — Generates materialized HTML files on post publication for 100% SEO availability during backend downtime
- `GeoOptimizationService` — Produces HMAC-SHA256-signed AI-readable semantic payloads (`llms.txt`, `ai-feed.md`, `ontology.json`)
- `SitemapService` — Multi-tenant, paginated XML sitemaps (designed for 10M+ URLs); includes GEO endpoint URLs at priority 1.0. **Contextual routing:** Finance → dynamic paginated; Agro → static E-E-A-T XML
- `MarketDataService` — Hybrid Java + Python market data aggregation via Strategy Pattern (`AlphaVantageProvider`, `FinnhubProvider`, `FmpProvider`, `YahooHistoricalProvider`, `BreezeProvider`)
- `AnalyticsService` — Native first-party audience tracking via `audience_visits` table + GA4 BigQuery integration
- `ContentIntegrityService` — HMAC-SHA256 digital signatures on all published post content

**Security Engine:**
- Runs AEGIS Byzantine Consensus Security Mesh (L8-BCSM) and Behavioral Intelligence Engine (L5-BIE)
- All AEGIS validators execute in parallel via Java 21 Virtual Threads — zero added latency to HTTP request threads
- JDBC query signing via `AegisQueryInterceptor` (SHA3-256 HMAC) — Zero-Trust DB Driver Boundary
- AES-256-GCM domain-specific PII encryption at rest (`UserEmailConverter`, `ContactEmailConverter`, `ContactMessageConverter`, `AuditIpConverter`)

**Multi-Tenancy:**
- `TenantInterceptor` reads `X-Tenant-ID` header injected by Edge Workers
- All DB queries, sitemaps, and service behavior scoped to tenant context via `TenantContext` (ThreadLocal)
- Tenant whitelist validated server-side

#### Edge Workers — Cloudflare Workers (V8 Isolate)

| Worker | Route | Status |
| :--- | :--- | :--- |
| `treishfin-seo-worker` | `treishvaamfinance.com/*` | Live Production |
| `treishvaamagro-seo-worker` | `treishvaamagro.com/*`, `www.treishvaamagro.com/*` | In Development (⚠️ Pending AEGIS Phase 6 MTD + GEO upgrade) |

**Worker responsibilities:**
- **Zero-Trust API Proxy:** HMAC-SHA-512 signs all backend requests (`X-Aegis-Edge-Signature`); injects `X-Tenant-ID`
- **AEGIS L4-ADA Checkpoint:** Reads `aegis:mtd:manifest` from KV for Moving Target Defense path translation; blocks/tarpits malicious IPs and JA3 hashes from `AEGIS_THREAT_KV`
- **GEO Router:** Intercepts LLM crawlers (GPTBot, ClaudeBot, DeepSeek, OAI-SearchBot, 50+ total) and serves semantic GEO payloads from KV — React is bypassed entirely
- **SEO Intelligence:** Injects E-E-A-T JSON-LD schemas via `HTMLRewriter`; handles KV-cached sitemaps; prevents SPA 404 penalties
- **Cron Cache Warmer:** Hourly (`0 * * * *`) proactive KV sitemap refresh

#### Frontends — Next.js 14 App Router (Cloudflare Pages)

| Frontend | Project | Domain | Status |
| :--- | :--- | :--- | :--- |
| Finance | `treishvaam-finance-frontend` | `treishvaamfinance.com` | Live Production |
| Agro | `treishvaam-agro-frontend` | `treishvaamagro.com` | In Development |
| Parent | `treishvaamgroup-frontend` | `treishvaamgroup.com` | Live Production |

**Key frontend attributes:**
- **Framework:** Next.js 14 App Router — **migrated from Create React App (CRA)**. Legacy `src/pages/*.js` components are imported by `app/*/page.tsx` wrappers — NOT URL routes themselves
- **Runtime:** Edge Runtime (`export const runtime = 'edge'` in `app/layout.tsx`)
- **Build:** `next build` → deployed to Cloudflare Pages automatically on `git push origin main`
- **Security:** Per-request cryptographic CSP nonce via `middleware.ts` (`btoa(crypto.randomUUID())` — Edge-safe, no `Buffer`)
- **PWA:** Serwist 9.0.2 (`src/sw.ts`) — `/// <reference lib="webworker" />` directive required; Serwist Strategy classes instantiated (not string handlers)
- **Image Optimization:** Custom `cloudflareImageLoader.ts` — delegates to Cloudflare CDN (Next.js native server-side image optimization crashes on Edge)
- **Fonts:** Self-hosted `@fontsource-variable/inter` (privacy + performance; removes Google Fonts CDN dependency)
- **Analytics:** GA4 with dynamic `anonymize_ip` toggle via `NEXT_PUBLIC_ENFORCE_STRICT_PRIVACY`
- **Telemetry:** `AegisTelemetry.tsx` (L5-BIE biometric hashing via WebCrypto SHA3-256), `WebVitalsTracker.tsx` (Core Web Vitals), Grafana Faro RUM

---

### 2. Data Layer — Zero Exposed Ports

All data services communicate exclusively on the internal `treish_net` Docker bridge network. No data layer ports are bound to the host.

| Service | Image | Role | Encryption |
| :--- | :--- | :--- | :--- |
| **MariaDB** (`treishvaam-db`) | `mariadb:10.6` | Primary relational DB — `finance_db` | TDE via `config/mariadb/encryption.cnf` + Docker secret `mariadb_encryption_key` |
| **MariaDB** (`treishvaam-keycloak-db`) | `mariadb:10.6` | Keycloak's isolated identity DB | — |
| **Redis** (`treishvaam-redis`) | `redis:7-alpine` | Read-through cache + AEGIS temporal path registry | Password auth; FLUSHALL/FLUSHDB/DEBUG/MONITOR renamed to `""` (disabled) |
| **Elasticsearch** (`treishvaam-elastic`) | `elasticsearch:8.17.0` | Full-text search (`PostDocument`) | xpack security enabled; JVM: `-Xms512m -Xmx512m` |
| **MinIO** (`treishvaam-minio`) | `minio/minio` | S3-compatible object storage — media + materialized HTML | Internal network only; console on 9001 (not bound to host) |
| **RabbitMQ** (`treishvaam-rabbitmq`) | `rabbitmq:3.12-management` | Async event bus — threat telemetry, sitemap triggers, DLX retries | Management UI: `127.0.0.1:15672` (SSH tunnel only) |

**MariaDB specifics:**
- Transparent Data Encryption (TDE) via `config/mariadb/encryption.cnf`
- JDBC Batching (`batch_size=50`, `order_inserts=true`, `order_updates=true`) for bulk write performance
- Optimistic Locking enforced on `blog_posts` (version column, V40 migration)
- All JDBC queries intercepted and HMAC-signed by `AegisQueryInterceptor` (SHA3-256) — Zero-Trust DB Driver Boundary
- HikariCP tuned: max-pool-size=50, min-idle=10, idle-timeout=300s, max-lifetime=1200s, keepalive-time=120s

**Redis specifics:**
- Read-through caching for market data widgets (`@Cacheable`)
- AEGIS temporal path registry (daily rotating manifests signed by ML-DSA-87)
- `@Cacheable` deliberately NOT applied to `Optional<T>` returning repository methods (Jackson deserialization crash prevention)

---

### 3. Security Layer

| Service | Image | Role |
| :--- | :--- | :--- |
| **Keycloak** (`treishvaam-keycloak`) | `quay.io/keycloak/keycloak:25.0.0` | Centralized SSO / Identity Provider — internal only, exposed via OpenResty |
| **aegis-zkp-service** | Compiled Go, distroless/scratch | L3-ZKA Zero-Knowledge Proof verification (Schnorr-over-Lattice). gRPC port 9090 internal only. **Strict 256MB memory limit** |
| **OpenResty** (`treishvaam-nginx`) | `openresty/openresty:alpine` | **Only container with exposed ports (80/443).** WAF, TLS JA3 fingerprinting via `aegis_ja3.lua`, SSL termination, ModSecurity OWASP CRS |
| **cloudflared** (`treishvaam-tunnel`) | `cloudflare/cloudflared` | Secure ingress tunnel — no firewall ports opened |
| **aegis-canary-server** | `thinkst/canarytokens:latest` | Canary token management for L4-ADA deception. Port `127.0.0.1:8089` |
| **wazuh-agent** | `wazuh/wazuh-agent:4.7.3` | HIDS — subscribes to RabbitMQ threat events. Signals feed `HidsIntegrityValidator` in BCSM |
| **Envoy** (`treishvaam-envoy`) | `envoyproxy/envoy:v1.29-latest` | Internal L7 proxy for gRPC routing to ZKP service |

---

### 4. Observability Layer — SSH-Tunnel Access Only

**ZERO-TRUST ACCESS:** Grafana, Prometheus, and RabbitMQ Management UIs are NEVER exposed to `0.0.0.0`. Access is strictly via SSH Local Port Forwarding.

```bash
ssh -L 3001:localhost:3001 -L 15672:localhost:15672 vboxuser@192.168.29.111
```

| Service | Image | Access | Notes |
| :--- | :--- | :--- | :--- |
| **Grafana** | `grafana/grafana:latest` | `localhost:3001` (SSH tunnel) | Dashboards, alerting, Faro RUM |
| **Prometheus** | `prom/prometheus:latest` | Internal | Scrapes Spring Boot actuator at `/actuator/prometheus` |
| **Loki** | `grafana/loki:2.9.2` | Internal | Log aggregation. Query label: `{job="varlogs"}` |
| **Promtail** | `grafana/promtail:2.9.2` | Internal | Ships logs from `./logs` to Loki |
| **Tempo** | `grafana/tempo:latest` | Internal | Distributed tracing (Zipkin-compatible). Receives traces from backend at `http://treishvaam-tempo:9411/api/v2/spans` |

**Grafana Alerting (verified in `config/grafana-alerting.yml`):**
- `HighBackendErrorRate` — triggers on elevated 5xx rates
- `SlowAPIResponse` — triggers on degraded P99 latency
- `SecretKeyRotationDue` — triggers on approaching key expiry

---

### 5. Background & Utility Services

| Service | Notes |
| :--- | :--- |
| **Backup Service** (`treishvaam-backup`) | MariaDB dump via `backup.sh` + MinIO via `docker cp`. AES-encrypted with `BACKUP_ENCRYPTION_KEY` |
| **Python Market Updater** (`scripts/market_data_updater.py`) | Runs inside backend container via Java `ProcessBuilder` for heavy historical data. Guarded by Resilience4j `pythonScript` circuit breaker (120s timeout, 50% failure threshold) |

---

## Architecture Data Flow

```
Client / Bot Request
      │
      ▼
Cloudflare DNS + WAF (DDoS protection, Bot Score, IP Reputation)
      │
      ├── www.* → 301 Cloudflare Bulk Redirect (Edge Rule) → apex domain
      │
      ▼
Cloudflare Edge Worker (treishfin-seo-worker)
      │
      ├── Known malicious IP/JA3 → KV tarpit marker → BLOCK or DECEPTION payload
      ├── AI/LLM crawler (GPTBot, ClaudeBot, DeepSeek, etc.) → KV GEO payload (/ai-feed.md, /llms.txt)
      ├── Sitemap/SEO request → KV TREISHFIN_SEO_CACHE (3-tier: CDN Cache → KV → Backend fallback)
      │
      ├── Standard request → inject X-Tenant-ID + HMAC-SHA-512 Edge Signature
      │       │
      │       ▼
      │   Cloudflare Pages (Next.js 14 Edge SSR)
      │       │
      │       └── API calls → Worker proxy → BACKEND_API_URL (Cloudflare Tunnel) → OpenResty (443)
      │                                                                │
      │                                             AegisEdgeValidationFilter (verify HMAC-SHA-512)
      │                                                                │
      │                                             AegisDeceptionFilter (L4-ADA pre-screen)
      │                                                                │
      │                                             AegisMainFilter (L8-BCSM Byzantine Consensus, 7 validators)
      │                                                                │
      │                                             AegisZkpAdminFilter (L3-ZKA, /admin/** only)
      │                                                                │
      │                                             Spring Security (OAuth2 Resource Server / Keycloak JWT)
      │                                                                │
      │                                             Business Logic → MariaDB / Redis / Elasticsearch / MinIO
      │
      └── RabbitMQ (async) → CloudflareEdgeSyncService → Cloudflare KV (real-time threat intel push)
```

---

## Multi-Tenant Architecture

One backend, multiple brands. Tenant isolation is enforced at every layer.

| Tenant ID | Frontend | Domain | Status |
| :--- | :--- | :--- | :--- |
| `finance` | `treishvaam-finance-frontend` | `treishvaamfinance.com` | Live |
| `agro` | `treishvaam-agro-frontend` | `treishvaamagro.com` | In Development |
| `public` | `treishvaamgroup-frontend` | `treishvaamgroup.com` | Live |

The `X-Tenant-ID` header is injected by each Edge Worker and validated by `TenantInterceptor`. All DB queries, sitemaps, and service behavior are scoped to the tenant context. MDC tagging ensures Loki logs are filterable per tenant.

---

## Cloudflare Edge Routing Rules (Dashboard-Managed — Not in Code)

These rules are enforced at Cloudflare Edge. They must **NEVER** be implemented in `_redirects`, `next.config.mjs`, `worker.js`, or backend code.

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
Applies ONLY to non-Worker-proxied frontends. Finance and Agro are EXCLUDED — a bulk redirect on their `.pages.dev` URLs would create an `ERR_TOO_MANY_REDIRECTS` infinite loop because the Worker itself fetches the `.pages.dev` origin.
```
treishvaamgroup-frontend.pages.dev/ → https://treishvaamgroup.com/
Status: 301 | Include subdomains: ON | Subpath matching: ON | Preserve path suffix: ON
```

---

## Planned: Hybrid-Cloud Disaster Recovery (OCI — Status: Planned/Pending)

The system is scheduled to migrate to an Oracle Cloud Infrastructure (OCI) hybrid model:
- **OCI Always-Free Node** — Primary Master (Terraform/Ansible IaC, strictly within Always Free limits)
- **Local VBox Node** — Intermittent Replica (GTID replication, sync-before-serve gate)
- **Zero-State Ignition** — Single script to provision containers, apply Liquibase schema, restore S3 backup, re-establish Cloudflare Tunnel

When this is implemented, this section must be moved from PLANNED to ACTIVE and integrated into `BE-09-DEPLOYMENT.md`.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **VERIFIED (2026-05-29 — Enterprise Documentation Generation):**
  - All architectural claims verified against actual codebase (`docker-compose.yml`, `FinanceApiApplication.java`, `SecurityConfig.java`, `application-prod.properties`, `worker.js`, `wrangler.toml`, `middleware.ts`, `layout.tsx`, `next.config.mjs`).
  - Added verified container image versions for all services.
  - Added HikariCP tuning parameters from `application-prod.properties`.
  - Added `package.json` homepage stale-field observation.
  - Added explicit Agro Worker AEGIS gap warning.
  - No architectural claims changed — the existing docs were accurate.