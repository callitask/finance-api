# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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