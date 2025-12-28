# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [tfin-financeapi-Develop.0.0.0.1] - Fort Knox Security Suite
### Security & Architecture
- **Fix (Silent SSO)**: Replaced the restrictive `X-Frame-Options: DENY` header with **Content Security Policy (CSP)** in both **Nginx** and **Cloudflare Worker**. This resolves the "Refused to display in a frame" error, allowing Keycloak's Silent SSO iframe to function correctly while strictly maintaining Clickjacking protection.
- **Edge (Worker)**: Upgraded `worker.js` to enforce `Content-Security-Policy: frame-ancestors 'self'` and `Strict-Transport-Security` (2 years), aligning the Edge Layer with the Backend Security Standard.
- **Fort Knox Protocol**: Fully enabled "Internal Service Locking". Critical write endpoints now require `X-Internal-Secret` validation in addition to JWTs (`InternalSecretFilter`), protecting against internal vector attacks.
- **IP Defense**: Enabled Application-Level Rate Limiting with a **"Fail-Open" Resilience Strategy**. If the Redis backing store fails, the system now prioritizes availability, allowing traffic to pass rather than causing a denial of service.
- **URL Hidden Strategy**: Completed the "URLS HIDDEN" initiative. All backend and frontend code now uses strictly injected environment variables. Public repositories contain zero hardcoded URLs or IPs.
- **Stable Version**: Designated `tfin-financeapi-Develop.0.0.0.1` as the Stable Restore Point for future rollback scenarios.

## [2.7.0] - Enterprise Zero Trust & Edge Hardening
### Security & Networking
- **Infra (Zero Trust)**: Implemented strict **"Port Lockdown"**. Removed public `ports` binding for all internal services (Redis, Elasticsearch, MariaDB, RabbitMQ, MinIO, Grafana). Access is now strictly internal via Docker network `treish_net`.
- **Edge (Cloudflare Worker)**: Hardened the Edge Layer.
    - **Secrets**: Replaced hardcoded backend URLs with Cloudflare Environment Variables (`BACKEND_URL`, `FRONTEND_URL`).
    - **Security Headers**: Injected Banking-Grade headers (`Strict-Transport-Security`, `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`) into every response.
- **Frontend (BFF Pattern)**: Refactored `apiConfig.js` and `AuthContext.js` to use dynamic environment injection (`process.env.REACT_APP_API_URL`).
    - **Fix**: Resolved `401 Unauthorized` in News Widget by correcting API base path construction.
    - **Sec**: Removed all hardcoded production URLs from the source code.
- **Docs**: Comprehensive update of `README.md`, `ARCHITECTURE.md`, `SECRETS.md`, and `SEO-EDGE.md` to reflect the new "Closed Port" architecture.

## [2.6.0] - Enterprise Security Hardening (Flash & Wipe)
### Infrastructure & Security
- **Sec**: Implemented **Flash & Wipe** secret injection strategy. Secrets are injected into memory during Docker startup and immediately wiped from disk (`auto_deploy.sh`), leaving only Identity tokens.
- **Infra**: Fully parameterized `docker-compose.yml` to remove **all** hardcoded credentials.
    - **RabbitMQ**: Replaced default `guest`/`guest` with `${RABBITMQ_DEFAULT_USER}` and `${RABBITMQ_DEFAULT_PASS}`.
    - **MinIO**: Replaced root credentials with `${MINIO_ROOT_PASSWORD}`.
    - **Grafana**: Secured Admin access with `${GRAFANA_ADMIN_PASSWORD}`.
    - **Keycloak**: Database and Admin passwords are now injected via Infisical.
- **Ops**: Updated `auto_deploy.sh` to support the new variable injection flow for the Backup Service and RabbitMQ.
- **Docs**: Comprehensive update of `SECRETS.md`, `09-DEPLOYMENT-OPS.md`, and `01-ARCHITECTURE.md` to reflect the Zero-Trust model.

## [2.5.0] - Phase 7: Orchestrator Injection & Final Stabilization
### Infrastructure & Security (Host-Level)
- **Arch**: Transitioned to **Orchestrator Injection Pattern**. Secrets are now fetched by the host and injected into standard Docker containers, removing all secret-fetching logic from the images.
- **Sec**: Moved `CLOUDFLARE_TUNNEL_TOKEN` to Infisical, achieving 100% Zero-Secrets-on-Disk (except for Identity tokens).
- **Ops**: Upgraded Infisical CLI to `v0.154+` via official artifact repository to support modern Machine Identity authentication.
- **Fix**: Resolved "Zombie Token" conflict in Cloudflare Tunnel (`TUNNEL_TOKEN` vs `CLOUDFLARE_TUNNEL_TOKEN`).
- **Fix**: Corrected malformed `PROD_DB_URL` injection that caused JDBC driver crashes.

## [2.4.0] - Phase 5 & 6: Enterprise Secret Management
### Infrastructure & Security (Infisical Integration)
- **Sec**: Implemented **Infisical** for Enterprise Secret Management. Secrets are now injected in-memory at runtime via CLI (Zero-Secrets-on-Disk).
- **Infra**: Removed **HashiCorp Vault** dependency (`spring-cloud-starter-vault-config`) to resolve 76s startup delay and connection timeouts.
- **Ops**: Migrated from local `.env` files to Machine Identity authentication (`INFISICAL_CLIENT_ID`, `INFISICAL_CLIENT_SECRET`).
- **Docs**: Added `SECRETS.md` guide and updated Architecture/Ops documentation.
- **Fix**: Finalized "Full Fidelity" Cloudflare Worker for Enterprise SEO (JSON-LD Schema & Geo-Headers).

## [2.3.0] - Phase 4: Security, SEO & Resilience
### Critical Security & Stability Fixes
- **Sec**: Implemented **Gateway-Level CORS** in Nginx (`Access-Control-Allow-Origin`) to prevent browser network errors during backend failures.
- **Sec**: Configured **ModSecurity WAF Whitelist** for Faro Monitoring (`/api/v1/monitoring/ingest`) and Admin APIs to prevent false positives.
- **Resilience**: Patched `RateLimitingFilter` to **"Fail Open"**. If Redis encounters disk/permission errors, the site remains online instead of crashing.
- **Infra**: Secured Cloudflare Tunnel using `TUNNEL_TOKEN` in `.env` (Removed insecure `config.yml` credentials path).
- **SEO**: Restored public access to Image (`/api/v1/uploads/**`) and News endpoints to fix broken media.
- **Fix**: Restored missing `PasswordEncoder` bean that caused backend startup crashes.

## [2.2.0] - Phase 3: Automation & Observability
### Deployment & Monitoring
- **Obs**: Deployed **LGTM Stack** (Loki, Grafana, Tempo, Prometheus) for full-stack observability.
- **Ops**: Added dedicated **Backup Service** container for automated daily MySQL dumps to MinIO.
- **Feat**: Introduced `auto_deploy.sh` for reliable, self-healing deployments via Cron.
- **Feat**: Implemented **Multi-Stage Docker Builds** to compile Java 21 inside the container.
- **Ops**: Added `init_automation.sh` for one-click server setup.

### Phase 2: CORS & Proxy Normalization
- **Fix**: Resolved "Double Header" CORS error by converting Nginx to a Transparent Proxy.
- **Sec**: Updated `SecurityConfig.java` to whitelist Grafana Faro headers (`x-faro-session-id`, `x-faro-trace-id`).
- **Sec**: Normalized `Access-Control-Allow-Credentials` handling between Nginx and Spring Boot.

## [2.1.1] - Phase 1 Optimization
### Resilience & Observability
- **Feat**: Enhanced Rate Limiter to return `X-RateLimit-Remaining` and `X-RateLimit-Retry-After` headers for better client-side handling.
- **Perf**: Offloaded Audit Logging to asynchronous execution (`CompletableFuture`) to ensure zero latency impact on main request threads.

## [2.1.0] - Analytics & Telemetry Resilience
### Phase 18: Real User Monitoring (RUM) & Analytics
- **Feat**: Added `/faro-collector/collect` endpoint to ingest Grafana Faro telemetry directly.
- **Feat**: Implemented "Catch-Up" logic in `AnalyticsService` to force synchronization of missing GA4 data on startup.
- **Fix**: Resolved CORS issues for telemetry ingestion.
- **Fix**: Corrected argument mismatch in `AnalyticsService` compilation.

## [2.0.0] - Enterprise Transformation Complete (Phases 1-17)
### Phase 17: IAM & Infrastructure Stabilization
- **Identity**: Replaced custom JWT auth with Keycloak (OIDC/OAuth2).
- **Fix**: Resolved "White Screen" and "502 Bad Gateway" via dynamic DNS in Nginx.
- **Feat**: Implemented Cold Start Data Recovery for News widget.

### Phase 16: Publisher-Grade SEO
- **Feat**: Responsive Image Engine (Lanczos resampling, Virtual Threads).
- **Feat**: Dynamic Schema Injection (NewsArticle, VideoObject) via Cloudflare Worker.
- **Feat**: High-Availability `robots.txt` served from Edge.

### Phase 15: Content Syndication
- **Feat**: Google News Sitemap (`sitemap-news.xml`) and RSS Feed (`feed.xml`).
- **Ops**: Local runner persistence for Maven (`~/.m2`), reducing build times to ~40s.

### Phase 14: Supply Chain Security
- **Sec**: Integrated OWASP Dependency-Check with NVD API.
- **Sec**: Enforced strict dependency versions to prevent "Split-Brain" conflicts.

### Phase 13: Zero Data Loss (PITR)
- **DB**: Enabled MariaDB Binary Logs (`log_bin`).
- **Ops**: Updated backup scripts to capture Master Data coordinates for point-in-time recovery.

### Phase 12: Static Quality Gates
- **CI**: Added Spotless (Google Java Style) and Checkstyle enforcement.
- **CI**: Builds now fail on style violations.

### Phase 11: Advanced Resilience (RUM)
- **Feat**: Integrated Grafana Faro for Frontend Real User Monitoring.
- **Backend**: Configured RabbitMQ Dead Letter Exchange (DLX) for failed messages.

### Phase 10: Infrastructure as Code
- **Ops**: Migrated server provisioning to Ansible Playbooks.
- **API**: Standardized all endpoints to `/api/v1/` versioning.

### Phase 9: Distributed Tracing
- **Obs**: Deployed Grafana Tempo.
- **Backend**: Added Micrometer Tracing (Brave) and JSON logging with correlation IDs.

### Phase 8: Automated QA
- **Test**: Replaced H2 with Testcontainers (Dockerized MariaDB/Redis) for integration tests.
- **CI**: Enforced `mvn verify` in pipeline (no skip tests).

### Phase 7: The "Iron Dome" Security
- **Sec**: Deployed OWASP ModSecurity WAF on Nginx.
- **Ops**: Hardened Linux Kernel memory maps for Elasticsearch.

### Phase 6: Zero-Touch Deployment
- **CI/CD**: Implemented self-hosted GitHub Actions runner.
- **Ops**: Automated rolling updates via Docker Compose.

### Phase 5: Disaster Recovery
- **Ops**: Automated daily encrypted backups to MinIO.
- **Ops**: Created one-command `restore.sh`.

### Phase 4: High Availability
- **Infra**: Scaled Backend to 2 replicas.
- **Net**: Configured Nginx upstream load balancing.

### Phase 3: Event-Driven Architecture
- **Feat**: Decoupled Search Indexing and Sitemap generation using RabbitMQ.

### Phase 2: Observability
- **Obs**: Implemented PLG Stack (Prometheus, Loki, Grafana).

## [1.0.0] - Phase 1 Complete
### Added
- Spring Boot 3.4 Backend setup.
- Circuit Breakers (Resilience4j) and Rate Limiting (Bucket4j).
- Liquibase database migrations (V1 to V39).

## [0.9.0] - Infrastructure Update: Multi-Branch & Automation
### Deployment & Ops
- **Feat**: Implemented **Multi-Branch Deployment Strategy**.
    - **Develop**: Daily active development branch.
    - **Staging**: Stable restore point and feature-complete branch.
    - **Main**: Production lock.
- **Feat**: Upgraded **Watchdog Script (`auto_deploy.sh`)**.
    - It now intelligently polls all 3 branches (`develop`, `staging`, `main`).
    - Automatically switches the server's Git context to the branch with the latest timestamp.
    - "Self-Healing" logic creates local branches if they are missing.
- **Feat**: Updated **GitHub Actions (`deploy.yml`)** to trigger builds on `staging` and `develop` pushes.
- **Docs**: Complete overhaul of `09-DEPLOYMENT-OPS.md` to reflect the Dual-Engine architecture (Builder vs. Watchdog).