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
    1.  **Phase 1 (Secure Streaming - Non-Transactional)**: Streams large files directly to MinIO outside of transactional boundaries.
    2.  **Phase 2 (Commit)**: Once the file is successfully uploaded, the database transaction commits the metadata.

## 3. Static Content Materialization (`HtmlMaterializerService`)

The `HtmlMaterializerService` generates static HTML files for blog posts, ensuring SEO optimization and immediate availability. It fetches an HTML shell, injects metadata (e.g., Open Graph, Twitter cards), and uploads the materialized content to MinIO for edge delivery.

### 3.1. Workflow
1. **Fetch Shell**: Retrieves the HTML shell from the frontend (internal or public URL).
2. **Inject Metadata**: Adds SEO tags, JSON-LD structured data, and post content.
3. **Upload**: Streams the materialized HTML to MinIO for edge caching.

## 4. Analytics & Diagnostics

### 4.1. Analytics Service (`AnalyticsService`)
The `AnalyticsService` integrates with Google Analytics 4 (GA4) to fetch historical audience data and manage audience dashboard queries.
* **Data Enrichment**: Maps GA4 data to local audience visit records.
* **Filtering**: Supports advanced filtering by date, region, OS, and session source.
* **Real-Time Analytics**: Provides insights into active sessions and user behavior.

### 4.2. API Diagnostics (`ApiFetchStatus`)
The `ApiFetchStatus` entity tracks the health and latency of external API calls.
* **Fields**:
    * `apiName`: Name of the external API.
    * `status`: Fetch status (e.g., SUCCESS, FAILURE).
    * `triggerSource`: Indicates whether the fetch was automatic or manual.
    * `details`: Stores error messages or additional context.
* **Usage**: Enables observability and reliability for external integrations.

---
