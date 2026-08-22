# DOCS AUDIT REPORT — `docs.zip` vs `finance-api` Codebase

> **Date:** 2026-08-22 · **Scope:** all 21 files in `docs.zip` (the repository `/docs` folder: 12 legacy docs + the 8-file rewritten suite + `SESSION_MEMORY_ANCHOR.md`).
> **Method:** every doc was read in full and every checkable claim compared against code-verified facts extracted directly from the backend source export (Parts 1–12). Verdict scale: **CRITICAL** (would mislead an engineer/integrator into wrong action) · **MODERATE** (wrong but recoverable) · **MINOR** (stale/cosmetic).

---

## 1. Executive Verdict

| Category | Count | Files |
|---|---|---|
| ✅ **Accurate — keep as-is** | 9 | BE-00-INDEX, BE-01-ARCHITECTURE, BE-02-SECURITY, BE-03-DEPLOYMENT, BE-04-API, BE-06-OBSERVABILITY, CROSS-SYSTEM-CONTEXT, BE-11-GEO-AI, SESSION_MEMORY_ANCHOR |
| 🔧 **Accurate core, small fix** | 1 | BE-05-DATABASE (25→23 table count — **fixed during this audit** in both copies) |
| 🛠️ **Required, needs update** | 4 | BE-10-CHANGELOG, BE-12-LOCAL-SETUP, BE-13-SECRET-MATRIX, BE-14-INCIDENT-RUNBOOK |
| ♻️ **Not required standalone — merge unique content, then delete** | 7 | BE-02-CORE, BE-03-API, BE-04-SERVICES, BE-06-INFRA-DEVOPS, BE-07-SECURITY, BE-08-SEO-EDGE, BE-09-DEPLOYMENT |

**Wrong-claim tally:** 14 CRITICAL · ~53 MODERATE · ~25 MINOR — **all in the 12 legacy docs**, except the single BE-05 count slip in the new suite (now fixed). The rewritten 2026-08-22 suite and BE-11-GEO-AI contain **zero** contradictions of the codebase.

---

## 2. Master Verdict Table

| Doc | Provenance | Wrong claims (C/M/m) | Unique content worth keeping | Verdict |
|---|---|---|---|---|
| BE-00-INDEX.md | new (08-22) | 0/0/0 | Invariants hub + open-items register | **KEEP** |
| BE-01-ARCHITECTURE.md | new (08-22) | 0/0/0 | Filter registration model, threading, profiles | **KEEP** |
| BE-02-CORE.md | legacy v0.0.0.7 (05-29) | **4**/11/3 | Argon2id claim, liboqs exclusion, Breeze=ICICI identity, CB params | **MERGE → BE-02-SECURITY** |
| BE-02-SECURITY.md | new (08-22) | 0/0/0 | Canonical AEGIS deep-dive | **KEEP** |
| BE-03-API.md | legacy v0.0.0.7 (05-29) | **2**/6/0 | Unique endpoint rows missing from BE-04 | **MERGE → BE-04-API** |
| BE-03-DEPLOYMENT.md | new (08-22) | 0/0/0 | Entire CI/CD domain | **KEEP** |
| BE-04-API.md | new (08-22) | 0/0/0 | Canonical endpoint+DTO reference | **KEEP** (absorb BE-03-API rows) |
| BE-04-SERVICES.md | legacy v0.0.0.7 | 0/7/2 | Only services-layer deep-dive in repo | **MERGE → BE-01 §8 expansion** |
| BE-05-DATABASE.md | new (08-22) | 0/1/0 *(fixed)* | Liquibase matrix + data topology | **KEEP** (count corrected 25→23) |
| BE-06-INFRA-DEVOPS.md | legacy (06-03) | **3**/10/5 | Compose rules, scripts table, sed/NOAUTH lore, Salt/Packer | **MERGE → BE-03-DEPLOYMENT** |
| BE-06-OBSERVABILITY.md | new (08-22) | 0/0/0 | Authoritative observability reference | **KEEP** |
| BE-07-SECURITY.md | legacy (05-29) | 0/3/1 | AEGIS 9-layer taxonomy, PII matrix | **MERGE → BE-02-SECURITY** |
| BE-08-SEO-EDGE.md | legacy (05-29) | 0/0/0 *(worker-side unverifiable)* | Sitemap KV architecture, SPA fallback, canonicals, robots | **MERGE → BE-11-GEO-AI** |
| BE-09-DEPLOYMENT.md | legacy v0.0.0.9 (06-03) | **3**/5/4 | Runner recovery, deploy sequences, rotation protocol | **MERGE → BE-03-DEPLOYMENT** |
| BE-10-CHANGELOG.md | legacy → v0.0.0.9 | **1**/3/3 (+ ~9 missing entries) | Only version history in repo | **UPDATE, then keep** |
| BE-11-GEO-AI.md | legacy (05-29) | 0/0/0 | AI-bot matrix, GEO flow, agro checklist | **KEEP** (merge target for BE-08) |
| BE-12-LOCAL-SETUP.md | legacy (06-03) | 0/3/1 | Only local-setup doc | **UPDATE, then keep** |
| BE-13-SECRET-MATRIX.md | legacy (06-03) | 0/2/4 | Vault routing, keygen, rotation protocols | **UPDATE, then keep** |
| BE-14-INCIDENT-RUNBOOK.md | legacy (05-29) | **1**/3/2 | Only incident runbook (8 scenarios) | **UPDATE, then keep** |
| CROSS-SYSTEM-CONTEXT.md | **new (08-22)** | 0/0/0 | The edge/backend wire contract | **KEEP** |
| SESSION_MEMORY_ANCHOR.md | new (08-22) | 0/0/0 | Session protocol artifact | Keep outside `/docs` (not a repo doc) |

---

## 3. The 14 CRITICAL Fabrications (all in legacy docs)

These are claims an engineer would act on and break something:

| # | Doc | Claim (paraphrased) | Code reality |
|---|---|---|---|
| 1 | BE-02-CORE | Filter chain = Deception(HP) → CORS → InputSanitization → RateLimiting inside security chain | Servlet chain: EdgeValidation(HP) → HP+1 tie {IpResolution, RequestId, Main, ZkpAdmin} → HP+2 tie {CORS, InputSanitization, ResponseMutator} → Deception(−105) → securityChain(−100); RateLimiting **precedes** InputSanitization in the chain; 4 filters missing from the doc |
| 2 | BE-02-CORE | All 7 BCSM validators functional (entropy health, Wazuh HIDS, deception corpus) | EntropyHealth + HidsIntegrity = constant-0/ALLOW stubs; DeceptionValidator reads one header only |
| 3 | BE-02-CORE | ZKP = "Schnorr-over-Lattice (Fiat-Shamir with Aborts) via gnark" | Go verifier accepts **any non-empty payload**, challenge ignored; circuits are scaffolding |
| 4 | BE-02-CORE | MTD maps `/api/v1/posts` → `/api/v1/data-node/2b9c` | Targets are only `/api/v1/{admin,auth,users,dashboard,analytics}` → `/api/v1/node/{8-hex}` |
| 5 | BE-03-API | `AegisMtdController` REST endpoints `GET /manifest`, `POST /sync-edge` at `/api/v1/aegis/mtd` | **No such endpoints** — it is a `@Service` with cron jobs; its change history's "confirmed in code" was a false confirmation |
| 6 | BE-03-API | Tarpit endpoint `GET/POST /api/v1/aegis/tarpit/trap`, 1 byte/sec | **Endpoint does not exist** (in-filter tarpitting); rate is 1 byte per **10 s** |
| 7 | BE-06-INFRA | ModSecurity (OWASP CRS) WAF protects origin | **ModSecurity stripped**; `whitelist.conf` leftover, unmounted |
| 8 | BE-06-INFRA | Deploy handoff = `nohup ./scripts/auto_deploy.sh … &` | Handoff = `sudo systemd-run --no-block --uid=vboxuser --gid=vboxuser --unit=treishvaam-deploy-$(date +%s) …` |
| 9 | BE-06-INFRA | Engine B = `up -d --build --remove-orphans` + `restart backend nginx` | Tiered `up -d --no-deps` (T1–T5), teardown `compose rm -f`, backend scale 1→2 after health gate (35×15 s curl) |
| 10 | BE-09 | Same `nohup` handoff claim | Same fix as #8 |
| 11 | BE-09 | Same `--remove-orphans` orchestration claim (+ missing integrity gate, sysctls, ghost purge, Flash & Wipe, Telegram, runner restart) | Same fix as #9 |
| 12 | BE-09 | Backup = `docker exec … mysqldump --all-databases \| gzip \| sudo tee /backup/…`, Redis BGSAVE docker-cp | Real pipeline: `mysqldump --single-transaction --quick --master-data=2 finance_db \| gzip \| openssl aes-256-cbc-pbkdf2` → MinIO `s3://treishvaam-backups` via aws-cli, 24 h loop, 7-day retention. Following the doc produces **unencrypted VM-disk backups outside the retention system** |
| 13 | BE-10 | "Converted ZKP Dockerfile to multi-stage (distroless)" | Code embeds a **single-stage mandate** (multi-stage panicked with Exit Code 2) — the changelog records the now-prohibited config |
| 14 | BE-14 | Data-restore recipes via manual `/backup/*` paths | Restore is **confirm-gated**, pulls from MinIO `s3://treishvaam-backups` and decrypts via openssl — the documented recovery path doesn't exist |

---

## 4. Recurring Error Patterns (fix once, fix everywhere)

1. **Deploy mechanism folklore** (docs 8–11 above): `nohup` + `--remove-orphans` + `restart` appears in BE-06-INFRA, BE-09, BE-10. Reality: systemd-run handoff, tiered `--no-deps`, `rm -f`, scale 1→2.
2. **Manual `/backup` mythology**: BE-09 §6/§7, BE-12 "Data Operations", BE-14 restore recipes all predate the backup-service container. Reality: encrypted MinIO pipeline only.
3. **Stub systems presented as live**: ZKP crypto (BE-02-CORE, BE-07), 3 BCSM validators (BE-02-CORE), entropy threshold routing (blueprint-era), WAF (BE-06-INFRA), MTD REST endpoints (BE-03-API).
4. **Stale hardcoded dates/hosts**: CF token "expires 2026-08-26" (BE-07, BE-13, BE-14 — policy is 90-day TTL + 7-day-prior alert), host IP `192.168.29.111` vs inventory `192.168.56.101`.
5. **Unit drift**: `X-Aegis-Edge-Timestamp` described as **seconds** in BE-07 — code compares **milliseconds** (±300 000 ms).
6. **Compose invocation myths**: `env_file:` directive (BE-06-INFRA, BE-09, BE-13 — no such key; `.env` is implicitly interpolated), `-Xmx768m` (real: 256 m), "17/20 containers" (real: 24 services), Prometheus "Node/Redis exporters" (real: one backend job).

---

## 5. What's Genuinely Unique in the Doomed Docs (merge before deleting)

| Source doc | Salvage into | Items |
|---|---|---|
| BE-02-CORE | BE-02-SECURITY | Argon2id password-hashing claim*, liboqs-java exclusion constraint, Breeze = ICICI Direct identity, Resilience4j parameter table* (*re-verify against source first) |
| BE-03-API | BE-04-API | Row-by-row reconcile of unique endpoint rows (`/recent`, `/featured`, `/tags/{tag}`, `/admin/publish/{id}`, `/admin/cache/clear`, system-properties, `/analytics/realtime`, `/status/history`, `/search/reindex`, market `/indices` `/movers` `/history`) — verify each against controllers before adding |
| BE-04-SERVICES | BE-01 §8 (expand into full services section) | Secure Stream & Commit pattern, Python decimal-28 precision, Smart Sync, HistoricalDataCache design, CB fallback behaviors, GA4 credential mount, per-tenant sitemap table, scheduler TenantContext rules |
| BE-06-INFRA | BE-03-DEPLOYMENT | §2.7 compose critical rules (YAML arrays, never-quote `.env`, `$$` escaping), scripts inventory table, boundary-sed/NOAUTH crash lore, SaltStack/Packer section |
| BE-07-SECURITY | BE-02-SECURITY | (Already covered — only the PII converter→column→key matrix adds marginal value) |
| BE-08-SEO-EDGE | BE-11-GEO-AI | Sitemap KV cache architecture, SPA-404 fallback, canonical rules, robots policy, OpenSearch |
| BE-09-DEPLOYMENT | BE-03-DEPLOYMENT | §3.5 runner recovery + clock-drift procedure, §4 per-repo deploy sequences, §8 CF token rotation protocol, "Last Verified Backup State" concept |

---

## 6. Required Updates for the 4 "UPDATE" Docs

- **BE-10-CHANGELOG** — correct the multi-stage Dockerfile entry (#13), nohup→systemd-run, `--remove-orphans`→tiered, registry-push story; append missing 2026-06→08 history (ModSecurity strip, Yauaa OOM fix, Loki 50 MB/s fix, Tempo yaml fix, Keycloak crash fix, CVE-006, Incidents 33/41/45).
- **BE-12-LOCAL-SETUP** — dev API port is **8081** (doc says 8080); compose service names are `minio`, `elasticsearch`, `rabbitmq` (not `treishvaam-*`); replace manual data-ops with backup-service reality; add code-verified dev facts (MariaDB URL, ddl-auto update, swagger on, Keycloak issuer absent in dev, `.env.dev.example` DEV_* names, Testcontainers stack incl. H2-fallback conflict).
- **BE-13-SECRET-MATRIX** — delete `YAHOO_FINANCE_API_KEY` (history runs keyless via yfinance); fix `env_file:` claim; add `CLOUDFLARE_ACCOUNT_ID` + `CLOUDFLARE_THREAT_KV_NAMESPACE_ID` and the DB-90-day rotation rule; replace hardcoded expiry dates with TTL policy.
- **BE-14-INCIDENT-RUNBOOK** — rewrite restore path (#14); fix sitemap diagnostic (`/api/v1/sitemap/sitemap.xml` does not exist — use `/api/public/sitemap/meta`); fix the `:9090` port-collision misdiagnosis (that's ZKP gRPC, not Prometheus); replace stale token date.

---

## 7. Target End-State for `/docs` (13 files)

```
docs/
├── BE-00-INDEX.md              (keep)
├── BE-01-ARCHITECTURE.md       (keep + absorbed BE-04-SERVICES content)
├── BE-02-SECURITY.md           (keep + salvaged BE-02-CORE/BE-07 nuggets)
├── BE-03-DEPLOYMENT.md         (keep + salvaged BE-06-INFRA/BE-09 sections)
├── BE-04-API.md                (keep + verified BE-03-API endpoint rows)
├── BE-05-DATABASE.md           (keep — table count fixed 2026-08-22)
├── BE-06-OBSERVABILITY.md      (keep)
├── BE-10-CHANGELOG.md          (updated)
├── BE-11-GEO-AI.md             (keep + BE-08 sitemap/SEO sections; drop its dead FIN-03 cross-ref)
├── BE-12-LOCAL-SETUP.md        (updated)
├── BE-13-SECRET-MATRIX.md      (updated)
├── BE-14-INCIDENT-RUNBOOK.md   (updated)
└── CROSS-SYSTEM-CONTEXT.md     (keep — authoritative bridge)
```

Deleted after merge: BE-02-CORE, BE-03-API, BE-04-SERVICES, BE-06-INFRA-DEVOPS, BE-07-SECURITY, BE-08-SEO-EDGE, BE-09-DEPLOYMENT (7 files). `SESSION_MEMORY_ANCHOR.md` stays out of the repo (session artifact).

## 8. Note on the Audit Itself

One correction was applied to the new suite during this audit: BE-05-DATABASE (and the BE-00 reference to it) claimed a "25-table inventory" while enumerating 23 — the enumeration matches the changelogs, so the count is now **23** in both the master suite (`TREISHVAAM_BACKEND_DOCS/`) and the zip copy (`docs_zip_extracted/docs/`). The `X-AEGIS-ZKP` heal-endpoint row in CROSS-SYSTEM-CONTEXT was double-checked against the filter source: it is **correct** (the Incident-45 removal happened in the *controller*; the *filter* special-case remains).
