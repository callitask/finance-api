/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Details the core runtime, security, concurrency, multi-tenancy, and I/O strategies of the Spring Boot Backend.
 *
 * Scope:
 * - Covers OAuth2/RBAC, TenantInterceptor boundaries, Virtual Threads, JPA batching, SSG Materialization, and Native Telemetry.
 *
 * Critical Dependencies:
 * - Keycloak (Auth), MariaDB (Persistence), Redis (Cache), MinIO (Storage).
 * - Cloudflare Edge Workers (for injecting X-Tenant-ID).
 *
 * Security Constraints:
 * - Multi-tenant data isolation must be enforced via TenantInterceptor and TenantContext ThreadLocals.
 * - Subprocess/Startup tasks must explicitly declare their TenantContext to prevent data cross-contamination.
 * - Docker Compose `.env` parsing requires raw strings (no quotes).
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED: Initial Treishvaam Finance Core Backend documentation.
 * - EDITED:
 * • Phase 3 Update: Upgraded Multi-Tenancy Architecture documentation.
 * • Detailed TenantInterceptor whitelisting ('finance', 'agro').
 * • Detailed MDC logging injection for tenant tracking.
 * • Added MarketDataInitializer TenantContext isolation constraints.
 * • Added Docker Compose .env quoting restriction to Configuration Management to prevent HikariCP parse failures.
 * • Documented Edge Worker KV caching and SPA fallback integration with the backend.
 * - EDITED:
 * • Added Section 12 for Native Telemetry & Diagnostics (RUM and API Status Tracking).
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted,
 * truncated, rewritten, or regenerated.
 * Future AI must append only.
 */

# Backend Core Architecture

## 1. Runtime Environment
* **Language**: Java 21 LTS (Temurin Distribution)
* **Framework**: Spring Boot 3.4.0
* **Build System**: Maven 3.9+
* **Container**: Docker (Distroless or Alpine-based OpenJDK 21)
* **Concurrency Model**: Utilizes **Java 21 Virtual Threads** (Project Loom) for high-throughput parallel processing.

## 2. Security Architecture (Zero-Trust)

The security layer is designed around the **OAuth2 Resource Server** pattern. The backend is stateless and delegates all identity management to **Keycloak**.

### 2.1. Authentication Flow
1.  **Frontend Login**: User logs in via the React frontend (using the Keycloak JS adapter).
2.  **Token Issuance**: Keycloak issues a JWT (Access Token).
3.  **API Request**: Frontend attaches the JWT in the `Authorization: Bearer <token>` header.
4.  **Validation**:
    * The Spring Boot backend validates the JWT signature against the Keycloak JWK Set (cached locally).
    * The `Issuer` claim (`iss`) is verified to ensure it matches the `treishvaam` realm.

### 2.2. Role-Based Access Control (RBAC)
We map Keycloak Realm Roles to Spring Security Authorities using a custom converter.

* **Converter Class**: `KeycloakRealmRoleConverter`
* **Mapping Logic**:
    * Extracts roles from the `realm_access.roles` claim in the JWT.
    * Prefixes them with `ROLE_` (e.g., `admin` -> `ROLE_ADMIN`).
    * Converts them to `SimpleGrantedAuthority` objects.

### 2.3. Security Filter Chain (`SecurityConfig.java`)
The filter chain is configured with strict ordering to ensure safety before any business logic executes.

1.  **CORS Filter**: Applied globally via `FilterRegistrationBean<CorsFilter>` at `Ordered.HIGHEST_PRECEDENCE`. Allows origins defined in `application-prod.properties`: `https://treishvaamfinance.com`, `https://www.treishvaamfinance.com`, `https://treishvaamgroup.com`, `https://www.treishvaamgroup.com`.
2.  **CSRF**: Disabled (Stateless API does not use session cookies for auth).
3.  **Session Management**: Set to `STATELESS`.
4.  **Authorization Rules** (explicit, in order):
    * **OPTIONS `/**`**: permitAll (CORS preflight)
    * **Public**: `/actuator/health`, `/api/v1/health/**`, `/api/v1/monitoring/ingest`
    * **Public GET**: `/api/v1/posts/**`, `/api/v1/categories/**`, `/api/v1/market/**`, `/api/v1/news/**`, `/api/v1/search/**`
    * **Public POST**: `/api/v1/market/quotes/batch`, `/api/v1/contact/**`
    * **Authenticated**: `POST /api/v1/posts/draft`, `PUT /api/v1/posts/draft/**`
    * **Authenticated**: `/api/v1/auth/**`
    * **EDITOR/PUBLISHER/ADMIN**: `/api/v1/posts/admin/**`, `PUT /api/v1/posts/**`, `DELETE /api/v1/posts/bulk`
    * **PUBLISHER/ADMIN**: `POST /api/v1/posts`, `DELETE /api/v1/posts/**`, `/api/v1/files/upload`
    * **ADMIN**: `/api/v1/admin/**`, `/api/v1/status/**`, `/actuator/**`
    * **Fallback**: `.anyRequest().authenticated()`

**IMMUTABLE RULE**: Always add explicit rules for new endpoints BEFORE the `.anyRequest()` fallback. Rule ordering matters — more specific rules must come first.

### 2.4. Subprocess Security (Market Engine)
The backend invokes a Python subsystem for complex financial analysis.

* **Credentials Handling**: Database credentials are **never** passed as command-line arguments (which are visible in `ps aux`).
* **Environment Injection**: `MarketDataService` uses `ProcessBuilder.environment()` to inject `DB_PASSWORD` and `DB_URL` securely into the Python process runtime.
* **Isolation**: The script runs as a non-privileged user inside the container.

## 3. Multi-Tenancy Architecture

The application is built to support multiple sub-brands (tenants) from a single deployment (e.g., Finance, Agro).

### 3.1. Tenant Context & Interception (`TenantInterceptor`)
* **Header**: Clients (specifically the Zero-Trust Edge Workers) must send the `X-Tenant-ID` header (e.g., `finance`, `agro`).
* **Validation**: The `TenantInterceptor` intercepts requests, sanitizes the input (regex `[^a-zA-Z0-9_-]`), and strictly enforces a whitelist of recognized tenants. Unknown tenants are downgraded to a safe default.
* **Context Holder**: `TenantContext` uses a `ThreadLocal` variable to store the Tenant ID for the duration of the request, ensuring thread-safety.
* **Logging Integration (MDC)**: The interceptor automatically injects the `tenantId` into the Mapped Diagnostic Context (MDC), ensuring all backend logs stream to Loki with clear tenant traceability.
* **Data Isolation**: Service layers use the `TenantContext` to filter database queries (e.g., `WHERE tenant_id = ?`), ensuring absolute data segregation between brands.

### 3.2. Contextual Routing (`SitemapService`)
Services are designed to dynamically alter behavior based on `TenantContext`. For example, `SitemapService` checks if the tenant is `agro`. If so, it hijacks the endpoint response to return a static XML sitemap tailored for enterprise pages, overriding the standard financial data pagination logic.

### 3.3. Static Content Materialization (`HtmlMaterializerService`)
The `HtmlMaterializerService` generates static HTML files for blog posts, ensuring SEO optimization and immediate availability. It fetches an HTML shell, injects metadata (e.g., Open Graph, Twitter cards), and uploads the materialized content to MinIO for edge delivery.

## 4. Concurrency & Virtual Threads (Enterprise Optimization)

We leverage **Java 21 Virtual Threads** to handle high-concurrency tasks without the overhead of OS threads.

### 4.1. Use Cases
* **Image Processing**: Parallel processing of user-uploaded images.
* **API Diagnostics**: Concurrent health checks for external APIs, tracked via the `ApiFetchStatus` entity.
* **Telemetry**: Real-time analytics and RUM (Real User Monitoring) data processing.

### 4.2. Benefits
* **Scalability**: Virtual Threads allow the backend to scale efficiently under high load.
* **Resource Efficiency**: Reduces memory and CPU overhead compared to traditional thread pools.

---

## 5. Native Telemetry & Diagnostics

### 5.1. Real User Monitoring (RUM)
The backend processes RUM data to provide insights into user behavior and application performance. This data is aggregated and visualized in Grafana.

### 5.2. API Diagnostics
The `ApiFetchStatus` entity tracks the health and latency of external API calls. It logs the API name, fetch status, trigger source, and error details, ensuring observability and reliability.