# BE-04 — API REFERENCE: REST Surface of `finance-api`

> **Verification basis:** backend controllers + DTOs (source export) · frontend `apiConfig.js` + consumers (cross-check — the "Used by" column reflects real frontend calls). Base URL: `https://backend.treishvaamgroup.com`. All requests pass the AEGIS chain (BE-02). **Re-verified 2026-08-23** (one tier correction: `DELETE /posts/bulk`).

---

## 1. Conventions

- **Auth:** `Authorization: Bearer <Keycloak JWT>` via the frontend Axios interceptor (`setAuthToken`); **no cookies** (`withCredentials` never used), no CSRF, no backend login/logout (`apiConfig.login = () => Promise.reject("Use Keycloak Login")`).
- **Tenancy:** Worker injects `X-Tenant-ID: finance` on every proxied request.
- **Errors:** `{"error","message"}` (500/409) · `{"error":"Request rejected"}` (BCSM 429) · `{"error":"Rate limit exceeded. Try again in 60 seconds."}` (429) · `{"error":"Invalid input detected. Request blocked."}` (400 sanitization) · 403 family from Edge/ZKP.
- **409 handling:** frontend checks `err.response.status === 409` only (body shape unused) after proactively sending `version`.
- **WebSocket: none** (confirmed both sides). Polling: quotes/watchlist 30 s, market quote page 30 s, admin posts 30 s, news 60 s, token refresh 60 s (`updateToken(70)`), biometrics 15 s.

## 2. Auth Tiers

Public (HMAC-edge only) · Authed (any JWT) · Role-gated (`ROLE_ADMIN/PUBLISHER/EDITOR/ANALYST` — frontend UI only checks `admin|publisher` for `isAdmin`) · ZKP-gated (admin scope + `X-AEGIS-ZKP-Proof`/`X-AEGIS-Challenge-ID`, or `X-AEGIS-ZKP` on the heal route).

## 3. Content — `BlogPostController` (`/api/v1/posts`)

| Method | Path | Tier | Notes (frontend usage) |
|---|---|---|---|
| GET | `/posts?page&size` | Public | default size **9** (`getPaginatedPosts(page, 9)`), sort `updatedAt` desc |
| GET | `/posts/{id}` | Public | edit-load (`version`, thumbnails, scheduledTime) |
| GET | `/posts/url/{urlArticleId}` | Public | article page + worker SSR preload (`cache:'no-store'`) |
| GET | `/posts/category/{cat}/{slug}/{id}` | Public | canonical full-slug route |
| GET | `/posts/admin/all` | E/P/A | polled 30 s on dashboard |
| GET | `/posts/admin/drafts` | E/P/A | manage-posts view |
| POST | `/posts/admin/backfill-slugs` | ADMIN | maintenance |
| POST | `/posts/draft` | Authed | JSON `{title, content, customSnippet, metaDescription, keywords, version}` → 201 |
| PUT | `/posts/draft/{id}` | Authed | same body; autosave w/ 409 handling |
| POST | `/posts` | P/A | **multipart**: `title* content* category* featured*` + `version, customSnippet, metaDescription, keywords, seoTitle, canonicalUrl, focusKeyword, displaySection, coverImageAltText, userFriendlySlug, tags[] (repeated), scheduledTime (ISO), coverImage, videoFile, thumbnailOrientation, thumbnailMetadata (JSON str, may repeat), newThumbnails[] (files)` → 201 |
| PUT | `/posts/{id}` | E/P/A | same multipart + `version` → 200/404/**409** |
| POST | `/posts/{id}/duplicate` | Authed | 201 copy-as-draft |
| DELETE | `/posts/{id}` | P/A | 204 |
| DELETE | `/posts/bulk` | E/P/A (*corrected 2026-08-23: the explicit `/posts/bulk` matcher grants EDITOR too, `SecurityConfig` — it precedes and does **not** inherit the stricter `DELETE /api/v1/posts/**` P/A rule*) | body = JSON id array |
| POST | `/posts/{id}/share` | P/A | `ShareRequest{message,tags}` → LinkedIn (501 if disabled) |

## 4. Categories, Contact, Files

| Method | Path | Tier | Notes |
|---|---|---|---|
| GET | `/categories` | Public | category **name string** sent back as `category` on posts |
| POST | `/categories` | ADMIN | `{"name"}`; slug auto-generated |
| POST | `/categories/admin/backfill-slugs` | ADMIN | |
| POST | `/contact` | Public | JSON `{name, email, message, honeypot}` — honeypot filled → decoy 200; validation name≤100, email regex, message 1–5000 |
| GET | `/contact/info` | Public | hardcoded contact card |
| GET | `/logo` | Public | 30 d cache |
| GET | `/uploads/{filename:.+}` | Public | MinIO stream; auto-`+.webp` retry; ⚠ some frontend previews build `/api/uploads/…` (missing `/v1`) — OP-19 |
| POST | `/files/upload` | P/A | multipart field **`file`** (images→WebP variants; videos→raw store); response `data` used as URL string |

## 5. Market — `/api/v1/market`

| Method | Path | Tier | Notes (frontend usage) |
|---|---|---|---|
| POST | `/quotes/batch` | Public | body = ticker array; ticker bar / watchlist (30 s poll) |
| GET | `/widget?ticker=` | Public | `WidgetDataDto{quoteData, historicalData, peers}`; worker KV `api:finance:market:{ticker}` TTL 3600 |
| GET | `/quote/{ticker}` | Public | market detail (30 s poll + SSR fetch) |
| GET | `/data/{ticker}` | Public | widget DTO |
| GET | `/top-gainers` `/top-losers` `/most-active` | Public | movers cards |
| GET | `/historical/{ticker}` | Public | 30-min cached; 503 on error |
| GET | `/news/highlights?page&size` | Public | default size 12; login page polls 60 s, slice 15 |
| GET | `/news/archived?page&size` | Public | ⚠ returns ACTIVE items (bug OP-15); frontend calls `/news/archive` variant on some components |
| POST | `/news/fetch` | Authed (fallback) | `region` param ignored |
| POST | `/news/deduplicate` | Authed (fallback) | no-op stub |
| POST | `/admin/refresh-movers` `/admin/refresh-indices` | ADMIN | ApiStatusPanel buttons |
| POST | `/admin/flush-movers` `/admin/flush-indices` | ADMIN | body `{"password"}` (PasswordDto) — JWT + BCrypt re-auth double gate → 401 on wrong password |
| POST | `/admin/flush-permanent-data` | — | defined in `apiConfig.js` but **no UI caller** (dead export) |

## 6. Search, Sitemap/GEO, Video

| Method | Path | Tier | Notes |
|---|---|---|---|
| GET | `/search?q=` | Public | ES `match_phrase_prefix` slop 2; UI triggers at `q.length > 1` |
| GET | `/api/public/sitemap/meta` | Public | `{blogs:[…], markets:[…]}` — worker hourly cron consumer (UA `Treishvaam-Worker-Crawler/1.0`, batches of 10) |
| GET | `/api/public/sitemap/blog/{page}.xml` · `/market/{page}.xml` | Public | 10 000-URL chunks |
| GET | `/api/public/geo/llms.txt` · `/ai-feed.md` · `/ontology.json` | Public | HMAC-signed (`CONTENT_SIGNING_KEY`), `max-age=3600`; worker KV `geo:finance:{path}` TTL 86400 |
| GET | `/api/v1/video/internal/key/{videoId}` | Authed (fallback) | ⚠ fresh random AES-128 key per call (OP-14); the **worker** proxy `/video-key/{videoId}` fronts it with Referer allowlist + edge signature, `max-age=300` |

## 7. Auth profile & preferences

| Method | Path | Tier | Notes |
|---|---|---|---|
| GET | `/auth/me` | Authed | `{username, email, displayName}` — UI uses `displayName` |
| PUT | `/auth/profile` | Authed | `{"displayName"}` |
| GET/PUT | `/user-preferences` | Authed | `radarToolbarEnabled`, `defaultHighlightColor` |

## 8. Admin, analytics & telemetry

| Method | Path | Tier | Notes |
|---|---|---|---|
| POST | `/admin/actions/regenerate-sitemap` | ADMIN + ZKP | clears sitemap caches |
| POST | `/admin/actions/analytics/heal` | ADMIN + ZKP (`X-AEGIS-ZKP` header variant) | async chunked healer |
| GET | `/analytics?…` | ANALYST/ADMIN | filters: dates, geo, OS, source, client-id include/exclude → `GroupedAudienceDataDto` |
| GET | `/analytics/filters` | same | dropdown distincts |
| POST | `/analytics/refresh` | ADMIN | `{"startDate","endDate"}` GA4 re-sync |
| POST | `/analytics/event` | **Public, MTD-exempt** | raw body (JSON **or** `text/plain` — sendBeacon rescue); events `page_view, scroll_depth(25/50/75/90/100), visibility_hidden, page_unload, exit_intent, web_vital` (`metricId` rename); `extra.platformVersion` Win11 Client-Hints |
| POST | `/aegis/telemetry` | **Public, MTD-exempt** | `{biometricHash, biometricRaw?}` every 15 s; 202 `{"status":"ok"}` manual Content-Length; headers `X-Aegis-Biometric-Hash/Raw` |
| POST | `/monitoring/ingest` | Public | Faro payload → `audience_visits` + Alloy forward — ⚠ current frontend posts Faro to `NEXT_PUBLIC_FARO_URL` (default `/faro/collect`) instead (OP-20) |
| GET | `/health` | ⚠ falls to `authenticated()` (OP-15) | legacy ALB probe |
| GET | `/api/v1/health/ping` | Public | `{"status":"ok","service":"treishvaam-finance-api"}` |
| GET | `/status/history` | ADMIN | `api_fetch_status` records (ApiStatusPanel) |
| GET | `/api/v1/oauth2/authorization/linkedin` | redirect | OAuth2 start (full-page) |

## 9. MTD Interplay (client guidance)

The browser **always calls canonical paths**. The Worker: (1) blocks KV-flagged IPs (`403 {"error":"Access Denied","_aegis_integrity":"blocked-by-edge-consensus"}`), (2) rewrites tarpit-flagged IPs to `/api/v1/aegis/tarpit/trap` (⚠ no backend handler — OP-05), (3) translates protected prefixes via the KV `aegis:mtd:manifest` and signs the **translated** path. Public-exempt from translation: `/api/v1/analytics/event`, `/api/v1/aegis/telemetry`, anything containing `/posts/`, `/market/`, `/categories`. Clients must never cache translated paths beyond the manifest TTL (24 h).

## 10. Open Items (⚠)

OP-14 video keys · OP-15 `/health` + `/news/archived` · OP-18 app-route 2-part HMAC · OP-19 `/api/uploads` missing `/v1` in some frontend previews · dead exports (`flush-permanent-data`, `refreshNewsData`) · no `editor`/`analyst` UI gating despite backend roles.
