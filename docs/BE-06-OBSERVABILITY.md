# BE-06 — OBSERVABILITY: Metrics, Logs, Traces, Telemetry Reality

> **Verification basis:** source export (obs configs, logback, actuator props) · frontend telemetry code (`faroConfig.js`, `aegis-biometrics.ts`, `WebVitalsTracker.tsx`) · SKILLS REF_3.

---

## 1. Stack Topology

```mermaid
flowchart LR
    BE["backend :8080"] -->|"/actuator/prometheus · 1 job · 5s"| PROM[("Prometheus")]
    BE -->|"stdout + /app/logs/backend.json (LogstashEncoder)"| PT["Promtail :9080"]
    HOST["/opt/treishvaam/logs/deploy_telemetry.ndjson"] -->|"/host-logs:ro"| PT --> LOKI[("Loki 2.9.2")]
    BE -->|"Zipkin v2 · sampling 0.1"| TEMPO[("Tempo :3200 (zipkin 9411 · otlp 4317/4318)")]
    PROM & LOKI & TEMPO --> GRAF["Grafana 127.0.0.1:3001 · mission-control"]
```

## 2. Metrics — Prometheus

Single job `treishvaam-backend` → `backend:8080/actuator/prometheus`, global `scrape_interval 5s`, no `remote_write`. Spring: exposure `health,prometheus`; details `when-authorized` (ROLE_ADMIN); Resilience4j indicators on (`pythonScript`, `fmpApi`). Primary series: `http_server_requests_seconds_{count,sum}`. ⚠ No infra exporters (MariaDB/Rabbit/Redis/host) — legacy claims of multi-job scraping are wrong. All new endpoints must carry Micrometer instrumentation (REF_3 rule).

## 3. Logs — Logback JSON → Promtail → Loki

- `logback-spring.xml`: `CONSOLE_JSON` + `FILE_JSON` (`/app/logs/backend.json`, 10 MB × 7 d × 100 MB cap), `LogstashEncoder` customFields `{"app_name":"finance-api"}`, root INFO; MDC (`requestId`, `tenantId`) serialized implicitly.
- Promtail (:9080), static configs: `varlogs` `/app/logs/*.json` + `engine-b-deploy` `/host-logs/deploy_telemetry.ndjson` (json → timestamp → labels `run_id/phase/status` → output).
- Loki 2.9.2: boltdb-shipper v11 filesystem, replication 1, ingestion raised to 50 MB/s (default-429 fix), ruler → `localhost:9093`. ⚠ No retention/compactor — unbounded growth.
- Access: Grafana `127.0.0.1:3001` + RabbitMQ `127.0.0.1:15672` via SSH tunnel.

## 4. Traces — Tempo

Zipkin receiver `:9411` (Spring endpoint `http://tempo:9411/api/v2/spans`), OTLP `:4317/:4318`, local block store. Sampling 0.1 (prod). `X-Request-ID` correlates logs ↔ traces (backend-generated; the frontend does not set it).

## 5. Grafana — Mission Control

3 panels (refresh 5 s, 15 m window): Traffic `rate(http_server_requests_seconds_count[1m])` by method/uri · Latency sum/count ratio · Logs `{job="varlogs"} | json`. ⚠ Provisioning YAML bodies absent from the export (OP-11) — documented alert rules: `HighBackendErrorRate`, `SlowAPIResponse`, `SecretKeyRotationDue` (watches `/opt/treishvaam/key_rotation.log` mtime), Dead-Man's `engine_b_stuck_in_progress` (STARTUP without terminal event within 5 min → critical, per REF_3).

## 6. Client Telemetry (the real pipelines)

| Pipeline | Producer → Endpoint | Cadence | Notes |
|---|---|---|---|
| First-party analytics | `faroConfig.js` `postEvent` → **`POST /api/v1/analytics/event`** (sendBeacon for `exit_intent/page_unload/visibility_hidden`; `fetch keepalive` otherwise) | on-event | events `page_view, scroll_depth, visibility_hidden, page_unload, exit_intent, web_vital` (`metricId` rename); sessionId per-tab UUID; `extra.platformVersion` Win11 Client-Hints |
| AEGIS L5-BIE biometrics | `aegis-biometrics.ts` → **`POST /api/v1/aegis/telemetry`** | **15 s** | mouse/keydown/jitter entropy hashed client-side (**SHA-256** — README claims SHA3-256, OP-21); headers `X-Aegis-Biometric-Hash` (+`-Raw` unless strict privacy); SSR-safe (no `window`) |
| Faro web-vitals | `faroConfig.js` → `NEXT_PUBLIC_FARO_URL` (default `https://backend.treishvaamgroup.com/faro/collect`) | on-load/vital | app `treishvaam-finance-frontend`; `faro.api.setUser` post-auth. ⚠ **No frontend call to `/api/v1/monitoring/ingest` exists** — the controller (→ `audience_visits` + forward `http://alloy:12347/collect`) is verified backend code but currently unexercised by the FE (OP-20) |
| GA4 / Ads | `app/layout.tsx` + deferred `ThirdPartyScripts.js` (interaction-gated: scroll/mousemove/touch/7 s) | — | `anonymize_ip` only when `NEXT_PUBLIC_ENFORCE_STRICT_PRIVACY=true` |

Server-side analytics jobs: `syncAegisTelemetryToAudienceVisits` 5 min · GA4 Data API daily 02:00 · BigQuery gated off · retention purge 03:30 (>365 d, DPDP).

## 7. Open Items (⚠)

OP-11 provisioning bodies · OP-20 Faro endpoint mismatch · Loki retention absent · no AEGIS-decision metrics exported (BLOCK/TARPIT counts live only in logs) · Alloy container referenced (`:12347`) but absent from compose · `X-Request-ID` unverified end-to-end (frontend never sets it).
