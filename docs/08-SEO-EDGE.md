# SEO, Edge Hydration & Security Architecture

**Status:** Phase 2 Complete (Edge Hydration & Zero Trust Security)

This document details the **"Edge-Side Rendering" (ESR)** and **"Edge Security"** strategy. It explains how we achieve perfect SEO, social sharing previews, and **Zero-Latency** page loads using Cloudflare Workers.

## 1. The Challenge
React SPAs (Single Page Applications) typically face three major problems:
1.  **SEO Visibility**: Legacy bots (LinkedIn, Twitter, some Google crawlers) do not execute JavaScript and see only an empty `<div id="root"></div>`.
2.  **Performance (The Spinner Problem)**: Users load the HTML, wait for React to boot, and *then* React fetches data. This "Double Fetch" creates a noticeable delay (loading spinners).
3.  **Security**: Standard static hosting often lacks advanced security headers (HSTS, CSP) needed for enterprise compliance.

## 2. The Solution: Cloudflare Worker Strategy

We deploy a custom Cloudflare Worker (`cloudflared/worker.js`) that acts as a smart proxy and **Edge Orchestrator** between the user and the application.

### 2.1. Core Innovation: Edge-Side Hydration (Zero Latency)
Unlike standard SPAs, Treishvaam Finance does **not** force the browser to fetch initial data.

**The Flow:**
1.  **Interception**: The Worker intercepts the request (e.g., `/category/news/market-rally`).
2.  **Edge Fetch**: The Worker immediately calls the Backend API (`/api/v1/posts/url/...`) internally.
3.  **Injection**: The Worker injects two things into the HTML `<head>` before sending it to the user:
    * **JSON-LD Schema**: For Google/SEO Bots.
    * **`window.__PRELOADED_STATE__`**: The actual JSON data of the post.
4.  **Instant Render**: When React loads on the client, it checks for `window.__PRELOADED_STATE__`. If found, it **skips the network call** and renders instantly.

### 2.2. XSS Prevention (Sanitized Injection)
Injecting JSON directly into HTML is a security risk (XSS). To prevent attackers from injecting scripts via blog content, we implement a strict **Sanitization Routine** in the Worker.

* **Function**: `safeStringify(data)`
* **Logic**: It replaces dangerous characters (`<`, `>`, `&`) with their unicode equivalents (`\u003c`, `\u003e`, `\u0026`) before injection.
* **Result**: Even if a blog post title contains `<script>alert(1)</script>`, it is rendered harmlessly as text.

## 3. Architecture & Request Flow

The Worker configures itself dynamically using Cloudflare Environment Variables (`BACKEND_URL`, `FRONTEND_URL`), ensuring zero hardcoded secrets in the repo.

### 3.1. Routing Logic
| Path | Action | Description |
| :--- | :--- | :--- |
| `/` (Homepage) | **Inject Meta** | Injects `WebSite` Schema and SEO Title/Description. |
| `/category/*` | **Hydrate** | Fetches Blog Post -> Injects `NewsArticle` Schema + Preloaded State. |
| `/market/*` | **Hydrate** | Fetches Market Data -> Injects `FinancialProduct` Schema + Preloaded State. |
| `/sitemap.xml` | **Proxy** | Proxies directly to Backend API (See Section 4). |
| `/api/*` | **Proxy** | Secure tunnel to Backend API. |
| `*` (Static) | **Pass-Through** | Serves static assets (JS/CSS/Images) with Security Headers. |

## 4. High Availability Robots.txt

We implement a **Dual-Layer Strategy** for `robots.txt` to ensure crawlers are never blocked, even if the static server fails.

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

## 5. Sitemap & Feed Proxying

To ensure Google receives the freshest content, the Worker proxies specific SEO paths directly to the Spring Boot Backend.

| Path | Proxy Target (Backend) | Purpose |
| :--- | :--- | :--- |
| `/sitemap.xml` | `/sitemap.xml` | Master Index |
| `/sitemap-news.xml` | `/sitemap-news.xml` | Google News Specific |
| `/feed.xml` | `/feed.xml` | RSS 2.0 Feed |

**Failover Logic**: If the backend is down, the Worker returns a `503 Service Unavailable` to prevent search engines from de-indexing the site due to "404 Not Found" errors.

## 6. Structured Data (JSON-LD)

The Worker injects specific Schema.org schemas based on content type to ensure Rich Snippets in Google Search.

### 6.1. Content Scenarios
* **Homepage**: Injects `WebSite` schema with internal search action.
* **Static Pages**: Injects `WebPage` and `Organization` schema for About/Contact pages.
* **Blog Posts**: Injects `NewsArticle` with Author, Date, and Image metadata.
* **Market Data**: Injects `FinancialProduct` schema for stock pages (e.g., `/market/AAPL`), including real-time price (`UnitPriceSpecification`) and currency.

## 7. Edge Security Hardening (Zero Trust)

The Worker injects **Banking-Grade Security Headers** into *every* response to protect users before traffic reaches the server.

| Header | Value | Purpose |
| :--- | :--- | :--- |
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` | Forces HTTPS for 2 years. Prevents downgrade attacks. |
| `Content-Security-Policy` | `frame-ancestors 'self';` | **Silent SSO**: Allows the site to be framed ONLY by itself (same origin). Blocks all external clickjacking attempts. |
| `X-Content-Type-Options` | `nosniff` | Prevents browsers from MIME-sniffing a response away from the declared content-type. |
| `X-XSS-Protection` | `1; mode=block` | Activates legacy browser XSS filtering. |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Protects user privacy by stripping path data when navigating to external sites. |
| `Permissions-Policy` | `geolocation=(), microphone=(), ...` | Blocks access to sensitive browser features (Camera, Mic) by default. |