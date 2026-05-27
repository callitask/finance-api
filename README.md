---
root: true
project: Treishvaam Finance API
stack: [Java 21, Spring Boot 3.4, Go, OpenResty, Docker, Python 3, Redis, MinIO, Cloudflare Workers]
entry_points: [FinanceApiApplication.java, auto_deploy.sh, docker-compose.yml]
status: Active
version: tfin-financeapi-Develop.0.0.0.7
---

# Treishvaam Finance API
## The Zero-Trust Multi-Tenant Engine

The Treishvaam Finance API is a **High-Performance Enterprise Backend** built with Spring Boot 3.4 and Java 21. It operates as the centralized intelligence engine for the entire Treishvaam Ecosystem, securely powering multiple decoupled Next.js frontend applications (Finance, Agro, Parent) from a single deployment.

Engineered for **Zero Latency**, **Decimal Precision**, and absolute **Zero Trust Security**, this system utilises **Java 21 Virtual Threads**, **Post-Quantum Cryptography (ML-DSA-87)**, **Byzantine Consensus Security (7-node BCSM)**, **Zero-Knowledge Proof Admin Auth**, and a **Hybrid SSG Architecture** to serve millions of indexable pages while autonomously neutralising AI-driven cyber threats.

---

## Branching Strategy

| Branch | Role | Deployment |
| :--- | :--- | :--- |
| `main` | Production | Protected — stable releases only |
| `staging` | Stable restore point | Feature-complete golden copy |
| `develop` | Active development | **Push here daily.** Auto-deployed by Watchdog |

**NEVER push directly to `main`.** All code flows through `develop` → server Watchdog → Docker rolling restart.

---

## Core Technology Stack

| Technology | Role | Version |
| :--- | :--- | :--- |
| Java 21 (Temurin) | Primary runtime — Virtual Threads | LTS |
| Spring Boot | Application framework | 3.4.0 |
| Go 1.22+ | AEGIS ZKP microservice (`aegis-zkp-service`) | 1.22+ |
| OpenResty | Reverse proxy + Lua WAF (JA3 fingerprinting) | Alpine |
| MariaDB | Primary relational database (TDE encrypted) | 10.6 |
| Redis | Caching + AEGIS temporal path registry | 7 Alpine |
| Elasticsearch | Full-text search engine | 8.17.0 |
| MinIO | S3-compatible object storage | Latest |
| RabbitMQ | Async event bus + threat telemetry | 3.12 Management |
| Keycloak | Identity & Access Management (SSO) | 25.0.0 |
| BouncyCastle | Post-Quantum Cryptography (PQC) | 1.78.1 (bcpkix-jdk18on) |
| Infisical | Zero-Trust secret management | Cloud |
| Cloudflare | Edge Workers, KV, WAF, Tunnel | Workers + Pages |
| Python 3 | Market data processing bridge | 3.x |

---

## AEGIS — 9-Layer Adaptive Security Framework

AEGIS (Adaptive Entropic Guardian Intelligence System) is the proprietary defence framework built into every request path:

| Layer | Name | Description |
| :--- | :--- | :--- |
| L0-HEA | Hardware Entropy Anchoring | 4096-bit SHA3-512 entropy pool from `/dev/urandom`, refreshed every 30s via Virtual Threads |
| L1-PQCf | Post-Quantum Cryptography | ML-DSA-87 (Dilithium5) JWT signing via BouncyCastle 1.78.1. Argon2id password hashing |
| L2-PPO | Polymorphic Protocol Obfuscation | Daily rotating temporal API path manifests (Moving Target Defence) pushed to Redis + Cloudflare KV |
| L3-ZKA | Zero-Knowledge Authentication | Admin endpoints require Schnorr-over-Lattice ZK proofs verified by isolated Go microservice |
| L4-ADA | Adversarial Deception Architecture | Honeypots, poisoned corpora, 1 byte/sec Virtual Thread tarpits, RabbitMQ threat telemetry |
| L5-BIE | Behavioural Intelligence Engine | Shannon Entropy request scoring + client-side biometric hashing (WebCrypto SHA3-256, GDPR compliant) |
| L6-MTD | Moving Target Defence Orchestration | Real-time Cloudflare KV threat sync via `CloudflareEdgeSyncService` |
| L7-CMCS | Chaos Mirror Counter-Attack | SHA3-256 PoW challenges, dynamic delay tarpits |
| L8-BCSM | Byzantine Consensus Security Mesh | 7 independent validators, parallel Virtual Thread execution, 100ms hard timeout, 2-fault tolerance |

**Critical constraint:** `liboqs-java` is permanently excluded — it breaks the Maven CI/CD pipeline. BouncyCastle is the sole PQC provider.

---

## Key Architecture Features

### Multi-Tenant Isolation
One backend powers Finance (`treishvaamfinance.com`), Agro (`treishvaamagro.com`), and Parent (`treishvaamgroup.com`). Each Cloudflare Edge Worker injects `X-Tenant-ID` into every proxied request. `TenantInterceptor` locks all DB queries, services, and MDC logging to the specific tenant. Zero cross-brand data leakage is possible.

### Generative Engine Optimization (GEO)
AI crawlers (GPTBot, ClaudeBot, DeepSeek, PerplexityBot, and 20+ others) are intercepted at the Cloudflare Edge and served HMAC-signed semantic Markdown payloads (`/llms.txt`, `/ai-feed.md`, `/ontology.json`) directly from KV — React rendering is bypassed entirely.

### Hybrid SSG Architecture
On post publication, `HtmlMaterializerService` generates static HTML (injected with JSON-LD + Open Graph) and uploads to MinIO. The Edge Worker can serve this as a last-resort fallback ensuring 100% SEO uptime even during backend downtime.

### High-Performance I/O
- **Virtual Threads** throughout — BCSM (7 parallel validators), tarpits, image processing, KV sync, market data
- **Streaming uploads** — files streamed to temp disk, never loaded into RAM (prevents OOM under heavy load)
- **Static asset offloading** — OpenResty serves `/api/uploads/**` directly from the MinIO volume mount, Java never blocked by file serving

### Financial Precision
`BigDecimal` (Java) and `decimal.Decimal` with 28-digit context (Python) throughout. No floating-point errors on monetary values.

---

## Package Structure

```
com.treishvaam.financeapi
├── security/aegis/          ← AEGIS framework (BCSM, AEL, BIE, deception, crypto, MTD)
│   ├── bcsm/                ← Byzantine Consensus + 7 validators + Merkle audit log
│   ├── crypto/              ← L0-HEA entropy, L1-PQC JWT, L7-CMCS chaos mirror
│   ├── ael/                 ← AEGIS Expression Language (ANTLR4 grammar + loader)
│   └── mtd/                 ← Moving Target Defence, threat consumer, Cloudflare KV sync
├── marketdata/              ← Strategy Pattern providers (AlphaVantage, Finnhub, FMP, Yahoo)
├── service/                 ← CMS, Sitemap (multi-tenant), GEO payloads, HTML materializer
├── analytics/               ← Native RUM + audience visit tracking
├── apistatus/               ← External API health diagnostics
├── config/tenant/           ← TenantInterceptor + TenantContext (ThreadLocal)
├── controller/              ← REST endpoints (Blog, Market, GEO, Sitemap, Admin, Analytics)
├── search/                  ← Elasticsearch full-text search (PostDocument, PostSearchRepository)
└── aegis/zkp-service/       ← Isolated Go gRPC microservice (Schnorr-over-Lattice ZKP)
```

---

## CI/CD — Dual Engine

### Engine A — GitHub Actions (`deploy.yml`)
Triggers on push to `develop`, `staging`, `main`:
1. Java 21 setup + Maven cache
2. **Gitleaks** secret scan — blocks pipeline on any credential detection
3. **GPG commit verification** (`git verify-commit HEAD`) — unsigned commits rejected
4. `mvn test` → `mvn clean package` → `mvn spotless:check`
5. SCP WAR artifact to Ubuntu VM
6. Signal Watchdog

**OWASP Dependency Check** runs in a separate, isolated cron job — never blocks the synchronous deploy pipeline.

### Engine B — Watchdog (`scripts/auto_deploy.sh`)
Runs on the Ubuntu VM:
1. Compares branch timestamps — most recent commit wins
2. Pulls non-compiled config changes (Nginx/Lua, Python scripts)
3. Executes **Flash & Wipe** secret injection via Infisical
4. `docker compose up -d` — zero-downtime rolling restart
5. OS cache flush before JVM startup

**NEVER SSH into the server to restart containers manually.** All deployments flow exclusively through `git push` to `develop`.

---

## Local Development

### Prerequisites
- JDK 21 (Temurin)
- Go 1.22+ (ZKP microservice)
- Docker & Docker Compose v2
- Maven 3.9+
- Infisical CLI v0.154+
- GnuPG (for GPG commit signing)

### GPG Commit Signing Setup (Required)
```powershell
gpg --full-generate-key
git config --global commit.gpgsign true
git config --global user.signingkey <YOUR_KEY_ID>
```

### First Run
```powershell
# 1. Copy env template
copy .env.dev.example .env

# 2. Inject secrets (dev environment)
infisical login --method=universal-auth
infisical run --env dev -- docker compose up -d

# 3. Build
mvn clean package -DskipTests=false
```

### Deployment (from Windows host — Backend)
```powershell
mvn spotless:apply
git checkout develop
git add .
git commit -m "feat: description"
git push origin develop
```

---

## Documentation Index

| File | Contents |
| :--- | :--- |
| `docs/BE-00-INDEX.md` | Master documentation index |
| `docs/BE-01-ARCHITECTURE.md` | System architecture, Docker topology, data flow |
| `docs/BE-02-CORE.md` | Spring Boot core, AEGIS filter chain, multi-tenancy, Virtual Threads |
| `docs/BE-03-API.md` | REST API reference — all endpoints, roles, contracts |
| `docs/BE-04-SERVICES.md` | Business logic — Market Data, Blog CMS, Sitemap, GEO |
| `docs/BE-05-DATABASE.md` | Database schema, Liquibase migrations (V1–V46), PII encryption |
| `docs/BE-06-INFRA-DEVOPS.md` | Docker Compose full reference, OpenResty/Lua, scripts, CI/CD, observability |
| `docs/BE-07-SECURITY.md` | AEGIS 9-layer framework — full technical reference |
| `docs/BE-08-SEO-EDGE.md` | Edge Worker architecture, KV cache, GEO routing, E-E-A-T injection |
| `docs/BE-09-DEPLOYMENT.md` | Operations manual — backups, secret rotation, SSH tunnel access |
| `docs/BE-10-CHANGELOG.md` | Chronological architectural history |
| `docs/BE-11-GEO-AI.md` | Generative Engine Optimization — full GEO payload and routing reference |
| `SECRETS.md` | Secret management policy — vault locations, variable registry, rotation policy |

---

## License
Proprietary software. All rights reserved by Treishvaam Group.