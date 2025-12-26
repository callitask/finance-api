# Treishvaam Finance Platform - Enterprise Edition

## Project Overview

The Treishvaam Finance Platform is a high-performance, enterprise-grade financial analytics and content delivery system. It is designed as a modular, microservices-ready monolith using Spring Boot 3.4 (Java 21) for the backend and React for the frontend, served via Cloudflare Edge.

The infrastructure is built on a Zero-Trust security model, utilizing Keycloak for Identity and Access Management (IAM), Infisical for secret management, and a self-healing Watchdog automation system for deployment.

## Technology Stack

### Backend Core
- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.4.0
- **Build Tool**: Maven
- **Database**: MariaDB 10.6
- **Caching**: Redis (Alpine)
- **Messaging**: RabbitMQ (3.12 Management)
- **Search Engine**: Elasticsearch 8.17.0
- **Object Storage**: MinIO (S3 Compatible)

### Frontend & Edge
- **Framework**: React 18
- **Styling**: Tailwind CSS
- **Edge Runtime**: Cloudflare Workers (V8 Isolate)
- **CDN**: Cloudflare Network

### Infrastructure & DevOps
- **Containerization**: Docker & Docker Compose
- **Orchestration**: Self-Healing Watchdog Script (Bash)
- **Reverse Proxy**: Nginx + OWASP ModSecurity (WAF)
- **Secret Management**: Infisical (End-to-End Encryption)
- **Observability**: Grafana, Prometheus, Loki, Tempo, Zipkin

## Architecture Highlights

1.  **Secure Identity**: All authentication is handled by Keycloak via OpenID Connect (OIDC). The backend acts as an OAuth2 Resource Server.
2.  **Flash & Wipe Deployment**: The deployment script injects secrets into memory during startup and immediately wipes them from the disk to prevent leakage.
3.  **SEO at the Edge**: A custom Cloudflare Worker intercepts crawler requests and dynamically injects meta tags before the React app loads, ensuring perfect SEO scoring.
4.  **Resilience**: Circuit Breakers (Resilience4j) are implemented for all external market data APIs (AlphaVantage, Finnhub, etc.).

## Quick Start (Local & Server)

### Prerequisites
- Docker Engine & Docker Compose
- Java 21 SDK
- Maven
- Infisical CLI (for secret injection)

### Deployment
To deploy the full stack, use the automated watchdog script which handles branch selection, secret injection, and container orchestration.

```bash
chmod +x scripts/auto_deploy.sh
./scripts/auto_deploy.sh
```

## Repository Structure

- `src/main/java`: Backend source code.
- `src/main/resources`: Configuration and Database migrations (Liquibase).
- `nginx/`: Web Application Firewall configurations.
- `scripts/`: Automation and maintenance scripts.
- `backup/`: Database and Object Storage backup logic.
- `docs/`: Detailed architectural documentation.

## License
Proprietary software. All rights reserved by Treishvaam Group.