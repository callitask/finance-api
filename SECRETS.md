# Security & Secret Management Policy

## Overview

This project adheres to a strict Zero-Trust security model. Hardcoded secrets are strictly prohibited in the codebase. All sensitive credentials are managed externally via **Infisical** and injected into the application runtime environment securely.

## Secret Injection Process (Flash & Wipe)

We utilize a "Flash & Wipe" strategy to ensure secrets never persist on the disk.

1.  **Storage**: Secrets are stored encrypted in the Infisical Vault (Production Environment).
2.  **Retrieval**: The `auto_deploy.sh` script authenticates with Infisical using a Machine Identity Token.
3.  **Injection**: Secrets are exported to a temporary `.env` file solely for the duration of the `docker compose up` command.
4.  **Wipe**: Immediately after container startup, the `.env` file is sanitized, removing all high-value secrets and leaving only the Machine Identity tokens.

## Required Environment Variables

The following variables must be present in the Infisical Project. These keys map directly to the interpolated values in `docker-compose.yml`.

### Infrastructure Secrets
| Variable Name | Description | Service(s) |
| :--- | :--- | :--- |
| `MINIO_ROOT_PASSWORD` | Root password for Object Storage. | MinIO, Backup Service |
| `GRAFANA_ADMIN_PASSWORD` | Admin password for Observability dashboards. | Grafana |
| `KEYCLOAK_DB_PASSWORD` | Password for the Identity Database. | Keycloak, Keycloak DB |
| `RABBITMQ_DEFAULT_USER` | Admin username for the Message Broker. | RabbitMQ, Backend |
| `RABBITMQ_DEFAULT_PASS` | Admin password for the Message Broker. | RabbitMQ, Backend |
| `BACKUP_MINIO_ACCESS_KEY` | Access key for Backup Service to talk to MinIO. | Backup Service |
| `CLOUDFLARE_TUNNEL_TOKEN` | Token for Zero Trust Tunnel connection. | Cloudflared |

### Application Secrets
| Variable Name | Description | Service(s) |
| :--- | :--- | :--- |
| `PROD_DB_URL` | JDBC URL for the main application database. | Backend |
| `PROD_DB_USERNAME` | Username for the main application database. | Backend |
| `PROD_DB_PASSWORD` | Password for the main application database. | Backend |
| `JWT_SECRET_KEY` | Secret for legacy token signing (if applicable). | Backend |
| `APP_ADMIN_EMAIL` | Email for the bootstrapped Admin user. | Backend |
| `APP_ADMIN_PASSWORD` | Password for the bootstrapped Admin user. | Backend, Keycloak |

### External API Keys
| Variable Name | Description | Service(s) |
| :--- | :--- | :--- |
| `MARKET_DATA_API_KEY` | Generic key for market data providers. | Backend |
| `ALPHAVANTAGE_API_KEY` | API Key for AlphaVantage. | Backend |
| `FINNHUB_API_KEY` | API Key for Finnhub. | Backend |
| `NEWS_API_KEY` | API Key for NewsAPI. | Backend |
| `GA4_PROPERTY_ID` | Google Analytics 4 Property ID. | Backend |

## Local Development

For local development, developers must have the Infisical CLI installed.

1.  **Login**: `infisical login`
2.  **Run**: `infisical run --env dev -- docker-compose up`

This command injects the development secrets directly into the process without creating a physical `.env` file.

## Rotation Policy

* **Database Passwords**: Rotate every 90 days. Requires full stack restart (`auto_deploy.sh`).
* **API Keys**: Rotate immediately upon vendor notification or suspected breach.
* **JWT Keys**: Rotate annually. Requires valid token invalidation.

## Access Control

Access to the Production Infisical environment is restricted to the DevOps Lead and CTO. Developers use the Development environment which contains separate, non-sensitive credentials.