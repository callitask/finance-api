# BE-02 — Backend Core Architecture

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized)
**Last Verified:** 2026-05-29 — All claims verified against `SecurityConfig.java`, `AegisBcsm.java`, `AegisEdgeValidationFilter.java`, `AegisMainFilter.java`, `AegisZkpAdminFilter.java`, `AegisPqcJwtService.java`, `AegisPqcKeyStore.java`, `AegisEntropyManager.java`, `AegisBehavioralEngine.java`, `TarpitManager.java`, `RabbitMQAttackPublisher.java`, `CloudflareEdgeSyncService.java`, `AegisChaosMirror.java`, `PowChallengeIssuer.java`, `TenantInterceptor.java`, `ContentIntegrityService.java`, `AegisQueryInterceptor.java`, `MerkleAuditLogService.java`, `application-prod.properties`

---

## 1. Runtime Environment

| Attribute | Value |
| :--- | :--- |
| **Language** | Java 21 LTS (Temurin Distribution) |
| **Framework** | Spring Boot 3.4.0 |
| **Build System** | Maven 3.9+ |
| **Container** | Docker (multi-stage build — compiled WAR served from OpenJDK 21) |
| **Concurrency Model** | Java 21 Virtual Threads (Project Loom) throughout |
| **Entry Point** | `FinanceApiApplication.java` (extends `SpringBootServletInitializer`) |

---

## 2. Security Architecture — AEGIS + Zero-Trust

The security layer is a **multi-stage hybrid model** integrating the AEGIS Adaptive Framework at the outermost perimeter, a cryptographic origin-enforcement gate, and the OAuth2 Resource Server for identity delegation via Keycloak.

### 2.1. Filter Execution Order

Filters execute in strict order before any business logic:

```
Incoming Request (already past OpenResty WAF + JA3 fingerprint)
      │
      ▼
[1] AegisEdgeValidationFilter         ← Registered at HIGHEST_PRECEDENCE via FilterRegistrationBean
      │  Validates X-Aegis-Edge-Signature (HMAC-SHA-512, 300s TTL replay prevention)
      │  Returns 403 immediately if signature missing, invalid, or expired
      │  Constant-time comparison (MessageDigest.isEqual()) — timing attack prevention
      │  Exempts: 127.0.0.1, /actuator/health, /actuator/prometheus
      ▼
[2] AegisDeceptionFilter              ← Ordered.HIGHEST_PRECEDENCE (before AegisMainFilter)
      │  Pre-screens requests for known deception triggers
      ▼
[3] AegisMainFilter                   ← @Order(Ordered.HIGHEST_PRECEDENCE + 1)
      │  Invokes L8-BCSM (Byzantine Consensus) — 7 validators in parallel via Virtual Threads
      │  Invokes L2-PPO (Temporal Path verification / translation)
      │  Routes to L4-ADA (Deception) or L7-CMCS (Tarpit) based on consensus result
      ▼
[4] AegisZkpAdminFilter               ← Injected before UsernamePasswordAuthenticationFilter
      │  Intercepts /api/v1/admin/** — requires X-AEGIS-ZKP-Proof header
      │  Issues gRPC call to aegis-zkp-service (Go, port 9090, Schnorr-over-Lattice)
      ▼
[5] Spring Security Filter Chain      ← SecurityConfig.java (OAuth2 Resource Server)
      │  CORS (FilterRegistrationBean at HIGHEST_PRECEDENCE — handles OPTIONS preflights)
      │  InputSanitizationFilter (SQL/NoSQL injection defense)
      │  RateLimitingFilter (Bucket4j + Redis)
      │  JWT validation (Keycloak JWK Set, cached)
      │  KeycloakRealmRoleConverter → Spring Security Authorities
      ▼
Business Logic
```

### 2.2. AegisEdgeValidationFilter — Cryptographic Origin Enforcement

- Validates `X-Aegis-Edge-Signature` and `X-Aegis-Edge-Timestamp` on every inbound request
- Uses **constant-time comparison** (`MessageDigest.isEqual()`) to prevent timing attacks
- Enforces a **300-second TTL** on the timestamp to block replay attacks
- Signing secret (`AEGIS_EDGE_SECRET`) injected via environment variable — never hardcoded
- Explicitly exempts localhost/internal monitoring traffic (Prometheus scrape, Grafana health checks)
- Built using BouncyCastle provider to maintain PQC dependency chain integrity

### 2.3. L8-BCSM — Byzantine Consensus Security Mesh

- **Class:** `AegisBcsm.java`
- **Execution:** 7 independent validators run in parallel via `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture`
- **Timeout:** Strict **100ms** hard ceiling — timed-out validators are treated as abstain votes
- **Consensus Logic:** 3 BLOCK votes → Block request | 2 EMERGENCY votes → Emergency response
- **Byzantine Fault Tolerance:** Tolerates up to 2 compromised or timed-out validators without disrupting the quorum
- **Migration Note:** Migrated from preview `StructuredTaskScope` API to stable `Executors.newVirtualThreadPerTaskExecutor()` for Java 21 GA compatibility

| Validator Class | What It Checks |
| :--- | :--- |
| `RateLimitValidator` | Bucket4j token bucket state for IP |
| `JwtValidator` | Token presence, expiry, issuer, audience |
| `BehavioralValidator` | Shannon Entropy score from `AegisBehavioralEngine` |
| `TemporalPathValidator` | Whether the path matches the current daily manifest |
| `EntropyHealthValidator` | Hardware entropy pool health (`AegisEntropyManager`) |
| `HidsIntegrityValidator` | Wazuh HIDS signals from RabbitMQ threat events |
| `DeceptionValidator` | Whether IP/JA3 is in active deception corpus |

All results aggregated into `SecurityDecision` enum: `ALLOW`, `BLOCK`, `DECEPTION`, `EMERGENCY`, `TARPIT`.

### 2.4. L3-ZKA — Zero-Knowledge Admin Authentication

- Admin endpoints (`/api/v1/admin/**`) require **Zero-Knowledge Proofs** instead of password transmission
- **Filter:** `AegisZkpAdminFilter` extracts `X-AEGIS-ZKP-Proof` and issues a gRPC call to `aegis-zkp-service`
- **Microservice:** Compiled Go binary in a distroless/scratch container, internal port 9090 only
- **Algorithm:** Schnorr-over-Lattice (Fiat-Shamir with Aborts for ML-DSA compatibility), implemented via gnark circuit (`circuit/schnorr_lattice.go`)
- **Memory:** Strictly bounded to **256MB** via Docker `deploy.resources` — never increase this limit
- **Resilience:** `AegisZkpServiceClient` wraps gRPC calls with Resilience4j Circuit Breaker

### 2.5. L1-PQCf — Post-Quantum Cryptographic Foundation

- **Library:** Pure-Java BouncyCastle `bcpkix-jdk18on` 1.78.1 + `bcprov-jdk18on` 1.78.1
- **`liboqs-java` is permanently excluded** — it breaks the Maven CI/CD pipeline. BouncyCastle is the sole PQC provider. This is an absolute architectural constraint — do not re-introduce.
- **JWT Signing:** `AegisPqcJwtService` signs JWTs using **ML-DSA-87 (Dilithium5)** — NIST Level 5
- **Key Management:** `AegisPqcKeyStore` manages the ML-DSA key pair lifecycle
- **Manual JWT construction** is used (no JJWT/Nimbus) — neither officially supports FIPS 204 ML-DSA
- **Password Hashing:** Argon2id via `argon2-jvm 2.11` — memory-hard KDF defeating GPU/ASIC attacks

### 2.6. L0-HEA — Hardware Entropy Anchoring

- **Class:** `AegisEntropyManager`
- Seeds all PQC operations with true entropy from: `/dev/urandom`, `/dev/random`, JVM timing jitter
- **Pool:** 4096-bit SHA3-512 folded entropy pool, refreshed every 30 seconds via Java 21 Virtual Threads (non-blocking `@Async`)
- **Docker:** Hardware entropy devices (`/dev/random`, `/dev/urandom`) mapped into backend container
- Future: `/dev/tpm0` support planned when physical TPM 2.0 is added to the server

### 2.7. L5-BIE — Behavioral Intelligence Engine

- **Backend:** `AegisBehavioralEngine` — records request timing jitter and endpoint access patterns per tenant; calculates **Shannon Entropy** via `ShannonEntropyCalculator`
- **Frontend:** `src/lib/aegis-biometrics.ts` — passive `mousemove`, `keydown`, `scroll` listeners; vectors **hashed client-side via WebCrypto SHA3-256** before transmission — zero raw PII leaves the browser (GDPR compliant)
- **Profile:** `SessionBehaviorProfile` stores per-session state scoped via `TenantContext`
- **Endpoint:** `POST /api/v1/aegis/telemetry` — public, accepts anonymous hashed payloads
- **SSR Safety:** `AegisTelemetry.tsx` is `'use client'` only — never runs in SSR context

### 2.8. L4-ADA — Adversarial Deception Architecture

- **Classes:** `AegisDeceptionEngine`, `PoisonCorpusGenerator`, `CanaryTokenService`, `TarpitManager`, `RabbitMQAttackPublisher`
- **Strategies:** `PLAUSIBLE_FAKE` (subtly wrong data), `SLOW_LEAK` (1 byte/sec tarpit), `RECURSIVE_LOOP` (redirect maze)
- **Tarpit:** `TarpitManager` uses Java 21 Virtual Threads to hold attacker connections open (1 byte/sec) without exhausting the Tomcat thread pool
- **Telemetry:** `RabbitMQAttackPublisher` fires attacker IP, JA3 hash, target path, trigger reason to `aegis.threat.exchange` RabbitMQ exchange (fire-and-forget, never blocks the request thread)
- **KV Sync:** `CloudflareEdgeSyncService` consumes RabbitMQ threat events and pushes malicious IPs/JA3 hashes to Cloudflare KV in real time (86400s TTL; `localBlockCache` deduplication for free-tier quota protection)
- **Cache-Control:** All deception responses carry `Cache-Control: no-store`

### 2.9. L2-PPO — Polymorphic Protocol Obfuscation (Moving Target Defense)

- **Classes:** `AegisTemporalPathManager`, `EndpointManifest`, `AegisMtdController`, `AegisResponseMutator`
- Generates a **daily temporal path manifest** (e.g., `/api/v1/posts` → `/api/v1/data-node/2b9c`) signed by ML-DSA-87, pushed to Redis
- `AegisResponseMutator` randomizes `X-Powered-By` and injects timing jitter to defeat AI fuzzers
- Emergency rotations triggered via RabbitMQ events through `AegisThreatConsumer`
- Edge Worker reads `aegis:mtd:manifest` from KV to perform path translation before forwarding

### 2.10. L7-CMCS — Chaos Mirror Counter-Attack System

- **Classes:** `AegisChaosMirror`, `TarpitManager`, `PowChallengeIssuer`
- Issues CPU-exhausting **SHA3-256 Proof-of-Work challenges** to credential stuffers and scrapers
- `TarpitManager.tarpitConnection(request, response, delayMs)` supports dynamic delay based on behavioral risk score from BCSM

### 2.11. Spring Security Filter Chain — Authorization Rules (`SecurityConfig.java`)

All rules are **explicit and ordered**. New endpoints must be declared before `.anyRequest().authenticated()`.

| Method/Path | Requirement |
| :--- | :--- |
| `OPTIONS /**` | permitAll (CORS preflight) |
| `/actuator/health` | permitAll |
| `/api/v1/health/**`, `/api/v1/monitoring/ingest` | permitAll |
| `GET /api/v1/posts/**`, `/categories/**`, `/market/**`, `/news/**`, `/search/**` | permitAll |
| `POST /api/v1/market/quotes/batch`, `/api/v1/contact/**` | permitAll |
| `POST /api/v1/analytics/**` | permitAll (Faro/beacon anonymous ingestion) |
| `POST /api/v1/aegis/telemetry` | permitAll (L5-BIE biometric hash ingestion) |
| `GET /api/public/geo/**`, `/llms.txt`, `/ai-feed.md` | permitAll (GEO crawler access) |
| `POST /api/v1/posts/draft`, `PUT /api/v1/posts/draft/**` | authenticated |
| `/api/v1/auth/**` | authenticated |
| `PUT /api/v1/posts/**`, `DELETE /api/v1/posts/bulk` | EDITOR / PUBLISHER / ADMIN |
| `POST /api/v1/posts`, `DELETE /api/v1/posts/**`, `/api/v1/files/upload` | PUBLISHER / ADMIN |
| `/api/v1/admin/**`, `/api/v1/status/**`, `/actuator/**` (except health) | ADMIN |
| Everything else | authenticated |

**CORS:** Managed via a globally registered `FilterRegistrationBean<CorsFilter>` at `Ordered.HIGHEST_PRECEDENCE`. Spring Security's native `.cors()` is disabled. Explicit allowed headers prevent custom injection header attacks.

**Role Mapping:** `KeycloakRealmRoleConverter` extracts roles from the `realm_access.roles` JWT claim and prefixes them with `ROLE_` → `SimpleGrantedAuthority`.

---

## 3. Multi-Tenancy Architecture

### 3.1. Tenant Context Lifecycle

1. Edge Worker injects `X-Tenant-ID: finance` (or `agro`) into every proxied request
2. `TenantInterceptor` sanitizes against a whitelist of recognized tenants
3. `TenantContext.setTenantId(value)` stores in a `ThreadLocal` variable (thread-safe)
4. MDC injection: `tenantId` tagged in all Loki logs for per-tenant filtering
5. Service layers filter queries with `WHERE tenant_id = ?`
6. `TenantContext.clear()` called in `afterCompletion` — prevents ThreadLocal leakage

### 3.2. Contextual Routing — SitemapService

`SitemapService` dynamically alters its response based on `TenantContext`:
- `finance` tenant: generates multi-page, paginated blog + market XML sitemaps; GEO payload URLs included at priority 1.0
- `agro` tenant: returns a static, enterprise-grade E-E-A-T XML payload for its fixed page set

### 3.3. Background Task Tenant Isolation

All `@Scheduled` tasks and `@Async` methods must explicitly call `TenantContext.setTenantId("finance")` at their entry points. This prevents cross-tenant data contamination and memory leaks during application restarts.

---

## 4. Concurrency — Java 21 Virtual Threads

| Use Case | Mechanism |
| :--- | :--- |
| L8-BCSM (7 validators in parallel) | `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture` with 100ms timeout |
| L0-HEA entropy pool refresh | `@Async` on 30-second schedule |
| L4-ADA tarpit connections | `TarpitManager` — offloads to Virtual Thread immediately (never blocks Tomcat pool) |
| Cloudflare KV threat sync | Java 21 `HttpClient` via Virtual Threads |
| Image processing (resize, WebP) | Virtual Thread executor in `ImageService` |
| HTML materialization on publish | `@Async` in `HtmlMaterializerService` |
| Market data fetch (Python bridge) | `ProcessBuilder` + Virtual Thread pool in `MarketDataService` |

---

## 5. Database Integrity — AegisQueryInterceptor

- Intercepts **every JDBC query** before execution
- Computes **HMAC-SHA3-256** of the query string using `AEGIS_DB_SIGNING_KEY` (environment variable)
- Logs signature for tamper-detection audit trail — replayed or injected queries produce signature mismatch
- Creates a **Zero-Trust DB Driver Boundary** complementary to application-layer SQL injection defenses

---

## 6. PII Encryption at Rest

All PII fields encrypted using **domain-specific AES-256-GCM JPA Attribute Converters**:

| Converter | Field | Entity | Key Variable |
| :--- | :--- | :--- | :--- |
| `UserEmailConverter` | `email` | `User` | `DOMAIN_EMAIL_ENCRYPTION_KEY` |
| `ContactEmailConverter` | `email` | `ContactMessage` | `DOMAIN_CONTACT_ENCRYPTION_KEY` |
| `ContactMessageConverter` | `message` | `ContactMessage` | `DOMAIN_CONTACT_MSG_ENCRYPTION_KEY` |
| `AuditIpConverter` | `ip_address` | `AuditLog` | `DOMAIN_AUDIT_IP_ENCRYPTION_KEY` |

All converters support **`v1:` prefix versioning** for future key rotation without full data re-encryption.

---

## 7. Content Integrity — ContentIntegrityService

- Computes **HMAC-SHA256** digital signatures on blog post content using `CONTENT_SIGNING_KEY`
- Signature stored in `content_signature` column (V45 migration)
- Signature mismatches trigger critical security alerts in the observability stack

---

## 8. Audit Integrity — MerkleAuditLogService

**Previously undocumented — verified in codebase.**

- `MerkleAuditLogService` maintains a Merkle tree hash chain over all `audit_log` entries
- Each new audit record's hash is chained to the previous entry's hash — creates a tamper-evident cryptographic ledger
- Any retroactive modification of an audit entry breaks the chain, detectable on validation
- Works in conjunction with `AuditIpConverter` (AES-256-GCM IP encryption) to provide both confidentiality and integrity on the audit trail

---

## 9. Resilience Configuration

| Circuit Breaker | Timeout | Threshold | Purpose |
| :--- | :--- | :--- | :--- |
| `pythonScript` | 120 seconds | 50% failure rate, window=10 | Python market data bridge |
| ZKP gRPC client | Via Resilience4j | Standard | Protects admin flow if ZKP service degrades |

---

## 10. Market Data Providers (Complete List)

Verified from codebase — `BreezeProvider` was previously undocumented:

| Provider Class | Data Source | Primary Use |
| :--- | :--- | :--- |
| `AlphaVantageProvider` | Alpha Vantage | Forex + technical indicators |
| `FinnhubProvider` | Finnhub | Real-time stock quotes + news |
| `FmpProvider` | Financial Modeling Prep | Bulk market movers (gainers/losers) |
| `YahooHistoricalProvider` | Yahoo Finance | Long-term historical candle data |
| `BreezeProvider` | ICICI Direct Breeze API | Indian market data (NSE/BSE) |

**Factory:** `MarketDataFactory` selects the appropriate provider based on symbol or region context.

---

## 11. Key Application Properties (Non-Sensitive, Code-Verified)

```properties
spring.application.name=finance-api
spring.jackson.serialization.write-dates-as-timestamps=false
spring.cloud.vault.enabled=false          # Vault disabled — startup delay prevention
springdoc.api-docs.enabled=false          # Hidden in prod — reconnaissance prevention
springdoc.swagger-ui.enabled=false
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
management.endpoints.web.exposure.include=health,prometheus   # Never expose env/heapdump
management.endpoint.health.show-details=when-authorized
management.endpoint.health.roles=ROLE_ADMIN
```

---

## 12. Python Bridge Security (Market Data Updater)

The backend invokes `scripts/market_data_updater.py` via `ProcessBuilder` for heavy financial computations. Database credentials are **never passed as CLI arguments**. They are injected securely into the Python process environment via `ProcessBuilder.environment()` — credentials never appear in `ps aux` output.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **VERIFIED + UPDATED (2026-05-29 — Enterprise Documentation Generation):**
  - All AEGIS layer descriptions verified against actual implementation files.
  - **ADDED:** `MerkleAuditLogService` documentation (Section 8) — this class existed in the codebase but was completely absent from all documentation files. It provides Merkle-chain tamper-evidence on the `audit_log` table, complementing the AES-256-GCM IP encryption from `AuditIpConverter`.
  - **ADDED:** `BreezeProvider` (ICICI Direct Breeze API) to market data providers table (Section 10) — was absent from previous docs. Confirmed in `src/main/java/.../provider/BreezeProvider.java`.
  - **CONFIRMED:** `StructuredTaskScope` → `Executors.newVirtualThreadPerTaskExecutor()` migration in BCSM (Section 2.3) — verified in actual `AegisBcsm.java` implementation.
  - **CONFIRMED:** All 7 BCSM validator class names verified against actual `AegisBcsm.java` implementation.
  - No architectural claims removed — all existing content verified as accurate.