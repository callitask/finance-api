# SEO & Edge Security Architecture

This document details the "Edge-Side Rendering" (ESR) and "Edge Security" strategy used to ensure perfect SEO, social sharing previews, and banking-grade security headers for the Single Page Application (SPA).

## 1. The Challenge
React SPAs render content specifically in the browser (Client-Side Rendering). This presents two major problems:
1.  **Crawlers**: Legacy bots (e.g., LinkedIn, Twitter, some Google bots) do not execute JavaScript, seeing only an empty `<div id="root"></div>`.
2.  **Security**: Standard static hosting often lacks advanced security headers (HSTS, CSP) needed for enterprise compliance.

## 2. The Solution: Cloudflare Worker Strategy

We deploy a custom Cloudflare Worker (`cloudflared/worker.js`) that acts as a smart proxy between the user and the application. It is configured dynamically using Cloudflare Environment Variables (`BACKEND_URL`, `FRONTEND_URL`).

### 2.1. Request Interception Flow
The Worker intercepts every request to the domain.

1.  **Configuration Loading**: The Worker loads target URLs from the Cloudflare Vault (Secrets), ensuring zero hardcoded values in the codebase.
2.  **Bot Detection**: The Worker checks if the `User-Agent` matches known bots (Googlebot, Bingbot, LinkedInBot, Twitterbot, WhatsApp, etc.).
    * **Regular Users**: Requests are passed to the CDN/Nginx to load the React app.
    * **Bots**: The Worker engages the "Edge Rendering" engine.
3.  **Security Injection**: Regardless of the user type (Bot or Human), strict security headers are injected into the response (see Section 7).

### 2.2. Metadata Fetching & Injection
If a bot requests a specific page (e.g., `/market/AAPL` or `/category/news/bitcoin`), the Worker:
1.  Fetches data from the Backend API (`/api/v1/...`) using the internal `BACKEND_URL`.
2.  Dynamically replaces the standard `<title>` and `<meta>` tags in the HTML.
3.  Injects structured JSON-LD data into the `<head>`.

## 3. High Availability Robots.txt

We implement a **Dual-Layer Strategy** for `robots.txt` to ensure crawlers are never blocked.

### Layer 1: Static Source (Primary)
The primary file serves rules for the frontend.
* **Location**: `public/robots.txt` (Frontend).

### Layer 2: Worker Fallback (Resilience)
If the upstream source fails (returns 4xx/5xx), the Worker immediately serves a hardcoded fallback file.

**Critical Rules Enforced:**
```txt
User-agent: *
Allow: /
# Enterprise SEO: Allow Googlebot to fetch API data for rendering
Allow: /api/posts
Allow: /api/market
Allow: /api/news

Disallow: /api/admin/
Disallow: /api/auth/
Disallow: /dashboard/
Disallow: /login

# Dynamic Sitemap Injection
Sitemap: [https://treishfin.treishvaamgroup.com/sitemap.xml](https://treishfin.treishvaamgroup.com/sitemap.xml)
```

## 4. Sitemap & Feed Proxying

To ensure Google receives the freshest content, the Worker proxies specific SEO paths directly to the Spring Boot Backend using the `BACKEND_URL` secret.

| Path | Proxy Target (Backend) | Purpose |
| :--- | :--- | :--- |
| `/sitemap.xml` | `/sitemap.xml` | Master Index |
| `/sitemap-news.xml` | `/sitemap-news.xml` | Google News Specific |
| `/feed.xml` | `/feed.xml` | RSS 2.0 Feed |

**Failover Logic**: If the backend is down, the Worker returns a `503 Service Unavailable` to prevent search engines from de-indexing the site due to "404 Not Found" errors.

## 5. Structured Data (JSON-LD)

The Worker injects specific Schema.org schemas based on content type.

### 5.1. Content Scenarios
* **Homepage**: Injects `WebSite` schema with internal search action.
* **Static Pages**: Injects `WebPage` and `Organization` schema for About/Contact pages.
* **Blog Posts**: Injects `NewsArticle` with Author, Date, and Image metadata.
* **Market Data**: Injects `FinancialProduct` schema for stock pages (e.g., `/market/AAPL`), including real-time price and currency.

## 6. Image Optimization

Images are stored in MinIO and served via Nginx. The Worker allows requests to `/api/v1/files/` to bypass the HTML logic, utilizing Cloudflare's global CDN for edge caching of binary assets.

## 7. Edge Security Hardening (Zero Trust)

The Worker injects **Banking-Grade Security Headers** into *every* response to protect users before traffic reaches the server.

| Header | Value | Purpose |
| :--- | :--- | :--- |
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` | Forces HTTPS for 2 years. Prevents downgrade attacks. |
| `X-Content-Type-Options` | `nosniff` | Prevents browsers from MIME-sniffing a response away from the declared content-type. |
| `X-Frame-Options` | `SAMEORIGIN` | Prevents Clickjacking. Other sites cannot embed the platform in an `<iframe>`. |
| `X-XSS-Protection` | `1; mode=block` | Activates legacy browser XSS filtering. |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Protects user privacy by stripping path data when navigating to external sites. |
| `Permissions-Policy` | `geolocation=(), microphone=(), ...` | Blocks access to sensitive browser features (Camera, Mic) by default. |