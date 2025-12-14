# 04-BACKEND-SERVICES.md

## 1. Blog Logic (`BlogPostService`)
- **Post Creation & Update:**
  - Posts are created and updated via methods that map DTOs to entities, set author/tenant, and handle status (DRAFT, SCHEDULED, PUBLISHED).
  - Thumbnails and cover images are processed and saved using the `ImageService`.
  - When publishing, the service ensures a unique `slug` and generates a `userFriendlySlug` from the title (lowercase, hyphenated, alphanumeric only).
  - Scheduled posts are published automatically if their scheduled time has passed.
- **Duplicate Post:**
  - The `duplicatePost` method creates a new draft with the same category and layout as the original, but resets content, tags, and group ID, and generates a new slug and user-friendly slug.
- **Slug Generation & Uniqueness:**
  - `generateUserFriendlySlug` converts a title to a URL-safe slug. The service ensures slugs are unique and backfills missing slugs for legacy posts.

## 2. Market Data Engine (`MarketDataService`)
- **MarketDataScheduler:**
  - Runs two main jobs:
    - Fetches US market movers (gainers/losers/active) every weekday at 22:00 UTC using FMP APIs.
    - Runs a Python data engine every 4 hours to sync global indices and historical data. The Python script uses a "Smart Sync" strategy, fetching only new data since the last DB update.
- **External Data Fetching:**
  - Data is fetched from providers like AlphaVantage and FMP, then stored in the `quote_data` and `market_data` tables.
  - Circuit breakers and fallback methods ensure resilience if APIs or scripts fail.
- **Smart Sync:**
  - Before fetching, the system checks the last date in the DB and only requests new data, reducing API calls and improving efficiency.
- **Self-Healing Cold Start:**
  - On application startup, the service listens for `ApplicationReadyEvent` and triggers a cold start sync if the market data tables are empty or stale, ensuring the system is always ready after downtime or redeployments.

## 3. Data Handling
- Entities are mapped to DTOs using Jackson's `ObjectMapper` for serialization and deserialization, ensuring clean API responses and internal data consistency.

## 4. ImageService
- **Responsive Image Generation:**
  - Uses Lanczos resampling for high-quality resizing and generates multiple responsive image sizes for each upload.
  - Leverages Java virtual threads for parallel image processing, improving throughput and reducing latency for bulk uploads.
  - All images are stored in MinIO and served via CDN URLs.

## 5. SitemapGenerationService
- **Google News Sitemap & RSS Feed Generation:**
  - Generates `/sitemap.xml`, `/sitemap-news.xml`, and `/feed.xml` endpoints.
  - Sitemaps are updated automatically on post publish/update events.
  - Google News sitemap includes only posts from the last 48 hours, as per Google News requirements.
  - RSS feed is compliant with major news aggregators.

---
This document summarizes the core business logic and data synchronization strategies for blog, market data, image, and sitemap services.