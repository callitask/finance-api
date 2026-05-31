# Treishvaam Finance Platform — Master Documentation Index

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Security Status:** AEGIS Active · Fort Knox Security Suite Enabled · 
**Last Updated:** 2026-05-29
**Classification:** Internal Architectural Reference (Sanitized — No Credentials, No Keys, No Internal IPs)

---

## Overview

The Treishvaam Finance Platform is an enterprise-grade, zero-trust, multi-tenant financial analytics and content delivery system. A **single shared Java Spring Boot 3.4 backend** (Java 21) powers multiple decoupled frontend applications (Finance, Agro, Parent Group) deployed exclusively on **Cloudflare Pages**, routed through **Cloudflare Edge Workers**, and protected by the **AEGIS Adaptive Security Framework** across 9 independent security layers.

All infrastructure runs in Docker Compose on a private Ubuntu VirtualBox VM. Zero internal ports are exposed to the public internet. The only externally reachable entry points are the OpenResty reverse proxy (ports 80/443) and the Cloudflare Tunnel (outbound-only).

---

## Verified Technology Stack

| Technology | Role | Version | Evidence |
| :--- | :--- | :--- | :--- |
| **Java 21 (Temurin LTS)** | Backend Runtime + Virtual Threads | 21 LTS | `pom.xml`, `FinanceApiApplication.java` |
| **Spring Boot** | Application Framework | 3.4.0 | `pom.xml` parent |
| **Next.js** | Finance Frontend | 14.2.x (App Router) | `package.json` |
| **React** | UI Library | 18.3.x | `package.json` |
| **Tiptap** | Rich Text Editor (replaced SunEditor) | 3.23.x | `package.json` |
| **MariaDB** | Primary Relational Database | 10.6 | `docker-compose.yml` |
| **Redis** | Caching + AEGIS Temporal Path Registry | 7 Alpine | `docker-compose.yml` |
| **RabbitMQ** | Async Event Bus + Threat Telemetry | 3.12 Management | `docker-compose.yml`, `RabbitMQConfig.java` |
| **Elasticsearch** | Full-Text Search | 8.17.0 | `pom.xml`, `PostDocument.java` |
| **MinIO** | S3-Compatible Object Storage | Latest | `docker-compose.yml`, `MinioConfig.java` |
| **Keycloak** | Identity Provider (SSO + OAuth2) | 25.0.0 | `docker-compose.yml`, `realm-export.json` |
| **Infisical** | Zero-Trust Secret Management | Cloud | `auto_deploy.sh`, `SECRETS.md` |
| **OpenResty** | Reverse Proxy + Lua WAF (replaces nginx) | Alpine | `docker-compose.yml`, `aegis_ja3.lua` |
| **BouncyCastle** | Post-Quantum Cryptography (PQC) | 1.78.1 (`bcpkix-jdk18on`) | `pom.xml` |
| **ANTLR4** | AEGIS Expression Language (AEL) Parser | 4.13.1 | `pom.xml`, `aegis.g4` |
| **Cloudflare** | Edge Network, Workers, KV, WAF, Pages | Workers + Pages | `wrangler.toml`, `worker.js` |
| **Go (distroless)** | AEGIS ZKP Microservice | Compiled binary | `aegis/zkp-service/Dockerfile` |
| **Grafana** | Observability Dashboards | Latest | `docker-compose.yml`, `config/` |
| **Loki** | Log Aggregation | 2.9.2 | `docker-compose.yml`, `loki-config.yml` |
| **Tempo** | Distributed Tracing | Latest | `docker-compose.yml`, `tempo.yaml` |
| **Prometheus** | Metrics Scraping | Latest | `docker-compose.yml`, `prometheus.yml` |
| **Liquibase** | Schema Migration | Spring default | `db.changelog-master.xml`, V1–V46 |
| **HikariCP** | JDBC Connection Pool | Spring default | `application-prod.properties` |
| **Bucket4j** | Rate Limiting | Spring Cloud | `Bucket4jConfig.java` |
| **Resilience4j** | Circuit Breaker | Spring Cloud 2024.0.0 | `ResilienceConfig.java` |
| **Serwist** | PWA Service Worker | 9.0.2 | `src/sw.ts`, `next.config.mjs` |
| **Tailwind CSS** | Frontend Styling | 3.4.x | `package.json`, `tailwind.config.js` |
| **Grafana Faro** | Real User Monitoring (RUM) | 2.0.2 | `faroConfig.js` |
| **Terraform** | Infrastructure as Code (OCI) | — | `terraform/main.tf` |
| **SaltStack** | Configuration Management | — | `saltstack/states/` |
| **Ansible** | Server Provisioning | — | `ansible/setup-server.yml` |
| **Packer** | Server Image Baking | — | `packer/server-image.pkr.hcl` |

---

## Multi-Tenant Architecture

One backend serves multiple brands. Tenant isolation is enforced at every layer.

| Tenant ID | Frontend Project | Domain | Status |
| :--- | :--- | :--- | :--- |
| `finance` | `treishvaam-finance-frontend` | `treishvaamfinance.com` | **Live (Production)** |
| `agro` | `treishvaam-agro-frontend` | `treishvaamagro.com` | In Development |
| `public` | `treishvaamgroup-frontend` | `treishvaamgroup.com` | Live (Production) |
| `thm` | *(planned)* | `thm.treishvaamgroup.com` | Planned |

Tenant ID is injected per-request by the Edge Worker (`X-Tenant-ID` header) and validated by `TenantInterceptor`. All DB queries, sitemaps, and service behavior are scoped to the tenant context via `TenantContext` (ThreadLocal).

---

## AEGIS Security Framework — 9 Layers

All layers verified in code. All run exclusively on Java 21 Virtual Threads.

| Layer | Name | Key Class(es) | Status |
| :--- | :--- | :--- | :--- |
| **L0-HEA** | Hardware Entropy Anchoring | `AegisEntropyManager`, `JitterEntropySource`, `UrandomEntropySource` | Active |
| **L1-PQCf** | Post-Quantum Cryptographic Foundation | `AegisPqcJwtService`, `AegisPqcKeyStore` (ML-DSA-87 / Dilithium5) | Active |
| **L2-PPO** | Polymorphic Protocol Obfuscation (MTD) | `AegisTemporalPathManager`, `EndpointManifest`, `AegisMtdController`, `AegisResponseMutator` | Active |
| **L3-ZKA** | Zero-Knowledge Admin Authentication | `aegis-zkp-service` (Go/gRPC), `AegisZkpAdminFilter`, `AegisZkpServiceClient` | Active |
| **L4-ADA** | Adversarial Deception Architecture | `AegisDeceptionEngine`, `TarpitManager`, `PoisonCorpusGenerator`, `CanaryTokenService`, `RabbitMQAttackPublisher` | Active |
| **L5-BIE** | Behavioral Intelligence Engine | `AegisBehavioralEngine`, `SessionBehaviorProfile`, `ShannonEntropyCalculator`, `aegis-biometrics.ts` | Active |
| **L6-MTD** | Moving Target Defense Orchestration | `CloudflareEdgeSyncService` → Cloudflare KV | Active |
| **L7-CMCS** | Chaos Mirror + Proof-of-Work | `AegisChaosMirror`, `PowChallengeIssuer` | Active |
| **L8-BCSM** | Byzantine Consensus Security Mesh | `AegisBcsm` (7 validators, BFT, 100ms timeout, Virtual Threads) | Active |
| **AEL** | AEGIS Expression Language | `AegisExpressionLanguage`, `AelRuleLoader`, `aegis.g4` (ANTLR4) | Active |

---

## Documentation Modules

### Core Architecture & Backend
| File | Description |
| :--- | :--- |
| **BE-00-INDEX.md** *(this file)* | Master index, tech stack, AEGIS summary, documentation map |
| **BE-01-ARCHITECTURE.md** | System architecture, Docker topology, data flow, Zero-Trust network, multi-tenant design |
| **BE-02-CORE.md** | Spring Boot internals, AEGIS filter chain execution order, BCSM validators, OAuth2 Resource Server |
| **BE-03-API.md** | Complete REST API reference — all controllers, endpoints, roles, request/response contracts |
| **BE-04-SERVICES.md** | Business logic layer — MarketData, BlogPost, Sitemap (contextual), Analytics, Schedulers |
| **BE-05-DATABASE.md** | Database schema, Liquibase V1–V46 changelog, encryption converters, entity relationships |
| **BE-06-INFRA-DEVOPS.md** | Docker Compose full container reference, CI/CD pipeline, auto-deploy watchdog, backup procedures |
| **BE-07-SECURITY.md** | AEGIS Framework deep-dive — all 9 layers, PII encryption, Zero-Trust DB driver boundary |
| **BE-08-SEO-EDGE.md** | Cloudflare Worker SEO logic, KV cache architecture, E-E-A-T schema injection, GEO routing |
| **BE-09-DEPLOYMENT.md** | Operations manual — Git branching, Flash & Wipe secrets, backup commands, rollback procedures |
| **BE-10-CHANGELOG.md** | Full architectural changelog — all release versions from 0.0.0.1 to 0.0.0.7 |
| **BE-11-GEO-AI.md** | GEO architecture — LLM crawler interception, payload generation, KV caching, ontology graph |

### Frontend (Finance)
| File | Description |
| :--- | :--- |
| **FIN-01-ARCHITECTURE.md** | Next.js 14 App Router architecture, migration status (CRA→Next.js), key dependencies |
| **FIN-02-COMPONENTS.md** | Component reference — layout, pages, dashboard, blog editor, market widgets |
| **FIN-03-WORKER-EDGE.md** | Edge Worker architecture, HMAC signing, GEO router, KV bindings, wrangler config |

### New Documents (Created by This Analysis)
| File | Description |
| :--- | :--- |
| **BE-12-LOCAL-SETUP.md** | Local development setup guide — Windows host + Ubuntu VM environment |
| **BE-13-SECRET-MATRIX.md** | Complete secret variables reference — all vaults, rotation policies, Cloudflare token expiry |
| **BE-14-INCIDENT-RUNBOOK.md** | Incident response runbook — backend down, DB issues, Cloudflare Worker failure, secret expiry |

---

## Critical Operational Notes

**⚠️ Cloudflare API Token Expiry:** The production Cloudflare API Token expires on **2026-08-26**. Rotation must be triggered no later than **2026-08-19** using `scripts/rotate_secrets.sh`. The token is scoped to IPs `192.168.29.111` and `192.168.56.101` only.

**⚠️ package.json homepage stale:** `package.json` `"homepage"` field still references `https://treishfin.treishvaamgroup.com` (legacy subdomain). This does not affect routing but should be updated to `https://treishvaamfinance.com`.

**⚠️ Agro Worker AEGIS Gap:** `treishvaamagro-seo-worker` has not yet received the Phase 6 MTD + GEO upgrades present in the Finance Worker. When the Agro frontend is finalized, the AEGIS Phase 6 logic must be mirrored from `worker/worker.js` per the Rules of Engagement.

**⚠️ Dead Code:** Legacy `src/App.js` and `src/index.js` from the CRA era exist in the Finance frontend. They are not used by any Next.js route. They are not harmful but add confusion during navigation.

---

## Security Notice

This documentation is sanitized for internal architectural reference. It contains **no credentials, no API keys, no internal IP addresses, no secret values, and no information that could be used to directly attack the system.** Secret management is handled exclusively via Infisical (backend), Cloudflare Worker Secrets (edge), and Cloudflare Pages Environment Variables (frontend).