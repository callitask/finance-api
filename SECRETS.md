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

1. `auto_deploy.sh` authenticates with Infisical using a **Machine Identity Token** (Universal Auth — no human credentials involved)
2. `infisical export` writes secrets to a temporary `.env` file
3. `docker compose up -d` reads the `.env` file via `env_file:` directive
4. Immediately after container startup, the `.env` file is **sanitised** — high-value secrets removed from disk
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
| `MARIADB_ENCRYPTION_KEY` | Docker secret for MariaDB TDE volume encryption | MariaDB (`encryption.cnf`) |
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

---

## 2. BACKEND: Application Secrets (Infisical → Docker Compose)

| Variable | Description | Used By |
| :--- | :--- | :--- |
| `JWT_SECRET_KEY` | Legacy JWT signing key (if applicable alongside PQC) | Backend |
| `INTERNAL_API_SECRET_KEY` | Master key for `InternalSecretFilter` — locks internal-only POST endpoints | Backend |
| `CONTENT_SIGNING_KEY` | HMAC-SHA256 key for blog post content integrity (`ContentIntegrityService`) | Backend |
| `AEGIS_DB_SIGNING_KEY` | SHA3-256 HMAC key for JDBC query signatures (`AegisQueryInterceptor`) — Zero-Trust DB Driver Boundary | Backend |
| `LINKEDIN_TOKEN_ENCRYPTION_KEY` | AES-256-GCM key for encrypting stored LinkedIn OAuth tokens | Backend |
| `USER_EMAIL_ENCRYPTION_KEY` | Domain-specific AES-256-GCM key for `users.email` at rest | Backend |
| `CONTACT_EMAIL_ENCRYPTION_KEY` | Domain-specific AES-256-GCM key for `contact_message.email` at rest | Backend |
| `CONTACT_MESSAGE_ENCRYPTION_KEY` | Domain-specific AES-256-GCM key for `contact_message.message` at rest | Backend |
| `AUDIT_IP_ENCRYPTION_KEY` | Domain-specific AES-256-GCM key for `audit_log.ip_address` at rest | Backend |
| `APP_ADMIN_EMAIL` | Email for the bootstrapped admin user (created by `DataInitializer`) | Backend |
| `APP_ADMIN_PASSWORD` | Password for the bootstrapped admin user | Backend |
| `FINANCE_FRONTEND_URL` | Production Finance frontend URL (used by Keycloak CORS config) | Keycloak |
| `FINANCE_FRONTEND_LOCAL_URL` | Local development Finance frontend URL | Keycloak |

---

## 3. BACKEND: External API Keys (Infisical → Docker Compose)

| Variable | Description | Used By |
| :--- | :--- | :--- |
| `ALPHAVANTAGE_API_KEY` | AlphaVantage — Forex and technical indicators | Backend (MarketDataService) |
| `FINNHUB_API_KEY` | Finnhub — Real-time stock quotes and news | Backend (MarketDataService) |
| `FMP_API_KEY` | Financial Modeling Prep — Market movers (gainers/losers) | Backend (MarketDataService) |
| `MARKET_DATA_API_KEY` | Generic key for additional market data providers | Backend |
| `NEWS_API_KEY` | NewsData.io — News headlines and articles | Backend (NewsHighlightService) |
| `GA4_PROPERTY_ID` | Google Analytics 4 Property ID for server-side reporting | Backend (AnalyticsService) |
| `GA4_BIGQUERY_PROJECT_ID` | Google Cloud project ID for GA4 BigQuery export | Backend (AnalyticsService) |
| `GA4_BIGQUERY_DATASET_ID` | BigQuery dataset ID for GA4 raw event data | Backend (AnalyticsService) |

**Note:** `ga4-credentials.json` (Google Cloud service account key for BigQuery) is mounted as a Docker volume — never committed to git.

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
- IP Fencing: Token is locked to server IPs only
- **TTL: 90 days.** The `SecretKeyRotationDue` Grafana alert fires 7 days before expiry.

---

## 5. EDGE: Cloudflare Worker Secrets

Set via `npx wrangler secret put <NAME>` — **never** in `wrangler.toml` or the repository.

### Finance SEO Worker (`treishfin-seo-worker`)

| Variable | Description |
| :--- | :--- |
| `AEGIS_EDGE_SECRET` | Cryptographic seed for HMAC-SHA-512 `X-Aegis-Edge-Signature` signing. Backend `AegisEdgeValidationFilter` verifies this on every request |
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
| `NEXT_PUBLIC_GA_MEASUREMENT_ID` | Google Analytics 4 Measurement ID (`G-XXXXXXXXXX`) | Yes |
| `NEXT_PUBLIC_GOOGLE_ADS_ID` | Google Ads Conversion ID (`AW-XXXXXXXXXX`) | Yes |
| `NEXT_PUBLIC_ADSENSE_CLIENT_ID` | Google AdSense Publisher ID (`ca-pub-XXXXXXXXXXXXXXXX`) | Yes |
| `NEXT_PUBLIC_ENFORCE_STRICT_PRIVACY` | `true` or `false` — injects `anonymize_ip: true` into GA4 without rebuild | Yes |
| `NEXT_PUBLIC_CHAIRMAN_PORTRAIT_URL` | Dynamic URL for team portrait (bypasses repo commits for asset swaps) | Yes |

**NEVER use `REACT_APP_*` prefix** — this is Next.js. Only `NEXT_PUBLIC_*` is supported.

---

## 7. Rotation Policy

| Secret Category | Rotation Frequency | Method |
| :--- | :--- | :--- |
| Database passwords | Every 90 days | Update Infisical → `scripts/rotate_secrets.sh` → Docker rolling restart |
| Cloudflare API Token | Every 90 days (hard TTL) | Regenerate in CF dashboard → update Infisical → re-deploy |
| `AEGIS_EDGE_SECRET` | On suspected breach or every 90 days | `npx wrangler secret put AEGIS_EDGE_SECRET` per worker |
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
- No observability UIs (Grafana, RabbitMQ) exposed to `0.0.0.0`
- No `liboqs-java` in `pom.xml` (breaks CI/CD — BouncyCastle is the sole PQC provider)
