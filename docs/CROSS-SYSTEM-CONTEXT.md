# CROSS-SYSTEM-CONTEXT — Backend ↔ Frontend/Edge Integration Contract

> **Purpose:** single source of truth for the Frontend/Edge session. Verified against three sources (backend code export, frontend repo `treishvaam-finance-frontend` + `treishfin-seo-worker`, Knowledge Tracker VOL 1+2). Supersedes all prior versions — earlier copies claiming ±30 s drift or "0-byte discard" are wrong (code: **±300 s**, **403**).

---

## 1. Edge HMAC Signing Contract (Worker → Backend)

```javascript
// worker/worker.js — actual implementation
const key   = await crypto.subtle.importKey("raw", encoder.encode(AEGIS_EDGE_SECRET),
                  { name: "HMAC", hash: "SHA-512" }, false, ["sign"]);
const data  = encoder.encode(backendPath.split('?')[0] + ":" + timestamp + ":" + clientIp);
signature   = HEX(HMAC-SHA-512(key, data));          // native WebCrypto — no polyfill
```

- **Sign the exact BACKEND path** (after MTD translation), **query string stripped** (`/api/v1/analytics/event?x=1` → sign `/api/v1/analytics/event`).
- Headers: `X-Aegis-Edge-Signature` (hex) · `X-Aegis-Edge-Timestamp` (**epoch ms**, ±300 s window) · `X-Aegis-Client-IP` · `X-Tenant-ID: finance` · `X-Visitor-City/Country` (from `request.cf`).
- Backend failure mode: **HTTP 403** `sendError` (Origin Access Denied / Malformed Timestamp / Payload Expired / Invalid Cryptographic Signature); constant-time compare; bypass only `(loopback|RFC-1918) AND (/actuator*|/health*)`.
- `AEGIS_EDGE_SECRET` (64-char) must byte-match the backend Infisical value.
- **Next.js server components** (e.g. `app/category/[…]/page.tsx`) replicate the 3-part signing with `clientIp='127.0.0.1'` — valid. ⚠ The app-route proxies (`app/llms.txt|ai-feed.md|ontology.json/route.ts`) sign **2-part** (`path:timestamp`) and send `X-Aegis-Timestamp` — **they will 403** against the current backend filter (OP-18, fix pending).

## 2. MTD Flow (who translates what)

```
Browser ──canonical path──▶ Worker ──KV aegis:mtd:manifest──▶ /api/v1/node/{8-hex}
        ◀────────────────── Worker signs TRANSLATED path ──────────────────┘
Worker ──node path + sigs──▶ Backend: AegisTemporalPathManager unwraps → controller
```

- Protected prefixes (translated): `/api/v1/{admin,auth,users,dashboard,analytics}` → `/api/v1/node/{8-hex}`; manifest Redis `aegis:mtd:manifest` **TTL 24 h** (lock `aegis:mtd:lock` 30 s), mirrored to KV (no TTL; rotates daily 03:00 + Sun 04:00 + emergency).
- Worker public-exempts from translation: `/api/v1/analytics/event`, `/api/v1/aegis/telemetry`, anything containing `/posts/`, `/market/`, `/categories`.
- Direct canonical hits to protected prefixes (origin bypass) → sentinel `DECEPTION` → poisoned response.
- KV-flagged IPs → `403 {"error":"Access Denied","_aegis_integrity":"blocked-by-edge-consensus"}`; TARPIT-flagged → rewritten to `/api/v1/aegis/tarpit/trap` (⚠ no backend handler exists — OP-05).
- **The browser/frontend never calls `/api/v1/node/*` and must never cache translated paths.**

## 3. Worker Roles & KV Schema (`treishfin-seo-worker`)

Route `treishvaamfinance.com/*` · cron `0 * * * *` (`scheduled()`; manual `GET /sys/force-update`, `/sys/purge-cache`) · secrets `AEGIS_EDGE_SECRET`, `BACKEND_API_URL` (+`BACKEND_URL` fallback) · KV bindings **`TREISHFIN_SEO_CACHE`** and **`AEGIS_THREAT_KV`** (namespace IDs live in `wrangler.toml`).

| KV key | TTL | Source/Content |
|---|---|---|
| `sitemap:finance:meta` | 90 000 s | `/api/public/sitemap/meta` → `{blogs:[…], markets:[…]}` |
| `sitemap:finance:/sitemap-dynamic/{type}/{n}.xml` | 90 000 s | XML chunks (public `/sitemap-dynamic/*` → backend `/api/public/sitemap/*`) |
| `aegis:mtd:manifest` | dynamic | backend push (path map) |
| `aegis:block:{ip}` / `aegis:block:ja3:{ja3}` | 86 400 s | backend threat consumer |
| `geo:finance:{path}` | 86 400 s | `/api/public/geo/llms.txt|ai-feed.md|ontology.json` |
| `api:finance:post:{id}` | 86 400 s | `/api/v1/posts/url/{id}` → injected as `window.__PRELOADED_STATE__` |
| `api:finance:market:{ticker}` | 3 600 s | `/market/widget` → preloaded widget data |

Three-tier serving: CDN cache → KV → backend fallback (async KV write, never blocking). AI bots on any HTML page are served `/ai-feed.md` (+`X-GEO-Bot-Detected: true`), excluding static assets, `/api/`, `/llms.txt`, `/ontology.json`. `aiBotsOnly` matrix: `GPTBot · ChatGPT-User · OAI-SearchBot · ClaudeBot · anthropic-ai · MetaExternalAgent · Amazonbot · Applebot · Applebot-Extended · PerplexityBot · DeepSeek · Bytespider · Qwen · Mistral · YouBot · Cohere-training · Diffbot`. HTMLRewriter SEO injection is wrapped in `if (!isRscRequest)`; SPA fallback carries `X-SPA-Fallback: Active`; all `/api` responses get `X-Robots-Tag: noindex, noarchive`. Video key proxy: `/video-key/{videoId}` → backend `/api/v1/video/internal/key/{videoId}` (Referer allowlist, octet-stream, `max-age=300`). Sitemap cron fetches use UA `Treishvaam-Worker-Crawler/1.0` in batches of 10.

## 4. Authentication Flow (browser)

1. keycloak-js **^25.0.0** (matched to server 25.0.0 — upgrade Incident 23): `url = NEXT_PUBLIC_AUTH_URL` (forced https except localhost), realm `treishvaam`, client `finance-app`, **`pkceMethod:'S256'`**, **`useNonce:false`** (Keycloak-25 nonce incompatibility — load-bearing), `responseMode:'query'`, `checkLoginIframe:false`, **`timeSkew:86400`** (VM clock drift — never remove), `onLoad:'check-sso'` + `silentCheckSsoRedirectUri:/silent-check-sso.html` (excluded from the CSP-nonce middleware matcher).
2. Token refresh: 60 s interval, `keycloak.updateToken(70)`; failure → logout.
3. API auth: Axios interceptor `Authorization: Bearer <token>`; **no cookies, no `withCredentials`**. Login = `keycloak.login({redirectUri: origin + '/dashboard'})`; logout = `keycloak.logout()`. No backend login endpoints (dead DTOs `LoginRequest`/`AuthResponse`).
4. Roles: `realm_access.roles`; UI checks only `admin|publisher` (`isAdmin`). Loop breakers (sessionStorage `kc_fatal_loop_breaker`, `kc_login_lock_time` 5 s, etc.) + fatal-error halt screen are deliberate — never "simplify" them.
5. Keycloak realm: brute-force protection (5 fails → 60 s → max 900 s); Web Origins include `https://treishvaamfinance.com` + `https://*.treishvaamfinance.com` (Incident 26).

## 5. Header Dictionary (complete wire contract)

| Header | Direction | Purpose |
|---|---|---|
| `X-Aegis-Edge-Signature` / `X-Aegis-Edge-Timestamp` / `X-Aegis-Client-IP` | Worker→BE | edge proof (§1) |
| `X-Tenant-ID: finance` | Worker→BE | tenant pin |
| `X-Visitor-City` / `X-Visitor-Country` | Worker→BE | geo from `request.cf` |
| `X-JA3-Fingerprint` | nginx→BE | `cf-client-ja3` or pseudo-hash → behavioral scoring |
| `X-Aegis-Biometric-Hash` / `X-Aegis-Biometric-Raw` | FE→BE | L5-BIE telemetry (raw only when strict privacy off) |
| `X-AEGIS-ZKP-Proof` / `X-AEGIS-Challenge-ID` | admin→BE | ZKP gate |
| `X-AEGIS-ZKP` | heal only | internal-secret/proof alternative on `/api/v1/admin/actions/analytics/heal` |
| `X-AEGIS-Test-Token: true` | CI only | must never be sent in production |
| `X-AEGIS-POW-Challenge` | BE→client | PoW challenge (24 zero bits, SHA3-256) |
| `X-Aegis-Edge-*` spoofing | — | `AegisIpResolutionFilter` pins verified IP; spoofed forwarding headers never reach controllers |

## 6. Public API Quick Contracts (frontend-consumed)

`GET /api/v1/posts?page&size(9)` · `GET /posts/url/{urlArticleId}` · drafts `POST/PUT /posts/draft[/{id}]` · publish multipart (`title* content* category* featured*` + `version, …, coverImage, videoFile, newThumbnails[], thumbnailMetadata`) · `409` on stale version · `GET /categories` · `GET /search?q=` (UI: `q.length>1`) · `POST /market/quotes/batch` · `GET /market/{widget,quote,data,top-gainers,top-losers,most-active,news/highlights}` · flush admins `{"password"}` · `GET/PUT /auth/{me,profile}` · `POST /contact {name,email,message,honeypot}` (honeypot empty) · `POST /files/upload` (field `file`) · beacons `POST /api/v1/analytics/event` (raw/sendBeacon) + `POST /api/v1/aegis/telemetry` (15 s biometrics) — **both must remain public + MTD-exempt** · sitemap `/api/public/sitemap/{meta,blog/{n}.xml,market/{n}.xml}` · GEO `/api/public/geo/{llms.txt,ai-feed.md,ontology.json}` (signed, 3600). Full tables: BE-04.

## 7. Error & Response Contract

429 `{"error":"Request rejected"}` (BCSM) / rate-limit body · 400 sanitization · 403 edge/ZKP family · 409 `{"error":"Conflict detected","message":"…modified by another user…"}` (frontend checks status only) · 500 `{"error","message"}`. Every response: 5–45 ms jitter, fake `X-Powered-By`, `Server: AEGIS-M`, no-store (never depend on real server headers).

## 8. Known Integration Bugs (open — fix list for the FE/BE teams)

| ID | Bug |
|---|---|
| OP-18 | App-route proxies sign 2-part + wrong header (`X-Aegis-Timestamp`) → 403 against backend |
| OP-19 | `BlogEditorPage` previews use `/api/uploads/…` (missing `/v1`); backend serves `/api/v1/uploads/**` only |
| OP-05 | Worker rewrites tarpit IPs to `/api/v1/aegis/tarpit/trap` — no backend handler |
| OP-14 | `/video/internal/key` returns random keys (per-video key store unimplemented); edge proxy mitigates exposure |
| OP-20 | Faro posts to `NEXT_PUBLIC_FARO_URL` (`/faro/collect`), not `/api/v1/monitoring/ingest` |
| OP-15 | `/news/archived` returns active items; some FE components call `/news/archive` |
| OP-24 | Local FE `.env` holds live-looking URLs — never commit it |

## 9. Non-Goals (confirmed absent)

No WebSockets (REST + polling only: quotes 30 s, news 60 s, token 60 s, biometrics 15 s) · no cookies · no CSRF · no login/logout endpoints · no frontend role gating beyond `admin|publisher` · Agro worker has **not** inherited MTD/GEO/HMAC yet (pending debt — mirror Finance when implemented, keep `BACKEND_ORIGIN` routing).
