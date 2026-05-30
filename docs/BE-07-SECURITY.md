# BE-07 — AEGIS Security Framework
## Adaptive Entropic Guardian Intelligence System

**Version:** Active in Production (Phase 6 Complete)
**Classification:** Internal Architectural Reference (Sanitized — No Keys, No Secrets, No Internal Endpoints)
**Last Verified:** 2026-05-29 — All claims verified against actual security class implementations

---

## Overview

AEGIS is a **living adversarial security system** implemented across the Treishvaam Group Ecosystem. Unlike classical security, which is purely reactive, AEGIS is **Proactive, Adaptive, and Active**. It uses post-quantum cryptography, active deception, behavioral telemetry, Byzantine fault-tolerant consensus, and zero-knowledge proofs to neutralize both classical and AI-driven threats.

**Key execution principle:** All AEGIS logic executes exclusively via **Java 21 Virtual Threads** — ensuring that even the most CPU-intensive security decisions add zero latency to legitimate Tomcat HTTP worker threads.

**Critical constraint:** `liboqs-java` is **permanently excluded**. It breaks the Maven CI/CD pipeline. BouncyCastle 1.78.1 is the sole PQC provider. This is an immutable architectural constraint — do not re-introduce `liboqs-java` under any circumstances.

---

## Filter Execution Order (Verified from `SecurityConfig.java`)

```
Incoming HTTP Request
      │
      ▼ (registered as FilterRegistrationBean at HIGHEST_PRECEDENCE)
[1] AegisEdgeValidationFilter
      │  Validates X-Aegis-Edge-Signature (HMAC-SHA-512)
      │  Validates X-Aegis-Edge-Timestamp (300s TTL replay prevention)
      │  Constant-time comparison via MessageDigest.isEqual()
      │  Returns 403 immediately on invalid/missing/expired signature
      │  Exempts: 127.0.0.1, /actuator/health, /actuator/prometheus
      ▼ (Ordered.HIGHEST_PRECEDENCE — Spring Security chain)
[2] AegisDeceptionFilter
      │  Pre-screens for known deception trigger patterns
      │  L4-ADA coordination
      ▼ (Ordered.HIGHEST_PRECEDENCE + 1)
[3] AegisMainFilter
      │  Invokes L8-BCSM (7 validators in parallel, Virtual Threads, 100ms timeout)
      │  Invokes L2-PPO (Temporal Path verification / translation)
      │  Routes to L4-ADA (Deception) or L7-CMCS (Tarpit) based on SecurityDecision
      ▼ (before UsernamePasswordAuthenticationFilter)
[4] AegisZkpAdminFilter
      │  Intercepts /api/v1/admin/** only
      │  Extracts X-AEGIS-ZKP-Proof header
      │  Issues gRPC call to aegis-zkp-service (Go, port 9090)
      │  Resilience4j Circuit Breaker wraps gRPC calls
      ▼
[5] Spring Security Filter Chain (SecurityConfig.java)
      │  CORS (FilterRegistrationBean at HIGHEST_PRECEDENCE — OPTIONS preflights)
      │  InputSanitizationFilter (SQL/NoSQL injection defense)
      │  RateLimitingFilter (Bucket4j + Redis token buckets)
      │  JWT validation (Keycloak JWK Set, cached by Spring)
      │  KeycloakRealmRoleConverter → Spring Security Authorities
      ▼
Business Logic
```

---

## The 9 Layers of AEGIS

### L0: Hardware Entropy Anchoring (HEA)

**Goal:** Seed all cryptographic operations with true, unpredictable entropy from multiple hardware sources.

| Attribute | Detail |
| :--- | :--- |
| **Classes** | `AegisEntropyManager`, `EntropySource` (interface), `JitterEntropySource`, `UrandomEntropySource` |
| **Pool Size** | 4096-bit SHA3-512 folded entropy pool |
| **Sources** | `/dev/urandom`, `/dev/random`, JVM timing jitter (RDRAND/RDSEED equivalent) |
| **Refresh** | Every 30 seconds — fully asynchronous via `@Async` Virtual Thread |
| **Infrastructure** | Docker volume-maps `/dev/random` and `/dev/urandom` into the backend container |
| **Future** | `/dev/tpm0` integration planned for physical TPM 2.0 hardware |

**Why:** SHA3-512 is used specifically because Grover's quantum algorithm halves symmetric key strength — a 512-bit pool maintains effectively 256-bit quantum-resistant entropy.

---

### L1: Post-Quantum Cryptographic Foundation (PQCf)

**Goal:** Full immunity to Shor's algorithm (asymmetric key breaks) and Grover's algorithm (symmetric key weakening).

| Attribute | Detail |
| :--- | :--- |
| **Classes** | `AegisPqcJwtService`, `AegisPqcKeyStore` |
| **Library** | Pure-Java BouncyCastle `bcpkix-jdk18on` + `bcprov-jdk18on` (1.78.1) |
| **Algorithm** | ML-DSA-87 (Dilithium5) — NIST FIPS 204, Level 5 quantum resistance |
| **JWT Structure** | Manual Base64Url encoding (no JJWT/Nimbus — neither officially supports FIPS 204) |
| **Password Hashing** | Argon2id via `argon2-jvm 2.11` — memory-hard KDF defeating GPU/ASIC clusters |

**DO-NOT-CHANGE:** `liboqs-java` is permanently excluded. BouncyCastle is the sole PQC provider.

---

### L2: Polymorphic Protocol Obfuscation (PPO) — Moving Target Defense

**Goal:** Prevent attackers and AI fuzzers from mapping the API surface.

| Attribute | Detail |
| :--- | :--- |
| **Classes** | `AegisTemporalPathManager`, `EndpointManifest`, `AegisMtdController`, `AegisResponseMutator` |
| **Mechanism** | Daily temporal path manifests: canonical paths (e.g., `/api/v1/posts`) are mapped to rotating obfuscated paths (e.g., `/api/v1/data-node/2b9c`) |
| **Signing** | Manifests signed by ML-DSA-87, pushed to Redis (`aegis:mtd:manifest`) |
| **Edge Sync** | Worker reads `aegis:mtd:manifest` from KV to translate paths before forwarding to backend |
| **Response Mutation** | `AegisResponseMutator` randomizes `X-Powered-By` headers and injects timing jitter |
| **Emergency Rotation** | Triggered via RabbitMQ events through `AegisThreatConsumer` |
| **MTD Controller** | `AegisMtdController` exposes manifest management endpoints (admin-gated via L3-ZKA) |

---

### L3: Zero-Knowledge Admin Authentication (ZKA)

**Goal:** Admin operations never require password transmission — Zero-Knowledge Proofs replace credentials.

| Attribute | Detail |
| :--- | :--- |
| **Microservice** | `aegis/zkp-service/` — compiled Go binary in distroless/scratch container |
| **gRPC** | Port 9090 — internal Docker network only (`127.0.0.1:9090`) |
| **Algorithm** | Schnorr-over-Lattice (Fiat-Shamir with Aborts), implemented in `circuit/schnorr_lattice.go` |
| **Circuit** | `circuit/auth_circuit.go` — gnark-based ZK circuit |
| **Protocol** | `aegis/zkp-service/aegis_zkp.proto` — gRPC service definition |
| **Filter** | `AegisZkpAdminFilter` — intercepts `/api/v1/admin/**`, extracts `X-AEGIS-ZKP-Proof` header |
| **Client** | `AegisZkpServiceClient` — wraps gRPC calls with Resilience4j Circuit Breaker |
| **Memory Limit** | **Strict 256MB** via Docker `deploy.resources.limits.memory` — prevents VirtualBox OOM |
| **Health Check** | TCP netcat check on gRPC port 9090 |

---

### L4: Adversarial Deception Architecture (ADA)

**Goal:** Actively deceive and trap attackers while gathering intelligence.

| Attribute | Detail |
| :--- | :--- |
| **Classes** | `AegisDeceptionEngine`, `AegisDeceptionFilter`, `TarpitManager`, `PoisonCorpusGenerator`, `CanaryTokenService`, `RabbitMQAttackPublisher` |
| **Strategies** | `PLAUSIBLE_FAKE` — realistic but bogus data payloads; `SLOW_LEAK` — 1 byte/sec Virtual Thread tarpit; `RECURSIVE_LOOP` — structured recursive payloads |
| **Tarpit** | `TarpitManager` uses Java 21 Virtual Threads to drain attacker connections at 1 byte/sec without consuming HTTP worker threads |
| **Poison Corpus** | `PoisonCorpusGenerator` creates plausible-but-false data that corrupts AI training attempts on scraped content |
| **Canary Tokens** | `CanaryTokenService` + `thinkst/canarytokens:latest` container for L4-ADA deception tripwire management |
| **Threat Telemetry** | Fire-and-forget RabbitMQ publish via `RabbitMQAttackPublisher` → `aegis.threat.exchange` exchange → `CloudflareEdgeSyncService` picks up and syncs to Cloudflare KV in real time |
| **Cache Semantics** | All deception responses carry `Cache-Control: no-store` — never cached by CDN |

**Edge Complement:** Worker reads `AEGIS_THREAT_KV` and blocks flagged IPs/JA3 hashes before they reach the origin, achieving real-time global botnet protection.

---

### L5: Behavioral Intelligence Engine (BIE)

**Goal:** Distinguish legitimate human users from bots through behavioral entropy analysis.

| Attribute | Detail |
| :--- | :--- |
| **Backend Classes** | `AegisBehavioralEngine`, `SessionBehaviorProfile`, `ShannonEntropyCalculator` |
| **Frontend** | `src/lib/aegis-biometrics.ts`, `src/components/AegisTelemetry.tsx` |
| **Signal Collection** | Mouse movement jitter, scroll velocity, interaction timing — collected client-side |
| **Hashing** | WebCrypto SHA3-256 client-side hash of biometric vector **before** transmission — zero raw PII leaves the browser |
| **Backend Endpoint** | `POST /api/v1/aegis/telemetry` — public endpoint (no auth required for telemetry) |
| **Analysis** | `ShannonEntropyCalculator` computes entropy of behavioral vectors; low-entropy (bot-like) sessions flag `BehavioralValidator` in BCSM |
| **SSR Safety** | `AegisTelemetry.tsx` is a `'use client'` component — never runs in SSR context; prevents `window` reference crashes at Edge |

---

### L6: Moving Target Defense Orchestration (MTD)

**Goal:** Close the airgap between backend threat detection and edge enforcement.

| Attribute | Detail |
| :--- | :--- |
| **Class** | `CloudflareEdgeSyncService` |
| **Mechanism** | Consumes threat events from RabbitMQ → calls Cloudflare KV API → writes malicious IP/JA3 hashes with 86400s TTL |
| **Deduplication** | `localBlockCache` prevents redundant KV writes — critical for Cloudflare Free Tier quota protection |
| **Trigger** | `AegisThreatConsumer` — reactive RabbitMQ consumer for emergency path rotation events |
| **API Token** | Scoped Cloudflare API Token (IP-fenced to `192.168.29.111` and `192.168.56.101`). **Expires 2026-08-26.** |

---

### L7: Chaos Mirror + Proof-of-Work Challenge System (CMCS)

**Goal:** Exhaust automated credential stuffers and scraping bots through computational challenges.

| Attribute | Detail |
| :--- | :--- |
| **Classes** | `AegisChaosMirror`, `PowChallengeIssuer` |
| **Mechanism** | `PowChallengeIssuer` issues SHA3-256 Proof-of-Work challenges — computational cost scales with behavioral risk score |
| **Chaos Mirror** | `AegisChaosMirror` dynamically generates plausible-looking response variations to confuse reconnaissance tools |
| **Tarpit Parameters** | Delay parameterized by the `SecurityDecision` behavioral risk score from L8-BCSM |

---

### L8: Byzantine Consensus Security Mesh (BCSM)

**Goal:** Make security decisions through distributed validator consensus — a single compromised validator cannot unilaterally allow or block traffic.

| Attribute | Detail |
| :--- | :--- |
| **Class** | `AegisBcsm` |
| **Execution** | 7 independent validators run in parallel via `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture.allOf()` |
| **Timeout** | Strict **100ms** hard ceiling — timed-out validators are treated as abstain votes |
| **Consensus** | 3 BLOCK votes → Block; 2 EMERGENCY votes → Emergency response; otherwise → ALLOW |
| **BFT Tolerance** | Tolerates up to 2 compromised or timed-out validators without disrupting quorum |
| **Migration Note** | Previously used preview `StructuredTaskScope` API — migrated to stable `Executors.newVirtualThreadPerTaskExecutor()` for Java 21 GA compatibility |

**The 7 Validators:**

| Validator Class | What It Checks |
| :--- | :--- |
| `RateLimitValidator` | Bucket4j token bucket state for the requesting IP |
| `JwtValidator` | JWT token presence, expiry, issuer, audience |
| `BehavioralValidator` | Shannon Entropy score from `AegisBehavioralEngine` (L5-BIE) |
| `TemporalPathValidator` | Whether the requested path matches the current daily rotating manifest (L2-PPO) |
| `EntropyHealthValidator` | Hardware entropy pool health from `AegisEntropyManager` (L0-HEA) |
| `HidsIntegrityValidator` | Wazuh HIDS signals received via RabbitMQ threat events |
| `DeceptionValidator` | Whether the requesting IP/JA3 is in the active deception corpus (L4-ADA) |

**SecurityDecision enum:** `ALLOW`, `BLOCK`, `DECEPTION`, `EMERGENCY`, `TARPIT`

---

### AEL: AEGIS Expression Language

**Goal:** Dynamic security policy evaluation without code deployment.

| Attribute | Detail |
| :--- | :--- |
| **Classes** | `AegisExpressionLanguage`, `AelRuleLoader` |
| **Grammar** | `aegis.g4` (ANTLR4 grammar, version 4.13.1) |
| **Purpose** | A custom DSL for expressing security rules that are evaluated at runtime without code injection risk |
| **Integration** | Rules evaluated within the BCSM lifecycle rather than hardcoded static blocks — changes require only rule file update, not deployment |

---

## PII Encryption at Rest (AES-256-GCM)

All personally identifiable information is encrypted at the JPA converter level before it touches the database.

| Converter Class | Column | Table | Encryption Key Variable |
| :--- | :--- | :--- | :--- |
| `UserEmailConverter` | `email` | `users` | `USER_EMAIL_ENCRYPTION_KEY` |
| `ContactEmailConverter` | `email` | `contact_message` | `CONTACT_EMAIL_ENCRYPTION_KEY` |
| `ContactMessageConverter` | `message` | `contact_message` | `CONTACT_MESSAGE_ENCRYPTION_KEY` |
| `AuditIpConverter` | `ip_address` | `audit_log` | `AUDIT_IP_ENCRYPTION_KEY` |

All converters extend `EncryptedStringConverter`. All support `v1:` prefix versioning for future key rotation without data migration. Keys are domain-specific — compromising one key does not expose data encrypted with others.

---

## Zero-Trust DB Driver Boundary (`AegisQueryInterceptor`)

Every JDBC query executed against MariaDB is intercepted and HMAC-signed with SHA3-256 using `AEGIS_DB_SIGNING_KEY` before execution. This creates a cryptographic audit trail and prevents query injection at the driver level.

---

## Content Integrity (`ContentIntegrityService`)

All published blog post content is HMAC-SHA256 signed at publish time using `CONTENT_SIGNING_KEY`. The signature is stored in the `content_signature` column (V45 migration). Any modification to post content without re-signing generates a signature mismatch alert — the system treats this as a critical security event.

---

## Edge Signature Verification (`AegisEdgeValidationFilter`)

Every inbound request to the backend must carry:
- `X-Aegis-Edge-Signature` — HMAC-SHA-512 hex signature (`path:timestamp:clientIp`, signed with `AEGIS_EDGE_SECRET`)
- `X-Aegis-Edge-Timestamp` — Unix epoch seconds (validated within 300-second window)

The filter uses **constant-time comparison** (`MessageDigest.isEqual()`) to prevent timing-based signature oracle attacks. Direct-IP backend access bypassing the Cloudflare Edge Worker is blocked at this filter.

**Implementation note:** The Worker uses WebCrypto `HMAC-SHA-512` (not SHA3-512) due to WebCrypto API limitations. BouncyCastle handles SHA3 on the Java side. This asymmetry is documented and accepted.

---

## Wazuh HIDS Integration

The `wazuh-agent` container runs with `privileged: true` and `pid: host` to monitor system calls. Threat signals are published to RabbitMQ and consumed by `HidsIntegrityValidator` inside the BCSM validators pool.

---

## IMMUTABLE CHANGE HISTORY (DO NOT DELETE)

- **VERIFIED (2026-05-29 — Enterprise Documentation Generation):**
  - All AEGIS layer claims verified against actual implementation files: `AegisBcsm.java`, `AegisDeceptionFilter.java`, `AegisEdgeValidationFilter.java`, `AegisMainFilter.java`, `AegisZkpAdminFilter.java`, `AegisBehavioralEngine.java`, `SessionBehaviorProfile.java`, `ShannonEntropyCalculator.java`, `AegisPqcJwtService.java`, `AegisPqcKeyStore.java`, `AegisTemporalPathManager.java`, `TarpitManager.java`, `RabbitMQAttackPublisher.java`, `CloudflareEdgeSyncService.java`, `AegisChaosMirror.java`, `PowChallengeIssuer.java`, `CanaryTokenService.java`, `PoisonCorpusGenerator.java`, `AegisEntropyManager.java`, `JitterEntropySource.java`, `UrandomEntropySource.java`, `AegisExpressionLanguage.java`, `AelRuleLoader.java`, `aegis.g4`.
  - Added complete 7-validator table with exact class names.
  - Added `AegisMtdController` (previously undocumented in this file).
  - Added Cloudflare API Token expiry warning to L6-MTD section.
  - Confirmed `StructuredTaskScope` → `Executors.newVirtualThreadPerTaskExecutor()` migration in BCSM.
  - No architectural claims changed — existing docs were accurate.