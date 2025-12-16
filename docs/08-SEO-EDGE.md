# 08-SEO-EDGE.md

## 1. Edge Rendering Strategy
The Cloudflare Worker intercepts requests to `/category/` URLs and injects SEO-critical Schema.org and meta tags directly into the HTML before the React app loads. This Edge-Side Rendering (ESR) ensures that search engines and social media crawlers receive rich metadata and structured data instantly, improving SEO and link previews even if the backend is slow or down.

### Flow Overview
1.  **Interception:** Requests to `/category/` are caught by the Cloudflare Worker.
2.  **Data Fetch:** The Worker fetches blog post data from the Backend API (`/api/v1/posts/url/...`).
3.  **Injection:** It generates and injects JSON-LD schemas (`NewsArticle` or `BlogPosting`, `BreadcrumbList`, `VideoObject`) and updates `<title>` and `<meta>` tags in the HTML response.
4.  **Delivery:** The modified HTML is returned to the client or crawler before the heavy React app loads.

## 2. High Availability Robots.txt
We use a **Dual-Layer** strategy for `robots.txt` to ensure maximum availability:

1.  **Source of Truth:** The primary file is located at `public/robots.txt` in the Frontend codebase. This is deployed to Cloudflare Pages.
2.  **Worker Fallback:** The Cloudflare Worker has a hardcoded copy of the rules. If the static asset fetch fails (e.g., Pages outage), the Worker serves the fallback immediately.

**Critical Rules:**
- `Allow: /api/posts`: Essential for Googlebot to fetch content for rendering.
- `Allow: /api/news` & `/api/categories`: Allows indexing of dynamic content.
- `Disallow: /api/admin/`: Protects backend administrative routes.
- `Disallow: /dashboard/`: Prevents indexing of user-specific pages.

## 3. Sitemap Proxying
Requests to `/sitemap.xml`, `/sitemap-news.xml`, `/feed.xml`, and `/sitemaps/*` are proxied by the Worker directly to the Backend (`backend.treishvaamgroup.com`).
- **Function:** This ensures Google receives the freshest XML files generated daily by the Spring Boot backend.
- **Failover:** If the backend is down, the Worker returns a generic 503 error for these specific paths to prevent search engines from de-indexing valid pages due to bad data.

## 4. Structured Data Injection
The Worker dynamically injects JSON-LD schemas based on the URL type:
- **Homepage:** `WebSite` schema with a `SearchAction` for enhanced Sitelinks.
- **Static Pages:** `WebPage` schema for About, Vision, and Contact pages.
- **Blog Posts:**
    - Fetches live data from the API.
    - Selects `NewsArticle` (for News/Market categories) or `BlogPosting` (for others).
    - Adds `VideoObject` if a YouTube video is detected in the content.

## 5. Image Handling Strategy
- **Storage:** Images are uploaded to MinIO/Local Storage (`/app/uploads`).
- **Access:** They are accessed via the standardized path: `/api/v1/uploads/filename.ext`.
- **Optimization:**
    - **Nginx:** Proxies these requests directly to the file system, bypassing Java logic for speed.
    - **Cloudflare:** Caches these images at the edge, reducing server load.
    - **Worker:** The Worker explicitly allows `/api` requests to pass through untouched so images load instantly.

## 6. Failover Handling
- **Backend Down?**
    - Sitemaps/Feeds return 503 (protecting SEO ranking).
    - `robots.txt` is served from the Worker (keeping crawlers active).
    - HTML pages attempt to serve a "Stale-While-Revalidate" cached version if available.