# Backend API Reference

**Stable Version:** `tfin-financeapi-Develop.0.0.0.1`
**Security Status:** Fort Knox Security Suite Enabled

This document provides a comprehensive reference for the REST API surface of the Treishvaam Finance Platform.

**Base URL**: `/api/v1` (unless otherwise noted)

## 1. Content Management

### Blog Post Controller (`BlogPostController`)
**Base Path**: `/posts`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/` | Public | List published posts with pagination. |
| **GET** | `/{id}` | Public | Get a single post by its numerical ID. |
| **GET** | `/public/{slug}` | Public | Get a post by its URL-friendly slug. |
| **GET** | `/url/{urlArticleId}` | Public | Get a post by its legacy URL Article ID. |
| **GET** | `/category/{categorySlug}` | Public | List published posts within a specific category. |
| **GET** | `/tags/{tag}` | Public | List published posts matching a specific tag. |
| **GET** | `/recent` | Public | Get the most recently published posts (Limit: 5). |
| **GET** | `/featured` | Public | Get posts marked as 'Featured'. |
| **GET** | `/search` | Public | (Database) Simple search by title/content keyword. |
| **POST** | `/draft` | **ADMIN** | Create a new blog post in `DRAFT` status. |
| **PUT** | `/draft/{id}` | **ADMIN** | Update an existing draft post. |
| **GET** | `/admin/drafts` | **ADMIN** | List all posts with `DRAFT` status. |
| **GET** | `/admin/all` | **ADMIN** | List all posts regardless of status (Published/Draft/Archived). |
| **POST** | `/admin/publish/{id}` | **ADMIN** | Change post status to `PUBLISHED`. |
| **DELETE** | `/admin/delete/{id}` | **ADMIN** | Permanently delete a post. |
| **POST** | `/{id}/duplicate` | **ADMIN** | Clone an existing post into a new draft. |
| **POST** | `/{id}/share` | **ADMIN** | Trigger a LinkedIn share for this post. |

### Category Controller (`CategoryController`)
**Base Path**: `/categories`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/` | Public | List all active categories. |
| **POST** | `/` | **ADMIN** | Create a new category. |
| **PUT** | `/{id}` | **ADMIN** | Update an existing category. |
| **DELETE** | `/{id}` | **ADMIN** | Delete a category. |

### File Controller (`FileController`)
**Base Path**: `/files`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **POST** | `/upload` | **ADMIN** | Upload an image/file to MinIO storage. Returns the public URL. |
| **GET** | `/{filename}` | Public | Serve the file content (if not served directly via Nginx). |

## 2. Market Data & News

### Market Data Controller (`MarketDataController`)
**Base Path**: `/market`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/indices` | Public | Get summary data for major global indices. |
| **GET** | `/movers` | Public | Get top gainers, losers, and active stocks. |
| **GET** | `/quote/{symbol}` | Public | Get real-time quote for a specific symbol. |
| **GET** | `/history/{symbol}` | Public | Get historical price data (candles) for charts. |
| **GET** | `/widget` | Public | Get optimized data payload for the frontend market widget. |
| **POST** | `/admin/refresh` | **ADMIN** | Force a manual refresh of market data from external providers. |

### News Highlight Controller (`NewsHighlightController`)
**Base Path**: `/news-highlights`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/ticker` | Public | Get scrolling news ticker items. |
| **GET** | `/intel` | Public | Get structured news intelligence data. |
| **GET** | `/top` | Public | Get the top headline news. |

## 3. System & Administration

### Admin Actions Controller (`AdminActionsController`)
**Base Path**: `/admin/actions`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **POST** | `/cache/clear` | **ADMIN** | Flush all Redis caches immediately. |
| **GET** | `/system-properties` | **ADMIN** | List dynamic system configuration properties. |
| **POST** | `/system-properties` | **ADMIN** | Update a system property. |

### Analytics Controller (`AnalyticsController`)
**Base Path**: `/analytics`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/visits` | **ADMIN** | Get daily/monthly visit counts. |
| **GET** | `/posts/top` | **ADMIN** | Get most viewed posts. |

### Health Check Controller (`HealthCheckController`)
**Base Path**: `/health`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/` | Public | Simple "UP" status for Docker/Load Balancer probes. |

### API Status Controller (`ApiStatusController`)
**Base Path**: `/status`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/external-apis` | **ADMIN** | Check quota and health of AlphaVantage, Finnhub, etc. |

## 4. Search & SEO

### Search Controller (`SearchController`)
**Base Path**: `/search`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/query` | Public | Full-text search via Elasticsearch (High Performance). |
| **POST** | `/reindex` | **ADMIN** | Rebuild the Elasticsearch index from the Database. |

### Sitemap Controller (`SitemapController`)
**Base Path**: `/sitemaps` (Root level in some configs)

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/sitemap.xml` | Public | Main sitemap index. |
| **GET** | `/posts.xml` | Public | Sitemap for blog posts. |
| **GET** | `/news.xml` | Public | Google News compatible sitemap. |

## 5. Security & Authentication

### Auth Controller (`AuthController`)
**Base Path**: `/auth`

*Note: Most authentication logic is handled by Keycloak. These endpoints exist for specific frontend helper flows or legacy support.*

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/user/me` | Authenticated | Get details of the currently logged-in user (from JWT). |
| **POST** | `/logout` | Authenticated | Trigger logout (Frontend should also clear tokens). |

### Internal Lock (The Fort Knox Protocol)
Certain high-risk internal endpoints (e.g., specific batch operations or inter-service POST requests) are protected by the **Internal Secret Filter**.
* **Header Required**: `X-Internal-Secret`
* **Value**: Must match the `APP_SECURITY_INTERNAL_SECRET` injected via Infisical.
* **Effect**: Requests without this header on protected internal routes will be rejected `403 Forbidden` regardless of the user's JWT roles.

## Request & Response Formats

### Standard Success Response
```json
{
  "status": "SUCCESS",
  "data": { ... },
  "timestamp": "2023-10-27T10:00:00Z"
}
```

### Standard Error Response
```json
{
  "status": "ERROR",
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2023-10-27T10:00:00Z"
}
```

## Rate Limiting Headers
Public endpoints are protected by `RateLimitingFilter`.
**Note:** In the event of a cache infrastructure outage (e.g., Redis down), the system implements a **"Fail-Open" Strategy** to maintain availability, and these headers may temporarily be omitted.

* `X-RateLimit-Remaining`: Requests left in the current window.
* `X-RateLimit-Retry-After`: Seconds to wait if blocked (HTTP 429).