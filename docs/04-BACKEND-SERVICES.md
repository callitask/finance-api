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
    * **Security Hardening**: Database credentials are injected securely via **Environment Variables** (`processBuilder.environment().put(...)`), ensuring passwords never appear in the process list (`ps aux`).
    * **Financial Precision**: The Python engine uses `decimal.Decimal` (28-place context) to prevent floating-point errors.

### 1.3. Smart Synchronization & Caching
* **Smart Sync**: Before fetching historical data, the system checks the `historical_price` table for the last available date. It only requests data *newer* than that date to preserve API quotas.
* **Caching (`HistoricalDataCache`)**: To prevent duplicate fetches within the same trading session, metadata about fetch requests is stored in the `historical_data_cache` table.
* **Redis Caching**: The `MarketDataController` caches the final JSON response for the frontend in Redis to minimize database load.

### 1.4. Resiliency & Circuit Breakers
The service utilizes **Resilience4j** to prevent cascading failures.
* **External APIs (`fmpApi`)**:
    * **Timeout**: 5 seconds.
    * **Fallback**: If the API fails or times out, the circuit opens, and the system serves stale data from the database.
* **Python Engine (`pythonScript`)**:
    * **Timeout**: 120 seconds.
    * **Logic**: Protects the server from hanging indefinitely if the subprocess stalls.

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

### 2.3. Enterprise I/O Strategy ("Secure Stream & Commit")
To guarantee high concurrency, memory safety, and prevent "Database Denial of Service," this service strictly separates Network I/O from Database Transactions.

* **The Problem**: 
    1.  **Connection Starvation**: Network calls (e.g., MinIO uploads) inside a transaction hold DB connections, freezing the app under load.
    2.  **Memory Exhaustion**: Loading large images into RAM (`byte[]`) causes Out-Of-Memory (OOM) crashes.
* **The Solution**:
    1.  **Phase 1 (Secure Streaming - Non-Transactional)**: 
        * **Zero-Allocation**: Uploads are streamed directly to `Files.createTempFile()`. RAM usage remains flat regardless of file size.
        * **Security**: **Apache Tika** analyzes the file signature (Magic Numbers) to validate MIME types before processing.
        * **Processing**: Image resizing happens in parallel Virtual Threads using the temp file as source.
    2.  **Phase 2 (Transactional Persistence)**: Once the file is safely in MinIO, the URL is passed to `persistPost()`, which is annotated with `@Transactional` for fast SQL execution.
    3.  **Phase 3 (Bulk Optimization)**: **JDBC Batching** is enabled (`batch_size=50`). When performing bulk updates (e.g., Sitemap regeneration), operations are grouped into batches to reduce network round-trips by 50x.
* **Result**: Database lock time is minimized, and the server is immune to large-file memory spikes.

### 2.4. Data Integrity (Optimistic Locking)
To prevent the "Lost Update" anomaly common in collaborative CMS environments:
* **Mechanism**: The `blog_posts` table uses a `@Version` column.
* **Logic**: When updating a post, the service compares the `version` provided by the client with the current database `version`.
* **Outcome**: If they mismatch (indicating another user modified the record), an `ObjectOptimisticLockingFailureException` is thrown (HTTP 409), ensuring no changes are silently overwritten.

### 2.5. High-Performance Caching
* **Strategy**: **Read-Through Caching**.
* **Implementation**: Critical read methods (e.g., `findPostForUrl`, `findByUrlArticleId`) are annotated with `@Cacheable`.
* **Flow**: 
    1.  Check Redis.
    2.  If Present: Return immediately (<5ms).
    3.  If Missing: Fetch from DB -> Serialize to JSON -> Store in Redis -> Return.
* **Consistency**: Write operations (`save`, `delete`) trigger `@CacheEvict` to invalidate stale keys.

### 2.6. SEO Materialization Integration
* **Service Integration**: Injects `HtmlMaterializerService`.
* **Triggers**:
    * **Immediate**: When `persistPost` saves a `PUBLISHED` post.
    * **Deferred**: When `checkAndPublishScheduledPosts` transitions a post from `SCHEDULED` to `PUBLISHED`.
* **Purpose**: Ensures that every public post has a corresponding `static/{slug}.html` file in MinIO for Cloudflare to serve.

## 3. SEO Materializer Engine (`HtmlMaterializerService`)

This service implements the "Hybrid Static Site Generation" logic.

* **Responsibility**: Converts dynamic React states into static HTML files for bots.
* **Process**:
    1.  **Fetch Shell**: Calls the internal Nginx URL (`http://treishvaam-nginx/`) to get the currently deployed `index.html`. This ensures the static file version exactly matches the live React app version.
    2.  **Inject Content**: Uses `Jsoup` to insert:
        * `<title>` and `<meta>` tags.
        * JSON-LD Schema (NewsArticle).
        * Full HTML body content into `<div id="server-content">`.
        * Redux State into `window.__PRELOADED_STATE__`.
    3.  **Upload**: Streams the generated HTML string directly to MinIO (bucket: `treish-public`) with `Cache-Control` headers.
* **Async Execution**: Runs in a separate thread (`@Async`) to avoid slowing down the Admin UI save operation.

## 4. Search Service (`SearchController` & Repositories)

Provides high-performance full-text search capabilities using **Elasticsearch**.

* **Document**: `PostDocument` mirrors the `BlogPost` entity but is optimized for indexing.
* **Sync Mechanism**:
    * **Real-time**: When a post is published/updated in MariaDB, an event is fired.
    * **Listener**: `MessageListener` catches the event and updates the Elasticsearch index via `PostSearchRepository`.
    * **Re-indexing**: `AdminActionsController` provides a `/reindex` endpoint to wipe and rebuild the Elasticsearch index from the primary database if data becomes inconsistent.

## 5. Media & File Management

### 5.1. Storage (`FileStorageService`)
* **Provider**: MinIO (S3 Compatible).
* **Operations**: Handles `putObject` for uploads and presigned URL generation for private access (if configured).
* **Static Offloading**: While uploads go through Java (for security validation), **READ** requests (`/api/uploads/**`) are intercepted by Nginx and served directly from MinIO, bypassing the Java application layer entirely for zero-latency delivery.

### 5.2. Image Processing (`ImageService`)
This service acts as the **Source of Truth** for image quality and security.

* **Secure Pipeline**:
    * **Input**: Uses `Files.createTempFile` to handle uploads, ensuring **Zero RAM Allocation** for incoming streams.
    * **Validation**: **Apache Tika** performs strict MIME detection to reject spoofed files (e.g., malware renamed as `.jpg`).
* **Java 21 Virtual Threads**: Utilizes `Executors.newVirtualThreadPerTaskExecutor()` to process image resizing tasks in parallel.
* **Quality**:
    * **Output**: Generates optimized WebP variants (Master, Desktop, Tablet, Mobile).
    * **Source**: Frontend sends raw PNGs; server handles all compression to avoid generation loss.
* **BlurHash**: Generates a compact string representation for "blur-up" loading effects.

## 6. Event-Driven Architecture (RabbitMQ)

The system uses an internal event bus to decouple services.

* **Publisher**: `MessagePublisher` sends `EventMessage` objects to the `internal-events` exchange.
* **Listener**: `MessageListener` subscribes to specific queues.
* **Use Cases**:
    * **Search Indexing**: Triggered when a post is created/updated.
    * **Audit Logging**: Asynchronous recording of user actions.
    * **Cache Eviction**: Clearing Redis keys when master data changes.
    * **Sitemap Regeneration**: Triggered after publication to ensure Googlebot sees fresh URLs.

## 7. External Integrations

### 7.1. LinkedIn Integration (`LinkedInService`)
* **Auth**: Manages OAuth2 tokens for LinkedIn users.
* **Sharing**: Allows Admins to share a published blog post directly to their LinkedIn profile or Company Page.
* **Flow**: Constructs a rich media share payload (Title, Thumbnail, Link) and posts to the LinkedIn API.

### 7.2. Analytics (`AnalyticsService`)
* **Ingestion**: Records `AudienceVisit` data (IP hash, User Agent, Page URL).
* **Aggregation**: Provides aggregated stats (Daily Visits, Top Posts) for the Admin Dashboard.

## 8. SEO & Sitemaps (`SitemapService`)

* **Dynamic Generation**: XML sitemaps are generated on-the-fly based on the current database state.
* **Endpoints**:
    * `/sitemap.xml`: Main index.
    * `/sitemap-news.xml`: Google News specific format (includes `<news:publication_date>`).
    * `/feed.xml`: RSS 2.0 feed for aggregators.
* **Edge Integration**: These endpoints are primarily consumed by the Cloudflare Worker, which caches and serves them to bots with correct headers.

## 9. Fort Knox Security Implementation

The Service Layer integrates directly with the Fort Knox Security Suite.

* **Internal Locking**: The `InternalSecretFilter` protects POST services by verifying the `X-Internal-Secret` against the injected environment variable. This ensures that even if an attacker bypasses the frontend, they cannot invoke critical write operations without the key.
* **Fail-Open Logic**: In `RateLimitingFilter.java`, a `try-catch` block specifically wraps the Redis bucket logic. If Redis throws a connection exception, the filter logs an error but **allows the request to proceed**. This architectural decision favors Platform Availability over Security during catastrophic infrastructure failures.