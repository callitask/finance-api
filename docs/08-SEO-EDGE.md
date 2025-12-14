# 08-SEO-EDGE.md

## 1. Edge Rendering Strategy
The Cloudflare Worker intercepts requests to `/category/` URLs and injects SEO-critical Schema.org and meta tags directly into the HTML before the React app loads. This Edge-Side Rendering ensures that search engines and social media crawlers receive rich metadata and structured data, improving SEO and link previews even if the backend is slow or down.

## 2. High Availability Robots.txt (Edge-Served)
Requests to `/robots.txt` are handled entirely at the edge. The Worker serves a static, always-available robots.txt that:
- Allows Googlebot and other crawlers to access public API endpoints for content rendering.
- Disallows indexing of sensitive paths (auth, admin, dashboard, internal search).
- Always includes a valid Sitemap reference.
This ensures that even if the backend is unavailable, search engines can still crawl and index the site correctly.

## 3. Sitemap Proxying
Requests to `/sitemap.xml`, `/sitemap-news.xml`, `/feed.xml`, and `/sitemaps/*` are proxied by the Worker to the backend (`backend.treishvaamgroup.com`). This guarantees that Google and other crawlers always receive the freshest sitemap and feed XML, even if the backend is behind a tunnel or protected by Cloudflare. If the backend is down, the Worker returns a 503 Service Unavailable.

## 4. Structured Data Injection
The Worker injects JSON-LD schemas for:
- **Homepage**: `WebSite` schema with search action for enhanced sitelinks.
- **Static Pages**: `WebPage` schema for About, Vision, and Contact pages.
- **Blog Posts**: For `/category/` URLs, the Worker fetches post data from the backend and injects either a `NewsArticle` or `BlogPosting` schema (based on category), plus a `BreadcrumbList` and (if present) a `VideoObject` schema for embedded YouTube videos. All schemas are injected as `<script type="application/ld+json">` in the `<head>`.

## 5. Failover Handling
- If the backend is down, the Worker serves a 503 for sitemaps and feeds, and falls back to a static robots.txt for crawlers.
- For HTML requests, if the backend is unavailable, the Worker attempts to serve a cached HTML shell from Cloudflare's edge cache, ensuring the site remains available to users and bots.

---
This document describes the Cloudflare Worker logic for SEO, edge rendering, and high-availability crawling for Treishvaam Finance.