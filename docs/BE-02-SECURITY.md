# BE-02 — SECURITY: The AEGIS 9-Layer Zero-Trust Framework

> **Verification basis:** backend source (Parts 1–12) · Knowledge Tracker VOL 1+2 · SKILLS REF_2 cross-check. Supersedes legacy BE-02-CORE + BE-07-SECURITY. Implementation-status caveats are explicit — this is the honest picture.

---

## 1. Framework Overview

```mermaid
flowchart TD
    REQ["Inbound (from OpenResty)"] --> L4["L4 · AegisEdgeValidationFilter<br/>HMAC-SHA-512 ±300s → 403"]
    L4 --> IP["AegisIpResolutionFilter (pin verified IP)"]
    IP --> RL["L7 · RateLimitingFilter (Bucket4j/Redis 10/200/60 RPM)"]
    RL --> SAN["L8 · InputSanitizationFilter (regex + sqli:blocked:{ip})"]
    SAN --> ZKP["L2 · AegisZkpAdminFilter (/api/v1/admin/**)"]
    ZKP --> MAIN["AegisMainFilter"]
    MAIN --> MTD["L9 · AegisTemporalPathManager (/api/v1/node/{hex})"]
    MTD -->|sentinel DECEPTION| DEC["L3 · poisoned response"]
    MTD --> BCSM["BCSM · 7 validators · 100 ms"]
    BCSM -->|BLOCK| BLK["429 {'error':'Request rejected'}"]
    BCSM -->|TARPIT/EMERGENCY| TARP["L3 · TarpitManager (1 byte/10 s forever)"]
    BCSM -->|ALLOW/WARN| CT["L6 · Keycloak JWT + RBAC → Controller"]
    DEC & TARP -.->|"aegis.threat.exchange"| KV["Cloudflare KV aegis:block:* (86400s)"]
```

| Layer | Name | Components | Status |
|---|---|---|---|
| L1 | PQC (ML-DSA-87) | `AegisPqcJwtService`, `AegisPqcKeyStore` | Implemented (in-memory keys) |
| L2 | ZKP admin gating | `AegisZkpAdminFilter`, `AegisZkpServiceClient`, Go service | Wired — **real BN254 Groth16 `AuthCircuit`** (`ChallengeBinding == MiMC(Secret, Challenge)`); production prover rollout gated on `AEGIS_ADMIN_ZKP_PUBLIC_KEY` pinning |
| L3 | Active deception & tarpitting | `AegisDeceptionEngine/Filter`, `TarpitManager`, `PoisonCorpusGenerator`, `CanaryTokenService` | Implemented |
| L4 | Edge HMAC validation | `AegisEdgeValidationFilter` + Worker | Implemented |
| L5 | Entropy & behavioral | `ShannonEntropyCalculator`, `AegisBehavioralEngine`, `AegisJwtEnhancer` | Implemented — `queryString` entropy wired into risk score (+20 when len>64 & H>5.2, OAuth exempt); `AegisEntropyManager.isHealthy()` (<120 s) |
| L6 | JWT + RBAC | SecurityConfig, `KeycloakRealmRoleConverter` | Implemented |
| L7 | Distributed rate limiting | `RateLimitingFilter`, `Bucket4jConfig` | Implemented |
| L8 | Sanitization & integrity | `InputSanitizationFilter`, `QuerySanitizationService`, `AegisQueryInterceptor`, `ContentIntegrityService` | Implemented |
| L9 | Moving Target Defense | `AegisTemporalPathManager`, `CloudflareEdgeSyncService`, `AegisResponseMutator` | Implemented |

## 2. Route Security Matrix (`SecurityConfig`)

Stateless: `csrf().disable()`, `SessionCreationPolicy.STATELESS`, `frameOptions.disable()`, OAuth2 JWT with `KeycloakRealmRoleConverter`. CORS disabled in-chain; global `CorsFilter` bean (HIGHEST+2) — allowed headers include the full AEGIS set (`X-AEGIS-ZKP-Proof`, `X-AEGIS-Challenge-ID`, `X-Aegis-Biometric-Hash/Raw`, `X-Aegis-Edge-Signature/Timestamp/Client-IP`, `X-CSRF-Token`); exposed `X-AEGIS-POW-Challenge`.

| Matcher | Rule |
|---|---|
| `OPTIONS /**` | permitAll |
| `/actuator/health`, `/api/v1/health/**`, `/api/v1/monitoring/ingest`, `/error` | permitAll |
| `/actuator/**` | ROLE_ADMIN |
| GET `/api/v1/uploads/**`, `/sitemap*.xml`, `/feed.xml`, `/sitemaps/**`, `/favicon.ico`, `/llms.txt`, `/ai-feed.md`, `/api/public/**` | permitAll |
| GET `/api/v1/{posts,categories,market,news,search}/**`, `/api/v1/logo` | permitAll |
| POST `/api/v1/market/quotes/batch`, `/api/v1/analytics`, `/api/v1/analytics/event`, `/api/v1/analytics/**`, `/api/v1/aegis/telemetry` | permitAll |
| `/api/v1/contact/**` | permitAll |
| `/api/v1/aegis/tarpit/trap` (GET, POST) | permitAll |
| `GET /api/v1/video/**` | permitAll (Edge-gated) |
| POST/PUT `/api/v1/posts/draft…` | authenticated |
| `POST /api/v1/posts/draft`, `PUT /api/v1/posts/draft/{id}`, `POST /api/v1/posts/{id}/duplicate` | PUBLISHER/ADMIN (role-restricted) |
| `POST /api/v1/market/news/fetch`, `POST /api/v1/market/news/deduplicate` | ROLE_ADMIN |
| `/api/v1/auth/**` | authenticated |
| GET `/api/v1/analytics` (dashboard) | ANALYST/ADMIN (method-level ADMIN on refresh) |
| `/api/v1/posts/admin/**` (above GET `/api/v1/posts/**`) | EDITOR/PUBLISHER/ADMIN |
| POST `/api/v1/posts` | PUBLISHER/ADMIN |
| PUT `/api/v1/posts/**` | EDITOR/PUBLISHER/ADMIN |
| DELETE `/api/v1/posts/**` | PUBLISHER/ADMIN |
| `/api/v1/files/upload` | PUBLISHER/ADMIN |
| `/api/v1/admin/**`, `/api/v1/status/**` | ROLE_ADMIN **+ ZKP filter** |
| fallback | `anyRequest().authenticated()` |

Keycloak realm roles: `admin`, `editor`, `publisher`, `analyst` (frontend UI checks only `admin|publisher`). ⚠ No `.exceptionHandling()` configured. Note: `/api/v1/analytics/event` permitAll was added 2026-07-15 (Incident 11) to unblock sendBeacon.

## 3. Layer 2 — ZKP Admin Gating

- Filter intercepts `/api/v1/admin/` prefix. Heal special-case: exact URI `/api/v1/admin/actions/analytics/heal` accepts header **`X-AEGIS-ZKP`** = `app.security.internal-secret` *or* `verifyProof("system_admin","ANALYTICS_HEAL_ACTION", value)` (hashed/constant-time comparison, `MessageDigest.isEqual`) → standard contract: **`X-AEGIS-ZKP-Proof` + `X-AEGIS-Challenge-ID`** via client. The CI-only `X-AEGIS-Test-Token` bypass is **removed** (Scoped-bypass design verified working 2026-08-05/07, Incidents 37–45; the global-bypass variant was **rejected** as an L3-ZKA violation. Controller-level ZKP was removed — Incident 45 — the gate lives in the filter.)
- Client: gRPC `ManagedChannelBuilder.forTarget("${aegis.zkp.target:aegis-zkp-service:9090}").usePlaintext()`, blocking stub, `@CircuitBreaker(name="zkpService")` fallback returns `false` (**fail-closed**). `verifyProof(adminId, publicKey, challengeId, zkpProof)` (Incident 40).
- Replay prevention: Redis `aegis:zkp:used:{id}` `setIfAbsent` **5 min** TTL after first use.
- Production pinning note: the admin ZKP prover is gated on the prover's verification key — enforce **`AEGIS_ADMIN_ZKP_PUBLIC_KEY`** pinning prior to production rollout (filter resolves the configured public key when non-blank; vk-path override `AEGIS_ZKP_VK_PATH` is the rollout-time knob).
- Failures → 403 JSON (`Zero-Knowledge Proof required…` / `Cryptographic identity proof invalid or expired.`).
- Go verifier: real BN254 Groth16 `AuthCircuit` (`aegis/zkp-service/circuit/auth_circuit.go` — `ChallengeBinding == MiMC(Secret, Challenge)`; `schnorr_lattice.go` exports `InitVerifier`/`VerifyLatticeSchnorrProof`; `auth_circuit_test.go` runs `TestAuthCircuitVerifyLatticeSchnorrProof`). Loopback-only port binding + internal DNS mitigate exposure.

## 4. Layers 3–5 — Deception, Entropy, Behavioral

### 4.1 L3 Deception
- Triggers: `HONEYPOT_PATHS (?i)^/(\.env|wp-admin|phpmyadmin|config\.php|\.git|vendor/phpunit).*`; `MALICIOUS_PAYLOADS (?i)(UNION\s+SELECT|script>|\.\./\.\./|base64_decode\()` on the query string → `publishAttackEvent(ip, ja3, path, "Deception Filter Intercept")` + strategy response.
- Strategies: `.env`/`config.php` → 200 fake env (canary-bearing); `wp-admin`/`phpmyadmin`/`vendor` → 301 self-redirect loop `?d={n+1}&t={canary}`; `api/` → 200 fake JWT; default → slow-leak tarpit.
- Tarpit: 200 `text/html`, keep-alive, virtual thread writes `"\n"` then **1 byte / 10 000 ms forever**. The Worker rewrites tarpitted IPs to `/api/v1/aegis/tarpit/trap` — served by `AegisTarpitController` (`GET/POST`, permitAll) — OP-05 closed. Redis `aegis:deception:score:{ip}` carries the deception score.
- **X-Real-IP fallback:** `AegisDeceptionFilter` reads `X-Real-IP` (fallback to raw `remoteAddr`) as the client identity (VULN-22a heritage).
- Canary: `AKIA` + 16 hex; Thinkst server + isolated Redis as siblings (canary env quirks: strict `CANARY_` prefix incl. `CANARY_WG_PRIVATE_KEY_SEED`, dual `switchboard.env`/`frontend.env`, `rm -f twistd.pid`).

### 4.2 Behavioral
- Score: missing/UNKNOWN JA3 **+40**; JA3 strikes >5 **+60**; missing biometric **+30**; `X-Aegis-Biometric-Hash` present → hash-derived risk bucket; VThread-safe explicit `queryString` Shannon entropy (+20 when len>64 & H>5.2, OAuth exempt); cap 100. Strikes in-memory `ConcurrentHashMap` (Redis sync = future work). `AegisJwtEnhancer`: risk ≥ **80** → token unsafe.
- **AI-crawler bypass:** UA ∈ {GPTBot, ClaudeBot, Google-Extended, anthropic-ai, PerplexityBot, Googlebot} **and** rDNS under `.googlebot.com` / `.search.msn.com` / `.outbound-enterprise.openai.com` / `.anthropic.com` → score 0.
- ⚠ `SessionBehaviorProfile` = dead code.

### 4.3 Entropy (implemented — OP-02 closed)
- `ShannonEntropyCalculator` — pure static function; `AegisBehavioralEngine` calls it on the explicit `queryString` (VThread-safe), +20 when len>64 & H>5.2, OAuth exempt (P3.2).
- `AegisEntropyManager` — 512-byte SHA3-512 XOR-folded pool, `isHealthy()` (<120 s); `getSecureRandom()` seeds from a random 64-byte window. Sources: `CPU_JITTER` (1000-iter `Math.sin` timing), `OS_URANDOM`.
- `PowChallengeIssuer`: difficulty **24 leading zero bits** (6 hex zeros of SHA3-256), challenge `v1:{difficulty}:{epochMillis}:{nonce}` in `X-AEGIS-POW-Challenge`; **2-min freshness window** (`CHALLENGE_TTL_MS = 120_000`) + Redis `aegis:pow:used:{nonce}`.

## 5. Layer 4 — Edge HMAC Contract (canonical)

```text
key       = AEGIS_EDGE_SECRET (64-char; Worker secret ↔ Infisical AEGIS_EDGE_SECRET must match; fail-closed `@PostConstruct` when blank/<32 chars — `AegisEdgeValidationFilter.java:58,105`)
data      = "${pathname}:${timestamp}:${clientIp}"   // pathname = exact BACKEND path (post-MTD),
                                                      // query string stripped (worker .split('?')[0])
headers   = X-Aegis-Edge-Signature (hex) · X-Aegis-Edge-Timestamp (epoch ms) · X-Aegis-Client-IP
drift     = ±300 s (ms compare, MAX_TIME_DRIFT_SECONDS = 300)
failure   = HTTP 403 sendError — "Origin Access Denied - …" / "- Malformed Timestamp" / "- Payload Expired" / "- Invalid Cryptographic Signature"
compare   = constant-time (MessageDigest.isEqual)
bypass    = (loopback | ::1 | 10.* | 192.168.* | 172.*) AND (/actuator* | /health*)   // nothing else
```

- IP resolution: `X-Aegis-Client-IP` → `CF-Connecting-IP` → `X-Real-IP` → `remoteAddr`; `AegisIpResolutionFilter` pins the verified value into all forwarding headers — **spoofed `X-Forwarded-For` never reaches controllers**. IP source must never be raw `getRemoteAddr()` (Docker bridge `172.18.0.x` → fingerprint collisions — Incidents 63–74).
- SSR nuance: Next.js server components sign with `clientIp='127.0.0.1'` (3-part, valid). ⚠ The frontend app-route proxies (`app/llms.txt` etc.) sign 2-part (`path:timestamp`) and send `X-Aegis-Timestamp` — **they will 403** (OP-18).
- Nginx JA3: `cf-client-ja3` passthrough else pseudo-hash `md5(ua|ssl_protocol|ssl_cipher)` → `X-JA3-Fingerprint`.

## 6. BCSM — Byzantine Consensus Security Mesh

7 validators on a per-call virtual-thread executor, **100 ms timeout** each; exception/timeout → `WARN(50, NODE_FAILURE/TIMEOUT_NODE)`; executor failure → **BLOCK** (fail-closed). Consensus: ≥2 EMERGENCY → EMERGENCY (tarpit); ≥3 BLOCK votes (or score>70) → BLOCK (→ **429** `{"error":"Request rejected"}`); avg>50 → DECEPTION; avg>30 → WARN; else ALLOW. Enum: `ALLOW WARN BLOCK DECEPTION TARPIT EMERGENCY`.

| Validator | Reality |
|---|---|
| Behavioral | Real (risk model §4.2, incl. Shannon entropy +20 & OAuth exemption) |
| Jwt | Real — alg-aware, 11 KB pre-guard (`parts[0].length() > 11_000` → 100/BLOCK) (`AegisPqcJwtService.validateToken`) |
| RateLimit | Real read-only (`RATE_LIMIT_EXHAUSTED` attr → 85/BLOCK) |
| TemporalPath | Real (`AEGIS_TEMPORAL_VALID`; `/sitemaps*`, `/` exempt) |
| Deception | Real (Redis `aegis:deception:score:<ip>`) |
| EntropyHealth | Real (`AegisEntropyManager.isHealthy()`) |
| HidsIntegrity | Real (all 7 validators wired; Wazuh bridge via `aegis.threat.exchange`) |

`MerkleAuditLogService` — SHA3-256 leaves over top-1000 audit rows every 5 min; ⚠ root only logged (OP-04).
**AEL** (ANTLR4 `aegis.g4`): `POLICY id { <condition> THEN <action> ELSE <action> }`; domains `BEHAVIOR/CRYPTO/NETWORK/TIME/SCORE`; actions `ALLOW · BLOCK("r") · DECEPTION · TARPIT(Nms) · ROTATE_MANIFEST · EMERGENCY · LOG("m") · CHALLENGE(ZKP|POW|CAPTCHA)`; `BailErrorStrategy` — bad rules fail startup.

## 7. Layers 7–9

### 7.1 L7 Rate limiting
Dedicated Lettuce client (`RedisURI.create(System.getenv("SPRING_DATA_REDIS_URL"))` — `@Value` shadowing banned after NOAUTH lockout, Incident 7). Key `clientIp:category`; `/auth/` 10 RPM · GET 200 RPM · else 60 RPM; 429 `{"error":"Rate limit exceeded. Try again in 60 seconds."}`; Redis failure → **503 fail-closed**. Nginx adds `keycloak_auth` 20 r/m burst 15 on `/auth/`; `api_write`/`api_read` zones declared but unapplied.

### 7.2 L8 Sanitization & integrity
- `InputSanitizationFilter` — query/form params through 4 regex families (SQL keywords incl. `xp_|sp_`; **chained-SQLi** `;\s*(drop|alter|create|truncate|…)` — refined 2026-08-16 after Tiptap `width: 50%;` false positives; quote escapes; boolean tautologies). Hit → 400 `{"error":"Invalid input detected. Request blocked."}` + Redis **`sqli:blocked:{ip}` TTL 1 h** (flush: `redis-cli KEYS "sqli:blocked:*" | xargs -r redis-cli DEL`).
- `AegisQueryInterceptor` — Hibernate `StatementInspector` **signs** SQL (SHA3-256 → HmacSHA256 `aegis.db.signing.key` → `/* AEGIS_HMAC:{hex} */`); never blocks, fails open. Changing the key invalidates prior signatures.
- `ContentIntegrityService` — HmacSHA256(`CONTENT_SIGNING_KEY`) over `title|slug|content|author|tenantId` → `content_signature` (VARCHAR 128); constant-time `MessageDigest.isEqual` verification; missing key → disabled (fail-open); tamper audit logging (`CONTENT_TAMPER_DETECTED`) + `app.security.block-tampered-posts` flag (`BlogPostServiceImpl.java:77,684`); AEL `ThreadLocal` concurrency fix.

### 7.3 L9 MTD
- Targets `/api/v1/{admin,auth,users,dashboard,analytics}` → `/api/v1/node/{8-hex}` (hex from entropy pool). Manifest `{"paths":{…},"issuedAt":…}` → Redis **`aegis:mtd:manifest` TTL 24 h**; rotation via `setIfAbsent` lock **`aegis:mtd:lock` 30 s** (split-brain fix 2026-08-03, Incidents 29–30 — replicas had booted independent manifests); PQC-signed + pushed to KV `aegis:mtd:manifest` (no TTL).
- Rotation: `@PostConstruct` + `0 0 3 * * *` + `0 0 4 * * SUN` (keypair regen) + emergency.
- Resolution: SEO exclusions (`/sitemap.xml`, `/sitemap-dynamic`, `/market`, `/blog`, `/category`, `/api/v1/public`) → beacon exemptions (`.startsWith("/api/v1/analytics/event")`, `/api/v1/aegis/telemetry` — tightened 2026-08-01, Incident 26) → temporal match **`.equals(prefix) || .startsWith(prefix + "/")`** (base-path fix, Incident 28) → unwrap via `CanonicalPathRequestWrapper` (URI/servletPath overridden; `ServletRequestPathUtils.PATH` + `HandlerMapping.lookupPath` attributes evicted; dispatched with `filterChain.doFilter` — the `.forward()` variant bypassed the JWT chain and is banned, Incident 26) → canonical-target access returns sentinel `"DECEPTION"` → poisoned response.
- **Flow reality:** the browser calls canonical paths; the **Worker** translates to `/api/v1/node/{hex}` via the KV manifest and signs over the translated path. Direct canonical hits (origin-bypass attackers) get DECEPTION.
- `AegisResponseMutator` — 5–45 ms Gaussian jitter, fake `X-Powered-By` pool, `Server: AEGIS-M`, no-store.

## 8. Layer 1 — PQC

`AegisPqcKeyStore`: `MLDSAParameterSpec.ml_dsa_87` (FIPS 204), BouncyCastle 1.87, entropy-pool-seeded, **in-memory keys** (restart/weekly rotation = new keypair). `issueHybridToken(innerKeycloakJwt, tenant, sid)`: header `{"alg":"ML-DSA-87","typ":"PQC-JWT"}`, payload `{sub: <full classical JWT>, tenant, sid, iat, exp: iat+3600}`, signature `Signature.getInstance("ML-DSA-87", BouncyCastleProvider)` over `header.payload`. **JJWT/Nimbus prohibited** (no FIPS 204) — manual encode/decode; **`liboqs-java` permanently excluded** (breaks CI/CD).

## 9. Threat Pipeline & Encryption at Rest

Pipeline: filters → `RabbitMQAttackPublisher` (payload `timestamp/ip/ja3/target_path/trigger`) → `aegis.threat.exchange` DIRECT rk `threat.detected` → `aegis.threat.queue` (annotation-declared; absent from `definitions.json`) → `AegisThreatConsumer` → `CloudflareEdgeSyncService` (24 h local dedup) → KV `aegis:block:{ip}` / `aegis:block:ja3:{ja3}` TTL 86400 s (Worker enforces pre-backend; TARPIT-flagged IPs routed to honeypot path — see OP-05).

At rest: MariaDB TDE (AES_CTR, `MARIADB_ENCRYPTION_KEY`, tables/log/tmp/binlog encrypted) + 5 JPA converters (`AES/GCM/NoPadding`, 12-byte IV, `"v1:"+Base64`): `users.email`, `users.linkedin_access_token`, `contact_message.email/message`, `audit_logs.ip_address` (env keys `*_ENCRYPTION_KEY`). Device fingerprints = SHA3-256(IP+UA+Accept-Language) — **raw IPs never persisted** (DPDP). `DatabaseCredentialProvider` keeps DB creds in `char[]` with zeroing.

## 10. Open Items (⚠)

OP-04 Merkle audit root logged only — unpersisted/exported (legacy context) · vk pinning tracked prior to production prover rollout. All other items on this line (ZKP, BCSM, entropy, tarpit, tamper, X-AEGIS-Test-Token) are **closed (2026-11-24)** — see BE-02 §3/§4/§6/§7.
