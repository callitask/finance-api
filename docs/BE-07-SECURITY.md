# 07 — AEGIS Security Framework
## Adaptive Entropic Guardian Intelligence System

**Version:** Active in Production (Phase 6 Complete)
**Classification:** Internal Architectural Reference (Sanitized — No Keys, No Secrets, No Internal Endpoints)

---

## Overview

AEGIS is a **living adversarial security system** implemented across the Treishvaam Group Ecosystem. Unlike classical security, which is purely reactive, AEGIS is **Proactive, Adaptive, and Active**. It uses post-quantum cryptography, active deception, behavioral telemetry, Byzantine fault-tolerant consensus, and zero-knowledge proofs to neutralize both classical and AI-driven threats.

All AEGIS logic executes exclusively via **Java 21 Virtual Threads** — ensuring that even the most CPU-intensive security decisions add zero latency to legitimate Tomcat request threads.

---

## The 9 Layers of AEGIS

### L0: Hardware Entropy Anchoring (HEA)
**Goal:** Seed all cryptographic operations with true, unpredictable entropy.

| Attribute | Detail |
| :--- | :--- |
| **Implementation** | `AegisEntropyManager`, `EntropySource` (interface), `JitterEntropySource`, `UrandomEntropySource` |
| **Pool Size** | 4096-bit SHA3-512 folded entropy pool |
| **Sources** | `/dev/urandom`, `/dev/random`, JVM timing jitter (RDRAND/RDSEED equivalent) |
| **Refresh** | Every 30 seconds — fully asynchronous via `@Async` Virtual Thread |
| **Infrastructure** | Docker volume-maps `/dev/random` and `/dev/urandom` into the backend container |
| **Future** | `/dev/tpm0` integration planned on physical TPM 2.0 hardware addition |

**Why:** SHA3-512 is used specifically because Grover's quantum algorithm halves symmetric key strength — a 512-bit pool maintains effectively 256-bit quantum-resistant entropy.

---

### L1: Post-Quantum Cryptographic Foundation (PQCf)
**Goal:** Full immunity to Shor's algorithm (asymmetric key breaks) and Grover's algorithm (symmetric key weakening).

| Attribute | Detail |
| :--- | :--- |
| **Implementation** | `AegisPqcJwtService`, `AegisPqcKeyStore` |
| **Library** | Pure-Java BouncyCastle `bcpkix-jdk18on` + `bcprov-jdk18on` (1.78.1) |
| **Algorithm** | ML-DSA-87 (Dilithium5) — NIST FIPS 204, Level 5 quantum resistance |
| **JWT Structure** | Manual Base64Url encoding (no JJWT/Nimbus — neither officially supports FIPS 204 yet) |
| **Password Hashing** | Argon2id via `argon2-jvm 2.11` — memory-hard KDF defeating GPU/ASIC clusters |

**CRITICAL:** `liboqs-java` is explicitly and permanently excluded. It breaks the Maven CI/CD pipeline. BouncyCastle is the sole PQC provider. This is a DO-NOT-CHANGE architectural constraint.

---

### L2: Polymorphic Protocol Obfuscation (PPO) — Moving Target Defense
**Goal:** Prevent attackers and AI fuzzers from mapping the API surface.

| Attribute | Detail |
| :--- | :--- |
| **Implementation** | `AegisTemporalPathManager`, `EndpointManifest`, `AegisMtdController`, `AegisResponseMutator` |
| **Mechanism** | Daily temporal path manifests: canonical paths (e.g., `/api/v1/posts`) are mapped to rotating obfuscated paths (e.g., `/api/v1/data-node/2b9c`) |
| **Signing** | Manifests signed by ML-DSA-87, pushed to Redis |
| **Edge Sync** | Worker reads `aegis:mtd:manifest` from KV to translate paths before forwarding |
| **Response Mutation** | `AegisResponseMutator` randomizes `X-Powered-By` headers and injects timing jitter |
| **Emergency Rotation** | Triggered via RabbitMQ events through `AegisThreatConsumer` |

---

### L3: Zero-Knowledge Authentication Layer (ZKA)
**Goal:** Unbreakable, secret-less admin authentication — no password is ever transmitted.

| Attribute | Detail |
| :--- | :--- |
| **Backend Filter** | `AegisZkpAdminFilter` — intercepts all `/api/v1/admin/**` |
| **Microservice** | `aegis-zkp-service` — compiled Go binary in distroless/scratch container |
| **Protocol** | gRPC on internal port 9090 (invisible outside `treish_net`) |
| **Algorithm** | Schnorr-over-Lattice Zero-Knowledge Proof (Fiat-Shamir with Aborts for ML-DSA compatibility) |
| **Circuit** | `circuit/schnorr_lattice.go` + `circuit/auth_circuit.go` (gnark framework) |
| **gRPC Schema** | `aegis_zkp.proto` |
| **Memory** | Strictly bounded to **256MB** via Docker `deploy.resources` — prevents VirtualBox host starvation |
| **Resilience** | `AegisZkpServiceClient` wraps all gRPC calls in a Resilience4j Circuit Breaker |

---

### L4: Adversarial Deception Architecture (ADA)
**Goal:** A labyrinth of honeypots and corrupted data to waste attacker resources and poison their AI training models.

| Attribute | Detail |
| :--- | :--- |
| **Implementation** | `AegisDeceptionEngine`, `PoisonCorpusGenerator`, `CanaryTokenService` |
| **Strategies** | `PLAUSIBLE_FAKE` (subtly wrong data), `SLOW_LEAK` (byte-trickle tarpit), `RECURSIVE_LOOP` (redirect maze) |
| **Tarpit** | `TarpitManager` holds TCP connections open at 1 byte/second using Java 21 Virtual Threads — Tomcat threads are never blocked |
| **Telemetry** | `RabbitMQAttackPublisher` fires attack events (IP, JA3, path, trigger reason) to `aegis.threat.exchange` RabbitMQ exchange asynchronously (fire-and-forget) |
| **Edge Sync** | `CloudflareEdgeSyncService` pushes threat intel to Cloudflare KV in real time (86400s TTL, deduplication via `localBlockCache`) |
| **Cache Safety** | All deception responses carry `Cache-Control: no-store` — Cloudflare never caches poisoned payloads |
| **Canary Tokens** | `thinkst/canarytokens` container issues traceable tokens embedded in deception data |

---

### L5: Behavioral Intelligence Engine (BIE)
**Goal:** Continuous behavioral tracking to detect automated bots, hijacked sessions, and AI scrapers.

| Attribute | Detail |
| :--- | :--- |
| **Backend** | `AegisBehavioralEngine` — calculates **Shannon Entropy** (`ShannonEntropyCalculator`) of request timing jitter and endpoint access patterns |
| **Profile** | `SessionBehaviorProfile` — scoped per session and tenant via `TenantContext` |
| **Frontend** | `src/lib/aegis-biometrics.ts` — passive `mousemove`, `keydown`, `scroll` listeners |
| **Privacy** | Biometric vectors are **hashed client-side using WebCrypto SHA3-256 before transmission** — no raw keystrokes, coordinates, or PII ever leave the browser |
| **GDPR** | 100% compliant — only hashed entropy values are transmitted |
| **Ingestion** | `POST /api/v1/aegis/telemetry` (public, anonymous) |

---

### L6: Moving Target Defense (MTD) — Orchestration Layer
**Goal:** Continuous automated infrastructure rotation to prevent pattern recognition.

Builds on L2-PPO with active orchestration:
- `AegisMtdController` handles emergency rotation API calls
- `CloudflareEdgeSyncService` closes the "Airgap" between backend threat detection and edge enforcement
- `AegisThreatConsumer` subscribes to RabbitMQ threat events for reactive rotations

---

### L7: Chaos Mirror Counter-Attack System (CMCS)
**Goal:** Weaponize attacks against the attacker to burn their compute resources.

| Attribute | Detail |
| :--- | :--- |
| **Implementation** | `AegisChaosMirror`, `TarpitManager`, `PowChallengeIssuer` |
| **PoW Challenge** | SHA3-256 Proof-of-Work challenge issued to credential stuffers — CPU-exhausting |
| **Tarpit** | Dynamic delay tarpits via `tarpitConnection(request, response, delayMs)` — delay proportional to attacker's behavioral risk score |
| **Virtual Threads** | All tarpit execution runs on Virtual Threads — zero Tomcat thread consumption |

---

### L8: Byzantine Consensus Security Mesh (BCSM)
**Goal:** Decentralized, fault-tolerant security decision making — no single component can compromise the system.

| Attribute | Detail |
| :--- | :--- |
| **Implementation** | `AegisBcsm` |
| **Execution** | `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture` — fully parallel |
| **Timeout** | Strict **100ms** hard ceiling — timed-out validators count as abstain votes |
| **Consensus** | 3 BLOCK votes → Block | 2 EMERGENCY votes → Emergency response |
| **BFT** | Tolerates up to 2 compromised or timed-out validators |

**The 7 Validators:**

| Validator | Class | Signal |
| :--- | :--- | :--- |
| Rate Limit | `RateLimitValidator` | Bucket4j token bucket state |
| JWT | `JwtValidator` | Token validity, expiry, issuer |
| Behavioral | `BehavioralValidator` | Shannon Entropy score |
| Temporal Path | `TemporalPathValidator` | Daily manifest path compliance |
| Entropy Health | `EntropyHealthValidator` | L0-HEA pool health |
| HIDS | `HidsIntegrityValidator` | Wazuh agent threat signals |
| Deception | `DeceptionValidator` | Active deception corpus membership |

---

## AEGIS Expression Language (AEL)

A custom Domain-Specific Language for defining dynamic security policies without code injection risk.

| Component | Detail |
| :--- | :--- |
| **Grammar** | `aegis.g4` (ANTLR4) |
| **Parser** | `AegisExpressionLanguage.java` |
| **Loader** | `AelRuleLoader.java` |
| **Example** | `POLICY BotDetection { BEHAVIOR.BOT_SCORE > 85 THEN DECEPTION }` |

---

## Database & Data Security

| Mechanism | Implementation | Detail |
| :--- | :--- | :--- |
| **TDE** | MariaDB + `config/mariadb/encryption.cnf` | Transparent Data Encryption for all data at rest |
| **Row-Level Encryption** | `UserEmailConverter`, `ContactEmailConverter`, `ContactMessageConverter`, `AuditIpConverter` | AES-256-GCM per domain; `v1:` prefix versioning for rotation |
| **Query Signatures** | `AegisQueryInterceptor` | HMAC-SHA3-256 on every JDBC query — Zero-Trust DB Driver Boundary |
| **Content Integrity** | `ContentIntegrityService` | HMAC-SHA256 on blog post content; mismatches trigger alerts |
| **Merkle Audit Log** | `MerkleAuditLogService` | Tamper-proof recording of all administrative actions |

---

## Cloudflare Edge Integration

| Component | Detail |
| :--- | :--- |
| **JA3 Fingerprinting** | OpenResty Lua module `nginx/lua/aegis_ja3.lua` — captures TLS JA3 hashes, injects as `X-JA3-Fingerprint` header to Tomcat |
| **Edge Signature** | Worker signs every backend request with `HMAC-SHA-512` (`X-Aegis-Edge-Signature` + `X-Aegis-Edge-Timestamp`) |
| **Note on SHA** | WebCrypto API natively limits to SHA-512 (not SHA3-512). HMAC-SHA-512 is retained for 0ms execution speed. BouncyCastle handles SHA3 on the Java side |
| **KV Threat Sync** | `CloudflareEdgeSyncService` → Cloudflare KV (malicious IPs and JA3 hashes, 24h TTL) |
| **MTD at Edge** | Worker reads `aegis:mtd:manifest` from KV for Moving Target Defense path translation |
| **GEO Interception** | Worker intercepts LLM crawlers (GPTBot, ClaudeBot, DeepSeek, OAI-SearchBot, etc.) and serves GEO payloads from KV cache — backend is not hit |
| **L4-ADA at Edge** | TARPIT-flagged IPs are routed to Virtual Thread tarpit at the origin; the Worker bypasses origin fetch entirely for known deception targets |

---

## Observability Integration

All new backend endpoints must be instrumented with **Prometheus Micrometer** to automatically trigger Grafana alerts defined in `config/grafana-alerting.yml`:

| Alert | Trigger |
| :--- | :--- |
| `HighBackendErrorRate` | Error rate exceeds threshold |
| `SlowAPIResponse` | Response time exceeds SLA |
| `SecretKeyRotationDue` | API token approaching 90-day TTL expiry |

Grafana Faro RUM (`faroConfig.js`) streams frontend Web Vitals and unhandled exceptions to the internal observability stack. Faro error boundaries must not be bypassed.
