# Treishvaam Finance Platform - Documentation Index

## Project Overview

**Stable Version:** `tfin-financeapi-Develop.0.0.0.1`
**Security Status:** Fort Knox Security Suite Enabled

The Treishvaam Finance Platform is a high-performance, enterprise-grade financial analytics and content delivery system. It is designed as a modular, microservices-ready monolith using Spring Boot 3.4 (Java 21) for the backend and React for the frontend, served via Cloudflare Edge.

This index serves as the central navigation map for all architectural, operational, and security documentation.

## Technology Stack Summary

| Technology | Role | Version |
| :--- | :--- | :--- |
| **Java 21** | Backend Runtime | LTS (Temurin) |
| **Spring Boot** | Application Framework | 3.4.0 |
| **React** | Frontend Framework | 18 |
| **MariaDB** | Primary Relational Database | 10.6 |
| **Redis** | Caching & Session Store | Alpine |
| **RabbitMQ** | Asynchronous Messaging | 3.12 Management |
| **Elasticsearch** | Full-Text Search Engine | 8.17.0 |
| **MinIO** | Object Storage (S3 Compatible) | Latest |
| **Keycloak** | Identity & Access Management | 23.0.0 |
| **Infisical** | Secret Management | Orchestrator |
| **Cloudflare** | Edge Network & WAF | Workers |

## Documentation Modules

### Core Architecture
| Module | Description |
| :--- | :--- |
| [01-ARCHITECTURE.md](01-ARCHITECTURE.md) | High-level system design, container interactions, data flow diagrams, and infrastructure topology. |
| [02-BACKEND-CORE.md](02-BACKEND-CORE.md) | Spring Boot configuration, Security Filter Chain (OAuth2 Resource Server), Tenant Context, and Async Executors. |
| [09-DEPLOYMENT-OPS.md](09-DEPLOYMENT-OPS.md) | The Operations Manual. Details the Watchdog Automation, Flash & Wipe security strategy, and Branching workflows. |

### API & Data Layer
| Module | Description |
| :--- | :--- |
| [03-BACKEND-API.md](03-BACKEND-API.md) | Comprehensive breakdown of REST Controllers, endpoints, and Request/Response contracts. |
| [04-BACKEND-SERVICES.md](04-BACKEND-SERVICES.md) | Business logic layer documentation, including Market Data strategies, Schedulers, and Event Listeners. |
| [05-DATABASE-SCHEMA.md](05-DATABASE-SCHEMA.md) | Database entity relationships (ERD), table definitions, and Liquibase changelog management. |

### Frontend & Edge
| Module | Description |
| :--- | :--- |
| [06-FRONTEND-ARCH.md](06-FRONTEND-ARCH.md) | React SPA architecture, State Management (Context API), and Component hierarchy. |
| [08-SEO-EDGE.md](08-SEO-EDGE.md) | Edge-Side Rendering strategy using Cloudflare Workers for dynamic SEO meta tag injection. |

### Security & Compliance
| Module | Description |
| :--- | :--- |
| [SECRETS.md](../SECRETS.md) | **Critical Security Policy.** Defines the Zero-Trust model, Infisical integration, and Secret Rotation protocols. |

### Maintenance history
| Module | Description |
| :--- | :--- |
| [10-CHANGELOG.md](10-CHANGELOG.md) | Chronological history of architectural changes, database migrations, and feature releases. |