# BE-03 — Backend API Reference

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Security Status:** Fort Knox Security Suite Enabled / AEGIS Active
**Classification:** Internal Reference (Sanitized)
**Last Verified:** 2026-05-29 — All endpoints verified against actual controller classes: `BlogPostController.java`, `CategoryController.java`, `FileController.java`, `ContactController.java`, `MarketDataController.java`, `NewsHighlightController.java`, `AdminActionsController.java`, `AnalyticsController.java`, `HealthCheckController.java`, `ApiStatusController.java`, `SearchController.java`, `SitemapController.java`, `GeoOptimizationController.java`, `AegisMtdController.java`, `MonitoringController.java`

**Base URL**: `/api/v1` (unless otherwise noted)

---

## 1. Content Management

### Blog Post Controller (`BlogPostController`)
**Base Path:** `/api/v1/posts`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Public | List published posts with pagination |
| `GET` | `/{id}` | Public | Get a single post by numerical ID. Returns `version` for optimistic locking |
| `GET` | `/public/{slug}` | Public | Get a post by URL-friendly slug |
| `GET` | `/url/{urlArticleId}` | Public | Get a post by legacy URL Article ID. **Redis-cached** |
| `GET` | `/category/{categorySlug}` | Public | List published posts within a category |
| `GET` | `/tags/{tag}` | Public | List published posts matching a tag |
| `GET` | `/recent` | Public | Get most recently published posts (limit: 5) |
| `GET` | `/featured` | Public | Get posts marked as Featured |
| `GET` | `/search` | Public | Simple keyword search by title/content (DB-level, not Elasticsearch) |
| `POST` | `/draft` | Auth | Create a new blog post in `DRAFT` status |
| `PUT` | `/draft/{id}` | Auth | Update an existing draft. **Body must include `version` field** |
| `GET` | `/admin/drafts` | Auth | List all posts with `DRAFT` status |
| `GET` | `/admin/all` | Auth | List all posts regardless of status (Published/Draft/Archived) |
| `PUT` | `/{id}` | EDITOR+ | Update a published post. **Requires `version` param** (optimistic locking — V40) |
| `POST` | `/admin/publish/{id}` | PUBLISHER+ | Change post status to `PUBLISHED` |
| `DELETE` | `/{id}` | PUBLISHER+ | Permanently delete a post |
| `POST` | `/{id}/duplicate` | Auth | Clone an existing post into a new draft |
| `POST` | `/{id}/share` | PUBLISHER+ | Trigger a LinkedIn share for this post |

**Optimistic Locking Rule:** All `PUT` operations on `blog_posts` require the current `version` value in the request body. If the version has changed since last fetch (concurrent edit), the server returns `409 Conflict`. The client must re-fetch and re-apply their changes.

### Category Controller (`CategoryController`)
**Base Path:** `/api/v1/categories`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Public | List all active categories |
| `POST` | `/` | ADMIN | Create a new category |
| `PUT` | `/{id}` | ADMIN | Update an existing category |
| `DELETE` | `/{id}` | ADMIN | Delete a category |

### File Controller (`FileController`)
**Base Path:** `/api/v1/files`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/upload` | ADMIN | Upload image/file to MinIO. Validates MIME type via **Apache Tika**. Streams to disk to prevent OOM. Max file size enforced |
| `GET` | `/{filename}` | Public | Serve file content (if not served directly via OpenResty static volume) |

### Contact Controller (`ContactController`)
**Base Path:** `/api/v1/contact`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/` | Public | Submit contact form. Email + message stored AES-256-GCM encrypted |
| `GET` | `/info` | Public | Get contact information (email, phone, address) |

---

## 2. Market Data & News

### Market Data Controller (`MarketDataController`)
**Base Path:** `/api/v1/market`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/indices` | Public | Summary data for major global indices |
| `GET` | `/movers` | Public | Top gainers, losers, and most active stocks |
| `GET` | `/quote/{symbol}` | Public | Real-time quote for a specific symbol |
| `GET` | `/history/{symbol}` | Public | Historical price data (candles) for charts |
| `GET` | `/widget` | Public | Optimized data payload for the frontend market widget. **Redis-cached** |
| `POST` | `/admin/refresh` | ADMIN | Force manual refresh of market data from external providers |

**Caching note:** `GET /widget` and `GET /quote/{symbol}` are Redis-cached via `@Cacheable`. `@Cacheable` is NOT applied to any method returning `Optional<T>` — Jackson cannot deserialize Optional from Redis JSON; doing so causes fatal 500 on cache hit.

### News Highlight Controller (`NewsHighlightController`)
**Base Path:** `/api/v1/news-highlights`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/ticker` | Public | Scrolling news ticker items |
| `GET` | `/intel` | Public | Structured news intelligence data |
| `GET` | `/top` | Public | Top headline news |

---

## 3. System & Administration

### Admin Actions Controller (`AdminActionsController`)
**Base Path:** `/api/v1/admin/actions`
**Auth:** Requires L3-ZKA Zero-Knowledge Proof (`X-AEGIS-ZKP-Proof` header) + ADMIN role

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/cache/clear` | ADMIN | Flush all Redis caches immediately |
| `GET` | `/system-properties` | ADMIN | List dynamic system configuration properties |
| `POST` | `/system-properties` | ADMIN | Update a system property |

### Analytics Controller (`AnalyticsController`)
**Base Path:** `/api/v1/analytics`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | ADMIN | Historical audience data. Query params: `startDate`, `endDate`, `country`, `region`, `city`, `operatingSystem`, `osVersion`, `sessionSource` |
| `POST` | `/` | permitAll | Audience visit ingestion endpoint (internal RUM beacon) |
| `GET` | `/realtime` | ADMIN | Real-time analytics data for active sessions and user behavior |

### Health Check Controller (`HealthCheckController`)
**Base Path:** `/api/v1/health`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Public | Health check endpoint. Returns `200 OK` when backend is live |

### API Status Controller (`ApiStatusController`)
**Base Path:** `/api/v1/status`
**Auth:** ADMIN

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | ADMIN | Most recent fetch status for each external API (AlphaVantage, Finnhub, FMP, Yahoo, NewsData) |
| `GET` | `/history` | ADMIN | Full historical log of API fetch attempts, latency, and failure codes |

---

## 4. Search & SEO

### Search Controller (`SearchController`)
**Base Path:** `/api/v1/search`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/query` | Public | Full-text search via Elasticsearch — high-performance, relevance-ranked |
| `POST` | `/reindex` | ADMIN | Rebuild the Elasticsearch index from the database |

### Sitemap Controller (`SitemapController`)
**Base Path:** `/api/public/sitemap`
**Auth:** Public — no authentication required

**Code-verified endpoints (from `SitemapController.java` — these are the actual implemented endpoints):**

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/public/sitemap/meta` | Returns JSON metadata listing all sitemap segment URLs. Consumed by Cloudflare Worker cron |
| `GET` | `/api/public/sitemap/blog/{page}.xml` | Returns paginated XML `<urlset>` of blog post URLs |
| `GET` | `/api/public/sitemap/market/{page}.xml` | Returns paginated XML `<urlset>` of market ticker URLs |

**Public-facing sitemap URLs (translated by Cloudflare Worker):**

| Public URL | Translated Backend Path | Served By |
| :--- | :--- | :--- |
| `/sitemap.xml` | Static index file | Cloudflare Pages (`public/sitemap.xml`) |
| `/sitemap-dynamic/blog/0.xml` | `/api/public/sitemap/blog/0.xml` | Worker KV cache → backend fallback |
| `/sitemap-dynamic/market/0.xml` | `/api/public/sitemap/market/0.xml` | Worker KV cache → backend fallback |

**Note on previous doc discrepancy:** The previous `BE-03-API.md` listed `/sitemap.xml`, `/sitemap-news.xml`, `/sitemaps/static.xml`, `/sitemaps/categories.xml`, and `/sitemaps/posts-{page}.xml` as backend endpoints. These are either static Cloudflare Pages files or legacy paths. The **actual implemented backend endpoints** are strictly the three `/api/public/sitemap/*` paths above, verified against `SitemapController.java`.

---

## 5. AEGIS Security & GEO

### Geo Optimization Controller (`GeoOptimizationController`)
**Base Path:** `/api/public/geo`
**Auth:** Public — GDPR-compliant, no PII ever in output

| Method | Endpoint | Content-Type | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/public/geo/llms.txt` | `text/plain` | AI agent discovery file — platform summary, citation guidelines, entity disambiguation |
| `GET` | `/api/public/geo/ai-feed.md` | `text/markdown` | Aggregated semantic Markdown feed — articles, market insights, company vision |
| `GET` | `/api/public/geo/ontology.json` | `application/json` | Structured JSON-LD knowledge graph — entity relationships and domain ontology |

All three endpoints return `Cache-Control: public, max-age=3600`. They are Cloudflare Worker KV-cached with a 24-hour TTL — direct backend hits only occur on KV cache misses.

### AEGIS MTD Controller (`AegisMtdController`)
**Base Path:** `/api/v1/aegis/mtd`
**Auth:** L3-ZKA Zero-Knowledge Proof required (ZKP-Auth)

| Method | Endpoint | Auth | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/manifest` | ZKP-Auth | Secure retrieval of the current daily Moving Target Defense temporal path manifest |
| `POST` | `/sync-edge` | ZKP-Auth | Forces manual push of the latest threat intelligence to Cloudflare Edge KV |

### Monitoring & Telemetry Controller (`MonitoringController`)
**Base Path:** `/api/v1/monitoring`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/ingest` | Public | Receives Grafana Faro RUM payloads and AEGIS L5-BIE biometric hashes. Whitelisted in WAF and AEGIS edge filter |

### AEGIS Tarpit Endpoint (L4-ADA)
**Base Path:** `/api/v1/aegis/tarpit`
**Auth:** Public (Worker-routed only — TARPIT-flagged IPs routed here by Edge Worker)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET/POST` | `/trap` | Virtual Thread tarpit. Holds attacker connections at 1 byte/sec. Never returns a useful response |

---

## 6. Internal / Utility Endpoints

### OpenAPI / Swagger (Dev Only)

- **`springdoc.api-docs.enabled=false`** — disabled in production
- **`springdoc.swagger-ui.enabled=false`** — disabled in production
- Available only in `dev` profile at `http://localhost:8080/swagger-ui.html`

### Spring Boot Actuator (Restricted)

| Endpoint | Exposure | Auth |
| :--- | :--- | :--- |
| `/actuator/health` | Public | None |
| `/actuator/prometheus` | Internal (Prometheus scrape only) | None (network-restricted) |
| All others | **Disabled** | N/A |

`env`, `heapdump`, `beans`, `mappings` endpoints are all disabled in production to prevent reconnaissance and data leakage.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **VERIFIED + UPDATED (2026-05-29 — Enterprise Documentation Generation):**
  - All endpoints verified against actual controller source files.
  - **CORRECTED:** Sitemap endpoint section. Previous version listed legacy/static sitemap paths as backend endpoints. Actual backend implementation in `SitemapController.java` is strictly: `/api/public/sitemap/meta`, `/api/public/sitemap/blog/{page}.xml`, `/api/public/sitemap/market/{page}.xml`. Added table distinguishing public Worker-translated URLs from actual backend API paths.
  - **ADDED:** AEGIS Tarpit endpoint (`/api/v1/aegis/tarpit/trap`) — used by the Worker to route TARPIT-flagged IPs. Was not documented previously.
  - **CONFIRMED:** `AegisMtdController` endpoints (`/manifest`, `/sync-edge`) — these were present in the original doc and confirmed in code.
  - **CONFIRMED:** `POST /api/v1/analytics/` (public RUM ingestion) endpoint exists alongside the ADMIN `GET /` analytics endpoint.
  - **ADDED:** Optimistic locking rule for Blog Post PUT operations.
  - **ADDED:** Redis caching note and `Optional<T>` constraint for MarketDataController.
  - **CONFIRMED:** Swagger/OpenAPI disabled in production via `springdoc.api-docs.enabled=false`.