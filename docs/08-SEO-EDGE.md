/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Documents the Treishvaam Group's Zero-Trust Edge SEO Architecture and Cloudflare Worker routing logic.
 *
 * Scope:
 * - Covers Cloudflare KV Caching, Edge-side E-E-A-T Schema Injection, SPA Fallback routing, and Hybrid SSG Materialization.
 *
 * Critical Dependencies:
 * - Cloudflare Workers (`treishvaamagro-seo-worker`, `treishfin-seo-worker`).
 * - Cloudflare KV Namespace (`TREISHFIN_SEO_CACHE`).
 * - Java Spring Boot Backend (For Hybrid SSG generation and dynamic sitemap fallbacks).
 *
 * Security Constraints:
 * - Zero-Trust Routing: Workers must NEVER contain hardcoded backend URLs. They must rely on `CF_PAGES_ORIGIN` and `BACKEND_ORIGIN` secrets.
 * - Tenant Isolation: Workers MUST inject the `X-Tenant-ID` header into every backend API proxy request.
 * - Canonicalization: Host canonicalization (www -> apex) MUST occur via Cloudflare Bulk Redirects, not inside the Worker or React code.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED: Initial Treishvaam Finance SSG Architecture (Materialized HTML).
 * - EDITED:
 * • Phase 3 Update: Completely overhauled for Multi-Tenant Edge Architecture.
 * • Documented `TREISHFIN_SEO_CACHE` Free-Tier KV read-through caching strategy with `ctx.waitUntil`.
 * • Added Edge-side SPA Fallback (404 -> 200 OK) logic for GSC error prevention.
 * • Added E-E-A-T Schema Injection (Organization, Founder mapping) via `HTMLRewriter`.
 * • Integrated Zero-Trust API reverse proxy logic (`X-Tenant-ID`).
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted,
 * truncated, rewritten, or regenerated.
 * Future AI must append only.
 */

# 08 - Enterprise SEO & Edge Architecture

The Treishvaam ecosystem relies on a highly intelligent **Cloudflare Edge Layer** to enforce Zero-Trust security, manage multi-tenant routing, and guarantee flawless SEO indexability. The Cloudflare Worker is the **absolute first layer of contact** for all incoming traffic.

## 1. The Zero-Trust Edge Intelligence (Worker Logic)

The Edge Workers (`treishvaamagro-seo-worker`, `treishfin-seo-worker`) execute four primary enterprise functions before a request ever reaches the React frontend or the Spring Boot backend.

### A. API Reverse Proxy & Tenant Isolation
Next.js frontends are completely blind to the actual backend infrastructure. They make requests to relative paths (e.g., `/api/v1/data`). 
* The Worker intercepts these requests.
* It injects the critical **`X-Tenant-ID`** header (e.g., `agro` or `finance`).
* It securely proxies the request to the `BACKEND_ORIGIN` secret (the Cloudflare Tunnel).

### B. SPA Fallback (Google Search Console Fix)
Traditional SPAs often return HTTP 404/403 status codes while client-side routing initializes, causing Googlebot to flag valid pages as "Soft 404s".
* **The Fix:** The Worker maintains a `KNOWN_SPA_ROUTES` array (e.g., `/about`, `/infrastructure`, `/products`).
* If the underlying Cloudflare Pages deployment returns a 404 for these routes, the Worker intercepts it, fetches the root `index.html`, and forces a **`200 OK`** response with an `X-SPA-Fallback: Active` header. 
* This mathematically eliminates false-positive indexing errors in GSC.

### C. Edge-Side E-E-A-T Schema Injection
To establish Domain Authority and Entity Mapping without bloating the React client bundle, the Worker uses Cloudflare's `HTMLRewriter` to inject raw JSON-LD directly into the HTML stream.
* **Organization & Founder Mapping:** Injects corporate hierarchy (Treishvaam Group as parent) and Founder details (Amitsagar Kandpal) on root routes.
* **Dynamic Title/Meta Rewriting:** Overrides basic static tags with highly optimized SEO metadata based on the exact request path.

---

## 2. Free-Tier KV Caching Strategy (Sitemaps)

To support 10M+ URLs without crashing the Spring Boot backend or exceeding Cloudflare Free-Tier limits, sitemaps are heavily cached at the Edge.

1. **Read-Through Cache:** Requests for `/sitemap.xml` check the `TREISHFIN_SEO_CACHE` KV namespace first.
2. **HIT:** Served instantly with `X-Cache-Status: HIT-KV`.
3. **MISS:** The Worker securely proxies the request to the Spring Boot `SitemapService` (passing the `X-Tenant-ID`).
4. **Asynchronous Update:** When the backend responds with the XML, the Worker serves it to the user immediately, and uses `ctx.waitUntil()` to save the XML into the KV cache in the background. This ensures the user/crawler never waits for the write operation.

---

## 3. Core Content Strategy: "Materialized HTML" (Hybrid SSG)

For highly dynamic, content-heavy tenants (like Finance blogs), we use a **Materialized HTML** pattern. This effectively turns a dynamic CMS into a Static Site Generator (SSG) on demand, bypassing React hydration errors (Error #418/#423).

### The Flow
1.  **Editor Publishes Post:**
    * Backend saves data to MariaDB.
    * Backend triggers `HtmlMaterializerService` (Async).
    * Service fetches the **Live React Shell** (`index.html`) from the internal Nginx gateway.
    * Service injects: SEO Title & Meta Tags, JSON-LD Schema (NewsArticle), **Full HTML Body Content** (into `<div id="server-content">`), and **Redux State** (into `window.__PRELOADED_STATE__`).
    * Service uploads the resulting `.html` file to MinIO (bucket: `treish-public`) under `posts/{slug}.html`.

2.  **Cloudflare Worker (The Router):**
    * Intercepts requests to `/category/...`.
    * **Strategy A (Static Hit):** Checks MinIO (via Nginx) for `posts/{slug}.html`.
        * **CRITICAL:** Injects `<base href="/">` into the `<head>`. This forces the browser to load CSS/JS from the root domain, preventing MIME type errors on deep URLs.
    * **Strategy B (Fallback):** If missing, falls back to fetching API JSON and injecting it into the empty shell (Edge Hydration).

3.  **Client (Browser/Googlebot):**
    * **Googlebot:** Sees fully rendered HTML immediately (`<body>...content...</body>`).
    * **User:** Sees content immediately (FCP < 0.5s).
    * **React (Client-Side):** Uses `ReactDOM.createRoot`. Detects `window.__PRELOADED_STATE__`, builds the interactive app in `<div id="root">`, and **removes** the static `<div id="server-content">` to prevent UI duplication.

---

## 4. Edge Canonicalization (Strict Rule)

**Host canonicalization must NEVER occur in Next.js/React code or inside the Worker execution block.**
To prevent duplicate content penalties, Canonicalization (e.g., routing `www.treishvaamagro.com` to `treishvaamagro.com`) is handled at the absolute outermost edge using **Cloudflare Bulk Redirect Rules**.

**Expression Target:** `concat("https://", substring(http.host, 4), http.request.uri.path)`

---

## 5. Verification & Debugging

### Worker Routing & KV Verification
Use `curl` or PowerShell to verify Edge Cache headers:
```bash
# Check Sitemap KV Cache
curl -I -L [https://treishvaamagro.com/sitemap.xml](https://treishvaamagro.com/sitemap.xml)
# Expected: X-Cache-Status: HIT-KV (or MISS-KV-FETCHED)

# Check SPA Fallback
curl -I -L [https://treishvaamagro.com/about](https://treishvaamagro.com/about)
# Expected: HTTP 200 OK with X-SPA-Fallback: Active (if backend page was absent)
```

### Materialized HTML Verification (Legacy SSG)
**Script:** `scripts/verify_seo.sh <slug>`

**Checks Performed:**
1.  **MinIO Check:** Verifies the `.html` file exists in the public bucket.
2.  **Worker Check:** Curls the public URL with `User-Agent: Googlebot`.
3.  **Header Check:** Confirms `X-Source: Materialized-HTML`.
4.  **Base Tag Check:** Confirms `<base href="/">` is present (preventing broken styles).