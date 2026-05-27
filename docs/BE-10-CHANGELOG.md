# 10 — Changelog

All notable changes to the Treishvaam Finance Platform are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [tfin-financeapi-Develop.0.0.0.7] — AEGIS Security Framework & GEO Complete

### Security — AEGIS Framework (All Phases)

- **L0-HEA (Hardware Entropy):** Deployed `AegisEntropyManager`. SHA3-512 folded 4096-bit entropy pool from `/dev/urandom`, `/dev/random`, and JVM jitter. Fully async via Java 21 Virtual Threads. Hardware entropy devices mapped into Docker backend container
- **L1-PQCf (Post-Quantum Crypto):** Deployed `AegisPqcJwtService` and `AegisPqcKeyStore`. JWTs signed with ML-DSA-87 (Dilithium5) via pure-Java BouncyCastle 1.78.1. Argon2id (`argon2-jvm 2.11`) for password hashing. `liboqs-java` permanently excluded (CI/CD incompatibility)
- **L2-PPO (Moving Target Defense):** Deployed `AegisTemporalPathManager`, `EndpointManifest`, `AegisMtdController`, `AegisResponseMutator`. Daily rotating path manifests signed by ML-DSA-87, pushed to Redis. Edge Worker reads `aegis:mtd:manifest` from KV for path translation
- **L3-ZKA (Zero-Knowledge Auth):** Deployed `aegis-zkp-service` (compiled Go, distroless/scratch container, gRPC port 9090). Schnorr-over-Lattice ZKP for all admin endpoints. `AegisZkpAdminFilter` intercepts `/api/v1/admin/**`. Resilience4j Circuit Breaker wraps gRPC calls. Strict 256MB memory limit on container
- **L4-ADA (Adversarial Deception):** Deployed `AegisDeceptionEngine`, `PoisonCorpusGenerator`, `CanaryTokenService`, `TarpitManager`. PLAUSIBLE_FAKE / SLOW_LEAK / RECURSIVE_LOOP strategies. Virtual Thread tarpits (1 byte/sec). Fire-and-forget RabbitMQ threat telemetry via `RabbitMQAttackPublisher`. All deception responses carry `Cache-Control: no-store`
- **L5-BIE (Behavioral Intelligence):** Deployed `AegisBehavioralEngine`, `SessionBehaviorProfile`, `ShannonEntropyCalculator`. Frontend `src/lib/aegis-biometrics.ts` hashes biometric vectors client-side (WebCrypto SHA3-256) before transmission — zero PII leaves the browser
- **L6-MTD (MTD Orchestration):** `CloudflareEdgeSyncService` closes the airgap — pushes malicious IP/JA3 hashes to Cloudflare KV in real time (86400s TTL, `localBlockCache` deduplication for free-tier quota protection). `AegisThreatConsumer` handles reactive RabbitMQ-triggered rotations
- **L7-CMCS (Chaos Mirror):** Deployed `AegisChaosMirror`, `PowChallengeIssuer`. SHA3-256 Proof-of-Work challenges for credential stuffers. Dynamic delay tarpits parameterized by behavioral risk score
- **L8-BCSM (Byzantine Consensus):** Deployed `AegisBcsm` with 7 independent validators running in parallel via `CompletableFuture` + Virtual Thread executor. 100ms hard timeout. BFT tolerates 2 compromised validators. Migrated from preview `StructuredTaskScope` to stable `Executors.newVirtualThreadPerTaskExecutor()` for Java 21 compatibility
- **AEL (AEGIS Expression Language):** Deployed `AegisExpressionLanguage`, `AelRuleLoader`, `aegis.g4` (ANTLR4 grammar). Dynamic security policy DSL without code injection risk
- **Edge Signature (Phase 6.3):** Deployed `AegisEdgeValidationFilter` as `FilterRegistrationBean` at HIGHEST_PRECEDENCE. Validates HMAC-SHA-512 `X-Aegis-Edge-Signature` on every inbound request (300s TTL replay prevention). Blocks direct-IP backend scanners
- **Worker Signing Fix (GEO Phase):** Refactored `crypto.subtle` signing into centralized `generateEdgeSignature()` helper in `worker.js`. Fixed critical zero-trust flaw where cron jobs and cache misses were sending stale signatures causing 403 rejections at `AegisEdgeValidationFilter`

### Generative Engine Optimization (GEO)

- **Edge Interception:** Worker's `GLOBAL_CRAWLER_MATRIX` and `aiBotsOnly` regex expanded to cover OAI-SearchBot, DeepSeek, Bytespider, Qwen, Mistral, Cohere-training, Diffbot. All LLM crawler HTML requests now intercepted and served GEO payloads directly from KV — React bypassed entirely
- **GEO Payloads:** `GeoOptimizationController` + `GeoOptimizationService` generating `/llms.txt`, `/ai-feed.md`, `/ontology.json`. Semantic `<semantic-chunk>` boundaries in Markdown. Advanced `@graph` JSON-LD ontology
- **HMAC Provenance:** `ai-feed.md` payload signed with `CONTENT_SIGNING_KEY` (HMAC-SHA256) to prevent MITM data poisoning during AI ingestion
- **Frontend Proxies:** `app/llms.txt/route.ts`, `app/ai-feed.md/route.ts`, `app/ontology.json/route.ts` — zero-trust Next.js API route handlers
- **Sitemap GEO Integration:** `SitemapService` includes `/llms.txt`, `/ai-feed.md`, `/ontology.json` at priority 1.0 in root sitemap
- **Layout GEO Tags:** `app/layout.tsx` includes `<link rel="llms-txt">`, `<link rel="alternate" type="application/json+ld">`, OpenSearch `<link rel="search">`
- **`<semantic-chunk>` Boundaries:** Layout wraps `{children}` with `id="main-content"` and `data-aegis-geo="active"` for AI content slicing
- **OpenSearch Syndication:** `app/opensearch.xml/route.ts` enables native browser search integration and AI plugin interfacing
- **KV Cache Miss Handling:** `handleGeoFeedFromKV()` serves backend response immediately while triggering async KV write via `ctx.waitUntil` — LLM crawlers never wait for write operations
- **HtmlMaterializerService GEO:** Materialized HTML files include `<link rel="alternate">` tags pointing to semantic JSON-LD Ontology and LLM Markdown Feeds in `<head>`

### Infrastructure Changes

- **OpenResty:** Replaced `nginx:alpine` with `openresty/openresty:alpine`. Enables `aegis_ja3.lua` for TLS JA3 fingerprinting injecting `X-JA3-Fingerprint` header to Tomcat
- **ZKP Service:** Converted `aegis-zkp-service` from raw Go image to compiled multi-stage `Dockerfile` (distroless/scratch). TCP netcat healthcheck on gRPC port 9090
- **Canary Server:** `thinkst/canarytokens:latest` added for L4-ADA deception token management
- **Wazuh Agent:** `wazuh/wazuh-agent:4.7.3` added — subscribes to RabbitMQ threat events for HIDS signals feeding `HidsIntegrityValidator`
- **Backend Build:** `build: .` context added to `backend` service. Enables `docker compose up --build` to compile locally, bypassing ghcr.io registry authentication denials after cache prune

### Database Migrations

- **V42:** Encrypt `linkedin_access_token` in users (AES-256-GCM)
- **V43:** Expand encryption columns (VARCHAR size increase for ciphertext)
- **V44:** Encrypt `ip_address` in `audit_log` via `AuditIpConverter`
- **V45:** Add `content_signature` to `blog_posts` (HMAC-SHA256 integrity)
- **V46:** Create `analytics_events` table for Faro/GA4 event ingestion

---

## [tfin-financeapi-Develop.0.0.0.6] — Enterprise Observability & Internal Analytics

- **Native Analytics Engine:** `AnalyticsController` + `AnalyticsService` for internal RUM. `audience_visits` table (V26) for first-party traffic logging
- **API Diagnostic Health:** `ApiStatusController` tracks external market API latency, success rates, and error details in `api_fetch_status` table (V30). Prevents silent third-party feed failures
- **Grafana Faro:** `faroConfig.js` on frontend streams Web Vitals and unhandled exceptions to internal observability stack
- **DB Migrations V36–V41:** Audit log table, news fields, blog editorial fields, optimistic locking (`version`), display name for users

---

## [tfin-financeapi-Develop.0.0.0.5] — Zero-Trust Multi-Tenant Architecture

- **Multi-Tenancy:** `TenantInterceptor` + `TenantContext` (ThreadLocal). `X-Tenant-ID` header injected by Edge Workers, validated and scoped to whitelist. MDC tagging for Loki per-tenant log filtering
- **Contextual Sitemap Routing:** `SitemapService` hijacks endpoint behavior based on tenant — Agro gets static E-E-A-T XML; Finance gets paginated dynamic sitemaps
- **Startup Isolation:** `MarketDataInitializer` wrapped with explicit `TenantContext.setTenantId("finance")` to prevent cross-tenant contamination on restart
- **KV Sitemap Caching:** `TREISHFIN_SEO_CACHE` namespace. Three-tier cache-shield (CDN Edge → KV → Backend). Async KV write via `ctx.waitUntil`
- **SPA Fallback:** Worker `KNOWN_SPA_ROUTES` array — 404s on known routes return 200 OK with root `index.html` and `X-SPA-Fallback: Active` header. Eliminates Google Search Console Soft 404 penalties
- **E-E-A-T Schema:** Worker injects Organization, Founder (Amitsagar Kandpal), WebPage JSON-LD via `HTMLRewriter`
- **Docker Compose `.env` Rule Enforced:** Single/double quotes in `.env` values cause HikariCP crash loop. Documented and enforced as a hard constraint

---

## [tfin-financeapi-Develop.0.0.0.4] — Hybrid SSG & Production Stabilization

- **Visual Integrity Fix:** Worker injects `<base href="/">` on deep URLs — prevents CSS/JS MIME type errors from relative path resolution
- **JSON Recursion Fix:** `@JsonIgnore` on `PostThumbnail.blogPost` + `@JsonIgnoreProperties` on `BlogPost` → eliminated infinite JSON recursion 500 errors
- **React Hydration Fix:** Switched from `hydrateRoot` to `createRoot` — resolves React Error #418/#423 on empty containers
- **Jackson Instant Fix:** `HtmlMaterializerService` manually serializes `Instant` fields to strings — prevents Jackson config crash during HTML materialization
- **Duplicate `#server-content` Cleanup:** `SinglePostPage.js` automatically removes the raw SEO text div on mount

---

## [tfin-financeapi-Develop.0.0.0.3] — High-Performance Caching

- **Redis Read-Through Caching:** `@Cacheable` applied to high-read, non-Optional market data endpoints
- **Optimistic Locking:** `version` field strategy on `blog_posts` (formalized in V40 migration)
- **JDBC Batching:** `batch_size=50` for bulk insert/update performance
- **HikariCP Tuning:** `max-lifetime` and `idle-timeout` tuned for hybrid Virtual Thread + RabbitMQ async load

---

## [tfin-financeapi-Develop.0.0.0.2] — Content Integrity & PII Encryption

- **HMAC Content Signing:** `ContentIntegrityService` — HMAC-SHA256 on all blog post content. V45 migration adds `content_signature` column
- **PII Encryption:** Domain-specific AES-256-GCM converters for email, contact messages, and audit IPs. V42–V44 migrations
- **RabbitMQ Integration:** `RabbitMQConfig` with `aegis.threat.exchange`. `MessagePublisher` + `MessageListener` for async post publishing events

---

## [tfin-financeapi-Develop.0.0.0.1] — Foundation

- Spring Boot 3.4 (Java 21) backend bootstrapped
- MariaDB + Liquibase (V1–V30) schema foundation
- Keycloak SSO integration (`KeycloakRealmRoleConverter`)
- Elasticsearch full-text search (`PostSearchRepository`, `PostDocument`)
- MinIO object storage (`FileStorageService`)
- Market data engine — Strategy Pattern over AlphaVantage, Finnhub, FMP, Yahoo providers
- Python bridge (`scripts/market_data_updater.py`) via `ProcessBuilder` with secure env injection
- LinkedIn sharing service (`LinkedInService`)
- GitHub Actions CI/CD pipeline with GPG commit signing, Gitleaks, Spotless
- Cloudflare Tunnel ingress (`cloudflared` container)
