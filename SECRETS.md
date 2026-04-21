# Security & Secret Management Policy

## Overview

This project adheres to a strict Zero-Trust security model ("Fort Knox Suite"). Hardcoded secrets are strictly prohibited in the codebase. All sensitive credentials are managed externally via **Infisical** for the backend and **Cloudflare Environment Variables** for the Edge and Frontend layers. 

All future frontends (e.g., Hiring Marketplace) MUST inherit this exact schema.

## Secret Injection Process (Flash & Wipe)

We utilize a "Flash & Wipe" strategy to ensure backend secrets never persist on the disk.

1.  **Storage**: Secrets are stored encrypted in the Infisical Vault (Production Environment).
2.  **Retrieval**: The `auto_deploy.sh` script authenticates with Infisical using a Machine Identity Token.
3.  **Injection**: Secrets are exported to a temporary `.env` file solely for the duration of the `docker compose up` command.
4.  **Wipe**: Immediately after container startup, the `.env` file is sanitized, removing all high-value secrets.

---

## EDGE: Cloudflare Worker Secrets
These are configured in the Cloudflare Dashboard -> Workers & Pages -> [Worker Name] -> Settings -> Variables.

### Finance SEO Worker (`treishfin-seo-worker`)
| Variable Name | Description |
| :--- | :--- |
| `BACKEND_URL` | Origin URL for API calls. |
| `FRONTEND_URL` | Public Frontend URL. |

### Agro SEO Worker (`treishvaamagro-seo-worker`)
| Variable Name | Description |
| :--- | :--- |
| `BACKEND_ORIGIN` | Strict backend ingress route (Do not hardcode). |
| `CF_PAGES_ORIGIN` | Allowed Cloudflare Pages domain for CORS/routing. |

---

## FRONTEND: Cloudflare Pages Secrets
These are configured in Cloudflare Dashboard -> Workers & Pages -> [Project Name] -> Settings -> Environment Variables.

### React Frontends (Finance & Agro)
| Variable Name | Description | Status |
| :--- | :--- | :--- |
| `REACT_APP_API_URL` | Base URL for the Backend API. | **Required** |
| `REACT_APP_AUTH_URL` | Base URL for Keycloak Auth. | **Required** |
| `REACT_APP_GA_MEASUREMENT_ID` | Google Analytics 4 ID (e.g., `G-XXXXX`). | Optional |
| `REACT_APP_ADSENSE_CLIENT_ID` | Google AdSense Pub ID (e.g., `ca-pub-XXXXX`). | Optional |
| `REACT_APP_GOOGLE_ADS_ID` | Google Ads Conversion ID (e.g., `AW-XXXXX`). | Optional |

### Next.js Frontends (Parent Group)
| Variable Name | Description | Status |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_API_URL` | Base URL for the Backend API. | **Required** |
| `NEXT_PUBLIC_GA_MEASUREMENT_ID` | Google Analytics 4 ID (e.g., `G-XXXXX`). | Optional |
| `NEXT_PUBLIC_ADSENSE_CLIENT_ID`| Google AdSense Pub ID (e.g., `ca-pub-XXXXX`). | Optional |
| `NEXT_PUBLIC_GOOGLE_ADS_ID` | Google Ads Conversion ID (e.g., `AW-XXXXX`). | Optional |

*Note: For maximum performance (0ms TBT), tracking IDs must NEVER be hardcoded in the codebase. They must be loaded dynamically based on the presence of these environment variables.*

---

## BACKEND: Infrastructure Secrets (Infisical)
| Variable Name | Description | Service(s) |
| :--- | :--- | :--- |
| `MINIO_ROOT_PASSWORD` | Root password for Object Storage. | MinIO, Backup Service |
| `GRAFANA_ADMIN_PASSWORD` | Admin password for Observability dashboards. | Grafana |
| `KEYCLOAK_DB_PASSWORD` | Password for the Identity Database. | Keycloak, Keycloak DB |
| `RABBITMQ_DEFAULT_USER` | Admin username for the Message Broker. | RabbitMQ, Backend |
| `RABBITMQ_DEFAULT_PASS` | Admin password for the Message Broker. | RabbitMQ, Backend |
| `BACKUP_MINIO_ACCESS_KEY` | Access key for Backup Service to talk to MinIO. | Backup Service |
| `CLOUDFLARE_TUNNEL_TOKEN` | Token for Zero Trust Tunnel connection. | Cloudflared |

## BACKEND: Application Secrets (Infisical)
| Variable Name | Description | Service(s) |
| :--- | :--- | :--- |
| `PROD_DB_URL` | JDBC URL for the main application database. | Backend |
| `PROD_DB_USERNAME` | Username for the main application database. | Backend |
| `PROD_DB_PASSWORD` | Password for the main application database. | Backend |
| `JWT_SECRET_KEY` | Secret for legacy token signing (if applicable). | Backend |
| `APP_ADMIN_EMAIL` | Email for the bootstrapped Admin user. | Backend |
| `APP_ADMIN_PASSWORD` | Password for the bootstrapped Admin user. | Backend, Keycloak |
| `INTERNAL_API_SECRET_KEY` | **Critical**. Master key for `InternalSecretFilter`. Used to lock down POST endpoints. | Backend |

## BACKEND: External API Keys (Infisical)
| Variable Name | Description | Service(s) |
| :--- | :--- | :--- |
| `MARKET_DATA_API_KEY` | Generic key for market data providers. | Backend |
| `ALPHAVANTAGE_API_KEY` | API Key for AlphaVantage. | Backend |
| `FINNHUB_API_KEY` | API Key for Finnhub. | Backend |
| `NEWS_API_KEY` | API Key for NewsAPI. | Backend |

---

## Rotation Policy

* **Database Passwords**: Rotate every 90 days. Requires full stack restart (`auto_deploy.sh`).
* **API Keys**: Rotate immediately upon vendor notification or suspected breach.
* **Worker Secrets**: Update in Cloudflare Dashboard immediately if domain changes.
* **Internal Secret**: Rotate manually via Infisical if internal service integrity is compromised.
* **Tracking IDs**: Can be added/removed from Cloudflare Pages seamlessly without requiring a code push.