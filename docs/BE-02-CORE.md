# 02 — Backend Core Architecture

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Classification:** Internal Reference (Sanitized)

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
      │  Exempts: 127.0.0.1, Prometheus/Grafana internal health paths
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

- Validates `X-Aegis-Edge-Signature` and `X-Aegis-Edge-Timestamp` headers on every inbound request
- Uses **constant-time comparison** (`MessageDigest.isEqual()`) to prevent timing attacks
- Enforces a **300-second TTL** on the timestamp to block replay attacks
- The signing secret (`aegis.edge.secret`) is injected via environment variable — never hardcoded
- Explicitly exempts localhost/internal monitoring traffic (Prometheus scrape, Grafana health checks)
- Built using BouncyCastle provider to maintain PQC dependency chain integrity

### 2.3. L8-BCSM — Byzantine Consensus Security Mesh

- **Class:** `AegisBcsm.java`
- **Execution:** 7 independent validators run in parallel via `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture`
- **Timeout:** Strict **100ms** hard ceiling — if validators time out, they are treated as abstain votes
- **Consensus Logic:** 3 BLOCK votes → Block request | 2 EMERGENCY votes → Emergency response
- **Byzantine Fault Tolerance:** Tolerates up to 2 compromised or timed-out validators without disrupting the quorum

| Validator | Class | What It Checks |
| :--- | :--- | :--- |
| Rate Limit | `RateLimitValidator` | Bucket4j token bucket state for IP |
| JWT | `JwtValidator` | Token presence, expiry, issuer |
| Behavioral | `BehavioralValidator` | Shannon Entropy score from `AegisBehavioralEngine` |
| Temporal Path | `TemporalPathValidator` | Whether the path matches the current daily manifest |
| Entropy Health | `EntropyHealthValidator` | Hardware entropy pool health (`AegisEntropyManager`) |
| HIDS | `HidsIntegrityValidator` | Wazuh agent signals from RabbitMQ threat events |
| Deception | `DeceptionValidator` | Whether IP/JA3 is in active deception corpus |

All results are aggregated into a `SecurityDecision` enum (ALLOW, BLOCK, DECEPTION, EMERGENCY, TARPIT).

### 2.4. L3-ZKA — Zero-Knowledge Admin Authentication

- Admin endpoints (`/api/v1/admin/**`) require **Zero-Knowledge Proofs** instead of password transmission
- **Filter:** `AegisZkpAdminFilter` extracts `X-AEGIS-ZKP-Proof` and issues a gRPC call to `aegis-zkp-service`
- **Microservice:** Compiled Go binary in a distroless/scratch container, internal port 9090 only
- **Algorithm:** Schnorr-over-Lattice (Fiat-Shamir with Aborts for ML-DSA compatibility), implemented via gnark circuit (`circuit/schnorr_lattice.go`)
- **Memory:** Strictly bounded to **256MB** via Docker `deploy.resources` to prevent VirtualBox host starvation
- **Resilience:** `AegisZkpServiceClient` wraps gRPC calls with a Resilience4j Circuit Breaker

### 2.5. L1-PQCf — Post-Quantum Cryptographic Foundation

- **Library:** Pure-Java BouncyCastle `bcpkix-jdk18on` 1.78.1 + `bcprov-jdk18on` 1.78.1
- **NOTE:** `liboqs-java` is explicitly excluded — it breaks the Maven CI/CD pipeline. BouncyCastle is the only PQC provider
- **JWT Signing:** `AegisPqcJwtService` signs JWTs using **ML-DSA-87 (Dilithium5)** — NIST Level 5 quantum resistance
- **Key Management:** `AegisPqcKeyStore` manages the ML-DSA key pair lifecycle
- **Manual JWT construction** is used (no JJWT/Nimbus) because neither library officially supports FIPS 204 ML-DSA yet
- **Password Hashing:** Argon2id (memory-hard KDF) via `argon2-jvm 2.11` to defeat GPU/ASIC attacks

### 2.6. L0-HEA — Hardware Entropy Anchoring

- **Class:** `AegisEntropyManager`
- Seeds all PQC operations with true entropy from hardware sources: `/dev/urandom`, `/dev/random`, JVM timing jitter
- **Pool:** 4096-bit SHA3-512 folded entropy pool, refreshed every 30 seconds via Java 21 Virtual Threads (non-blocking)
- **Docker:** Hardware entropy devices (`/dev/random`, `/dev/urandom`) are mapped into the backend container
- Future: `/dev/tpm0` support planned when physical TPM 2.0 is added to the server

### 2.7. L5-BIE — Behavioral Intelligence Engine

- **Backend:** `AegisBehavioralEngine` — records nanosecond request timing jitter and endpoint access patterns per tenant; calculates **Shannon Entropy** via `ShannonEntropyCalculator`
- **Frontend:** `src/lib/aegis-biometrics.ts` — passive `mousemove`, `keydown`, `scroll` listeners; biometric vectors are **hashed client-side using WebCrypto SHA3-256** before transmission (no raw keystrokes or PII leave the browser — GDPR compliant)
- **Profile:** `SessionBehaviorProfile` stores per-session state scoped via `TenantContext`
- **Transmission endpoint:** `POST /api/v1/aegis/telemetry` (public, accepts anonymous hashed payloads)

### 2.8. L4-ADA — Adversarial Deception Architecture

- **Classes:** `AegisDeceptionEngine`, `PoisonCorpusGenerator`, `CanaryTokenService`
- **Strategies:** PLAUSIBLE_FAKE (subtly wrong data), SLOW_LEAK (byte-trickle tarpit), RECURSIVE_LOOP (redirect maze)
- **Tarpit:** `TarpitManager` uses Java 21 Virtual Threads to hold attacker connections open (1 byte/second) without exhausting the Tomcat thread pool
- **Telemetry:** `RabbitMQAttackPublisher` fires attacker IP, JA3 hash, target path, and trigger reason to `aegis.threat.exchange` RabbitMQ exchange (fire-and-forget, never blocks the request thread)
- **KV Sync:** `CloudflareEdgeSyncService` consumes RabbitMQ threat events and pushes malicious IPs/JA3 hashes to Cloudflare KV in real time (Java 21 HttpClient + Virtual Threads; 86400s TTL enforced; deduplication via in-memory `localBlockCache` to protect free-tier write quotas)
- **Cache-Control:** All deception responses carry `Cache-Control: no-store` to guarantee Cloudflare never caches poisoned payloads

### 2.9. L2-PPO — Polymorphic Protocol Obfuscation (Moving Target Defense)

- **Classes:** `AegisTemporalPathManager`, `EndpointManifest`, `AegisMtdController`, `AegisResponseMutator`
- Generates a **daily temporal path manifest** (e.g., `/api/v1/posts` → `/api/v1/data-node/2b9c`) signed by ML-DSA-87 and pushed to Redis
- `AegisResponseMutator` randomizes `X-Powered-By` and injects timing jitter into responses to defeat AI fuzzers
- Emergency rotations triggered via RabbitMQ events through `AegisThreatConsumer`
- Edge Worker reads `aegis:mtd:manifest` from KV to perform path translation before forwarding requests

### 2.10. L7-CMCS — Chaos Mirror Counter-Attack System

- **Classes:** `AegisChaosMirror`, `TarpitManager`, `PowChallengeIssuer`
- Issues CPU-exhausting SHA3-256 Proof-of-Work challenges to credential stuffers
- `TarpitManager.tarpitConnection(request, response, delayMs)` supports dynamic delay based on behavioral score

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

**CORS:** Managed via a globally registered `FilterRegistrationBean<CorsFilter>` at `Ordered.HIGHEST_PRECEDENCE`. Spring Security's native `.cors()` is disabled to let this global filter handle preflights at the very start of the Servlet chain. Explicit allowed headers prevent custom injection header attacks.

**Role Mapping:** `KeycloakRealmRoleConverter` extracts roles from the `realm_access.roles` JWT claim and prefixes them with `ROLE_` → `SimpleGrantedAuthority`.

---

## 3. Multi-Tenancy Architecture

### 3.1. Tenant Context Lifecycle

1. Edge Worker injects `X-Tenant-ID: finance` (or `agro`) into every proxied request
2. `TenantInterceptor` sanitizes the value against a whitelist of recognized tenants
3. `TenantContext.setTenantId(value)` stores it in a `ThreadLocal` variable (thread-safe)
4. `TenantInterceptor` injects `tenantId` into the MDC (Mapped Diagnostic Context) — all Loki logs are tenant-tagged
5. Service layers filter queries with `WHERE tenant_id = ?`
6. `TenantContext.clear()` is called in the interceptor's `afterCompletion` to prevent leakage

### 3.2. Contextual Routing — SitemapService

`SitemapService` dynamically alters its response based on `TenantContext`:
- `finance` tenant: generates multi-page, paginated blog + market XML sitemaps, GEO payload URLs included at priority 1.0
- `agro` tenant: returns a static, enterprise-grade E-E-A-T XML payload for its fixed page set

### 3.3. Background Task Tenant Isolation

All `@Scheduled` tasks and `@Async` methods explicitly call `TenantContext.setTenantId("finance")` at their entry points. This prevents cross-tenant data contamination and memory leaks during application restarts (e.g., `MarketDataInitializer`).

---

## 4. Concurrency — Java 21 Virtual Threads

| Use Case | Mechanism |
| :--- | :--- |
| L8-BCSM (7 validators in parallel) | `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture` with 100ms timeout |
| L0-HEA entropy pool refresh | `@Async` on 30-second schedule |
| L4-ADA tarpit connections | `TarpitManager` — offloads to Virtual Thread immediately to avoid DoS against own Tomcat pool |
| Cloudflare KV threat sync | Java 21 `HttpClient` via Virtual Threads |
| Image processing (resize, WebP) | Virtual Thread executor in `ImageService` |
| HTML materialization on publish | `@Async` in `HtmlMaterializerService` |
| Market data fetch (Python bridge) | `ProcessBuilder` + Virtual Thread pool in `MarketDataService` |

---

## 5. Database Integrity — AegisQueryInterceptor

- Intercepts **every JDBC query** before execution
- Computes an **HMAC-SHA3-256** signature of the query string using `AEGIS_DB_SIGNING_KEY` (injected via environment variable)
- Logs the signature for tamper-detection — if the logged signature doesn't match a replayed query, it's flagged as injection
- This creates a **Zero-Trust DB Driver Boundary**

---

## 6. PII Encryption at Rest

All fields containing Personally Identifiable Information are encrypted using **domain-specific AES-256-GCM JPA converters**:

| Converter | Field | Entity |
| :--- | :--- | :--- |
| `UserEmailConverter` | `email` | `User` |
| `ContactEmailConverter` | `email` | `ContactMessage` |
| `ContactMessageConverter` | `message` | `ContactMessage` |
| `AuditIpConverter` | `ipAddress` | `AuditLog` |

All converters use the `v1:` prefix versioning scheme to support future key rotation without requiring data migration.

---

## 7. Content Integrity — ContentIntegrityService

- Computes **HMAC-SHA256** digital signatures on blog post content using `CONTENT_SIGNING_KEY` (environment variable)
- Signature is stored in the `content_signature` column (V45 migration)
- Signature mismatches trigger critical security alerts in the observability stack

---

## 8. Resilience Configuration

| Circuit Breaker | Timeout | Threshold | Purpose |
| :--- | :--- | :--- | :--- |
| `pythonScript` | 120 seconds | 50% failure rate, window=10 | Python market data bridge |
| ZKP gRPC client | Configured via Resilience4j | Standard | Protects admin flow if ZKP service degrades |

---

## 9. Key Application Properties (Non-Sensitive)

All sensitive values are injected via environment variables. The following are safe structural defaults:

```properties
spring.application.name=finance-api
spring.jackson.serialization.write-dates-as-timestamps=false
spring.cloud.vault.enabled=false
springdoc.api-docs.enabled=false        # Disabled globally; enabled only in dev profile
springdoc.swagger-ui.enabled=false
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

---

## 10. Market Data — Python Bridge Security

The backend invokes `scripts/market_data_updater.py` via `ProcessBuilder` for heavy financial computations. Database credentials are **never passed as CLI arguments**. They are injected securely into the Python process environment via `ProcessBuilder.environment()`.
