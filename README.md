---
root: true
project: Treishvaam Finance API
stack: [Java 21, Spring Boot 3.4, Go 1.22, OpenResty, Lua, Python 3, MariaDB, Redis, Elasticsearch, MinIO, RabbitMQ, Keycloak, Envoy, Prometheus, Grafana, Loki, Promtail, Tempo, Wazuh, BouncyCastle, gRPC, ANTLR4, Liquibase, Docker]
entry_points: [FinanceApiApplication.java, auto_deploy.sh, docker-compose.yml]
status: Active
version: tfin-financeapi-Develop.0.0.0.7
---

# Treishvaam Finance API
## Enterprise Zero-Trust Multi-Tenant Backend

The Treishvaam Finance API is an enterprise-grade, post-quantum-hardened backend built on Spring Boot 3.4 and Java 21. It powers the entire Treishvaam Group ecosystem — Finance, Agro, and Parent frontends — from a single zero-trust deployment. Every request passes through a 9-layer adaptive security framework (AEGIS) before any business logic executes.

---

## Branching Strategy

| Branch | Role | Deployment |
| :--- | :--- | :--- |
| `main` | Production | Protected — stable releases only |
| `staging` | Stable restore point | Feature-complete golden copy |
| `develop` | Active development | **Push here daily — Watchdog auto-deploys** |

**NEVER push directly to `main`.** All code flows: `develop` → GitHub Actions → Ubuntu VM Watchdog → Docker rolling restart.

---

## Complete Technology Stack

Every entry in this table is confirmed present in `docker-compose.yml` or `pom.xml`.

### Application Runtime

| Technology | Version | Role |
| :--- | :--- | :--- |
| **Java 21 (Temurin LTS)** | 21 LTS | Primary runtime — Virtual Threads (Project Loom) throughout |
| **Spring Boot** | 3.4.0 | Application framework |
| **Spring WebFlux** | (via Boot) | Reactive programming support alongside MVC |
| **Spring Security** | (via Boot) | Security filter chain + OAuth2 Resource Server |
| **Spring Data JPA** | (via Boot) | ORM / Hibernate |
| **Spring Data Redis** | (via Boot) | Redis client integration |
| **Spring Data Elasticsearch** | (via Boot) | Elasticsearch client integration |
| **Spring AMQP** | (via Boot) | RabbitMQ messaging client |
| **Spring Boot Actuator** | (via Boot) | Health checks + Prometheus metrics endpoint |
| **Spring Cloud Vault** | (via Boot) | Secret management integration |
| **Go 1.22+** | 1.22+ | AEGIS ZKP microservice (`aegis-zkp-service`) |
| **Python 3** | 3.x | Market data processing bridge (invoked via `ProcessBuilder`) |

### Data Layer

| Technology | Version | Role |
| :--- | :--- | :--- |
| **MariaDB** | 10.6 | Primary relational database — Transparent Data Encryption (TDE) |
| **MariaDB Java Client** | (via pom) | JDBC driver |
| **Liquibase** | (via Boot) | Database schema migrations (V1–V46) |
| **HikariCP** | (via Boot) | Enterprise JDBC connection pool |
| **Redis** | 7 Alpine | Read-through cache + AEGIS MTD temporal path registry |
| **Bucket4j** | 8.10.1 | Rate limiting — token bucket algorithm backed by Redis |
| **Elasticsearch** | 8.17.0 | Full-text search engine (`PostDocument`, `PostSearchRepository`) |
| **MinIO** | 8.5.7 (SDK) | S3-compatible object storage — media files, materialized HTML |
| **AWS S3 SDK** | 2.25.11 | S3-compatible client for backup service |

### Security & Cryptography

| Technology | Version | Role |
| :--- | :--- | :--- |
| **BouncyCastle** (`bcpkix-jdk18on`) | 1.78.1 | Post-Quantum Cryptography — ML-DSA-87 JWT signing |
| **BouncyCastle** (`bcprov-jdk18on`) | 1.78.1 | PQC provider — AES-256-GCM, SHA3-512 |
| **Argon2-jvm** | 2.11 | Memory-hard password hashing (Argon2id) |
| **gRPC Netty Shaded** | 1.64.0 | gRPC transport for ZKP microservice calls |
| **gRPC Protobuf** | 1.64.0 | Protobuf serialisation for `aegis_zkp.proto` |
| **gRPC Stub** | 1.64.0 | Generated gRPC client stubs |
| **gnark** (Go) | (in ZKP service) | Zero-Knowledge Proof circuit (Schnorr-over-Lattice) |
| **Resilience4j** | 2.2.0 | Circuit Breaker — wraps ZKP gRPC calls + Python bridge |
| **ANTLR4 Runtime** | 4.x | AEGIS Expression Language (AEL) parser |
| **Spring Security OAuth2** | (via Boot) | JWT validation — Keycloak JWK Set, cached |
| **Keycloak** | 25.0.0 | Centralised SSO / Identity Provider |

### Messaging & Events

| Technology | Version | Role |
| :--- | :--- | :--- |
| **RabbitMQ** | 3.12-management | Async event bus — threat telemetry, sitemap triggers, DLX retries |

### Infrastructure & Proxy

| Technology | Version | Role |
| :--- | :--- | :--- |
| **OpenResty** | Alpine | Reverse proxy — replaces plain Nginx. Enables Lua WAF execution |
| **Lua** | (OpenResty built-in) | `aegis_ja3.lua` — TLS JA3 fingerprinting, real-IP resolution |
| **Envoy Proxy** | v1.29-latest | Internal L7 sidecar proxy — routes gRPC traffic to `aegis-zkp-service` |
| **Cloudflare Tunnel** (`cloudflared`) | Latest | Secure zero-port ingress from Cloudflare edge to Docker network |
| **Docker / Docker Compose** | v3.8 | Container orchestration — all services on `treish_net` bridge |

### Security Services

| Technology | Version | Role |
| :--- | :--- | :--- |
| **Wazuh Agent** | 4.7.3 | HIDS — subscribes to RabbitMQ threat events, feeds `HidsIntegrityValidator` |
| **Thinkst Canarytokens** | Latest | L4-ADA deception — traceable canary tokens embedded in poisoned corpora |
| **Gitleaks** | (CI) | Pre-push secret scanning — blocks pipeline on any credential detection |
| **OWASP Dependency Check** | 12.1.0 | Vulnerability scanning — isolated cron job, never blocks deploy pipeline |
| **ModSecurity (OWASP CRS)** | (OpenResty module) | WAF — SQL injection, XSS, protocol attack prevention |

### Observability Stack

| Technology | Version | Role |
| :--- | :--- | :--- |
| **Grafana** | Latest | Mission Control dashboard — SSH tunnel access only (`127.0.0.1:3001`) |
| **Prometheus** | Latest | Metrics scraping — Spring Boot Actuator + Micrometer |
| **Micrometer** (`micrometer-registry-prometheus`) | (via Boot) | Exposes Spring Boot metrics at `/actuator/prometheus` |
| **Micrometer Tracing** (`micrometer-tracing-bridge-brave`) | (via Boot) | Distributed tracing bridge (Brave/Zipkin protocol) |
| **Zipkin Reporter** (`zipkin-reporter-brave`) | (via Boot) | Ships trace data to Tempo at `http://tempo:9411/api/v2/spans` |
| **Loki** | 2.9.2 | Log aggregation — query label: `{job="varlogs"}` |
| **Promtail** | 2.9.2 | Log shipping — tails `./logs/*.log`, attaches MDC `tenantId` label |
| **Tempo** | Latest | Distributed tracing backend (Zipkin-compatible receiver) |
| **Logstash Logback Encoder** | 7.4 | Structured JSON log output for Promtail → Loki pipeline |

### Third-Party Integrations

| Technology | Version | Role |
| :--- | :--- | :--- |
| **AlphaVantage** | API | Forex and technical indicators market data |
| **Finnhub** | API | Real-time stock quotes and news |
| **Financial Modeling Prep (FMP)** | API | Market movers — top gainers/losers |
| **Yahoo Finance** (via `yfinance` Python) | API | Historical candle data |
| **NewsData.io** | API | News headlines and articles |
| **Google Analytics Data API** | 0.58.0 | Server-side GA4 reporting |
| **Google Cloud BigQuery** | 2.40.1 | GA4 raw event data — un-sampled historical analytics |

### Image & File Processing

| Technology | Version | Role |
| :--- | :--- | :--- |
| **Thumbnailator** | 0.4.20 | Server-side image resizing and thumbnail generation |
| **ImageIO WebP** | 3.10.1 | WebP encode/decode support |
| **Twelve Monkeys ImageIO Core** | 3.10.1 | Extended image format support |
| **Blurhash** | 1.0.0 | Blur placeholder hash generation for progressive image loading |
| **Jsoup** | 1.17.2 | HTML parsing and sanitisation — used by `HtmlMaterializerService` |
| **Apache Tika** | 2.9.2 | MIME type detection for uploaded files |
| **YAUAA** | 7.26.1 | User-agent parsing — bot detection and device classification |

### Build & Code Quality

| Technology | Version | Role |
| :--- | :--- | :--- |
| **Maven** | 3.9+ | Build system |
| **Spotless** | 2.43.0 | Code formatting enforcement — `mvn spotless:apply` before every commit |
| **Checkstyle** | 3.3.1 | Java style enforcement via `checkstyle.xml` |
| **Lombok** | 1.18.30 | Boilerplate reduction |
| **Protobuf Maven Plugin** | 0.6.1 | Generates Java stubs from `aegis_zkp.proto` |
| **ANTLR4 Maven Plugin** | 4.x | Generates AEL parser from `aegis.g4` grammar |
| **Springdoc OpenAPI** | 2.5.0 | API docs — disabled in prod, enabled in dev profile only |

### Testing

| Technology | Version | Role |
| :--- | :--- | :--- |
| **Spring Boot Test** | (via Boot) | Integration test framework |
| **Spring Security Test** | (via Boot) | Security context for tests |
| **Reactor Test** | (via Boot) | WebFlux reactive testing |
| **Testcontainers** | (via Boot) | Real Docker containers in tests — MariaDB, Elasticsearch, RabbitMQ, Redis |
| **JUnit Jupiter** | (via Boot) | Unit test runner |

---

## AEGIS — 9-Layer Adaptive Security Framework

AEGIS (Adaptive Entropic Guardian Intelligence System) runs on every request path before any business logic executes.

| Layer | Name | Key Classes |
| :--- | :--- | :--- |
| **L0-HEA** | Hardware Entropy Anchoring | `AegisEntropyManager`, `JitterEntropySource`, `UrandomEntropySource` |
| **L1-PQCf** | Post-Quantum Cryptography | `AegisPqcJwtService`, `AegisPqcKeyStore` — ML-DSA-87 via BouncyCastle 1.78.1 |
| **L2-PPO** | Polymorphic Protocol Obfuscation | `AegisTemporalPathManager`, `EndpointManifest`, `AegisResponseMutator` |
| **L3-ZKA** | Zero-Knowledge Authentication | `AegisZkpAdminFilter`, `aegis-zkp-service` (Go gRPC, Envoy-routed, 256MB limit) |
| **L4-ADA** | Adversarial Deception Architecture | `AegisDeceptionEngine`, `TarpitManager`, `RabbitMQAttackPublisher`, `CloudflareEdgeSyncService` |
| **L5-BIE** | Behavioural Intelligence Engine | `AegisBehavioralEngine`, `ShannonEntropyCalculator`, `SessionBehaviorProfile` |
| **L6-MTD** | Moving Target Defence Orchestration | `AegisMtdController`, `AegisThreatConsumer`, `CloudflareEdgeSyncService` |
| **L7-CMCS** | Chaos Mirror Counter-Attack | `AegisChaosMirror`, `PowChallengeIssuer`, `TarpitManager` |
| **L8-BCSM** | Byzantine Consensus Security Mesh | `AegisBcsm` — 7 validators, parallel Virtual Threads, 100ms timeout, 2-fault tolerance |

**Critical:** `liboqs-java` is permanently excluded — breaks Maven CI/CD. BouncyCastle is the sole PQC provider.

---

## Key Architecture Features

### Multi-Tenant Isolation
One backend serves Finance (`treishvaamfinance.com`), Agro (`treishvaamagro.com`), and Parent (`treishvaamgroup.com`). The Edge Worker injects `X-Tenant-ID` on every request. `TenantInterceptor` locks all DB queries, service calls, and MDC log tags to that tenant. Zero cross-brand data leakage is architecturally possible.

### Generative Engine Optimization (GEO)
AI crawlers (GPTBot, ClaudeBot, DeepSeek, PerplexityBot, 20+ others) are intercepted at the Cloudflare Edge and served HMAC-SHA256-signed semantic Markdown payloads (`/llms.txt`, `/ai-feed.md`, `/ontology.json`) directly from KV — React and the Java backend are never contacted.

### Hybrid SSG
On post publication, `HtmlMaterializerService` (async Virtual Thread) generates static HTML with injected JSON-LD and Open Graph tags, uploads to MinIO. The Edge Worker serves this as a last-resort fallback guaranteeing 100% SEO uptime during backend downtime.

### Observability — Grafana Stack
Prometheus scrapes Spring Boot Actuator (`/actuator/prometheus`). Micrometer ships distributed traces (Zipkin protocol) to Tempo. Promtail ships structured JSON logs to Loki. Grafana unifies all three. Access strictly via SSH local port forwarding — never exposed on `0.0.0.0`.

---

## Package Structure

```
com.treishvaam.financeapi
├── security/aegis/          ← AEGIS framework (all 9 layers)
│   ├── bcsm/                ← L8 Byzantine Consensus + 7 validators + Merkle audit log
│   ├── crypto/              ← L0 entropy, L1 PQC JWT, L7 chaos mirror
│   ├── ael/                 ← AEGIS Expression Language (ANTLR4 grammar + interpreter)
│   └── mtd/                 ← L2/L6 MTD, L4 tarpit, Cloudflare KV threat sync
├── marketdata/              ← Strategy Pattern over AlphaVantage/Finnhub/FMP/Yahoo
├── service/                 ← Blog CMS, Sitemap (10M+ scale), GEO payloads, HTML materializer
├── analytics/               ← Native RUM + GA4 BigQuery integration
├── apistatus/               ← External API health diagnostics
├── config/tenant/           ← TenantInterceptor + TenantContext (ThreadLocal)
├── controller/              ← REST endpoints (Blog, Market, GEO, Sitemap, Admin, Analytics)
├── search/                  ← Elasticsearch full-text search (PostDocument, PostSearchRepository)
└── aegis/zkp-service/       ← Isolated Go gRPC microservice (Schnorr-over-Lattice ZKP)
```

---

## Infrastructure — Docker Containers (Complete)

All containers on `treish_net` bridge network. Only `nginx` (OpenResty) exposes ports 80/443 to the host.

| Container | Image | Host Ports | Role |
| :--- | :--- | :--- | :--- |
| `treishvaam-nginx` | `openresty/openresty:alpine` | **80, 443** | Reverse proxy, Lua WAF, JA3 fingerprinting, TLS |
| `treishvaam-tunnel` | `cloudflare/cloudflared` | Outbound only | Secure zero-port ingress from Cloudflare |
| `backend` (×2 replicas) | `ghcr.io/callitask/finance-api:latest` | Internal only | Spring Boot application |
| `treishvaam-db` | `mariadb:10.6` | Internal only | Primary app database (TDE) |
| `treishvaam-keycloak-db` | `mariadb:10.6` | Internal only | Keycloak SSO database |
| `treishvaam-keycloak` | `quay.io/keycloak/keycloak:25.0.0` | Internal only | Identity Provider / SSO |
| `treishvaam-redis` | `redis:7-alpine` | Internal only | Cache + MTD path registry |
| `treishvaam-elastic` | `elasticsearch:8.17.0` | Internal only | Full-text search |
| `treishvaam-minio` | `minio/minio` | Internal only | Object storage (media, HTML) |
| `treishvaam-rabbitmq` | `rabbitmq:3.12-management` | `127.0.0.1:15672` (tunnel) | Async event bus |
| `treishvaam-envoy` | `envoyproxy/envoy:v1.29-latest` | Internal only | L7 sidecar — routes gRPC to ZKP service |
| `aegis-zkp-service` | `treishvaam/aegis-zkp-service:latest` (Go) | `127.0.0.1:9090` | L3-ZKA ZKP verification (256MB limit) |
| `aegis-canary-server` | `thinkst/canarytokens:latest` | `127.0.0.1:8089` | L4-ADA canary token management |
| `wazuh-agent` | `wazuh/wazuh-agent:4.7.3` | Internal only | HIDS — feeds BCSM HidsIntegrityValidator |
| `treishvaam-grafana` | `grafana/grafana:latest` | `127.0.0.1:3001` (tunnel) | Mission Control dashboard |
| `treishvaam-prometheus` | `prom/prometheus:latest` | Internal only | Metrics scraping |
| `treishvaam-loki` | `grafana/loki:2.9.2` | Internal only | Log aggregation |
| `treishvaam-promtail` | `grafana/promtail:2.9.2` | Internal only | Log shipping to Loki |
| `treishvaam-tempo` | `grafana/tempo:latest` | Internal only | Distributed tracing (Zipkin) |
| `treishvaam-backup` | `./backup` (custom) | Internal only | Encrypted MariaDB + MinIO backups to S3 |

---

## CI/CD — Dual Engine

### Engine A — GitHub Actions (`deploy.yml`)
Triggers on push to `develop`, `staging`, `main`:

```
1. Java 21 (Temurin) setup + Maven cache
2. Gitleaks secret scan              ← BLOCKS pipeline on any credential detection
3. GPG commit verification           ← BLOCKS on unsigned commits (git verify-commit HEAD)
4. mvn test
5. mvn clean package
6. mvn spotless:check
7. SCP artifact to Ubuntu VM
8. Signal Watchdog
```

OWASP Dependency Check (v12.1.0) runs in a **separate isolated cron job** — never blocks the deploy pipeline.

### Engine B — Watchdog (`scripts/auto_deploy.sh`)
Runs on the Ubuntu VM every minute via cron:

```
1. Compare branch timestamps — most recent wins
2. Pull config changes (Nginx/Lua, Python scripts, Docker configs)
3. Flash & Wipe — infisical export → .env → sanitize after docker compose up
4. docker compose up -d (zero-downtime rolling restart)
5. OS cache flush (prevents OOM on dual-replica JVM boot)
```

---

## Local Development

### Prerequisites
- JDK 21 (Temurin)
- Go 1.22+ (ZKP microservice)
- Docker + Docker Compose v2
- Maven 3.9+
- Infisical CLI v0.154+
- GnuPG (GPG commit signing — mandatory)

### GPG Signing Setup
```powershell
gpg --full-generate-key
git config --global commit.gpgsign true
git config --global user.signingkey <YOUR_KEY_ID>
```

### First Run
```powershell
copy .env.dev.example .env
# Fill in dev values
docker compose up -d
mvn clean package -DskipTests=false
```

### Deploy (Windows Host — Backend)
```powershell
mvn spotless:apply
git checkout develop
git add .
git commit -m "feat: description"
git push origin develop
```

---

## Documentation

| File | Contents |
| :--- | :--- |
| `docs/BE-00-INDEX.md` | Master documentation index |
| `docs/BE-01-ARCHITECTURE.md` | System architecture, Docker topology, data flow |
| `docs/BE-02-CORE.md` | Spring Boot core, AEGIS filter chain, multi-tenancy, Virtual Threads |
| `docs/BE-03-API.md` | REST API reference — all endpoints, roles, contracts |
| `docs/BE-04-SERVICES.md` | Business logic — Market Data, Blog CMS, Sitemap, GEO |
| `docs/BE-05-DATABASE.md` | Schema, Liquibase migrations (V1–V46), PII encryption |
| `docs/BE-06-INFRA-DEVOPS.md` | Docker Compose full reference, OpenResty/Lua, scripts, CI/CD, observability |
| `docs/BE-07-SECURITY.md` | AEGIS 9-layer framework — full technical reference |
| `docs/BE-08-SEO-EDGE.md` | Edge Worker architecture, KV cache, GEO routing, E-E-A-T injection |
| `docs/BE-09-DEPLOYMENT.md` | Operations manual — backups, secret rotation, SSH tunnel access |
| `docs/BE-10-CHANGELOG.md` | Chronological architectural history |
| `docs/BE-11-GEO-AI.md` | Generative Engine Optimization — full GEO payload and routing reference |
| `SECRETS.md` | Secret management policy — vault locations, variable registry, rotation |

---

## License

This software is proprietary. See [LICENSE.md](LICENSE.md) for full terms.
All rights reserved by Amitsagar Kandpal (Treishvaam Group) © 2024–2026.
