# BE-00 — MASTER INDEX: Treishvaam `finance-api` Backend Documentation Suite

> **Document status:** Final consolidated edition (2026-08-22), re-verified 2026-08-23 against the full code export (336 files — see corrections below) **+ verified 2026-11-25** against live `finance-api` HEAD `4e9150e` (develop) per 09 §1 Ground-Truth Matrix. Verified against three independent sources: the full backend source export, the DevSecOps Operational Knowledge Tracker (VOL 1 + VOL 2, incidents 1–141 through 2026-08-20), and the live frontend/Edge-Worker codebase (`treishvaam-finance-frontend`, worker `treishfin-seo-worker`). Where sources disagree, **code wins**; conflicts are recorded in the Open Items register.
>
> **2026-08-23 verification pass (7 corrections, all code-evidenced):** ① market updater runs **35** tickers (not 34) — `scripts/market_data_updater.py` TICKERS list; ② **V48 is INCLUDED in the Liquibase master** (`db.changelog-master.xml:106` — adds `screen_resolution`/`platform_version` to `analytics_events`); the "V48 deliberately skipped" narrative was stale; ③ Grafana provisioning bodies **exist in the export** (`config/grafana-alerting.yml` with 5 full alert rules + `dashboards/mission-control.json`, both compose-mounted) — OP-11 downgraded/resolved; ④ OP-24 re-worded — the frontend `.env` holds **localhost dev values**, not live URLs; ②/③ also corrected in BE-03/BE-05/BE-06; ⑤ doc-tree §2 matches the repository as exported (no `archive_legacy/`, no `SESSION_MEMORY_ANCHOR.md`); ⑥ new OP-25 (OWASP scan job is non-blocking) and OP-26 (`github/workflows/` path) registered; ⑦ Terraform target corrected in BE-03 §11 (**VM.Standard.A1.Flex, 2 OCPU, 12 GB, ap-hyderabad-1**) — see `OCI-MIGRATION-TARGET-ARCHITECTURE.md`.
>
> **Suite scope:** This file set supersedes the legacy `BE-00..BE-14` suite. The 12 legacy files were audited (14 critical, ~53 moderate errors found — all in legacy files) and are retired to `docs/archive_legacy/`. Their surviving unique content has been absorbed into this suite.

---

## 1. System Overview

**Treishvaam `finance-api`** is the backend core of the Treishvaam Group financial-media platform: a **Java 21 / Spring Boot 3.5.16 modular monolith** surrounded by two satellite processes and a 24-service Docker stack.

| Dimension | Value (code-verified) |
|---|---|
| Core runtime | Java 21.0.13.1 (Temurin), Spring Boot `3.5.16`, Spring Cloud `2025.0.3`, WAR `finance-api.war` (host-mounted `backend-app.war`) |
| Security fabric | **AEGIS** — 9-layer zero-trust chain + BCSM validator consensus + post-quantum crypto (BouncyCastle 1.87, FIPS 204 ML-DSA-87, sole PQC provider) |
| Identity | Keycloak `26.8.2-3` OIDC (realm `treishvaam`, public client `finance-app` + bearer-only `finance-api`); frontend `keycloak-js 25.0.6`, PKCE S256, `useNonce: false`, `timeSkew: 86400` |
| Relational store | **MariaDB 11.4** ×2 (app `finance_db` + Keycloak), TDE at rest, ROW binlogging for PITR, Liquibase V1→V51 (24 tables) |
| Cache / state | Redis 7 ×2 (app + canary isolate); cache prefix `treishfin_` |
| Messaging | RabbitMQ `4.4.1-management` — topic `internal.exchange`, direct `dead_letter_exchange`, direct `aegis.threat.exchange` (annotation-declared) |
| Search / storage | Elasticsearch `8.19.25` (index `blog_posts`) · MinIO (bucket `treishvaam-uploads`, presign 7 d) |
| Satellites | **Go ZKP verifier** (gRPC `127.0.0.1:9090` — real BN254 Groth16 `AuthCircuit`, `ChallengeBinding == MiMC(Secret, Challenge)`, 5m `aegis:zkp:used:{id}` replay lock) · **Python transcoder** (`alpine:3.24`, non-root GID 1000, ffmpeg → 1080p HLS, `video.transcode.queue`) · **Python yfinance updater** (**40 tickers** — indices, commodities, FX, crypto-INR, `URL.create()` builder) |
| Edge path | Cloudflare Worker `treishfin-seo-worker` (HMAC signer, MTD translator, GEO router) → Tunnel → OpenResty `:80` (JA3 Lua) → backend ×2 `:8080` |
| HIDS / deception | Wazuh 4.14.10 (manager+agent) · Thinkst canarytokens |
| Observability | Prometheus · Loki+Promtail · Tempo (Zipkin `:9411`/OTLP) · Grafana (`mission-control`) |
| CI/CD | Engine A (GitHub Actions, self-hosted runner, GPG gate) → `sudo systemd-run --no-block auto_deploy.sh` (Engine B, tiered ignition) |

## 2. Documentation Suite (final tree)

```
docs/
├── BE-00-INDEX.md               ← you are here
├── BE-01-ARCHITECTURE.md        runtime topology, filter chain, services deep-dive
├── BE-02-SECURITY.md            AEGIS 9 layers, BCSM, ZKP status, encryption at rest
├── BE-03-DEPLOYMENT.md          Engine A/B, anti-regression rules, incident runbook, history
├── BE-04-API.md                 REST reference + frontend consumption map
├── BE-05-DATABASE.md            MariaDB, Liquibase matrix, 23 tables, Redis/Rabbit/ES/MinIO
├── BE-06-OBSERVABILITY.md       metrics/logs/traces/telemetry reality
├── CROSS-SYSTEM-CONTEXT.md      the Frontend/Edge integration contract (self-contained)
├── DISASTER_RECOVERY.md         BCDR plan (Sev-1 bridge collapse, PITR, Keycloak, MTD split-brain, FE rollback)
├── DOCS_AUDIT_REPORT.md         2026-08-22 audit of the legacy doc set (historical record)
└── RUNBOOKS.md                  operational runbooks (AEGIS triage, secret rotation, KV purge, runner, TDE)
```

> [!NOTE]
> **Tree corrected 2026-08-23** to match the repository as exported: `SESSION_MEMORY_ANCHOR.md` and `docs/archive_legacy/` (referenced by the 2026-08-22 edition) are **not present** in the current export; `DISASTER_RECOVERY.md`, `DOCS_AUDIT_REPORT.md` and `RUNBOOKS.md` **are** present and are listed above. The legacy-suite retirement narrative stands; the archive folder itself was not shipped with this export.

| Document | Critical contents |
|---|---|
| **BE-01-ARCHITECTURE.md** | 24-service topology, request lifecycle, two-layer filter registration, services layer (market engine, content pipeline, analytics, video), threading, multi-tenancy |
| **BE-02-SECURITY.md** | Edge HMAC contract, ZKP gating (honest stub status), deception/tarpits, PQC, BCSM consensus, AEL DSL, rate limiting, sanitization, MTD, encryption at rest |
| **BE-03-DEPLOYMENT.md** | Engine A/B, tiered ignition, **`docker compose down` fatality**, 13 golden anti-regression rules, corrected incident runbook, implementation history 2026-06→08 |
| **BE-04-API.md** | Every endpoint, auth tier, DTOs, error contract, how the frontend actually calls each API |
| **BE-05-DATABASE.md** | TDE, Liquibase V1→V50 (V48 included — telemetry columns), 23-table inventory, encrypted columns |
| **BE-06-OBSERVABILITY.md** | Prometheus/promtail/Loki/Tempo/Grafana as deployed + telemetry beacon reality |
| **CROSS-SYSTEM-CONTEXT.md** | Worker signing code, KV keys/TTLs, MTD translation flow, bot matrix, known integration bugs |

## 3. Verified Architectural Invariants (code-locked)

| # | Invariant | Verified value |
|---|---|---|
| 1 | Edge HMAC material | `HMAC-SHA-512(AEGIS_EDGE_SECRET, "${pathname}:${timestamp}:${clientIp}")` — pathname **without query string** (worker `.split('?')[0]`), signed over the **exact backend path** (post-MTD translation), not the public path |
| 2 | Edge headers | `X-Aegis-Edge-Signature` (hex) + `X-Aegis-Edge-Timestamp` (**epoch ms**) + `X-Aegis-Client-IP`; worker adds `X-Tenant-ID: finance`, `X-Visitor-City/Country` |
| 3 | Edge drift / failure | **±300 s** (`MAX_TIME_DRIFT_SECONDS = 300`, ms compare); failure = **HTTP 403** `sendError`; constant-time `MessageDigest.isEqual`; no 0-byte-discard branch exists |
| 4 | Edge bypass | Only `(loopback | 10.* | 192.168.* | 172.*) AND (/actuator* | /health*)`. Docker-bridge `172.18.*` misses log DEBUG but still 403 |
| 5 | Relational DB | **MariaDB 11.4**, healthchecks/`backup.sh`/`restore.sh` use `MYSQL_PWD` + `--skip-ssl` (client default `--ssl=0`), `MariaDBDialect`, TDE `file_key_management` AES_CTR |
| 6 | Auth | No backend login/refresh/logout endpoints; Keycloak OIDC Bearer only; no cookies; no CSRF; `realm_access.roles` (+client roles) → `ROLE_*` |
| 7 | MTD (L9) | Targets `/api/v1/{admin,auth,users,dashboard,analytics}` → `/api/v1/node/{8-hex}`; Redis `aegis:mtd:manifest` TTL **24 h** + `aegis:mtd:lock` 30 s (`setIfAbsent`, split-brain fix 2026-08-03); rotation daily 03:00 + Sun 04:00 + emergency; sentinel `"DECEPTION"`; exemptions `.startsWith("/api/v1/analytics/event")`, `/api/v1/aegis/telemetry`. **The browser never calls `/api/v1/node/*` — the Worker translates canonical→obfuscated via the KV manifest** |
| 8 | Threat pipeline | `RabbitMQAttackPublisher` → `aegis.threat.exchange` (DIRECT, rk `threat.detected`) → `aegis.threat.queue` → `AegisThreatConsumer` → `CloudflareEdgeSyncService` → KV `aegis:block:{ip}` / `aegis:block:ja3:{ja3}` TTL 86400 s, 24 h dedup |
| 9 | CI/CD handoff | `sudo systemd-run --no-block --uid=vboxuser --gid=vboxuser --unit=treishvaam-deploy-$(date +%s) … auto_deploy.sh --force` |
| 10 | Container lifecycle | **`docker compose down` never appears in any script** (deletes `treish_net` → SSH loss). Engine B = `docker compose rm -f` + tiered `up -d --no-deps`, backend scale 1→2 after health |
| 11 | Runner recovery | Engine B ends with `sudo -n systemctl restart actions.runner.*` (TCP half-open zombie cure); `setup_runner_service.sh` is empty (OP-09) |
| 12 | CI trigger | Empty commits are dropped by GitHub path filters — canonical trigger is `echo " " >> VER.txt` + real commit (**VER.txt is a deliberate mechanism, not junk**) |
| 13 | Secrets | Infisical machine identity → temp `.env` → Flash & Wipe; Cloudflare token **IP-fence removed 2026-07-15** (public NAT), current TTL expires **2026-12-31** |
| 14 | Rate limits | `/auth/` 10 RPM · GET 200 RPM · other 60 RPM · 429 JSON · Redis failure → fail-closed 503 |
| 15 | Field encryption | 5 JPA converters, `AES/GCM/NoPadding`, 12-byte IV, `"v1:"+Base64(IV‖ct)`, env keys `*_ENCRYPTION_KEY` |
| 16 | Prohibited dependencies | `liboqs-java` (breaks CI/CD); JJWT/Nimbus (no FIPS 204 support) — manual PQC-JWT encoding; BouncyCastle 1.87 (FIPS 204 `ML-DSA` / `ml_dsa_87`) is the sole PQC provider |

## 4. Consolidated Open Items Register (⚠ Requires clarification)

| ID | Item | Severity |
|---|---|---|
| OP-01 | **Resolved (2026-11-24)** — real BN254 Groth16 verifier: `aegis/zkp-service/circuit/auth_circuit.go` (Groth16, `ChallengeBinding == MiMC(Secret, Challenge)`), `aegis/zkp-service/circuit/schnorr_lattice.go` (`InitVerifier`/`VerifyLatticeSchnorrProof`), Redis `aegis:zkp:used:{id}` 5m replay lock (`AegisZkpAdminFilter`) — Master Spec 09 §1 | Resolved |
| OP-02 | **Resolved (2026-11-24)** — `AegisEntropyManager` (512-byte SHA3-512 pool, `isHealthy()` <120s); `PowChallengeIssuer` 2-min TTL + `aegis:pow:used:{nonce}` — Master Spec 09 §1 | Resolved |
| OP-03 | **Resolved (2026-11-24)** — `AegisBcsm` `VALIDATORS_TOTAL = 7` (DeceptionValidator, EntropyHealthValidator, HidsIntegrityValidator live; 100 ms quorum) — Master Spec 09 §1 | Resolved |
| OP-04 | Merkle audit root logged, never persisted/exported | Medium |
| OP-05 | **Resolved (2026-11-24)** — `AegisTarpitController` serves `/api/v1/aegis/tarpit/trap` (route registered, `SecurityConfig.java:241`) — Master Spec 09 §1 | Resolved |
| OP-06 | `spring.threads.virtual.enabled` unset (explicit `newVirtualThreadPerTaskExecutor()` usage in AEGIS components IS real; Tomcat remains platform-threaded) | Medium |
| OP-07 | **Resolved (2026-11-24)** — `TenantInterceptor` registered via `addInterceptors()` in `WebConfig.java` (constructor injection, `/api/**`, `/sitemap*`, `/llms.txt`) — Master Spec 09 §1 | Resolved |
| OP-08 | **Resolved (2026-11-24)** — `FilterConfig` disables root auto-registration of all 6 chain filters; order strictly owned by `SecurityConfig.java` — Master Spec 09 §1 | Resolved |
| OP-09 | `setup_runner_service.sh` empty file | Medium |
| OP-10 | Legacy docs claimed ±30 s drift / 0-byte discard — corrected everywhere (code = ±300 s / 403) | Resolved |
| OP-11 | ~~Grafana provisioning bodies absent from export~~ **RESOLVED 2026-08-23**: `config/grafana-alerting.yml` (5 full managed rules: `high_error_rate`, `high_backend_error_rate`, `Slow API Response` p95>2 s, `secret_key_rotation_due` >90 d, Dead-Man's `engine_b_stuck_in_progress`) and `dashboards/mission-control.json` both exist **and are compose-mounted** (`docker-compose.yml:792-796`). Alert rule uids are snake_case, not CamelCase | Resolved |
| OP-12 | Liquibase orphans: `db.changelog-3.0.xml` unreachable, dup `V30` changeset ids, empty `V22` (0 bytes), missing V7/V23/V24; only V42 has rollback. ~~V48 deliberately skipped~~ **CORRECTED 2026-08-23: V48 IS included** (`db.changelog-master.xml:106`) — adds `screen_resolution` + `platform_version` (VARCHAR(50)) to `analytics_events`; the master file's own history says the V48 collision was "resolved", i.e. the skip decision was reversed before this export | Medium |
| OP-13 | `video.transcode.queue` + threat topology absent from `definitions.json` (annotation/runtime-declared) | Low |
| OP-14 | **Resolved (2026-11-24)** — `VideoKeyController` returns deterministic HMAC-SHA256 16-byte AES-128 keys (`VIDEO_KEY_MASTER_SECRET` / `CONTENT_SIGNING_KEY`, `^[0-9]{1,18}$`, fail-closed 503) — Master Spec 09 §1 | Resolved |
| OP-15 | **Resolved (2026-11-24)** — archived news (`NewsHighlightController.java:16-19,58` — `GET /archived` returns truly archived items), PoW TTL (`PowChallengeIssuer` 2-min + `aegis:pow:used:{nonce}`), tamper audit (`BlogPostServiceImpl.java:77,684` — `CONTENT_TAMPER_DETECTED` + `app.security.block-tampered-posts`); **minor residual still open:** `GET /health` falls through to authenticated, and the disabled `AlphaVantageProvider` URL is malformed — Master Spec 09 §1 | Resolved |
| OP-16 | **Resolved (2026-11-24)** — nginx `api_write`/`api_read` zones declared in `docker-compose.yml` and mounted (`RABBITMQ_SERVER_DEFINITION_FILE` / `definitions.json`) — Master Spec 09 §1 | Resolved |
| OP-18 | **Resolved (2026-11-24)** — frontend app-route proxies now sign the 3-part backend contract (Master Spec 09 §1) | Resolved |
| OP-19 | **Resolved (2026-11-24)** — frontend preview path now includes `/v1` (`/api/v1/uploads/**`) — Master Spec 09 §1 | Resolved |
| OP-20 | Faro endpoint mismatch: frontend posts to `NEXT_PUBLIC_FARO_URL` (default `backend…/faro/collect`); **no frontend call to `/api/v1/monitoring/ingest` exists** (controller + Alloy forward verified in backend code) | Medium |
| OP-21 | Biometric hash: code uses WebCrypto **SHA-256**; frontend README claims SHA3-256 | Low |
| OP-22 | Liquibase skew risk: V50 existed before registration in master (boot `SchemaManagementException` 2026-08-13 — fixed); health patience now 35×15 s = 525 s vs `start_period 240s` | Resolved |
| OP-23 | Junk files: `aegis/v.txt` (empty), `terraform/vdsfggfd.txt` (`VER.txt` reclassified as CI trigger — keep) | Low |
| OP-24 | Frontend `.env` (local folder) contains `NEXT_PUBLIC_API_URL` / `NEXT_PUBLIC_AUTH_URL` **localhost dev values** (`localhost:8080`) — **corrected 2026-08-23**: earlier wording said "live-looking"; the values are dev-local. Still git-ignored and must never be committed | Low |
| OP-25 | **Resolved (2026-11-24)** — the swallowing `|| echo` was removed from `deploy.yml` (lines 158–159); `-DfailBuildOnCVSS=8` now fails hard — Master Spec 09 §1 | Resolved |
| OP-26 | **NEW 2026-08-23:** the workflow lives at `github/workflows/deploy.yml` in this export (no leading dot). GitHub Actions only recognizes `.github/workflows/` — if the real repo mirrors this path, the pipeline would not trigger; most likely a ZIP-export flattening artifact, but verify on the real checkout | Medium |

## 5. Reading Paths by Role

- **New backend engineer:** BE-01 → BE-04 → BE-05.
- **Security engineer:** BE-02 → CROSS-SYSTEM-CONTEXT → §3 invariants above.
- **DevOps / SRE:** BE-03 → BE-06.
- **Frontend/Edge session:** **CROSS-SYSTEM-CONTEXT.md only** (self-contained bridge).

## 6. Documentation Standards (binding)

1. **Never hallucinate** — every value traces to code, the Knowledge Tracker (VOL 1/2), or the frontend repo; otherwise it carries "⚠ Requires clarification".
2. **Code wins over docs**; the Knowledge Tracker wins over legacy REF/SKILLS files where they conflict with code.
3. **No `AI-CONTEXT` headers; no secret values** — names and public identifiers only (KV namespace IDs live in `wrangler.toml`, tunnel ID in `cloudflared/config.yml`).
4. GitHub alerts + Mermaid diagrams; append-only history lives in the Knowledge Tracker, not in these files.
