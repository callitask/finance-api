# BE-05 — DATA LAYER: MariaDB, Liquibase, Redis, RabbitMQ, Elasticsearch, MinIO

> **Verification basis:** source export (mariadb cnf, 58 changelogs, configs, entities) · Knowledge Tracker (V47/V49/V50 delivery history). **Re-verified changelog-by-changelog 2026-08-23** — 23-table count re-confirmed; one substantive correction: **V48 IS included in the master** (see §3).

---

## 1. MariaDB 10.6

Two containers (app `finance_db` + keycloak). **TDE**: `file_key_management` plugin, AES_CTR, keyfile from `MARIADB_ENCRYPTION_KEY` (`1;<hex>`; extraction must use `grep -oE '[A-Fa-f0-9]{64}'` — never global quote-strip), tables/log/tmp/binlog encrypted. **PITR**: `log_bin=mysql-bin`, **ROW** format, 7 d expiry, 256 MB max. Slow log 2.0 s. App: `MariaDBDialect`, Hikari max 50, `ddl-auto=validate` (prod) — schema is Liquibase's exclusively; JDBC batching 50.

## 2. Table Inventory (23 tables, post-V50)

| Domain | Tables |
|---|---|
| Content | `blog_posts`, `post_tags`, `post_thumbnails`, `categories`, `page_content` |
| Identity | `users`, `roles`, `user_roles`, `user_preferences` |
| Market | `market_data`, `quote_data`, `historical_price`, `historical_data_cache`, `market_holiday` |
| News | `news_highlights` |
| Analytics | `analytics_events`, `audience_visits` |
| Audit | `audit_logs` |
| Contact | `contact_message` |
| Ops | `api_fetch_status`, `system_property` |
| Video | `video_assets`, `video_quality_variants` |

Key mechanics: `blog_posts.version` `@Version` (409 flow) · tenant `@Filter` · `content_signature` VARCHAR(128) · `analytics_events.device_fingerprint` VARCHAR(64) SHA3-256 **with B-Tree index** (V47, sub-2 ms GROUP BY) · `audience_visits.device_fingerprint/brand/class` (**V47 adds the fingerprint column + `idx_audience_fingerprint` index to `audience_visits` as well** — verified in both changeset and entity, 2026-08-23) · `news_highlights` unique index `idx_news_link` on `link` · `user_preferences` unique `user_sub`.

## 3. Liquibase Matrix

**Master order** (`db.changelog-master.xml`) — *re-verified line-by-line 2026-08-23*: V1–V6, 008, V8_5, V9, V10, 010, V11, V12×3, V13, V14, V15×2, V16–V21, V25–V30(add_missing), V31, V32, V33(slug), V34(enum), V33(market), V34(thumb), V33(enabled), V35–V47, **V48**, V49, V50. (The master header comment still says "V1 through V49" — stale comment only; V50 is included at line 108.)

Highlights: V1–V6 core tables · 008/010 scheduling + thumbnails · V8_5 status migration · V11/V25/V27/V33/V34 market stack · V20/V21 contact + historical cache · V26 audience_visits · V30 five ops tables · V36 audit_logs · V40 `version` · V41 `display_name` · **V42** linkedin token → VARCHAR(2048) (only changeset with `<rollback>`) · **V43** drop email-unique indexes (randomized ciphertext) + TEXT widenings · **V44** `audit_logs.ip_address` → TEXT · **V45** content_signature · **V46** analytics_events + 4 indexes · **V47** fingerprints + device metadata on `analytics_events` **and** `audience_visits` (2026-08-05) · **V48 IS included** (`master:106`) — adds `screen_resolution` + `platform_version` VARCHAR(50) to `analytics_events`; *2026-08-23 correction: the earlier "V48 deliberately skipped (hallucination guard)" claim was wrong — the master's own history records the V48 changeset-id collision as **resolved**, and the file ships in the include list* · **V49** video tables (2026-08-11) · **V50** user_preferences (2026-08-13 — initially unregistered, caused `SchemaManagementException` boot hang until added to master; fixed same day).

**Orphan register (⚠ OP-12):** `db.changelog-3.0.xml` unreachable (contains changeset ids "3"/"8" duplicating V10/V8_5 concerns) · `V30__create_api_fetch_status_table.xml` duplicate changeset ids (`30-1-create-api-fetch-status`, `30-3-create-market-holiday` collide with the in-master `V30__add_missing_tables.xml`) and not in master — **do not include it without renaming the ids** · `V22` **0-byte file** · V7/V23/V24 no files · preConditions `onFail="MARK_RAN"` throughout (late V-changesets lack them). *(All re-verified 2026-08-23.)*

## 4. Redis 7

Cache prefix `treishfin_`. `blogPostHtml` 1 h · `marketWidget` 5 m · `quotesBatch` 5 m · default 10 m · **`aegis:mtd:manifest` 24 h** + `aegis:mtd:lock` 30 s · **`sqli:blocked:{ip}` 1 h** · Bucket4j keys `clientIp:category` (dedicated Lettuce client, isolated from Spring Data). Cache errors never propagate (warn-only handler). Dangerous commands renamed `""`.

## 5. RabbitMQ 3.12

**definitions.json:** exchange `internal.exchange` (topic) + `dead_letter_exchange` (direct); queues `search_index_queue` (rk `event.search`), `sitemap_queue` (rk `event.sitemap`), `internal.queue` (market updates via default exchange), `dead_letter_queue`; DLX args on all main queues. **Annotation/runtime-declared (⚠ OP-13):** `aegis.threat.exchange` DIRECT → `aegis.threat.queue` rk `threat.detected` (`@RabbitListener @QueueBinding`); `video.transcode.queue` declared by the transcoder.

`EventMessage{eventType, entityId, payload, source}` — flows: search index (PUBLISHED only), sitemap evict (lazy regen), `MARKET_UPDATE` → python updater (rethrow → DLX), video transcode `{"videoId"}`, threat telemetry → KV blocks.

## 6. Elasticsearch & MinIO

ES 8.17, index `blog_posts` (`PostDocument`: title/snippet english-analyzer; keyword fields slug/status/categorySlug/userFriendlySlug/urlArticleId); maintained only via `event.search`; query `match_phrase_prefix` slop 2. ⚠ test stack uses ES 7.17.10 (skew).
MinIO bucket `treishvaam-uploads` (path-style): media `UUID.ext`, image variants `{prefix}{uuid}-{1200|800|480}.webp`, materialized HTML `posts/{slug}.html` (max-age 3600), backups `s3://treishvaam-backups`. Presigned GET **7 days**.

## 7. Open Items (⚠)

OP-12 orphans · OP-13 runtime-declared topology · ES version skew · `MarketHoliday` vestigial · `HistoricalDataCache.data` MEDIUMTEXT growth · `system_property.prop_value` typed TIMESTAMP.
