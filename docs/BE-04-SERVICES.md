# BE-04 — Backend Services Layer

/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Details the business logic implementation, service layer architecture, and data synchronization strategies.
 * - Explains how the Zero-Trust Multi-Tenant Engine routes logic dynamically based on context.
 *
 * Scope:
 * - Covers Market Data (Python bridge), Blog Post CRUD, Sitemap Generation (Contextual Routing),
 *   Analytics, API Tracking, Background Scheduling, Content Integrity, and Audit Integrity.
 *
 * Critical Dependencies:
 * - Cloudflare Edge Workers (for Edge caching and X-Tenant-ID injection)
 * - TenantInterceptor & TenantContext
 * - Resilience4j (Circuit Breakers)
 * - RabbitMQ (async event bus for threat telemetry and sitemap triggers)
 *
 * Security Constraints:
 * - Background/Scheduled tasks MUST explicitly declare their TenantContext (e.g., "finance") to prevent cross-tenant contamination.
 * - Heavy I/O must remain outside of @Transactional boundaries to prevent connection pool exhaustion.
 * - Python bridge: DB credentials injected via ProcessBuilder.environment() — never as CLI args.
 *
 * Non-Negotiables:
 * - @Cacheable must NEVER be applied to methods returning Optional<T> — Jackson cannot deserialize Optional from Redis.
 * - MerkleAuditLogService must never be removed — it provides cryptographic tamper-evidence on the audit trail.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED: Initial Backend Services Layer documentation.
 * - EDITED:
 *   • Phase 3 Update: Integrated Multi-Tenant logic across services.
 *   • Added SitemapService contextual routing documentation.
 *   • Added strict TenantContext isolation rules for MarketDataInitializer and Schedulers.
 *   • Clarified Edge Worker interplay with HTML Materialization and Caching.
 * - EDITED:
 *   • Documented Internal Analytics Engine (AnalyticsService).
 *   • Documented external API tracking mechanics.
 *   • Clarified HtmlMaterializerService Jackson Instant serialization fix.
 * - VERIFIED + UPDATED (2026-05-29 — Enterprise Documentation Generation):
 *   • Added BreezeProvider (ICICI Direct Breeze API) — previously undocumented 5th market data provider.
 *   • Added MerkleAuditLogService (Section 8) — previously undocumented; provides Merkle-chain tamper evidence on audit_log.
 *   • Verified all service class names against actual codebase.
 *   • All existing content confirmed accurate against implementation.
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted,
 * truncated, rewritten, or regenerated.
 * Future AI must append only.
 */

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized)
**Last Verified:** 2026-05-29

---

## 1. Market Data Engine (`MarketDataService`)

The Market Data Engine aggregates real-time and historical financial data from multiple external providers. Currently scoped strictly to the `finance` tenant.

### 1.1. Provider Strategy Pattern

The service uses the Strategy Pattern over the `MarketDataProvider` interface.

**Implementations (verified — 5 providers):**

| Provider Class | Data Source | Primary Use |
| :--- | :--- | :--- |
| `AlphaVantageProvider` | Alpha Vantage | Forex + technical indicators |
| `FinnhubProvider` | Finnhub | Real-time stock quotes + news |
| `FmpProvider` | Financial Modeling Prep | Bulk market movers (gainers/losers) |
| `YahooHistoricalProvider` | Yahoo Finance | Long-term historical candle data |
| `BreezeProvider` | ICICI Direct Breeze API | Indian market data (NSE/BSE) |

**Factory:** `MarketDataFactory` selects the appropriate provider based on requested symbol or region.

### 1.2. Hybrid Data Fetching (Java + Python)

**Java Native:** Direct REST calls via `RestClient` for real-time quotes and lightweight data.

**Python Bridge:** For heavy historical data processing and `yfinance` library requirements:
- Execution: `ProcessBuilder` via `MarketDataService`
- **Security:** DB credentials injected securely via `ProcessBuilder.environment().put(...)` — passwords never appear in `ps aux` output
- **Financial Precision:** Python engine uses `decimal.Decimal` (28-place context) to prevent floating-point errors

### 1.3. Smart Synchronization & Caching

- **Smart Sync:** Before fetching historical data, the system checks `historical_price` for the last available date — only requests data newer than that date (API quota preservation)
- **`HistoricalDataCache`:** Metadata about fetch requests stored in `historical_data_cache` table — prevents duplicate fetches within the same trading session
- **Redis Caching:** `MarketDataController` caches final JSON responses in Redis
- **`@Cacheable` constraint:** Never applied to methods returning `Optional<T>` — Spring Data Redis cannot deserialize `Optional<BlogPost>` from JSON; doing so causes fatal 500 on first cache hit

### 1.4. Resiliency & Circuit Breakers (Resilience4j)

| Circuit Breaker | Timeout | Failure Threshold | Fallback |
| :--- | :--- | :--- | :--- |
| `fmpApi` (external APIs) | 5 seconds | Opens on repeated failure | Serves stale data from database |
| `pythonScript` | 120 seconds | 50% failure rate, window=10 | Logs error, returns partial data |

---

## 2. Content Management (`BlogPostService`)

Handles the full lifecycle of editorial content across multiple tenants.

### 2.1. Logic Flow

- **CRUD:** Maps `BlogPostDto` to `BlogPost` entities. Manages Category + User relationships
- **Slug strategy:**
  - `slug` — Immutable unique internal identifier
  - `userFriendlySlug` — SEO-optimized string (e.g., `market-rally-2024`). Uniqueness enforced by appending numeric suffixes on collision
- **Scheduling:** Posts with `PostStatus.SCHEDULED` + future `scheduledTime` are hidden from public endpoints until time passes

### 2.2. Multi-Tenancy (Zero-Trust Boundaries)

- `X-Tenant-ID` header injected by Edge Workers, validated by `TenantInterceptor`
- `TenantContext` (ThreadLocal) scopes all created posts and fetch queries to the tenant
- Cross-tenant data contamination is architecturally impossible at the query layer

### 2.3. Enterprise I/O Strategy — "Secure Stream & Commit"

Separates network I/O from database transactions to prevent connection pool starvation and OOM:

**Phase 1 (Non-Transactional):** Stream file directly to MinIO outside transactional boundaries
**Phase 2 (Transactional Commit):** After successful MinIO upload, commit DB metadata record

This prevents two classes of failure:
1. **Connection Starvation:** Network I/O inside `@Transactional` holds HikariCP connections during MinIO upload — freezes app under load
2. **Memory Exhaustion:** Loading large images into `byte[]` causes OOM — streaming bypasses heap

### 2.4. Optimistic Locking

`blog_posts.version` column (V40 migration) enforces JPA Optimistic Locking. All `PUT` operations must include the current `version` value. Concurrent editors receive `409 Conflict` — prevents lost updates silently overwriting each other's work.

---

## 3. Static Content Materialization (`HtmlMaterializerService`)

Generates static HTML files for blog posts at publish time for maximum SEO availability during backend downtime.

### 3.1. Workflow

1. **Fetch HTML shell** from the frontend (internal URL)
2. **Inject metadata** — Open Graph tags, Twitter cards, Article JSON-LD schema
3. **Inject GEO tags** — `<link rel="alternate">` pointing to `/llms.txt`, `/ontology.json`
4. **Stream to MinIO** — Materialized HTML stored in MinIO for edge delivery
5. **Async execution** — `@Async` Virtual Thread; never blocks the publish HTTP request

**Jackson Instant fix (IMMUTABLE):** `HtmlMaterializerService` manually serializes `Instant` fields to ISO-8601 strings before injection. Jackson's default `Instant` serialization crashes during HTML materialization context — this manual conversion is intentional and must not be "optimized away."

---

## 4. Sitemap Generation (`SitemapService`)

Designed for 10M+ URLs. Multi-tenant, paginated, incremental.

### 4.1. Tenant-Contextual Behavior

| Tenant | Behavior |
| :--- | :--- |
| `finance` | Dynamic: paginated blog + market XML sitemaps; GEO payload URLs at priority 1.0 |
| `agro` | Static: Enterprise E-E-A-T XML payload for fixed page set |

### 4.2. Background Task Safety

All `@Scheduled` tasks that call `SitemapService` must declare `TenantContext.setTenantId("finance")` explicitly. Failure to do so causes cross-tenant contamination in Virtual Thread workers.

### 4.3. Endpoints

Consumed by Cloudflare Worker cron for KV cache population:
- `GET /api/public/sitemap/meta` → JSON list of segment paths
- `GET /api/public/sitemap/blog/{page}.xml` → paginated blog URLs
- `GET /api/public/sitemap/market/{page}.xml` → market ticker URLs

---

## 5. GEO Optimization (`GeoOptimizationService`)

Generates semantically structured payloads for AI/LLM crawler ingestion. GDPR-compliant — no PII, no draft content, no internal data.

| Method | Output | Description |
| :--- | :--- | :--- |
| `buildLlmsTxt()` | `text/plain` | AI agent discovery file — platform summary, citation guidelines |
| `buildAiFeed()` | `text/markdown` | Aggregated semantic feed — recent articles, market highlights, vision statement |
| `buildSemanticOntology()` | `application/json` | JSON-LD knowledge graph — entity relationships, domain ontology |

All outputs signed with `CONTENT_SIGNING_KEY` (HMAC-SHA256) for provenance verification. Output cached in Cloudflare KV (`geo:finance:*` keys, 86400s TTL).

---

## 6. Analytics & Diagnostics

### 6.1. Analytics Service (`AnalyticsService`)

Integrates with Google Analytics 4 via BigQuery API (`GA4_PROPERTY_ID`, `GA4_BIGQUERY_PROJECT_ID`, `GA4_BIGQUERY_DATASET_ID`). Maps GA4 data to local `audience_visits` records. Supports advanced filtering by date, region, OS, and session source. Provides real-time session insights.

GA4 BigQuery credentials: `ga4-credentials.json` service account key mounted at `/app/ga4-credentials.json` inside the backend container.

### 6.2. API Diagnostics (`ApiFetchStatus`)

`ApiFetchStatus` entity + `ApiStatusController` tracks health and latency of all external API calls:
- `apiName` — provider name
- `status` — SUCCESS / FAILURE
- `triggerSource` — automatic or manual
- `latency_ms` — response time
- `details` — error message on failure

Visible at `GET /api/v1/status` (ADMIN). Prevents silent third-party feed failures.

---

## 7. Content Integrity (`ContentIntegrityService`)

HMAC-SHA256 digital signatures on all blog post content using `CONTENT_SIGNING_KEY` (environment variable). Signature stored in `content_signature` column (V45 migration). Mismatches trigger critical security alerts in Grafana.

---

## 8. Audit Integrity (`MerkleAuditLogService`)

**Previously undocumented — verified in codebase (2026-05-29).**

Maintains a **Merkle tree hash chain** over all `audit_log` entries:
- Each new audit record's hash is chained to the previous entry — cryptographic ledger
- Any retroactive modification of any audit entry breaks the chain
- Chain validation detectable at any time — provides tamper-evidence independent of DB-level constraints
- Works alongside `AuditIpConverter` (AES-256-GCM) — audit log has both confidentiality (encrypted IPs) and integrity (Merkle chain)

**Do not remove `MerkleAuditLogService`** — it is a compliance and forensic integrity component.

---

## 9. Background Scheduling

All scheduled jobs run via Java 21 Virtual Threads. All must explicitly set `TenantContext` before any service call.

| Scheduler | Class | Schedule | Purpose |
| :--- | :--- | :--- | :--- |
| Market Data Refresh | `MarketDataScheduler` | Configurable interval | Refreshes live market quotes from external providers |
| Market Data Initializer | `MarketDataInitializer` | On startup | Populates market data table on fresh deploy |
| Sitemap Refresh | RabbitMQ consumer | Event-driven (on post publish) | Triggers sitemap regeneration |
| News Highlights Refresh | `NewsHighlightService` | Configurable | Fetches latest news via `NEWS_API_KEY` |

RabbitMQ `aegis.threat.exchange` is used for async threat telemetry distribution — not for blocking request paths.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **ADDED:** Initial Backend Services Layer documentation.
- **EDITED:** Phase 3 Update — Multi-Tenant logic, SitemapService contextual routing, TenantContext isolation rules.
- **EDITED:** Internal Analytics Engine, external API tracking, HtmlMaterializerService Jackson Instant fix.
- **VERIFIED + UPDATED (2026-05-29 — Enterprise Documentation Generation):**
  - **ADDED:** `BreezeProvider` (ICICI Direct Breeze API, 5th market data provider) — verified in `src/main/java/.../provider/BreezeProvider.java`.
  - **ADDED:** `MerkleAuditLogService` (Section 8) — verified in codebase; provides Merkle-chain tamper-evidence on `audit_log`. Was entirely absent from all documentation.
  - **ADDED:** `GeoOptimizationService` (Section 5) — previously undocumented in services layer.
  - **CONFIRMED:** All existing service descriptions accurate against implementation.
  - **CONFIRMED:** `@Cacheable` Optional<T> constraint — verified in Redis cache implementation.
  - **CONFIRMED:** `ProcessBuilder.environment()` DB credential injection — verified in `MarketDataService.java`.
  - **CONFIRMED:** Jackson `Instant` manual serialization fix in `HtmlMaterializerService` — verified as intentional.