# BE-08 — Enterprise SEO, GEO & Edge Architecture

**Version:** Active in Production (Phase 6 + GEO Provenance Complete)
**Classification:** Internal Architectural Reference (Sanitized)
**Last Verified:** 2026-05-29 — Verified against `SitemapController.java`, `GeoOptimizationController.java`, `GeoOptimizationService.java`, `SitemapService.java`, `worker/worker.js`, `app/llms.txt/route.ts`, `app/ai-feed.md/route.ts`, `app/ontology.json/route.ts`

---

## Overview

All traffic entering the Treishvaam Finance ecosystem passes through the **Cloudflare Edge Worker before any React frontend or Spring Boot backend is contacted**. The Worker acts simultaneously as a Zero-Trust security gateway, a multi-tenant API proxy, an SEO intelligence layer, and a GEO (Generative Engine Optimization) router.

This document covers the SEO, GEO, and sitemap systems. For the complete Worker security architecture see `FIN-03-WORKER-EDGE.md`. For the AEGIS security layers see `BE-07-SECURITY.md`.

---

## 1. Sitemap Architecture

### Design Principles

The sitemap system is designed for **10M+ URLs** with incremental, non-blocking generation:

- **Paginated XML output** — sitemaps are generated in page-sized chunks, not monolithic files
- **Multi-tenant routing** — `SitemapService` applies contextual logic based on `TenantContext`
- **Worker KV caching** — sitemaps are cached at the Cloudflare Edge, independent of backend availability
- **Hourly refresh** — Worker cron (`0 * * * *`) proactively updates KV cache

### Backend Endpoints (`SitemapController.java`)

Base path: `/api/public/sitemap/` — publicly accessible, no authentication required, read-only.

| Endpoint | Description |
| :--- | :--- |
| `GET /api/public/sitemap/meta` | Returns JSON metadata listing all sitemap segment URLs. Consumed by Worker cron |
| `GET /api/public/sitemap/blog/{page}.xml` | Returns paginated XML `<urlset>` of blog post URLs |
| `GET /api/public/sitemap/market/{page}.xml` | Returns paginated XML `<urlset>` of market ticker URLs |

**Metadata response format:**
```json
{
  "blogs": ["/sitemap-dynamic/blog/0.xml", "/sitemap-dynamic/blog/1.xml"],
  "markets": ["/sitemap-dynamic/market/0.xml"]
}
```

### Sitemap URL Conventions

| URL seen by Search Engines / Worker | Actual Backend Path |
| :--- | :--- |
| `/sitemap-dynamic/blog/0.xml` | `/api/public/sitemap/blog/0.xml` |
| `/sitemap-dynamic/market/0.xml` | `/api/public/sitemap/market/0.xml` |
| `/sitemap.xml` | Static index from `public/sitemap.xml` — links to `/sitemap-dynamic/*` |

The Worker translates the public `/sitemap-dynamic/` paths to backend `/api/public/sitemap/` paths inside `handleDynamicSitemapFromKV()`.

### KV Cache Architecture

```
KV Namespace: TREISHFIN_SEO_CACHE (Production)

Key: sitemap:finance:meta
Value: {"blogs":[...],"markets":[...]}
TTL: 90000 seconds (25 hours)

Key: sitemap:finance:/sitemap-dynamic/blog/0.xml
Value: <urlset>...</urlset>   (XML string)
TTL: 90000 seconds

Key: sitemap:finance:/sitemap-dynamic/market/0.xml
Value: <urlset>...</urlset>   (XML string)
TTL: 90000 seconds
```

**Three-tier serving priority:**
1. Cloudflare CDN Edge Cache (`caches.default`) — fastest, 0ms KV read
2. `TREISHFIN_SEO_CACHE` KV — milliseconds, survives backend outage
3. Backend API fallback — only on KV miss; async KV write after serving

**High-availability guarantee:** If the backend is completely unavailable, Googlebot and AI crawlers continue to receive sitemap XML from KV indefinitely until TTL expires. The cron warmer keeps KV populated proactively.

### Tenant-Contextual Sitemap Behavior (`SitemapService.java`)

- **Finance tenant** → Dynamic paginated blog + market sitemaps (generated from DB queries)
- **Agro tenant** → Static E-E-A-T XML payload (returned as-is, no DB query)

All background scheduler jobs that call `SitemapService` must explicitly declare `TenantContext.set("finance")` to prevent cross-tenant contamination in Virtual Thread workers.

---

## 2. GEO (Generative Engine Optimization) Architecture

### Design Principles

GEO is the practice of structuring content specifically for ingestion by LLM-based search agents (ChatGPT, Perplexity, Claude, DeepSeek, Gemini). Instead of forcing AI bots to parse and execute React/Next.js HTML, AEGIS intercepts them at the Cloudflare Edge and serves condensed, semantically structured, signed payloads.

**Key insight:** AI crawlers do not execute JavaScript. Serving them full React HTML wastes compute and produces poor AI comprehension. GEO payloads are purpose-built for token-efficient AI ingestion.

### Complete GEO Request Flow

```
AI Bot (GPTBot, ClaudeBot, DeepSeek, OAI-SearchBot, PerplexityBot, etc.)
  User-Agent matches aiBotsOnly regex
      │
      ▼
Cloudflare Edge Worker
  isAiBot = true
      │
  GET request, not asset/API/llms.txt/ontology.json
      │
      ▼
handleGeoFeedFromKV(request → /ai-feed.md)
      │
      ├─ CDN Cache HIT → serve immediately
      ├─ KV HIT (key: geo:finance:/ai-feed.md) → serve + cache
      └─ KV MISS → fetch /api/public/geo/ai-feed.md from backend
                    (signed with AEGIS Edge Signature)
                    + ctx.waitUntil → async KV write (expirationTtl: 86400)
                    + ctx.waitUntil → async CDN cache write
                    Response header: X-GEO-Bot-Detected: true
      │
      ▼
React HTML completely bypassed — no JavaScript execution, no hydration
```

### Backend GEO Endpoints (`GeoOptimizationController.java`)

Base path: `/api/public/geo/` — publicly accessible, GDPR-compliant (no PII ever in output).

| Endpoint | Content-Type | Description |
| :--- | :--- | :--- |
| `GET /api/public/geo/llms.txt` | `text/plain` | AI agent discovery file — platform summary, citation guidelines, entity disambiguation |
| `GET /api/public/geo/ai-feed.md` | `text/markdown` | Aggregated semantic feed — recent market data, published articles, company vision |
| `GET /api/public/geo/ontology.json` | `application/json` | Absolute JSON-LD knowledge graph — entity relationships, domain ontology |

All endpoints return `Cache-Control: public, max-age=3600`.

### Frontend GEO Proxy Routes (Next.js)

These `app/` route handlers proxy backend GEO payloads to the frontend domain, enabling direct browser-accessible GEO URLs:

| Next.js Route | File | Proxies To |
| :--- | :--- | :--- |
| `GET /llms.txt` | `app/llms.txt/route.ts` | `GET /api/public/geo/llms.txt` |
| `GET /ai-feed.md` | `app/ai-feed.md/route.ts` | `GET /api/public/geo/ai-feed.md` |
| `GET /ontology.json` | `app/ontology.json/route.ts` | `GET /api/public/geo/ontology.json` |

**Execution priority:** The Worker intercepts AI crawlers before they reach Next.js and serves KV-cached payloads. These Next.js handlers are the **fallback path** — they execute only when:
- A human browser directly requests `/llms.txt` (non-AI user-agent)
- The KV cache misses and the Worker delegates to the backend, which proxies through Next.js to backend

Each route handler includes `headers: { 'X-Tenant-ID': 'finance' }` in its backend fetch.

### GEO KV Cache Keys

```
Key: geo:finance:/llms.txt
Key: geo:finance:/ai-feed.md
Key: geo:finance:/ontology.json
TTL: 86400 seconds (24 hours)
```

### GEO Discovery Tags in `app/layout.tsx`

The following tags are embedded in every page's `<head>` to enable AI agent self-discovery:
```html
<link rel="llms-txt" href="/llms.txt">
<link rel="alternate" type="application/json+ld" href="/ontology.json">
<link rel="search" type="application/opensearchdescription+xml" href="/opensearch.xml">
<semantic-chunk id="main-content" data-aegis-geo="active">
  {children}
</semantic-chunk>
```

The Worker additionally injects `<link rel="alternate" type="text/markdown" href="/llms.txt">` and `<link rel="alternate" type="application/json+ld" href="/ontology.json">` into every HTML response via `HTMLRewriter`.

---

## 3. Edge-Side SEO Intelligence

### E-E-A-T Schema Injection (HTMLRewriter)

The Worker uses Cloudflare's `HTMLRewriter` API to inject structured data without requiring a backend call for most pages:

| Route | Schema Type | Source |
| :--- | :--- | :--- |
| `/`, `/home` | `WebSite` JSON-LD | Static in Worker |
| `/category/…/:id` | Dynamic — fetched from `GET /api/v1/posts/url/{id}` | Backend API call |
| `/market/:ticker` | Dynamic — fetched from `GET /api/v1/market/widget?ticker=` | Backend API call |

Dynamic schema injections also populate `window.__PRELOADED_STATE__` in the HTML `<head>` for client-side hydration — eliminating a secondary API round-trip after React mounts. The payload is sanitized via `safeStringify()` to prevent XSS.

### SPA 404 Prevention

Known SPA routes that return 404 from Next.js Pages (e.g., `/newsroom`, `/investors`, `/careers`) are intercepted by the Worker's SPA fallback logic:
- If response is 404/403 and pathname matches known SPA routes or has no file extension
- Worker fetches `/index.html` from Cloudflare Pages
- Serves it with status 200 and `X-SPA-Fallback: Active` header

This prevents Googlebot from indexing spurious 404 errors on valid SPA routes.

### Canonical URL Architecture (Cloudflare Dashboard — Not in Code)

Canonical enforcement is handled exclusively at the Cloudflare Edge Rules layer. **Never implement canonical redirects in `_redirects`, `next.config.mjs`, `worker.js`, or backend code.**

| Rule | Expression | Target |
| :--- | :--- | :--- |
| www → apex (301) | `http.host in {"www.treishvaamfinance.com" "www.treishvaamgroup.com" "www.treishvaamagro.com"}` | `concat("https://", substring(http.host, 4), http.request.uri.path)` |
| Legacy subdomain (301) | `http.host eq "treishfin.treishvaamgroup.com"` | `concat("https://treishvaamfinance.com", http.request.uri.path)` |

Finance and Agro `.pages.dev` URLs are **NOT** in the Bulk Redirect list — doing so would cause `ERR_TOO_MANY_REDIRECTS` because the Worker fetches `.pages.dev` as its origin. Canonical protection for Worker-proxied sites is handled via `<link rel="canonical">` HTML tags in Next.js.

---

## 4. High-Availability SEO Guarantee

The system is intentionally architected so that:

| Condition | SEO Behavior |
| :--- | :--- |
| Backend fully available | Sitemaps served from KV cache (backend updates KV on publish events) |
| Backend temporarily down | Sitemaps served from KV cache (up to 25 hours). GEO payloads served from KV (up to 24 hours) |
| KV cache miss + backend down | Worker returns 503 with correct `Content-Type`; Googlebot retries |
| CDN cache available | Content served globally at 0ms latency from Cloudflare PoP |

No backend outage affects sitemap availability for search engines or AI crawlers within the KV TTL windows. This is a deliberate architectural guarantee.

---

## 5. robots.txt & Crawler Policy

`public/robots.txt` defines crawler access policies. The Worker passes `robots.txt` through directly from Cloudflare Pages (`addSecurityHeaders()` wrapper applied).

Key rules (verified in `public/robots.txt`):
- All known good bots: `Allow: /`
- Known content scrapers: `Disallow: /`
- Sitemap declaration: `Sitemap: https://treishvaamfinance.com/sitemap.xml`

---

## 6. OpenSearch Discovery

`GET /opensearch.xml` — served by `app/opensearch.xml/route.ts` (self-contained, no backend call). Provides browser search engine integration. Referenced in `app/layout.tsx` via `<link rel="search" ...>`.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **VERIFIED (2026-05-29 — Enterprise Documentation Generation):**
  - Verified `SitemapController.java` endpoints: `/meta`, `/blog/{page}.xml`, `/market/{page}.xml`.
  - Verified `GeoOptimizationController.java` endpoints: `/llms.txt`, `/ai-feed.md`, `/ontology.json`.
  - Verified `app/llms.txt/route.ts`, `app/ai-feed.md/route.ts`, `app/ontology.json/route.ts`, `app/opensearch.xml/route.ts`.
  - Verified KV key naming convention: `sitemap:finance:*` and `geo:finance:*` — confirmed in `worker.js` lines 68 and 398.
  - Verified three-tier caching logic: CDN cache → KV → backend fallback.
  - Verified `ctx.waitUntil()` pattern for non-blocking async KV writes.
  - Verified aggressive GEO intercept logic: all AI bot GET requests (non-asset, non-API) routed to `/ai-feed.md` via `handleGeoFeedFromKV`.
  - Added `window.__PRELOADED_STATE__` documentation.
  - Added Agro Worker GEO gap observation (pending AEGIS Phase 6 upgrade).