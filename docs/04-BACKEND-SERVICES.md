/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Details the business logic implementation, service layer architecture, and data synchronization strategies.
 * - Explains how the Zero-Trust Multi-Tenant Engine routes logic dynamically based on context.
 *
 * Scope:
 * - Covers Market Data (Python bridge), Blog Post CRUD, Sitemap Generation (Contextual Hijacking), Analytics, API Tracking, and Background Scheduling.
 *
 * Critical Dependencies:
 * - Cloudflare Edge Workers (for Edge caching and X-Tenant-ID injection).
 * - TenantInterceptor & TenantContext.
 * - Resilience4j (Circuit Breakers).
 *
 * Security Constraints:
 * - Background/Scheduled tasks MUST explicitly declare their TenantContext (e.g., "finance") to prevent cross-tenant data contamination.
 * - Heavy I/O must remain outside of @Transactional boundaries to prevent connection pool exhaustion.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED: Initial Backend Services Layer documentation.
 * - EDITED:
 * • Phase 3 Update: Integrated Multi-Tenant logic across services.
 * • Added comprehensive documentation for SitemapService contextual routing (Agro static payload vs. Finance dynamic pagination).
 * • Added strict TenantContext isolation rules for MarketDataInitializer and Schedulers.
 * • Clarified Edge Worker interplay with internal HTML Materialization and Caching.
 * - EDITED:
 * • Documented Internal Analytics Engine (AnalyticsService).
 * • Documented external API tracking mechanics.
 * • Clarified HtmlMaterializerService Jackson Instant serialization fixes.
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted,
 * truncated, rewritten, or regenerated.
 * Future AI must append only.
 */

# Backend Services Layer

This document details the business logic implementation, service layer architecture, multi-tenant contextual routing, and data synchronization strategies used in the Treishvaam Group Platform (`finance-api`).

## 1. Market Data Engine (`MarketDataService`)

The Market Data Engine is the most complex component, responsible for aggregating real-time and historical financial data from multiple external providers. *Note: This service is currently strictly scoped to the `finance` tenant.*

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

Handles the lifecycle of editorial content across multiple tenants.

### 2.1. Logic Flow
* **CRUD**: Maps `BlogPostDto` to `BlogPost` entities. Handles relationship management with `Category` and `User` entities.
* **Slugs**:
    * `slug`: Immutable, unique identifier used for internal routing.
    * `userFriendlySlug`: SEO-optimized string (e.g., `market-rally-2024`) derived from the title. Logic ensures uniqueness by appending numeric suffixes if collisions occur.
* **Scheduling**: Posts with `PostStatus.SCHEDULED` and a future `scheduledTime` are effectively hidden from public endpoints until the time passes.

### 2.2. Multi-Tenancy (Zero-Trust Boundaries)
* **Header Interception**: Cloudflare Edge Workers explicitly attach an `X-Tenant-ID` header (e.g., `finance`, `agro`) to all incoming `/api/*` requests.
* **Context Awareness**: `TenantInterceptor` validates this header and stores it in `TenantContext` (a `ThreadLocal` variable). The service checks `TenantContext.getTenantId()` to ensure all created posts are stamped with the correct Tenant ID.
* **Isolation**: Fetch queries automatically filter by the current tenant context. Data from `agro` can never bleed into `finance` responses.

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

## 3. Sitemap & Edge Caching Service (`SitemapService`)

This service dynamically generates XML sitemaps for Google Search Console, highly optimized for Cloudflare Edge Workers and Multi-Tenant routing.

### 3.1. Contextual Routing (The "Tenant Hijack")
Because the backend powers entirely different corporate websites, `SitemapService` dynamically alters its output based on `TenantContext`.
* **Finance Tenant**: Generates paginated, large-scale dynamic XML sitemaps reading from the `blog_posts` and `market_data` tables. Handles 10M+ URLs via strict chunking.
* **Agro Tenant**: The Agro site is an enterprise marketing portal without market data. When `TenantContext.getTenantId().equals("agro")`, the service bypasses the database entirely and serves a static array of enterprise URLs (`/about`, `/infrastructure`, `/products`) with specific E-E-A-T priority tunings.

### 3.2. Edge Worker Caching Integration
The backend is designed to **not** serve sitemaps directly to end-users. 
1. The Cloudflare Edge Worker checks the Free-Tier `TREISHFIN_SEO_CACHE` KV namespace.
2. On a cache miss, the Worker proxies to this service (with the `X-Tenant-ID`).
3. This service generates the XML.
4. The Worker intercepts the response, serves it, and uses `ctx.waitUntil` to asynchronously update the KV Cache.
* **Result**: The backend is shielded from aggressive crawler polling.

## 4. SEO Materializer Engine (`HtmlMaterializerService`)

This service implements the "Hybrid Static Site Generation" logic.

* **Responsibility**: Converts dynamic React states into static HTML files for bots.
* **Process**:
    1.  **Fetch Shell**: Calls the internal Nginx URL (`http://treishvaam-nginx/`) to get the currently deployed `index.html`. This ensures the static file version exactly matches the live React app version.
    2.  **Inject Content**: Uses `Jsoup` to insert:
        * `<title>` and `<meta>` tags.
        * JSON-LD Schema (NewsArticle).
        * Full HTML body content into `<div id="server-content">`.
        * Redux State into `window.__PRELOADED_STATE__`.
    3.  **State Serialization Constraint**: Manually serializes `Instant` fields (e.g., `createdAt`) to Strings to avoid Jackson JSON mapping failures.
    4.  **Upload**: Streams the generated HTML string directly to MinIO (bucket: `treish-public`) with `Cache-Control` headers.
* **Async Execution**: Runs in a separate thread (`@Async`) to avoid slowing down the Admin UI save operation.

## 5. Analytics & Telemetry Engine (`AnalyticsService`)

A robust internal engine for processing and aggregating visitor and API telemetry data.

* **Audience Telemetry**: Processes Real User Monitoring (RUM) data from the frontend. It groups and filters records by `country`, `region`, `city`, `OS`, and `sessionSource` to provide a GDPR-compliant internal analytics dashboard, removing total reliance on GA4.
* **API Fetch Tracking**: Works in tandem with the `ApiStatusController` to log the health, latency, and success rates of external market data providers. Prevents blind spots if third-party data feeds silently degrade.

## 6. Data Initialization & Scheduling (Startup Safety)

Background tasks and startup initializers run outside the standard HTTP request lifecycle, meaning they lack an injected `TenantContext`. **Strict isolation protocols apply.**

### 6.1. The Data Initializers (`DataInitializer`, `MarketDataInitializer`)
* **Role**: Bootstraps the system with essential roles, admin users, and initial market data payloads.
* **Security Constraint**: Because these run on boot, threads MUST explicitly be wrapped in `TenantContext.setTenantId("finance")`. Failure to do so risks corrupting the `agro` tenant or throwing `NullPointerException`s during entity saves.
* **Cleanup**: `TenantContext.clear()` must be executed in a `finally` block to prevent memory leaks in the thread pool.

### 6.2. Market Data Scheduler (`MarketDataScheduler`)
Automates periodic data ingestion.
* **US Market Movers Fetch**: Runs Monday–Friday at 10 PM UTC. Calls `fetchAndStoreMarketData`.
* **Global Market Data Sync**: Runs every 4 hours. Triggers the Python data engine.
* **Tenant Isolation Rule**: As with initializers, `@Scheduled` methods must explicitly declare their Tenant Context before performing operations.