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
| **GET** | `/{id}` | Public | Get a single post by its numerical ID. Returns `version` for locking. |
| **GET** | `/public/{slug}` | Public | Get a post by its URL-friendly slug. |
| **GET** | `/url/{urlArticleId}` | Public | Get a post by its legacy URL Article ID. **Cached (Redis).** |
| **GET** | `/category/{categorySlug}` | Public | List published posts within a specific category. |
| **GET** | `/tags/{tag}` | Public | List published posts matching a specific tag. |
| **GET** | `/recent` | Public | Get the most recently published posts (Limit: 5). |
| **GET** | `/featured` | Public | Get posts marked as 'Featured'. |
| **GET** | `/search` | Public | (Database) Simple search by title/content keyword. |
| **POST** | `/draft` | **Auth** | Create a new blog post in `DRAFT` status. |
| **PUT** | `/draft/{id}` | **Auth** | Update an existing draft. **Body must include `version`.** |
| **GET** | `/admin/drafts` | **Auth** | List all posts with `DRAFT` status. |
| **GET** | `/admin/all` | **Auth** | List all posts regardless of status (Published/Draft/Archived). |
| **PUT** | `/{id}` | **EDITOR+** | Update a post. **Requires `version` param** to prevent lost updates. |
| **POST** | `/admin/publish/{id}` | **PUBLISHER+** | Change post status to `PUBLISHED`. |
| **DELETE** | `/{id}` | **PUBLISHER+** | Permanently delete a post. |
| **POST** | `/{id}/duplicate` | **Auth** | Clone an existing post into a new draft. |
| **POST** | `/{id}/share` | **PUBLISHER+** | Trigger a LinkedIn share for this post. |

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
| **POST** | `/upload` | **ADMIN** | Upload an image/file to MinIO. **Security:** Validates MIME type via **Apache Tika** and streams to disk to prevent OOM. |
| **GET** | `/{filename}` | Public | Serve the file content (if not served directly via Nginx). |

### Contact Controller (`ContactController`)
**Base Path**: `/contact`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **POST** | `/` | Public | Submit a contact form. Persists message to database. |
| **GET** | `/info` | Public | Get contact information (email, phone, address). |

## 2. Market Data & News

### Market Data Controller (`MarketDataController`)
**Base Path**: `/market`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/indices` | Public | Get summary data for major global indices. |
| **GET** | `/movers` | Public | Get top gainers, losers, and active stocks. |
| **GET** | `/quote/{symbol}` | Public | Get real-time quote for a specific symbol. |
| **GET** | `/history/{symbol}` | Public | Get historical price data (candles) for charts. |
| **GET** | `/widget` | Public | Get optimized data payload for the frontend market widget. **Cached.** |
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
| **GET** | `/` | **ADMIN** | Get historical audience data (filterable by date, country, region, city, OS, etc.). |
| **GET** | `/visits` | **ADMIN** | Get daily/monthly visit counts. |
| **GET** | `/posts/top` | **ADMIN** | Get most viewed posts. |

### Health Check Controller (`HealthCheckController`)
**Base Path**: `/health`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/` | Public | Health check endpoint. |

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
**Base Path**: `/`

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| **GET** | `/sitemap.xml` | Public | Master sitemap index (XML). |
| **GET** | `/sitemap-news.xml` | Public | Google News sitemap (last 48h, XML). |
| **GET** | `/sitemaps/static.xml` | Public | Sitemap for static pages (XML). |
| **GET** | `/sitemaps/categories.xml` | Public | Sitemap for categories (XML). |
| **GET** | `/sitemaps/posts-{page}.xml` | Public | Sharded post sitemaps (XML, paginated). |

---

*This document is auto-synchronized with the codebase as of January 2026.*