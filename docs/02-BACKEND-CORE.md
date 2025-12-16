# 02-BACKEND-CORE.md

## Backend Core & Security (Production)

### 1. Security Architecture
The application now uses **Keycloak (OIDC/OAuth2)** for authentication and authorization, replacing the legacy custom JWT filter. All authentication flows are handled via Keycloak, and user roles are mapped using a `KeycloakRealmRoleConverter` for Spring Security RBAC. The custom JWT filter has been fully removed.
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
- The backend uses `oauth2ResourceServer` with JWT support, fully integrated with Keycloak.
- JWTs are validated and authorities are mapped using a custom `KeycloakRealmRoleConverter`, which translates Keycloak roles into Spring Security authorities for RBAC.

### 3. Rate Limiting (Bucket4j)
- **Bucket4j** is used to enforce API rate limits per IP and/or user, protecting against brute-force and abuse.
- **Client Feedback**: The API now includes standard RFC-compliant headers to help clients manage backpressure:
  - `X-RateLimit-Remaining`: The number of tokens left in the current bucket.
  - `X-RateLimit-Retry-After`: The number of seconds to wait before retrying (provided when a 429 is returned).
- Rate limits are configured in application properties and can be adjusted per endpoint or user role.

### 4. Circuit Breakers (Resilience4j)
- **Resilience4j** is used for circuit breaking, retries, and bulkheading on all external API calls (e.g., market data, news, email).
- Circuit breaker configs are set in `application-prod.properties` and monitored via Actuator endpoints.

### 5. CORS Configuration
- Allowed origins: `https://treishfin.treishvaamgroup.com`, `http://localhost:3000`
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `HEAD`, `PATCH`
- All headers and credentials are permitted; CORS is applied globally.

### 6. Production Configuration (`application-prod.properties`)
Key configuration groups (secrets/values not shown):
- **Base URL & Server**: `app.base-url`, `server.port`
- **Vault Integration**: Loads secrets from HashiCorp Vault
- **Observability**: Prometheus, health endpoints, tracing (Zipkin/Tempo)
- **Database**: MariaDB connection URL, username, password, driver
- **JPA**: Hibernate dialect, DDL mode
- **Redis**: Host, port, cache TTL
- **Elasticsearch**: URI, timeouts
- **RabbitMQ**: Host, port, credentials, exchange, DLX
- **Logging**: File path for logs
- **JSON Serialization**: Ensures ISO date strings

This file overrides the default `application.properties` for production deployments.

### 7. Caching
- The application uses `@EnableCaching` (in both the main application and a dedicated `CachingConfig` class).
- **Redis** is configured as the cache provider, with custom serializers and TTLs for different cache groups (e.g., blog posts, market widgets, batch quotes).

### 8. Audit Logging
- **Asynchronous Execution**: Critical administrative actions (e.g., flushing cache, manual data refresh) are tracked via the `@LogAudit` annotation.
- **Performance**: The logging mechanism runs asynchronously (`CompletableFuture`) to ensure that writing to the `audit_logs` database table never impacts the latency of the user's request.
- **Scope**: Captures Actor, Action, Target Entity, IP Address, and Status.

---
This configuration ensures secure, scalable, observable, and resilient backend operations in production, with modern IAM, rate limiting, and circuit breaking.