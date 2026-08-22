# RUNBOOKS — Treishvaam `finance-api` Operational Runbooks

> **Location:** `finance-api/docs/RUNBOOKS.md` · **Version:** 1.0 (2026-08-22)
> **Verification basis:** backend source (`scripts/`, `backup/`, `docker-compose.yml`, AEGIS filters), frontend `worker/worker.js`, Knowledge Tracker VOL 1+2. Every command is taken from the actual scripts; deviations are flagged.

---

## 1. AEGIS Incident Triage Matrix (HTTP 403 / 429 differentiation)

The edge returns several distinct failure bodies. Identify the layer by the **exact message**, then follow the matching runlet.

| Observed response | Layer | Root cause | Diagnostic | Fix |
|---|---|---|---|---|
| `403` · *"Origin Access Denied - Direct IP Access Blocked"* | **L4 Edge HMAC** | Request arrived **without** `X-Aegis-Edge-Signature`/`X-Aegis-Edge-Timestamp` — direct-to-origin access (bypassing the Worker), or the Worker omitted headers | `docker logs treishvaam-backend-11 2>&1 \| grep "Direct IP"` — if `remoteAddr` is a real client IP, traffic bypassed Cloudflare (expected for scanners); if it is the tunnel, check Worker proxy path | Expected behavior for origin-bypass traffic. If legitimate traffic: verify the Worker route covers the hostname and `BACKEND_API_URL` points at the tunnel, not the public domain |
| `403` · *"- Malformed Timestamp"* | **L4 Edge HMAC** | `X-Aegis-Edge-Timestamp` is not **epoch milliseconds** | Inspect the request headers at the Worker (`console.log` in `generateEdgeSignature`) | Sender must use `Date.now().toString()`. Known offender: the frontend app-route proxies sending `X-Aegis-Timestamp` (see §1.1) |
| `403` · *"- Payload Expired"* | **L4 HMAC clock drift** | `\|now − ts\| > 300 000 ms` — almost always the **backend VM clock** (Cloudflare edge clocks are reliable) | On the VM: `timedatectl status` (look for drift > 300 s); `timedatectl set-ntp true`; check `chrony`/`systemd-timesyncd` | Engine B already runs `timedatectl set-ntp true` before secret injection — if drift recurs between deploys, fix NTP reachability from the VM. Note Keycloak `timeSkew: 86400` on the frontend absorbs UI-side skew; the HMAC filter does **not** tolerate it |
| `403` · *"- Invalid Cryptographic Signature"* | **L4 secret/path mismatch** | (a) `AEGIS_EDGE_SECRET` differs between Worker and backend; (b) signed string wrong — must be `HMAC-SHA-512(secret, backendPath.split('?')[0] + ":" + ts + ":" + clientIp)` over the **MTD-translated backend path**; (c) frontend app-route proxies sign `path:timestamp` only (2-part) — known bug | Compare `wrangler secret list`/local value vs Infisical `AEGIS_EDGE_SECRET` (64-char). Reproduce: `curl -s https://treishvaamfinance.com/llms.txt -o /dev/null -w "%{http_code}"` (Worker-signed → 200). Test direct: reproduce the signature in a shell with `openssl dgst -sha512 -hmac "$SECRET"` over the same string | Resync the secret (§2); fix senders that sign the wrong string. The app-route proxy fix is pending (open item OP-18) |
| `403` · `{"error":"Access Denied","_aegis_integrity":"blocked-by-edge-consensus"}` | **L4-ADA Edge block** (Worker KV) | IP or JA3 present in Cloudflare KV `aegis:block:{ip}` / `aegis:block:ja3:{ja3}` (written by the backend threat pipeline, TTL 86 400 s) | CF Dashboard → Workers KV → `AEGIS_THREAT_KV` → inspect the two key families. Backend side: `docker logs <backend> \| grep -i "threat"` (RabbitMQ `aegis.threat.exchange` publisher) | If a false positive: delete the KV key (`DELETE .../values/aegis:block:{ip}` via API or dashboard). Note the backend also keeps a **24 h in-memory dedup** (`CloudflareEdgeSyncService`) — restarting the backend clears it early. Do not disable the pipeline |
| `429` · `{"error":"Request rejected"}` | **BCSM BLOCK** (L5 behavioral / consensus) | Behavioral validator scored >80 (missing/unknown JA3 +40, known-malicious JA3 strikes +60, missing biometric +30) or ≥3 BLOCK votes | Check `X-JA3-Fingerprint` at nginx (pseudo-hash `md5(ua\|tls\|cipher)` when Cloudflare doesn't supply `cf-client-ja3`); backend `ja3RiskCache` is **in-memory** — `docker logs` shows strikes | Restarting a backend replica clears in-memory strikes; legitimate clients flagged by bad JA3 data should be verified via the AI-crawler rDNS bypass list. Rate-limit 429s carry a different body (see below) |
| `429` · `{"error":"Rate limit exceeded. Try again in 60 seconds."}` | **L7 Bucket4j** | Per-IP category bucket exhausted (`/auth/` 10 RPM · GET 200 RPM · other 60 RPM) | Redis keys `clientIp:category` (dedicated Bucket4j client) | Wait or raise limits in `RateLimitingFilter` (code change). Redis outage produces **503 fail-closed**, not 429 |
| Deception content (fake `.env`, fake JWT, redirect loop `?d=N`) | **L3 / L9 sentinel** | Request hit a honeypot pattern **or** a canonical protected path (`/api/v1/{admin,auth,users,dashboard,analytics}`) — the MTD sentinel `"DECEPTION"` | Confirm the request went direct-to-origin or canonical: Worker-translated `/api/v1/node/{hex}` paths never trigger this | No fix needed for attackers. For legit clients: they must go through the Worker; never call canonical admin paths directly |
| `404/401` on `/api/v1/node/{hex}` | **L9 MTD mismatch** | Stale hex — manifest rotated (daily 03:00 / Sun 04:00 / emergency) and the Worker has an old manifest | Compare manifests (§DISASTER_RECOVERY scenario 4): Redis `aegis:mtd:manifest` vs KV `aegis:mtd:manifest` | Force manifest re-sync (DR-4). Clients must never cache translated paths |

### 1.1 Known-bug quick reference
- **App-route proxies** (`app/llms.txt`, `app/ai-feed.md`, `app/ontology.json` route handlers) sign **2-part** and send `X-Aegis-Timestamp` → guaranteed 403 against the current filter. Until fixed, their KV-first fallbacks mask it.
- **Frontend preview URLs** `/api/uploads/...` (missing `/v1`) → 404 against backend `/api/v1/uploads/**`.
- **Worker tarpit rewrite** targets `/api/v1/aegis/tarpit/trap` — no backend handler exists (in-filter tarpitting only).

---

## 2. Two-Sided Secret Rotation — `AEGIS_EDGE_SECRET` (zero-downtime protocol)

> [!IMPORTANT]
> The backend validates against a **single** secret (`aegis.edge.secret` ← env `AEGIS_EDGE_SECRET`). Until dual-secret support exists in code, a rotation has an unavoidable sub-minute 403 window. Run in a low-traffic window and execute the steps back-to-back.

```mermaid
sequenceDiagram
    participant OP as Operator
    participant IF as Infisical
    participant BE as Backend ×2
    participant WRK as treishfin-seo-worker
    participant PG as Pages (app-routes env)

    OP->>OP: 1. NEW=$(openssl rand -hex 32)
    OP->>IF: 2. Update AEGIS_EDGE_SECRET (prod env)
    OP->>BE: 3. Re-materialize .env + force-recreate backend (403 window OPENS)
    OP->>WRK: 4. npx wrangler secret put AEGIS_EDGE_SECRET (both workers)
    OP->>PG: 5. Update Pages server-env AEGIS_EDGE_SECRET + rebuild
    OP->>OP: 6. Verify /llms.txt 200 via domain; window CLOSED
```

**Exact steps (VM = `vboxuser@<host>`, repo at `/opt/treishvaam`):**

1. **Generate** (64 hex chars): `NEW_SECRET=$(openssl rand -hex 32)`
2. **Infisical first** (the durable source of truth): update `AEGIS_EDGE_SECRET` in the prod environment (dashboard or CLI). ⚠ If you skip this, the next Engine B run silently reverts the rotation.
3. **Backend side** — recreate the replicas with the new secret materialized:
   ```bash
   ssh vboxuser@<host>
   cd /opt/treishvaam
   cp .env.template .env
   # stateless login, export prod env (same pattern as auto_deploy.sh):
   export INFISICAL_TOKEN=$(infisical login --method=universal-auth \
     --client-id="$INFISICAL_CLIENT_ID" --client-secret="$INFISICAL_CLIENT_SECRET" \
     --silent | grep -oE 'eyJ[A-Za-z0-9._-]+')
   infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv | \
     sed -E "s/='(.*)'$/=\1/" >> .env
   # rotate pattern (identical to scripts/rotate_secrets.sh step 5):
   docker compose stop backend || true
   docker compose rm -f -s -v backend || true
   docker compose up -d --force-recreate --no-deps backend
   cp .env.template .env   # Flash & Wipe immediately
   ```
   *(Alternative: skip manual work — commit `echo " " >> VER.txt` + push and let Engine B do steps 3–6 of this list automatically.)*
4. **Edge side** (immediately after): from the frontend repo on the workstation:
   ```bash
   cd worker && npx wrangler secret put AEGIS_EDGE_SECRET   # paste the same value
   # repeat for any other worker holding the secret (agro worker when it inherits HMAC)
   ```
   Worker secrets apply to new isolates without a redeploy — propagation is seconds.
5. **Pages server-env**: the Next.js app-routes (`app/llms.txt/route.ts` etc.) read a server-side `AEGIS_EDGE_SECRET` — update it in the Pages project settings and trigger a rebuild (empty commit). ⚚ Until OP-18 is fixed these routes 403 either way; keep the value in sync for when the signing bug is repaired.
6. **Verify**: `curl -s -o /dev/null -w "%{http_code}\n" https://treishvaamfinance.com/llms.txt` → `200`; `curl -s https://treishvaamfinance.com/api/v1/health/ping` → `{"status":"ok",...}`; backend logs clean of `Invalid Cryptographic Signature`.
7. **Rollback** if verification fails: re-set the *old* value in Infisical + Worker (reverse order, same speed).

> [!NOTE]
> **What this rotation does NOT touch:** `JWT_SECRET_KEY`/`INTERNAL_API_SECRET_KEY` (use `scripts/rotate_secrets.sh` — interactive `ROTATE` confirm, backs up `.env`, logs to `key_rotation.log`, same restart pattern), the five AES field-encryption keys (breach-only, require re-encryption migration), and the PQC Dilithium keypair (regenerates in-memory on weekly rotation).

---

## 3. Frontend Edge Cache Management (KV purge for SEO/GEO feeds)

Cached surfaces: `TREISHFIN_SEO_CACHE` KV keys `sitemap:finance:meta` + `sitemap:finance:/sitemap-dynamic/...` (TTL 90 000 s), `geo:finance:*` (TTL 86 400 s), `api:finance:post:{id}` (86 400 s), `api:finance:market:{ticker}` (3 600 s) — plus the Cloudflare CDN layer (`caches.default`) in front of KV.

**Full purge (worker admin route — exact behavior: lists and deletes every `TREISHFIN_SEO_CACHE` key):**
```bash
curl -s "https://treishvaamfinance.com/sys/purge-cache"
```
- The MTD manifest is **not** affected: the worker reads `aegis:mtd:manifest` from `AEGIS_THREAT_KV` (with `TREISHFIN_SEO_CACHE` only as a fallback binding) — so purging the SEO cache cannot desync routing.
- After a purge, re-warm: `curl -s "https://treishvaamfinance.com/sys/force-update"` (re-fetches `/api/public/sitemap/meta` + chunks — same as the hourly cron) and hit `/llms.txt`, `/ai-feed.md`, `/ontology.json` once to repopulate GEO keys.

**Selective purge (single key):**
```bash
# Dashboard: Workers & Pages → KV → TREISHFIN_SEO_CACHE → delete key  geo:finance:/llms.txt
# API equivalent:
curl -X DELETE "https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/storage/kv/namespaces/${CLOUDFLARE_THREAT_KV_NAMESPACE_ID_OR_SEO_NS}/values/geo%3Afinance%3A%2Fllms.txt" \
     -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}"
```

> [!WARNING]
> - KV purge does **not** clear the browser PWA service worker (Golden Rule 13) — for user-facing staleness, also test in a clean profile.
> - If stale content survives a KV purge, the CDN layer is holding it: run a zone **Cache Purge** (dashboard or API, scope available on the token).
> - GEO payloads are HMAC-provenance-signed by the backend (`CONTENT_SIGNING_KEY`); if you rotate that key, purge all `geo:finance:*` keys — old signatures become invalid on next backend push anyway.

---

## 4. CI/CD Recovery — Zombified Self-Hosted Runner

**Symptom:** GitHub shows the runner **Offline**, but on the VM `systemctl status 'actions.runner.*'` is **active** — the classic **TCP Half-Open Zombie** (Rule 24.1). The STP bridge flap ~30 s into every Engine B run routinely severs the runner's heartbeat; a healthy deploy already ends with the cure.

**Fix (exact):**
```bash
ssh vboxuser@<host>
sudo -n systemctl restart actions.runner.*
# watch it re-register:
journalctl -u actions.runner.<org-...> -f --since "1 min ago"
```

Rules that protect this path:
- **Never** SIGKILL/SIGTERM the runner process manually (Rule 24.3) — systemd owns it (`Restart=always`, `RestartSec=10`, `After=time-sync.target` — provisioned by ansible).
- **Never** remove `--no-block` from the `sudo systemd-run` Engine-B launch in `deploy.yml` (Golden Rule 1) — the runner's shutdown cascade would kill Engine B mid-deploy.
- **An SSH disconnect during Engine B is not a deployment failure** (Rule 24) — check Telegram + `watchdeploy` (`tail -f /opt/treishvaam/deploy_pipeline.log`) before intervening.

**Companion failure — deploy stuck `IN_PROGRESS`:** the Dead-Man's alert (`engine_b_stuck_in_progress`, 5 min without a terminal telemetry event) fires. Diagnose with `tail -5 /opt/treishvaam/logs/deploy_telemetry.ndjson` and `journalctl -u treishvaam-deploy-<ts>` — `systemd-run` buffers stdout during long sleeps, so a "hung" loop is not proof of death (Golden Rule 4). If genuinely wedged: clear the stale lock `rm -f /tmp/treishvaam_deploy.lock` only after confirming no `flock` holder (`ls /proc/*/fd | grep treishvaam_deploy.lock`), then re-trigger.

---

## 5. Database Maintenance — MariaDB TDE Keyfile Rotation

**Mechanism:** `config/mariadb/encryption.cnf` loads `file_key_management`, reading `/etc/mysql/encryption/keyfile` (format `<key_id>;<64-hex>`). The entrypoint writes that file at container start from env **`MARIADB_ENCRYPTION_KEY`** (format `1;<hex>`). TDE covers tables, redo log, tmp tables/files, and **binlogs**.

> [!CAUTION]
> `file_key_management` has **no online master-key rotation**. Data pages written under the old key are unreadable under a new keyfile. Rotating therefore requires a **dump → recreate → restore** maintenance window. Never rotate by simply changing the env var and restarting — that bricks the volume.

**Procedure (maintenance window, ~minutes for this data size):**
1. **Pre-flight:** confirm the newest encrypted backup exists:
   `docker exec backup-service aws --endpoint-url http://minio:9000 s3 ls s3://treishvaam-backups/ | tail -3`
2. **Fresh safety dump** (unencrypted, local, deleted after):
   `docker exec treishvaam-db sh -c 'mariadb-dump --single-transaction --quick -u root -p"$MARIADB_ROOT_PASSWORD" finance_db' | gzip > /opt/treishvaam/tde_migration.sql.gz`
3. **Rotate the key in Infisical**: `MARIADB_ENCRYPTION_KEY=1;$(openssl rand -hex 32)` (prod env).
4. **Recreate the DB container under the new key** (data volume is abandoned intentionally — do **not** reattach it):
   ```bash
   cd /opt/treishvaam
   # re-materialize .env from Infisical (§2 pattern), then:
   docker compose stop treishvaam-db keycloak backend nginx || true
   docker volume rm <finance-db-volume>        # identify via: docker inspect treishvaam-db
   docker compose up -d --no-deps treishvaam-db && sleep 20
   ```
5. **Restore** the dump: `gunzip < /opt/treishvaam/tde_migration.sql.gz | docker exec -i treishvaam-db mysql -u root -p"$MARIADB_ROOT_PASSWORD" finance_db` → restart dependents (`keycloak`, `backend`, `nginx`) with `docker compose up -d --no-deps …`.
6. **Verify & clean:** `docker exec treishvaam-db mariadb -u root -p"$MARIADB_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM finance_db.blog_posts;"` · app health `curl localhost/actuator/health` · `rm /opt/treishvaam/tde_migration.sql.gz` · `cp .env.template .env` (Flash & Wipe).
7. **Retain the old key value** (Infisical history/backup) until you are certain no old encrypted artifacts (old backups, exported binlogs) need reading — old `.enc` backups decrypt with `BACKUP_ENCRYPTION_KEY` regardless of TDE, but **binlog replay of pre-rotation logs requires the old TDE keyfile**.

---

*Companion document: `DISASTER_RECOVERY.md` (Sev-1 bridge collapse, PITR, Keycloak, MTD split-brain, frontend rollback). Full architecture context: `BE-00-INDEX.md` → `BE-02-SECURITY.md` / `BE-03-DEPLOYMENT.md`.*
