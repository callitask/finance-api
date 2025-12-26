# Treishvaam Finance Documentation Index

## Project Overview
Treishvaam Finance is an enterprise-grade financial platform built with Spring Boot 3.4 (Java 21) for the backend and React for the frontend. It is designed for robust, scalable, and secure financial operations, leveraging modern DevOps and cloud-native best practices.

## Tech Stack Summary
| Technology   | Purpose/Role                        |
|--------------|-------------------------------------|
| Java 21      | Core backend language               |
| Spring Boot 3.4 | Enterprise backend framework     |
| MariaDB      | Relational database                 |
| Redis        | Caching and session management      |
| Keycloak     | Identity and access management      |
| Infisical    | Secret Management (Zero-Secrets-on-Disk) |
| Docker       | Containerization and deployment     |
| Nginx        | Reverse proxy and static serving    |

## Documentation Modules
| Module | Description |
|--------|-------------|
| [01-ARCHITECTURE.md](01-ARCHITECTURE.md) | High-level system architecture, data flow, component list, and infrastructure overview (Docker, Nginx, Infisical). |
| [02-BACKEND-CORE.md](02-BACKEND-CORE.md) | Spring Boot configuration, security, authentication, and exception handling details. |
| [03-BACKEND-API.md](03-BACKEND-API.md) | API endpoints, controller logic, and request/response payload documentation. |
| [04-BACKEND-SERVICES.md](04-BACKEND-SERVICES.md) | Business logic, service layer, schedulers, and external API integrations. |
| [05-DATABASE-SCHEMA.md](05-DATABASE-SCHEMA.md) | Database schema, Liquibase changelogs, and entity relationship diagrams. |
| [SECRETS.md](../SECRETS.md) | **[NEW]** Guide to Infisical Secret Management, Zero-Trust security model, and rotation policies. |
| [08-SEO-EDGE.md](08-SEO-EDGE.md) | SEO, edge logic, Cloudflare Worker, robots.txt, and sitemap generation. |
| [09-DEPLOYMENT-OPS.md](09-DEPLOYMENT-OPS.md) | **[UPDATED]** The Operational Manual. Covers the Multi-Branch Strategy (Develop/Staging/Main), Dual-Engine Automation, and Disaster Recovery. |
| [10-CHANGELOG.md](10-CHANGELOG.md) | Chronological log of project changes, release notes, and version history. |

---
For detailed information, navigate to the respective module above. This index serves as the single source of truth for all project documentation.