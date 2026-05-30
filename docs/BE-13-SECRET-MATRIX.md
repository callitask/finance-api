# BE-13 — Complete Secret & Environment Variable Matrix

**Classification:** Internal Reference (Sanitized — Variable NAMES only. No actual values. No keys.)
**Verified Against:** `SECRETS.md`, `docker-compose.yml`, `application-prod.properties`, `wrangler.toml`, `auto_deploy.sh`, `rotate_secrets.sh`
**Last Updated:** 2026-05-29

---

## Overview — Three Vaults, Zero Overlap

All secrets are routed to exactly one vault based on their consumer. **Never cross-inject** (e.g., do not put a backend secret into Cloudflare Pages env vars).

| Vault | Consumers | Injection Mechanism |
| :--- | :--- | :--- |
| **Infisical** | Backend Spring Boot containers | `auto_deploy.sh` Flash & Wipe → `.env` → `docker-compose.yml` `env_file:` |
| **Cloudflare Worker Secrets** | Edge Worker (`worker.js`) | `npx wrangler secret put <KEY>` |
| **Cloudflare Pages Env Vars** | Next.js build + runtime | Cloudflare Pages Dashboard → Settings → Environment Variables |

---

## Flash & Wipe — How Backend Secrets Are Injected

Secrets never persist unencrypted on disk longer than required:

1. `auto_deploy.sh` authenticates with Infisical via **Machine Identity Token** (Universal Auth — no human password)
2. `infisical export` writes all secrets to a temporary `.env` file, piped through `sed "s/['\"]//g"` to strip literal quotes (HikariCP crash prevention)
3. `docker compose up -d` reads `.env` via `env_file:` directive — all containers receive their variables in-memory
4. After startup, the `.env` is **sanitised** (high-value secret values removed)
5. Containers retain secrets exclusively in their in-memory environment

**NEVER:** Commit `.env` to git. NEVER wrap `.env` values in single or double quotes.

---

## 1. Backend — Infrastructure Secrets (Infisical → Docker Compose)

| Variable | Description | Consumer(s) |
| :--- | :--- | :--- |
| `PROD_DB_URL` | JDBC connection URL for `finance_db` MariaDB | Backend (`application-prod.properties`) |
| `PROD_DB_USERNAME` | Application DB username | Backend |
| `PROD_DB_PASSWORD` | Application DB password | Backend, MariaDB (`MYSQL_ROOT_PASSWORD`) |
| `MARIADB_ENCRYPTION_KEY` | Docker secret → MariaDB TDE volume encryption key | MariaDB (`encryption.cnf`), Docker secrets |
| `KEYCLOAK_DB_PASSWORD` | Keycloak's dedicated MariaDB instance password | Keycloak, `treishvaam-keycloak-db` |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin console password | Keycloak |
| `REDIS_PASSWORD` | Redis `requirepass` + Spring Data Redis auth | Redis, Backend |
| `ELASTIC_PASSWORD` | Elasticsearch built-in security password | Elasticsearch, Backend |
| `MINIO_ACCESS_KEY` | MinIO access key (root user) | MinIO, Backend (`MinioConfig.java`) |
| `MINIO_SECRET_KEY` | MinIO secret key (root user) | MinIO, Backend |
| `MINIO_ROOT_USER` | MinIO container root username | MinIO |
| `MINIO_ROOT_PASSWORD` | MinIO container root password | MinIO, Backup Service |
| `SPRING_RABBITMQ_USERNAME` | RabbitMQ admin username for Spring | RabbitMQ, Backend |
| `SPRING_RABBITMQ_PASSWORD` | RabbitMQ admin password | RabbitMQ, Backend |
| `RABBITMQ_DEFAULT_USER` | RabbitMQ admin username (container env) | RabbitMQ |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ admin password (container env) | RabbitMQ |
| `CLOUDFLARE_TUNNEL_TOKEN` | Cloudflare Zero Trust Tunnel authentication token | `cloudflared` container |
| `GRAFANA_ADMIN_PASSWORD` | Grafana admin console password | Grafana |
| `BACKUP_MINIO_ACCESS_KEY` | Dedicated MinIO access key for backup service | Backup Service |
| `BACKUP_ENCRYPTION_KEY` | AES key for encrypting MariaDB dump files at rest | Backup Service |

---

## 2. Backend — Application Secrets (Infisical → Docker Compose)

| Variable | Description | Consumer | Notes |
| :--- | :--- | :--- | :--- |
| `JWT_SECRET_KEY` | Legacy JWT signing secret (512-bit) | Backend (`application-prod.properties` `jwt.secret`) | Rotated every 90 days via `rotate_secrets.sh`. Rotation invalidates ALL active user sessions |
| `INTERNAL_API_SECRET_KEY` | Master key for `InternalSecretFilter` | Backend — internal endpoint guard | Rotated every 90 days |
| `CONTENT_SIGNING_KEY` | HMAC-SHA256 key for blog post content integrity | Backend `ContentIntegrityService` | Used to sign `content_signature` column (V45 migration) |
| `AEGIS_DB_SIGNING_KEY` | SHA3-256 HMAC key for JDBC query signatures | Backend `AegisQueryInterceptor` | Zero-Trust DB Driver Boundary. Changing this invalidates all existing signed queries |
| `AEGIS_EDGE_SECRET` | HMAC-SHA-512 seed for edge request signing | Backend `AegisEdgeValidationFilter` + Edge Worker | **64-character minimum.** Must match exactly between backend and Worker |
| `USER_EMAIL_ENCRYPTION_KEY` | AES-256-GCM key for `users.email` column | Backend `UserEmailConverter` | Domain-specific. `v1:` prefix versioning supported |
| `CONTACT_EMAIL_ENCRYPTION_KEY` | AES-256-GCM key for `contact_message.email` | Backend `ContactEmailConverter` | Domain-specific |
| `CONTACT_MESSAGE_ENCRYPTION_KEY` | AES-256-GCM key for `contact_message.message` | Backend `ContactMessageConverter` | Domain-specific |
| `AUDIT_IP_ENCRYPTION_KEY` | AES-256-GCM key for `audit_log.ip_address` | Backend `AuditIpConverter` | Domain-specific. Added in V44 migration |
| `LINKEDIN_TOKEN_ENCRYPTION_KEY` | AES-256-GCM key for `users.linkedin_access_token` | Backend | Added in V42 migration |
| `APP_ADMIN_EMAIL` | Bootstrapped admin user email | Backend `DataInitializer` | Created on first startup if admin doesn't exist |
| `APP_ADMIN_PASSWORD` | Bootstrapped admin user password | Backend `DataInitializer` | |
| `FINANCE_FRONTEND_URL` | Production Finance frontend URL | Keycloak CORS config | `https://treishvaamfinance.com` |
| `FINANCE_FRONTEND_LOCAL_URL` | Local dev Finance frontend URL | Keycloak | `http://localhost:3000` |
| `KEYCLOAK_CREDENTIALS_SECRET` | Keycloak client secret for backend OAuth2 | Backend OAuth2 Resource Server | From Keycloak realm config |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Keycloak JWT issuer URI | Backend JWT validation | |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | Keycloak JWKS endpoint | Backend JWT validation | |

---

## 3. Backend — External API Keys (Infisical → Docker Compose)

| Variable | Provider | Consumer | Rate Limit Notes |
| :--- | :--- | :--- | :--- |
| `ALPHAVANTAGE_API_KEY` | Alpha Vantage | Backend `AlphaVantageProvider` | Free tier: 25 requests/day |
| `FINNHUB_API_KEY` | Finnhub | Backend `FinnhubProvider` | Free tier: 60 calls/minute |
| `MARKET_DATA_API_KEY` (= FMP key) | Financial Modeling Prep | Backend `FmpProvider` | Varies by plan |
| `YAHOO_FINANCE_API_KEY` | Yahoo Finance | Backend `YahooHistoricalProvider` | — |
| `NEWS_API_KEY` | NewsData.io | Backend `NewsHighlightService` | Free tier: 200 requests/day |
| `GA4_PROPERTY_ID` | Google Analytics 4 | Backend `AnalyticsService` | — |
| `GA4_BIGQUERY_PROJECT_ID` | Google Cloud BigQuery | Backend `AnalyticsService` | Needs `ga4-credentials.json` mounted at `/app/ga4-credentials.json` |
| `GA4_BIGQUERY_DATASET_ID` | Google Cloud BigQuery | Backend `AnalyticsService` | — |

---

## 4. Cloudflare Worker Secrets (`npx wrangler secret put`)

Injected into the Worker V8 isolate via the Cloudflare dashboard or Wrangler CLI. **Never in `wrangler.toml`.**

| Secret | Description | Worker Usage |
| :--- | :--- | :--- |
| `AEGIS_EDGE_SECRET` | HMAC-SHA-512 seed for `generateEdgeSignature()` | Signs all backend-forwarded requests. Must match backend `AEGIS_EDGE_SECRET` exactly |
| `BACKEND_API_URL` | Cloudflare Tunnel URL to Spring Boot backend | Used for all backend API proxying |
| `BACKEND_URL` | Fallback alias for `BACKEND_API_URL` | Used as secondary lookup if `BACKEND_API_URL` is absent |

**How to inject:**
```powershell
cd "C:\Users\7303150607\OneDrive\Desktop\PrOJEct\treishvaam-finance-frontend\worker"
npx wrangler secret put AEGIS_EDGE_SECRET
# Prompts for value — paste the 64-char hex secret
npx wrangler secret put BACKEND_API_URL
# Paste the Cloudflare Tunnel URL
```

---

## 5. Cloudflare Pages Environment Variables (Dashboard → Settings)

Used by the Next.js build and Edge Runtime. **Set in Cloudflare Pages Dashboard**, not committed to code.

| Variable | Description | Notes |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_API_URL` | Backend API base URL (proxied via Worker) | Used by Next.js GEO route handlers (`app/llms.txt/route.ts` etc.) |
| `NEXT_PUBLIC_GA_MEASUREMENT_ID` | Google Analytics 4 Measurement ID | Format: `G-XXXXXXXXXX` |
| `NEXT_PUBLIC_GOOGLE_ADS_ID` | Google Ads conversion ID | Format: `AW-XXXXXXXXXX` |
| `NEXT_PUBLIC_ADSENSE_CLIENT_ID` | Google AdSense publisher ID | Format: `ca-pub-XXXXXXXXXX` |
| `NEXT_PUBLIC_ENFORCE_STRICT_PRIVACY` | GDPR/DPDP toggle — `true` injects `anonymize_ip: true` into GA4 | Default: `false` (full data collection for Indian jurisdiction). Do NOT permanently set to `true` without business approval |
| `NEXT_PUBLIC_CHAIRMAN_PORTRAIT_URL` | Dynamic URL for chairman headshot | Points to MinIO or `treishvaam-media.pages.dev`. Never hardcode portrait in `/public/` |

---

## 6. Cloudflare API Token — Expiry & Rotation

**⚠️ CRITICAL ALERT:** The production Cloudflare API Token is IP-fenced and scoped.

| Attribute | Value |
| :--- | :--- |
| **Account** | `Treishvaamgroup@gmail.com` |
| **Expiry Date** | **2026-08-26** |
| **7-Day Warning Trigger** | **2026-08-19** |
| **Allowed IPs** | `192.168.29.111` (primary) and `192.168.56.101` (secondary) |
| **Scope** | Account Settings: Read, Workers KV: Edit, Workers Routes: Edit, Zone (`treishvaamfinance.com`): Zone Read + Cache Purge |
| **Used By** | Backend `CloudflareEdgeSyncService` → pushes malicious IP/JA3 hashes to Edge KV in real time |

**Rotation Protocol:**
1. Generate new scoped token in Cloudflare Dashboard (same scopes and IP fencing)
2. Update `CLOUDFLARE_API_TOKEN` in Infisical
3. Run `scripts/rotate_secrets.sh` or manually trigger `auto_deploy.sh`
4. Verify `CloudflareEdgeSyncService` continues syncing threat intel to KV
5. Revoke old token in Cloudflare Dashboard

---

## 7. Key Generation Reference (Windows)

When a new cryptographic key is required:

```powershell
# 32-byte (256-bit) key — for AES-256-GCM encryption keys
[System.Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Maximum 256) }))

# 64-byte (512-bit) key — for JWT / HMAC-SHA-512 secrets
[System.Convert]::ToBase64String((1..64 | ForEach-Object { [byte](Get-Random -Maximum 256) }))

# Or using OpenSSL (if installed via Git Bash or WSL):
openssl rand -base64 32   # 256-bit
openssl rand -base64 64   # 512-bit
```

**Important:** Strip trailing `=` padding and `/` characters as needed by the consuming library. The `rotate_secrets.sh` script demonstrates this pattern with `tr -d '\n' | tr -d '/'`.

---

## 8. Adding a New Secret — Mandatory Protocol

When a new feature requires a new secret:

1. **Identify the vault:** Backend secret → Infisical. Edge secret → CF Worker Secrets. Frontend public value → CF Pages Env Vars.
2. **Generate the key** using the commands above.
3. **Add to the correct vault** (never commit to git).
4. **Update `docker-compose.yml`** with the new environment variable mapping (for backend secrets).
5. **Update `application-prod.properties`** with the Spring placeholder `${VAR_NAME}`.
6. **Document the variable** in this file (BE-13) under the correct section.
7. **Update IMMUTABLE CHANGE HISTORY** in the affected files.
8. **Test** that `docker-compose config` passes validation before deploying.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **ADDED (2026-05-29 — Enterprise Documentation Generation):**
  - Created `BE-13-SECRET-MATRIX.md` from scratch.
  - Why: `SECRETS.md` in the repository exists but is partially complete — it documents variable names for sections 1–4 but does not include the Cloudflare API Token expiry/rotation protocol, the key generation reference, or the new-secret provisioning protocol. This document consolidates and completes the full secret architecture.
  - Source of truth: `SECRETS.md`, `docker-compose.yml`, `application-prod.properties`, `wrangler.toml`, `auto_deploy.sh`, `rotate_secrets.sh`, `Rules_of_Engagement.pdf`.
