# 08 — Enterprise SEO & Edge Architecture

**Version:** Active in Production (Phase 6 + GEO Complete)
**Classification:** Internal Architectural Reference (Sanitized)

---

## Overview

All traffic entering the Treishvaam ecosystem passes through the **Cloudflare Edge Worker before any React frontend or Spring Boot backend is ever contacted**. The Worker acts simultaneously as a Zero-Trust security gateway, a multi-tenant API proxy, an SEO intelligence layer, and a GEO (Generative Engine Optimization) router.

---

## 1. Zero-Trust Edge Intelligence

### A. Cryptographic Origin Enforcement

Every request the Worker forwards to the backend is signed with **HMAC-SHA-512** using the `AEGIS_EDGE_SECRET` (a Cloudflare Worker Secret — never hardcoded):

```
X-Aegis-Edge-Signature: <hex-encoded HMAC-SHA-512 of path:timestamp:ip>
X-Aegis-Edge-Timestamp: <unix epoch seconds>
```

The backend's `AegisEdgeValidationFilter` verifies this signature on every inbound request with a **300-second replay prevention TTL**. Requests without a valid signature are instantly rejected with 403 Forbidden. Direct IP access to the backend bypassing the Worker is blocked.

**Implementation note:** WebCrypto API natively limits to SHA-512 (not SHA3-512). HMAC-SHA-512 is used at the Edge for 0ms execution speed; BouncyCastle handles SHA3 natively on the Java backend side.

### B. Global Crawler Matrix

The Worker maintains a compiled `GLOBAL_CRAWLER_MATRIX` regex (evaluated once per V8 isolate for 0ms per-request overhead):

| Category | Examples |
| :--- | :--- |
| Search Engines | Googlebot, Bingbot, DuckDuckBot, YandexBot, Baiduspider |
| Google Ecosystem | Google-Extended, Googlebot-News, AdsBot-Google, Mediapartners-Google |
| AI & LLMs | GPTBot, ChatGPT-User, OAI-SearchBot, ClaudeBot, anthropic-ai, PerplexityBot, DeepSeek, Bytespider, Qwen, Mistral, Cohere-training, Diffbot, YouBot |
| Social & Unfurl | Twitterbot, facebookexternalhit, LinkedInBot, Slackbot |
| News & Feeds | flipboard, feedly, NewsBlur, Inoreader |
| Archivers | ia_archiver, archive.org_bot, Wikipedia, SemanticScholarBot |
| Internal | Treishvaam-Worker-Crawler |

SEO/AI crawlers bypass the AEGIS threat evaluation pipeline entirely to preserve indexability.

### C. Multi-Tenant API Proxy & Tenant Isolation

Next.js frontends are completely blind to backend infrastructure. They make requests to relative API paths. The Worker:
1. Intercepts all `/api/**` requests
2. Injects `X-Tenant-ID: finance` (or `agro`) into the forwarded request headers
3. Generates and attaches the HMAC-SHA-512 Edge Signature
4. Securely proxies to the `BACKEND_API_URL` Cloudflare Worker Secret (Cloudflare Tunnel)

The backend `TenantInterceptor` reads `X-Tenant-ID` and scopes all data queries to that tenant.

**CRITICAL:** The `generateMetadata()` server-side fetch in `app/category/.../page.tsx` runs at the Edge and bypasses the Worker. It must include `headers: { 'X-Tenant-ID': 'finance' }` explicitly.

### D. AEGIS L4-ADA Edge Deception

1. Worker checks `aegis:mtd:manifest` from KV for Moving Target Defense path translation
2. If the requesting IP/JA3 hash is flagged as `TARPIT` in the KV threat manifest, the Worker routes to the backend's Virtual Thread tarpit directly
3. Known deception targets receive poisoned corpus payloads without the origin being fetched for real business data

---

## 2. Three-Tier KV Cache-Shield Strategy

All SEO/sitemap requests follow a strict cache hierarchy to minimize free-tier quota consumption:

```
Request for /sitemap.xml or /sitemap-dynamic/*
      │
      ▼
[Tier 1] CDN Edge Cache (caches.default)
      HIT → Serve immediately (0 KV reads, 0 network cost)
      MISS ↓
      ▼
[Tier 2] Cloudflare KV (TREISHFIN_SEO_CACHE namespace)
      HIT → Serve + populate Tier 1 cache
      MISS ↓
      ▼
[Tier 3] Backend Fallback (Spring Boot SitemapService)
      Response served to user immediately
      ctx.waitUntil() → async KV write (user never waits for the write)
```

**KV Key Structure:**

| Key | Value Format | Purpose |
| :--- | :--- | :--- |
| `sitemap:meta` | JSON `{"markets":[...],"blogs":[...]}` | Sitemap index manifest |
| `sitemap:/sitemap-dynamic/blog/0.xml` | XML urlset | Blog post sitemap page 0 |
| `sitemap:/sitemap-dynamic/market/0.xml` | XML urlset | Market ticker sitemap page 0 |
| `aegis:mtd:manifest` | JSON path manifest | MTD temporal path translations |

**Rules:**
- `TREISHFIN_SEO_CACHE_preview` is never used for production logic
- KV `null` returns (cache misses) are always handled gracefully — never crash the Worker
- Cache misses trigger an async backend fetch via `ctx.waitUntil` to protect free-tier write quotas

---

## 3. Generative Engine Optimization (GEO) Routing at the Edge

When an AI LLM crawler is detected (from `aiBotsOnly` regex subset), the Worker intercepts **all HTML content requests** (not just root) and serves GEO payloads directly from KV:

```
AI Bot Request (any HTML path)
      │
      ▼
Worker detects: aiBotsOnly regex match
      │
      ▼
handleGeoFeedFromKV()
      ├── KV HIT  → Serve /ai-feed.md or /llms.txt from KV instantly
      │             Tag response: X-GEO-Bot-Detected: true
      └── KV MISS → Fetch directly from backend GeoOptimizationController
                    ctx.waitUntil() → async KV write for next request
```

This completely bypasses React HTML rendering and Next.js hydration — LLMs get dense, semantic Markdown optimized for vector database ingestion without any JavaScript execution overhead.

**GEO Endpoints served at Edge:**

| Path | Content-Type | Backend Origin |
| :--- | :--- | :--- |
| `/llms.txt` | `text/plain` | `GET /api/public/geo/llms.txt` |
| `/ai-feed.md` | `text/markdown` | `GET /api/public/geo/ai-feed.md` |
| `/ontology.json` | `application/json` | `GET /api/public/geo/ontology.json` |

---

## 4. E-E-A-T Schema Injection (HTMLRewriter)

For standard search engine crawlers (Googlebot, etc.), the Worker uses Cloudflare's `HTMLRewriter` to inject JSON-LD directly into the HTML response stream on root routes:

- **Organization schema:** Treishvaam Group corporate hierarchy, founding date, contact info
- **Person/Founder schema:** Amitsagar Kandpal (Founder mapping for E-E-A-T authority)
- **WebPage schema:** Optimized metadata per route path
- **Dynamic title/meta override:** Path-specific SEO metadata injected without touching the React bundle

---

## 5. SPA Fallback Strategy (GSC 404 Prevention)

The Worker maintains a `KNOWN_SPA_ROUTES` array. If Cloudflare Pages returns a 404 for a known frontend route (e.g., `/about`, `/vision`, `/contact`, `/privacy`, `/terms`), the Worker:
1. Intercepts the 404
2. Fetches the root `index.html` from Pages
3. Forces a `200 OK` response with `X-SPA-Fallback: Active` header

This eliminates false-positive "Soft 404" indexing penalties in Google Search Console.

---

## 6. RSC (React Server Component) Stream Protection

The Worker wraps the entire "SEO Intelligence" block in an `isRscRequest` check. Next.js RSC requests (`?_rsc=` query parameter) bypass all Worker-side HTML injection to prevent breaking server-streaming JSON payloads that would otherwise return a 500 error.

---

## 7. Scheduled Tasks (Cron Jobs)

The Finance Worker runs a **scheduled cron job** to proactively warm the KV sitemap cache:
1. Fetches `/api/public/sitemap/meta` from the backend (with HMAC Edge Signature)
2. Iterates through all sitemap URLs in the manifest
3. Fetches and writes each XML page to KV with appropriate TTLs

This ensures the KV cache is always warm before Google's daily crawl, eliminating Tier 3 backend fallbacks during peak crawler activity.

---

## 8. Frontend SEO — Next.js Metadata API

For page-level SEO, the Finance frontend uses the **Next.js native `metadata` export** and `generateMetadata()` function from Server Components:

- Static pages: `export const metadata: Metadata = { ... }` in `app/*/page.tsx`
- Dynamic post pages: `generateMetadata({ params })` fetches post data server-side for unique `title`, `description`, `openGraph`, and JSON-LD per article
- **Canonical tags:** All pages include `<link rel="canonical">` — this is the SEO duplicate-content protection mechanism for Worker-proxied frontends (bulk redirects on `.pages.dev` would create infinite loops)

**GEO discovery tags in `<head>` (from `app/layout.tsx`):**
```html
<link rel="llms-txt" href="/llms.txt" />
<link rel="alternate" type="application/json+ld" href="/ontology.json" />
<link rel="search" type="application/opensearchdescription+xml" href="/opensearch.xml" />
```

**`<semantic-chunk>` boundaries** wrap `{children}` in `app/layout.tsx` with `id="main-content"` and `data-aegis-geo="active"` — allows AI bots to perfectly slice core content from navigation/footer noise without script execution.

---

## 9. Third-Party Script Architecture (0ms TBT)

**Zero hardcoding rule:** All tracking IDs are injected via `NEXT_PUBLIC_*` environment variables (Cloudflare Pages). They are never in the repository.

GA4 fires unconditionally via Next.js `<Script strategy="afterInteractive">` in `app/layout.tsx`. The `NEXT_PUBLIC_ENFORCE_STRICT_PRIVACY` toggle conditionally injects `anonymize_ip: true` into the GA4 config — maintaining 100% data fidelity by default for Indian jurisdiction while allowing instant GDPR/DPDP compliance activation without a rebuild.

**ABSOLUTE RESTRICTION:** GA4 tracking configuration, compliance layers, and measurement scripts must not be modified, removed, or "optimized" in any way that disrupts, limits, or alters the current data collection payload.

AdSense passive verification (`<meta name="google-adsense-account">`) is injected at build-time via `process.env.NEXT_PUBLIC_ADSENSE_CLIENT_ID` — never hardcoded.

---

## 10. Sitemap Architecture (Backend — SitemapService)

The Spring Boot `SitemapService` generates SEO-compliant XML sitemaps designed for **10M+ articles/URLs**:

- **Pagination:** Blog and market sitemaps are paginated (configurable page size) to comply with the 50,000 URL per sitemap limit
- **Multi-Tenant:** `TenantContext` determines output — Finance gets dynamic blog + market XML; Agro gets static enterprise XML
- **GEO URLs:** `/llms.txt`, `/ai-feed.md`, `/ontology.json` are included in the root sitemap at `priority 1.0` — ensures AI agents discover GEO payloads during routine Google sitemap crawls
- **Duplicate Prevention:** `LinkedHashSet` eliminates duplicate ticker entries while preserving insertion order (prevents Google Search Console validation errors)
- **URL Construction:** Must match frontend React Router paths exactly — `https://treishvaamfinance.com/category/{catSlug}/{userSlug}/{articleId}`

---

## 11. Hybrid SSG — Materialized HTML (HtmlMaterializerService)

On post publication, `HtmlMaterializerService` asynchronously:
1. Fetches the internal HTML shell from OpenResty
2. Injects Open Graph, Twitter Card, and JSON-LD metadata via Jsoup
3. Injects `<link rel="alternate">` tags pointing to GEO endpoints and ontology graph
4. Uploads the complete static HTML to MinIO

The Edge Worker can serve this materialized HTML as a last-resort SSG fallback if the Next.js application is unavailable — guaranteeing 100% SEO uptime even during backend downtime.

---

## 12. Cloudflare Canonicalization Rules (Managed in Dashboard — Never in Code)

Host canonicalization is handled exclusively at the Cloudflare Edge via Dynamic URL Redirect rules. It must never be implemented in `_redirects`, `next.config.mjs`, `worker.js`, or backend code.

See [01-ARCHITECTURE.md](01-ARCHITECTURE.md) for the exact rule expressions.
