# SEO & Edge Architecture

This document details the "Edge-Side Rendering" (ESR) strategy used to ensure perfect SEO and social sharing previews for the Single Page Application (SPA).

## 1. The Challenge
React SPAs render content specifically in the browser (Client-Side Rendering). This presents two major problems:
1.  **Crawlers**: Legacy bots (e.g., LinkedIn, Twitter, some Google bots) do not execute JavaScript, seeing only an empty `<div id="root"></div>`.
2.  **Performance**: Waiting for the full React bundle to download just to read meta tags adds latency to link previews.

## 2. The Solution: Cloudflare Worker Strategy

We deploy a custom Cloudflare Worker (`cloudflared/worker.js`) that acts as a smart proxy between the user and the application.

### 2.1. Request Interception Flow
The Worker intercepts requests based on the URL path and the `User-Agent` header.

1.  **Bot Detection**: The Worker checks if the `User-Agent` matches known bots (Googlebot, Bingbot, LinkedInBot, Twitterbot, WhatsApp, etc.).
    * **Regular Users**: Requests are passed through to the CDN/Nginx to load the React app immediately.
    * **Bots**: The Worker engages the "Edge Rendering" engine.

2.  **Metadata Fetching**:
    * If a bot requests a blog post (e.g., `/category/market-news/bitcoin-rally`), the Worker makes a high-speed, server-to-server call to the Backend API:
        * **Endpoint**: `/api/v1/posts/public/{slug}`
    * **Caching**: This API response is cached using the Cloudflare Cache API to prevent hammering the backend.

3.  **HTML Injection**:
    * The Worker takes the raw `index.html` template.
    * It dynamically replaces the standard `<title>` and `<meta>` tags with the specific data from the API (Title, Description, Cover Image URL).
    * It injects structured JSON-LD data into the `<head>`.

4.  **Response**: The bot receives a fully hydration-ready HTML file with all metadata present, ensuring a perfect 100/100 SEO score and rich social cards.

## 3. High Availability Robots.txt

We implement a **Dual-Layer Strategy** for `robots.txt` to ensure crawlers are never blocked, even during downtime.

### Layer 1: Static Source (Primary)
The primary file serves rules for the frontend.
* **Location**: `public/robots.txt` (Frontend).
* **Deployment**: Served via Cloudflare Pages / Nginx.

### Layer 2: Worker Fallback (Resilience)
If the upstream source fails (returns 4xx/5xx), the Worker immediately serves a hardcoded fallback file.

**Critical Rules Enforced:**
```txt
User-agent: *
Allow: /
Allow: /api/v1/posts
Allow: /api/v1/news-highlights
Allow: /api/v1/categories
Disallow: /api/v1/admin/
Disallow: /api/v1/auth/
Disallow: /dashboard/
Disallow: /profile/
```

## 4. Sitemap & Feed Proxying

To ensure Google receives the freshest content, the Worker proxies specific SEO paths directly to the Spring Boot Backend, bypassing the Frontend hosting entirely.

| Path | Proxy Target (Backend) | Purpose |
| :--- | :--- | :--- |
| `/sitemap.xml` | `/sitemap.xml` | Master Index |
| `/sitemap-news.xml` | `/sitemap-news.xml` | Google News Specific |
| `/feed.xml` | `/feed.xml` | RSS 2.0 Feed |

**Failover Logic**: If the backend is down (Connection Refused/Timeout), the Worker intercepts the error and returns a `503 Service Unavailable`. This tells Google to "come back later" rather than indexing an error page or a 404, preserving domain reputation.

## 5. Structured Data (JSON-LD)

The Worker injects specific Schema.org schemas based on content type.

### 5.1. Blog Posts & News
* **Schema Type**: Dynamically selects `NewsArticle` (for News/Market categories) or `BlogPosting` (for others).
* **Key Fields**:
    * `headline`: Post Title.
    * `image`: Full URL to the cover image.
    * `datePublished` / `dateModified`: ISO 8601 timestamps.
    * `author`: Links to the Author profile.
* **VideoObject**: If a YouTube link is detected in the content, a `VideoObject` schema is added to make the post eligible for the Google "Video" tab.

### 5.2. Breadcrumbs
A `BreadcrumbList` schema is automatically generated to reflect the category hierarchy (e.g., Home > Market > Crypto > Post).

## 6. Image Optimization & Serving

Images are stored in MinIO and served via Nginx, bypassing the Java application layer for maximum throughput.

* **Upload Path**: `/api/v1/files/upload` (Backend Logic).
* **Serving Path**: `/api/v1/files/{filename}`.
* **Nginx Config**:
    ```nginx
    location /api/v1/files/ {
        alias /app/uploads/;
        expires 30d;
        add_header Cache-Control "public, no-transform";
    }
    ```
* **Cloudflare**: The Worker allows all requests starting with `/api/v1/files/` to pass through untouched, allowing Cloudflare's global CDN to cache the binary image data at the edge.