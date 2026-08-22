# Security & Secret Management Policy

**Classification:** Internal — No actual secret values are stored here. This file documents variable names, locations, and rotation policies only.

---

## Overview

This project adheres to a strict **Zero-Trust Security Model** ("Fort Knox Suite"). Hardcoded secrets are strictly prohibited anywhere in the codebase. All sensitive credentials are managed externally:

- **Backend secrets** → **Infisical** (Production Environment)
- **Edge secrets** → **Cloudflare Worker Secrets** (`npx wrangler secret put`)
- **Frontend public variables** → **Cloudflare Pages Environment Variables**

All future frontends (e.g., Treishvaam Hiring Marketplace) **must** inherit this exact schema.

---

## Flash & Wipe Secret Injection Process

Secrets never persist unencrypted on disk. The lifecycle per deployment:

1. `auto_deploy.sh` authenticates with Infisical using a **Machine Identity Token** (Universal Auth — no human credentials involved; the login JWT is captured statelessly from stdout, never written to a session file)
2. `infisical export` appends secrets to a temporary `.env` file (boundary-only quote stripping — see rotation notes)
3. `docker compose up -d` interpolates variables from the `.env` file automatically (note: `docker-compose.yml` declares **no** `env_file:` key — Compose reads `.env` from the project directory implicitly)
4. Immediately after container startup, the `.env` file is **sanitised** (EXIT trap restores the secret-free template, gated on the deploy lock ownership)
5. Containers retain secrets only in their in-memory environment

**Never commit `.env` to git.** It is in `.gitignore`.

**Never wrap `.env` values in quotes.** Single or double quotes cause HikariCP to receive literal quote characters in the JDBC URL → fatal crash loop.

---

## 1. BACKEND: Infrastructure Secrets (Infisical → Docker Compose)

| Variable | Description | Used By |
| :--- | :--- | :--- |
| `PROD_DB_URL` | JDBC URL for the application database | Backend |
| `PROD_DB_USERNAME` | App DB username | Backend |
| `PROD_DB_PASSWORD` | App DB password | Backend, MariaDB healthcheck |
| `MARIADB_ENCRYPTION_KEY` | Docker secret for MariaDB TDE volume encryption (64 hex; extract with `grep -oE '[A-Fa-f0-9]{64}'` — never global quote-strip) | MariaDB (`encryption.cnf`) |
| `KEYCLOAK_DB_PASSWORD` | Keycloak's dedicated MariaDB password | Keycloak, Keycloak-DB |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin console password | Keycloak |
| `REDIS_PASSWORD` | Redis AUTH password | Redis, Backend |
| `ELASTIC_PASSWORD` | Elasticsearch built-in security password | Elasticsearch, Backend |
| `MINIO_ROOT_PASSWORD` | MinIO root/admin password | MinIO, Backend, Backup Service |
| `RABBITMQ_DEFAULT_USER` | RabbitMQ admin username | RabbitMQ, Backend |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ admin password | RabbitMQ, Backend |
| `CLOUDFLARE_TUNNEL_TOKEN` | Cloudflare Zero Trust Tunnel token | cloudflared |
| `GRAFANA_ADMIN_PASSWORD` | Grafana dashboard admin password | Grafana |
| `BACKUP_MINIO_ACCESS_KEY` | Dedicated access key for backup service → MinIO | Backup Service |
| `BACKUP_ENCRYPTION_KEY` | AES key for encrypting MariaDB dump files at rest | Backup Service |
| `TELEGRAM_BOT_TOKEN` | Deploy-notification bot token | Engine B (`auto_deploy.sh`) |
| `TELEGRAM_CHAT_ID` | Ops group chat id (supergroup ids are `-100`-prefixed) | Engine B |

---

## 2. BACKEND: Application Secrets (Infisical → Docker Compose)

| Variable | Description | Used By |
| :--- | :--- | :--- |
| `JWT_SECRET_KEY` | Legacy JWT signing key (alongside PQC hybrid tokens) | Backend |
| `INTERNAL_API_SECRET_KEY` | Master key for `InternalSecretFilter` + scoped ZKP-heal bypass | Backend |
| `CONTENT_SIGNING_KEY` | HMAC-SHA256 key for content integrity + GEO provenance (`ContentIntegrityService`, `GeoOptimizationService`) | Backend |
| `AEGIS_DB_SIGNING_KEY` | SHA3-256 HMAC key for JDBC query signatures (`AegisQueryInterceptor`) | Backend |
| `AEGIS_EDGE_SECRET` | 64-char HMAC-SHA-512 seed — **must byte-match the Worker secret** (`AegisEdgeValidationFilter`) | Backend |
| `LINKEDIN_TOKEN_ENCRYPTION_KEY` | AES-256-GCM key for encrypting stored LinkedIn OAuth tokens | Backend |
| `USER_EMAIL_ENCRYPTION_KEY` | Domain-specific AES-256-GCM key for `users.email` at rest | Backend |
| `CONTACT_EMAIL_ENCRYPTION_KEY` | Domain-specific AES-256-GCM key for `contact_message.email` at rest | Backend |
| `CONTACT_MESSAGE_ENCRYPTION_KEY` | Domain-specific AES-256-GCM key for `contact_message.message` at rest | Backend |
| `AUDIT_IP_ENCRYPTION_KEY` | Domain-specific AES-256-GCM key for `audit_log.ip_address` at rest | Backend |
| `APP_ADMIN_EMAIL` | Email for the bootstrapped admin user (`DataInitializer`) | Backend |
| `APP_ADMIN_PASSWORD` | Password for the bootstrapped admin user | Backend |
| `FINANCE_FRONTEND_URL` | Production Finance frontend URL (Keycloak redirect/CORS config) | Keycloak |
| `FINANCE_FRONTEND_LOCAL_URL` | Local development Finance frontend URL | Keycloak |

---

## 3. BACKEND: External API Keys (Infisical → Docker Compose)

| Variable | Description | Used By |
| :--- | :--- | :--- |
| `ALPHAVANTAGE_API_KEY` | AlphaVantage — legacy historical series (currently disabled in code) | Backend |
| `FINNHUB_API_KEY` | Finnhub — quotes (currently disabled in code) | Backend |
| `MARKET_DATA_API_KEY` | Financial Modeling Prep (FMP) — market movers; consumed as `fmp.api.key` (⚠ the literal name `FMP_API_KEY` is not referenced in code) | Backend (`FmpProvider`) |
| `NEWS_API_KEY` | NewsData.io — news highlights | Backend (`NewsHighlightService`) |
| `GA4_PROPERTY_ID` | Google Analytics 4 Property ID for server-side reporting | Backend (AnalyticsService) |
| `GA4_BIGQUERY_PROJECT_ID` | Google Cloud project ID for GA4 BigQuery export (gated off by default) | Backend (AnalyticsService) |
| `GA4_BIGQUERY_DATASET_ID` | BigQuery dataset ID for GA4 raw event data | Backend (AnalyticsService) |
| `ENABLE_4K_TRANSCODING` | Video transcoder toggle (default `false`, 1080p ceiling) | Transcoder |

**Note:** `ga4-credentials.json` (service-account key) is mounted as a Docker volume — never committed to git. Market history runs keyless via the Python yfinance updater.

---

## 4. BACKEND: AEGIS / Cloudflare Secrets (Infisical → Docker Compose)

| Variable | Description | Used By |
| :--- | :--- | :--- |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare account identifier | Backend (`CloudflareEdgeSyncService`) |
| `CLOUDFLARE_THREAT_KV_NAMESPACE_ID` | KV namespace ID for the `AEGIS_THREAT_KV` store | Backend (`CloudflareEdgeSyncService`) |
| `CLOUDFLARE_API_TOKEN` | Scoped Cloudflare API token for KV writes and cache purge | Backend (`CloudflareEdgeSyncService`) |

**Cloudflare API Token Scope:**
- Account: `Workers KV: Edit`, `Workers Routes: Edit`, `Account Settings: Read`
- Zone: `treishvaamfinance.com` — `Zone: Read`, `Cache Purge: Purge`
- **IP fencing was REMOVED on 2026-07-15** (the deployment host sits behind public NAT — an IP-fenced token intermittently fails). Do not re-add it.
- **Current token TTL expires 2026-12-31.** The `SecretKeyRotationDue` Grafana alert fires 7 days before expiry; rotate via `rotate_secrets.sh` when it fires.

---

## 5. EDGE: Cloudflare Worker Secrets

Set via `npx wrangler secret put <NAME>` — **never** in `wrangler.toml` or the repository.

### Finance SEO Worker (`treishfin-seo-worker`)

| Variable | Description |
| :--- | :--- |
| `AEGIS_EDGE_SECRET` | Cryptographic seed for HMAC-SHA-512 `X-Aegis-Edge-Signature` signing (64-char minimum). Backend `AegisEdgeValidationFilter` verifies this on every request — must match the Infisical value exactly |
| `BACKEND_API_URL` | Cloudflare Tunnel URL to the Spring Boot backend — never the public domain |

### Agro SEO Worker (`treishvaamagro-seo-worker`)

| Variable | Description |
| :--- | :--- |
| `AEGIS_EDGE_SECRET` | Same purpose as Finance worker — HMAC-SHA-512 signing key |
| `BACKEND_ORIGIN` | Backend ingress route for Agro — do not hardcode |
| `CF_PAGES_ORIGIN` | Agro Cloudflare Pages origin URL for internal Worker fetch |

---

## 6. FRONTEND: Cloudflare Pages Environment Variables

Set in Cloudflare Dashboard → Workers & Pages → [Project] → Settings → Environment Variables.

### Finance Frontend (`treishvaam-finance-frontend`)

| Variable | Description | Required |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_API_URL` | Spring Boot Backend API base URL | **Yes** |
| `NEXT_PUBLIC_AUTH_URL` | Keycloak auth base URL (`https://backend.treishvaamgroup.com/auth`) | **Yes** |
| `NEXT_PUBLIC_FARO_URL` | Grafana Faro collector endpoint | Yes |
| `NEXT_PUBLIC_GA_MEASUREMENT_ID` | Google Analytics 4 Measurement ID (`G-XXXXXXXXXX`) | Yes |
| `NEXT_PUBLIC_GOOGLE_ADS_ID` | Google Ads Conversion ID (`AW-XXXXXXXXXX`) | Yes |
| `NEXT_PUBLIC_ADSENSE_CLIENT_ID` | Google AdSense Publisher ID (`ca-pub-XXXXXXXXXXXXXXXX`) | Yes |
| `NEXT_PUBLIC_ENFORCE_STRICT_PRIVACY` | `true`/`false` — injects `anonymize_ip: true` into GA4 without rebuild | Yes |
| `NEXT_PUBLIC_CHAIRMAN_PORTRAIT_URL` | Dynamic URL for team portrait (bypasses repo commits for asset swaps) | Yes |

**NEVER use `REACT_APP_*` prefix** — this is Next.js. Only `NEXT_PUBLIC_*` is supported. Note: `NEXT_PUBLIC_*` values are statically baked at build time — changing them requires a rebuild (an empty commit re-triggers Pages CI).

---

## 7. Rotation Policy

| Secret Category | Rotation Frequency | Method |
| :--- | :--- | :--- |
| Database passwords | Every 90 days | Rotate in **Infisical first**, then run `scripts/rotate_secrets.sh` → Docker rolling restart (⚠ the script edits the local `.env` only — if Infisical is not updated, the next deploy's Flash & Wipe reverts the rotation) |
| Cloudflare API Token | Hard TTL (current token expires **2026-12-31**) | Regenerate in CF dashboard → update Infisical → re-deploy |
| `AEGIS_EDGE_SECRET` | On suspected breach or every 90 days | `npx wrangler secret put AEGIS_EDGE_SECRET` per worker **and** Infisical update (both sides must match) |
| Encryption keys (AES, HMAC) | On suspected breach only | Requires DB re-encryption migration — coordinate carefully |
| External API keys | Immediately on vendor notification or breach | Update Infisical — auto-reloaded on next deploy |
| Keycloak admin password | Every 90 days | Update Infisical + Keycloak admin UI |
| `NEXT_PUBLIC_*` frontend vars | As needed | Update Cloudflare Pages env vars — triggers automatic rebuild |

---

## 8. What Must NEVER Happen

- No secret value stored in git (enforced by Gitleaks on every CI push)
- No `.env` file committed (enforced by `.gitignore`)
- No hardcoded URLs, origins, or backend addresses in any source file
- No credentials passed as CLI arguments to Python scripts (use `ProcessBuilder.environment()`)
- No observability UIs (Grafana `:3001`, RabbitMQ `:15672`, ZKP `:9090`) exposed to `0.0.0.0`
- No `liboqs-java` in `pom.xml` (breaks CI/CD — BouncyCastle is the sole PQC provider); no JJWT/Nimbus for token handling (no FIPS 204 support)
- No `docker compose down` on production hosts (destroys the `treish_net` bridge — permanent SSH loss)
