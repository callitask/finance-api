# Treishvaam Finance Platform — Documentation Index

**Stable Version:** `tfin-financeapi-Develop.0.0.0.7`
**Security Status:** AEGIS Active · Fort Knox Security Suite Enabled
**Last Updated:** 2026-05-25
**Classification:** Internal Architectural Reference (Sanitized — No Credentials, No Keys, No Internal IPs)

---

The Treishvaam Finance Platform is a high-performance, enterprise-grade financial analytics and content delivery system. A **single shared Java Spring Boot 3.4 backend** (Java 21) powers multiple decoupled frontend applications (Finance, Agro, Parent Group) via a strict **Zero-Trust Cloudflare Edge**.

---

## Technology Stack

| Technology | Role | Version |
| :--- | :--- | :--- |
| **Java 21 (Temurin LTS)** | Backend Runtime | LTS |
| **Spring Boot** | Application Framework | 3.4.0 |
| **Next.js** | Finance Frontend Framework | 14.2.x (App Router) |
| **React** | UI Library | 18.3.x |
| **Tiptap** | Rich Text Editor | 3.23.x |
| **MariaDB** | Primary Relational Database | 10.6 |
| **Redis** | Caching & Temporal Path Registry | 7 Alpine |
| **RabbitMQ** | Asynchronous Messaging & Threat Telemetry | 3.12 Management |
| **Elasticsearch** | Full-Text Search Engine | 8.17.0 |
| **MinIO** | Object Storage (S3-Compatible) | Latest |
| **Keycloak** | Identity & Access Management (SSO) | 25.0.0 |
| **Infisical** | Zero-Trust Secret Management | Cloud/Self-Hosted |
| **OpenResty** | Reverse Proxy + Lua WAF (replaces plain Nginx) | Alpine |
| **BouncyCastle** | Post-Quantum Cryptography (PQC) | 1.78.1 (bcpkix-jdk18on) |
| **Cloudflare** | Edge Network, Workers, KV, WAF | Workers + Pages |
| **Grafana / Loki / Tempo / Prometheus** | Observability Stack | Latest (SSH-Tunnel Access Only) |

---

## Documentation Modules

### Core Architecture
| Module | Description |
| :--- | :--- |
| [01-ARCHITECTURE.md](01-ARCHITECTURE.md) | High-level system design, Docker container topology, data flow, Zero-Trust network map. |
| [02-BACKEND-CORE.md](02-BACKEND-CORE.md) | Spring Boot configuration, AEGIS Filter Chain, OAuth2 Resource Server, Multi-Tenancy, Virtual Threads. |
| [09-DEPLOYMENT-OPS.md](09-DEPLOYMENT-OPS.md) | Operations Manual — Git branching, Watchdog automation, Flash & Wipe secret strategy, backup commands. |

### API & Data Layer
| Module | Description |
| :--- | :--- |
| [03-BACKEND-API.md](03-BACKEND-API.md) | REST Controllers, endpoints, request/response contracts, role requirements. |
| [04-BACKEND-SERVICES.md](04-BACKEND-SERVICES.md) | Business logic layer — Market Data, Blog, Sitemap (contextual hijacking), Analytics, Schedulers. |
| [05-DATABASE-SCHEMA.md](05-DATABASE-SCHEMA.md) | Entity Relationship Diagram, table definitions, Liquibase changelog management. |

### Frontend & Edge
| Module | Description |
| :--- | :--- |
| [06-FRONTEND-ARCH.md](06-FRONTEND-ARCH.md) *(Finance frontend docs/)* | Next.js 14 App Router architecture, routing strategy, component hierarchy. |
| [07-FRONTEND-COMPONENTS.md](07-FRONTEND-COMPONENTS.md) *(Finance frontend docs/)* | Component responsibilities — Navbar, Footer, Dashboard, Blog Editor, Market widgets. |
| [08-SEO-EDGE.md](08-SEO-EDGE.md) | Cloudflare Worker logic — Zero-Trust routing, KV Cache-Shield, E-E-A-T schema injection, SPA fallback. |

### Security, GEO & AI
| Module | Description |
| :--- | :--- |
| [07-SECURITY-AEGIS.md](07-SECURITY-AEGIS.md) | **AEGIS Framework.** 9-layer adaptive security covering PQC, ZKP, BFT Consensus, ADA Deception, and BIE Telemetry. |
| [11-GEO-AI-OPTIMIZATION.md](11-GEO-AI-OPTIMIZATION.md) | **GEO & AI Routing.** Generative Engine Optimization, LLM crawler interception, semantic payloads, ontology graph. |

### Changelog
| Module | Description |
| :--- | :--- |
| [10-CHANGELOG.md](10-CHANGELOG.md) | Chronological architectural history — feature releases, DB migrations, security phases. |

---

## Security Notice

This documentation is sanitized for internal architectural reference. It contains **no credentials, no API keys, no internal IP addresses, no secret values, and no information that could be used to directly attack the system.** Secret management is handled exclusively via Infisical (backend), Cloudflare Worker Secrets (edge), and Cloudflare Pages Environment Variables (frontend).
