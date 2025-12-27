# Backend Services Layer

This document details the business logic implementation, service layer architecture, and data synchronization strategies used in the Treishvaam Finance Platform.

## 1. Market Data Engine (`MarketDataService`)

The Market Data Engine is the most complex component, responsible for aggregating real-time and historical financial data from multiple external providers.

### 1.1. Provider Strategy Pattern
The service uses the Strategy Pattern to interact with different data vendors interchangeably.
* **Interface**: `MarketDataProvider`
* **Implementations**:
    * `AlphaVantageProvider`: Primary source for forex and technical indicators.
    * `FinnhubProvider`: Source for real-time stock quotes and news.
    * `FmpProvider` (Financial Modeling Prep): Source for bulk market movers (gainers/losers).
    * `YahooHistoricalProvider`: Source for long-term historical candle data.
* **Factory**: `MarketDataFactory` selects the appropriate provider based on the requested symbol or region.

### 1.2. Hybrid Data Fetching (Java + Python)
The architecture employs a hybrid approach for robustness:
1.  **Java Native**: Direct REST calls via `RestClient` for real-time quotes and lightweight data.
2.  **Python Bridge**: For heavy historical data processing and specific library requirements (e.g., `yfinance`), the service triggers a local Python script (`scripts/market_data_updater.py`).
    * **Execution**: Managed via `ProcessBuilder`.
    * **Data Flow**: Python fetches data -> Writes to Database -> Java Service reads from Database.

### 1.3. Smart Synchronization & Caching
* **Smart Sync**: Before fetching historical data, the system checks the `historical_price` table for the last available date. It only requests data *newer* than that date to preserve API quotas.
* **Caching (`HistoricalDataCache`)**: To prevent duplicate fetches within the same trading session, metadata about fetch requests is stored in the `historical_data_cache` table.
* **Redis Caching**: The `MarketDataController` caches the final JSON response for the frontend in Redis to minimize database load.

### 1.4. Resiliency
* **Circuit Breakers**: Annotated with `@CircuitBreaker(name = "external-api")`. If a provider fails repeatedly (e.g., 50% failure rate), the system switches to an "Open" state and rejects requests immediately to prevent cascading failures.

## 2. Content Management (`BlogPostService`)

Handles the lifecycle of editorial content.

### 2.1. Logic Flow
* **CRUD**: Maps `BlogPostDto` to `BlogPost` entities. Handles relationship management with `Category` and `User` entities.
* **Slugs**:
    * `slug`: Immutable, unique identifier used for internal routing.
    * `userFriendlySlug`: SEO-optimized string (e.g., `market-rally-2024`) derived from the title. Logic ensures uniqueness by appending numeric suffixes if collisions occur.
* **Scheduling**: Posts with `PostStatus.SCHEDULED` and a future `scheduledTime` are effectively hidden from public endpoints until the time passes.

### 2.2. Multi-Tenancy
* **Context Awareness**: The service checks `TenantContext.getTenantId()` to ensure all created posts are stamped with the correct Tenant ID (e.g., `TREISHFIN`).
* **Isolation**: Fetch queries automatically filter by the current tenant context.

## 3. Search Service (`SearchController` & Repositories)

Provides high-performance full-text search capabilities using **Elasticsearch**.

* **Document**: `PostDocument` mirrors the `BlogPost` entity but is optimized for indexing.
* **Sync Mechanism**:
    * **Real-time**: When a post is published/updated in MariaDB, an event is fired.
    * **Listener**: `MessageListener` catches the event and updates the Elasticsearch index via `PostSearchRepository`.
    * **Re-indexing**: `AdminActionsController` provides a `/reindex` endpoint to wipe and rebuild the Elasticsearch index from the primary database if data becomes inconsistent.

## 4. Media & File Management

### 4.1. Storage (`FileStorageService`)
* **Provider**: MinIO (S3 Compatible).
* **Operations**: Handles `putObject` for uploads and presigned URL generation for private access (if configured).
* **Public Access**: Files are typically served directly via Nginx mapping to the MinIO volume for performance, bypassing the Java application layer for reads.

### 4.2. Image Processing (`ImageService`)
* **Processing**: Uses the Java `ImageIO` and standard libraries to resize and compress uploaded images.
* **Responsiveness**: Generates multiple thumbnails (Small, Medium, Large) for every uploaded cover image to optimize frontend loading speeds.
* **Parallelism**: Uses Virtual Threads (Java 21) to process multiple image resizes concurrently.

## 5. Event-Driven Architecture (RabbitMQ)

The system uses an internal event bus to decouple services.

* **Publisher**: `MessagePublisher` sends `EventMessage` objects to the `internal-events` exchange.
* **Listener**: `MessageListener` subscribes to specific queues.
* **Use Cases**:
    * **Search Indexing**: Triggered when a post is created/updated.
    * **Audit Logging**: Asynchronous recording of user actions.
    * **Cache Eviction**: Clearing Redis keys when master data changes.

## 6. External Integrations

### 6.1. LinkedIn Integration (`LinkedInService`)
* **Auth**: Manages OAuth2 tokens for LinkedIn users.
* **Sharing**: Allows Admins to share a published blog post directly to their LinkedIn profile or Company Page.
* **Flow**: Constructs a rich media share payload (Title, Thumbnail, Link) and posts to the LinkedIn API.

### 6.2. Analytics (`AnalyticsService`)
* **Ingestion**: Records `AudienceVisit` data (IP hash, User Agent, Page URL).
* **Aggregation**: Provides aggregated stats (Daily Visits, Top Posts) for the Admin Dashboard.

## 7. SEO & Sitemaps (`SitemapService`)

* **Dynamic Generation**: XML sitemaps are not static files. They are generated on-the-fly based on the current database state.
* **Endpoints**:
    * `/sitemap.xml`: Main index.
    * `/sitemap-news.xml`: Google News specific format (includes `<news:publication_date>`).
    * `/feed.xml`: RSS 2.0 feed for aggregators.
* **Caching**: Results are cached heavily to prevent database load from crawler bots.