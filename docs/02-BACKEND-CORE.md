# 02-BACKEND-CORE.md

## Backend Core & Security (Production)

### 1. Security Architecture
The application uses a `SecurityFilterChain` to define endpoint access:
- **Public Endpoints**: No authentication required for:
  - `/actuator/**`, `/health` (metrics/health)
  - `GET` requests to `/api/v1/posts`, `/api/v1/posts/public/**`, `/api/v1/posts/url/**`, `/api/v1/categories`, `/api/v1/uploads/**`, `/api/v1/market/**`, `/api/v1/news/**`, `/api/v1/search/**`, `/sitemap.xml`, `/sitemap-news.xml`, `/feed.xml`, `/sitemaps/**`, `/favicon.ico`
  - All requests to `/api/v1/auth/**`, `/api/v1/contact/**`, `/api/v1/market/quotes/batch`, `/api/v1/market/widget`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error`
- **Role-Based Endpoints**:
  - `/api/v1/analytics/**`: Requires `ANALYST` or `ADMIN` role
  - `/api/v1/posts/admin/publish/**`, `/api/v1/posts/admin/delete/**`, `/api/v1/market/admin/**`, `/api/v1/status/**`, `/api/v1/admin/actions/**`, `/api/v1/files/upload`: Requires `PUBLISHER` or `ADMIN` role
  - `/api/v1/posts/draft`, `/api/v1/posts/draft/**`, `/api/v1/posts/admin/**`: Requires `EDITOR`, `PUBLISHER`, or `ADMIN` role
- **All other endpoints** require authentication.

### 2. Authentication Flow
- The backend uses `oauth2ResourceServer` with JWT support.
- JWTs are validated and authorities are mapped using a custom `KeycloakRealmRoleConverter`, which translates Keycloak roles into Spring Security authorities for RBAC.

### 3. CORS Configuration
- Allowed origins: `https://treishfin.treishvaamgroup.com`, `http://localhost:3000`
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `HEAD`, `PATCH`
- All headers and credentials are permitted; CORS is applied globally.

### 4. Production Configuration (`application-prod.properties`)
Key configuration groups (secrets/values not shown):
- **Base URL & Server**: `app.base-url`, `server.port`
- **Vault Integration**: Loads secrets from HashiCorp Vault
- **Observability**: Prometheus, health endpoints, tracing (Zipkin/Tempo)
- **Database**: MariaDB connection URL, username, password, driver
- **JPA**: Hibernate dialect, DDL mode
- **Redis**: Host, port, cache TTL
- **Elasticsearch**: URI, timeouts
- **RabbitMQ**: Host, port, credentials, exchange
- **Logging**: File path for logs
- **JSON Serialization**: Ensures ISO date strings

This file overrides the default `application.properties` for production deployments.

### 5. Caching
- The application uses `@EnableCaching` (in both the main application and a dedicated `CachingConfig` class).
- **Redis** is configured as the cache provider, with custom serializers and TTLs for different cache groups (e.g., blog posts, market widgets, batch quotes).

---
This configuration ensures secure, scalable, and observable backend operations in production.