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

1.  **CORS Filter**: Applied globally. Allows origins defined in `application-prod.properties` (e.g., `https://treishfin.treishvaamgroup.com`, `https://treishvaamagro.com`).
2.  **CSRF**: Disabled (Stateless API does not use session cookies for auth).
3.  **Session Management**: Set to `STATELESS`.
4.  **Authorization Rules**:
    * **Public**: `/actuator/health`, `/api/v1/auth/**`, `/api/v1/posts/public/**`.
    * **Protected**: All other endpoints require a valid JWT.
    * **Admin**: Endpoints like `/api/v1/admin/**` require `ROLE_ADMIN`.

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

## 4. Concurrency & Virtual Threads (Enterprise Optimization)

We leverage **Java 21 Virtual Threads** to handle high-concurrency tasks without the overhead of OS threads.

### 4.1. Image Processing
* **Executor**: `Executors.newVirtualThreadPerTaskExecutor()` is used in `ImageService.java`.
* **Use Case**: Parallel resizing of uploaded images into multiple WebP variants (Master, Desktop, Tablet, Mobile).
* **Benefit**: Virtual threads block cheaply. This allows us to process dozens of images simultaneously without exhausting the thread pool, even if the CPU or I/O waits are significant.

### 4.2. Data Integrity (Optimistic Locking)
* **Goal**: Prevent "Lost Updates" when multiple admins edit the same record.
* **Mechanism**: JPA Optimistic Locking via the `@Version` field.
* **Handshake**:
    * **Read**: The frontend fetches the current `version` of an entity.
    * **Write**: The update request *must* include this `version`.
    * **Check**: If `dbVersion != clientVersion`, the backend throws `ObjectOptimisticLockingFailureException` (HTTP 409 Conflict), rejecting the stale write.

### 4.3. Startup Data Isolation (Tenant Boundaries)
* **Problem**: Startup processes (e.g., `CommandLineRunner`) execute outside the standard HTTP request lifecycle and therefore lack an injected `TenantContext`.
* **Solution**: Classes like `MarketDataInitializer` that execute background seeding must explicitly wrap their execution threads in `TenantContext.setTenantId("finance")`.
* **Benefit**: Guarantees that heavy financial data fetching or DB seeding never bleeds into the `agro` tenant architecture during cross-tenant deployment restarts.

## 5. Transactional Integrity & I/O Strategy

To prevent database connection pool exhaustion—a common failure mode in Enterprise apps—we enforce a **Strict Separation of Concerns** and optimize write patterns.

### 5.1. The "Plan First, Commit Later" Pattern (Write Path)
* **Rule**: Network I/O (e.g., MinIO Uploads, Third-party API calls) is **FORBIDDEN** inside `@Transactional` methods.
* **Reasoning**: If a network call takes 2 seconds inside a transaction, it holds a database connection for 2 seconds. Under load, this starves the DB pool.
* **Implementation**:
    1.  **Secure Streaming**: Uploads are streamed to `Files.createTempFile` (Disk), utilizing **Apache Tika** for MIME verification.
    2.  **Parallel Processing**: Image resizing happens in Virtual Threads.
    3.  **Late Transaction**: Only AFTER files are safe on MinIO does the `persistPost()` transaction begin.

### 5.2. Static Asset Offloading (Read Path)
* **Rule**: The Java Backend should **never** serve static image files.
* **Implementation**:
    * **Nginx Interception**: Requests to `/api/uploads/**` are intercepted by the Nginx Gateway.
    * **Direct MinIO Proxy**: Nginx proxies the request directly to the MinIO storage container.
    * **Caching**: Nginx applies `Cache-Control: public, max-age=31536000, immutable` headers.
* **Benefit**: Zero JVM thread usage for serving media assets.

### 5.3. Database Write Optimization (Batching)
* **Problem**: JPA/Hibernate typically executes inserts sequentially (N+1 problem during bulk imports).
* **Solution**: Enabled JDBC Batching in `application.properties`.
    * `spring.jpa.properties.hibernate.jdbc.batch_size=50`
    * `spring.jpa.properties.hibernate.order_inserts=true`
* **Effect**: 1,000 records are inserted in ~20 network round-trips instead of 1,000.

### 5.4. SEO Materialization (Hybrid SSG)
* **Problem**: SPAs (Single Page Applications) often suffer from poor SEO and high Time-To-Interactive (TTI).
* **Solution**: We implement **"Publish-Time Materialization"**.
    1.  **Trigger**: When a post is published, the `HtmlMaterializerService` activates.
    2.  **Generation**: It fetches the current React shell (`index.html`) from the internal Nginx gateway.
    3.  **Injection**: It injects the full HTML content into `<div id="server-content">` and the JSON state into `window.__PRELOADED_STATE__`.
    4.  **Robust Serialization**: To prevent 500 errors during materialization, the service manually converts `Instant` fields (e.g., `createdAt`) to Strings before serialization.
    5.  **Storage**: The resulting `.html` file is uploaded to MinIO/S3 for direct serving.

### 5.5. Edge SEO Intelligence & SPA Fallback
* **Sitemaps**: Instead of the backend rendering `sitemap.xml` directly to users, Cloudflare Edge Workers serve it from a Free-Tier KV Cache. Cache misses route to the backend with the appropriate `X-Tenant-ID` header, and the Worker asynchronously updates the cache (`ctx.waitUntil`).
* **API Stability & Recursion Protection**: Complex entity relationships (e.g., `BlogPost` <-> `Category`) utilize `@JsonIgnore` and `@JsonIgnoreProperties` to prevent Infinite Recursion (StackOverflowError) during JSON serialization.

## 6. Resilience & Reliability

To prevent cascading failures when external APIs (AlphaVantage, Finnhub) go down, we use **Resilience4j**.

### 6.1. Circuit Breakers
* **External APIs (`fmpApi`)**:
    * **Timeout**: 5 seconds.
    * **Threshold**: 50% failure rate opens the circuit.
    * **Fallback**: Returns stale data from the database/cache if available.
* **Market Engine (`pythonScript`)**:
    * **Timeout**: 120 seconds (Complex calculations).
    * **Protection**: Prevents long-running scripts from piling up and exhausting server resources.

### 6.2. Rate Limiting (Bucket4j)
* **Purpose**: Protects the API from abuse and DDoS attempts.
* **Filter**: `RateLimitingFilter` checks the user's IP or User ID against a token bucket backed by **Redis**.
* **Fail-Open Strategy**: In the event of a Redis failure, the filter is designed to **Fail Open** to prioritize availability.

## 7. Async Processing & Event Bus

The application avoids blocking the main HTTP threads for long-running tasks.

### 7.1. Task Execution
* **Config**: `AsyncConfig.java` defines a `ThreadPoolTaskExecutor`.
* **Usage**: Methods annotated with `@Async` (e.g., sending emails) run in a separate thread pool.

### 7.2. Messaging (RabbitMQ)
* **Publisher**: `MessagePublisher` sends events to the `internal-events` exchange.
* **Consumer**: `MessageListener` processes events asynchronously (e.g., audit logging, search indexing).
* **Reliability**: Dead Letter Queues (DLQ) catch failed messages.

## 8. Caching Strategy

**Redis** is the backbone of our performance strategy.

* **Config**: `CachingConfig.java`.
* **Global TTL**: Defaults to 10 minutes (`600000ms`) to prevent data staleness.
* **Patterns**:
    * **Read-Through**: Critical read paths are annotated with `@Cacheable`.
    * **Cache-Aside**: Updates (`save`) and Deletes (`deleteById`) trigger `@CacheEvict`.

## 9. Audit Logging

All critical actions are audited for security and compliance.

* **Aspect**: `AuditAspect.java` uses AOP to intercept methods annotated with `@LogAudit`.
* **Async Logging**: The database write to `audit_logs` is asynchronous.

## 10. Configuration Management (Infisical)

We strictly adhere to the 12-Factor App methodology.

### 10.1. Secrets Injection Strategy
* **Source of Truth**: Infisical (External Secrets Manager).
* **Mechanism**: Secrets are injected into the container environment at runtime via `auto_deploy.sh` and `docker-compose`.
* **Flash & Wipe**: The temporary `.env` file is deleted immediately after container startup.

### 10.2. The Docker Compose Quoting Trap (Critical Constraint)
* **Issue**: Docker Compose reads `.env` files literally. Single quotes (`'`) or double quotes (`"`) around values are passed directly into the container environment.
* **Failure Mode**: If `PROD_DB_URL='jdbc:mariadb...'` is used, Spring Boot's HikariCP fails to parse the connection string (crashing with `Failed to determine suitable jdbc url`).
* **Rule**: Secrets and URLs in the `.env` file MUST be raw strings without surrounding quotes.

## 11. Financial Precision Architecture

To ensure Enterprise-grade accuracy in financial data (Stock Prices, Crypto), we strictly avoid floating-point arithmetic.

* **Java Layer**: All monetary fields use `java.math.BigDecimal`.
* **Python Layer**: The Market Data Engine uses `decimal.Decimal` with a precision context of 28 places.
* **Database**: Columns are defined as `DECIMAL(19, 4)` or higher.
* **Why?**: Prevents IEEE 754 errors (e.g., `0.1 + 0.2 = 0.30000000000000004`) ensuring exact penny-perfect calculations.

## 12. Native Telemetry & Diagnostics

The backend natively processes operational health and audience telemetry without relying strictly on third-party opaque providers.

### 12.1. Real User Monitoring (RUM)
* **Mechanism**: The `AnalyticsService` ingests raw visitor footprints, standardizes them, and persists them into the `audience_visits` table.
* **Capabilities**: Provides GDPA-compliant aggregation by Country, Region, City, OS, and Session Source.

### 12.2. API Fetch Diagnostics
* **Mechanism**: To prevent silent failures of critical third-party data feeds, all interactions via `MarketDataService` are logged to the `api_fetch_status` table.
* **Capabilities**: The `ApiStatusController` provides a real-time health dashboard detailing HTTP status codes, latency, and failure messages for rapid triage.