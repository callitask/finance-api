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

## 3. Data Handling
- Entities are mapped to DTOs using Jackson's `ObjectMapper` for serialization and deserialization, ensuring clean API responses and internal data consistency.

---
This document summarizes the core business logic and data synchronization strategies for blog and market data services.