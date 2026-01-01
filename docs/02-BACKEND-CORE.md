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

1.  **CORS Filter**: Applied globally. Allows origins defined in `application-prod.properties` (e.g., `https://treishfin.treishvaamgroup.com`).
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

The application is built to support multiple sub-brands (tenants) from a single deployment.

### 3.1. Tenant Context
* **Header**: Clients must send the `X-Tenant-ID` header (e.g., `TREISHFIN`, `TREISHAGRO`).
* **Interceptor**: `TenantInterceptor` captures this header before the controller is reached.
* **Context Holder**: `TenantContext` uses a `ThreadLocal` variable to store the Tenant ID for the duration of the request.
* **Data Isolation**: Service layers use the `TenantContext` to filter database queries (e.g., `WHERE tenant_id = ?`), ensuring data segregation.

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
    4.  **Robust Serialization**: To prevent 500 errors during materialization, the service manually converts `Instant` fields (e.g., `createdAt`) to Strings before serialization, ensuring the JSON payload is strictly compatible with the Frontend's hydration logic.
    5.  **Storage**: The resulting `.html` file is uploaded to MinIO/S3 at `posts/{slug}.html` for direct serving by Cloudflare.

### 5.5. API Stability & Recursion Protection
* **Problem**: Complex entity relationships (e.g., `BlogPost` <-> `Category`, `BlogPost` <-> `PostThumbnail`) can cause Infinite Recursion (StackOverflowError) during JSON serialization, crashing the API (HTTP 500).
* **Solution**: We enforce strict **JSON Back-References**:
    * **Thumbnails**: The `PostThumbnail` entity uses `@JsonIgnore` on the `blogPost` field.
    * **Categories**: The `BlogPost` entity uses `@JsonIgnoreProperties` on the `category` field to ignore bidirectional links.
    * **Result**: The API is mathematically guaranteed to produce a DAG (Directed Acyclic Graph) JSON structure, preventing recursion crashes.

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
* **Usage**: Methods annotated with `@Async` (e.g., sending emails, generating sitemaps) run in a separate thread pool.

### 7.2. Messaging (RabbitMQ)
* **Publisher**: `MessagePublisher` sends events to the `internal-events` exchange.
* **Consumer**: `MessageListener` processes events asynchronously (e.g., audit logging, search indexing).
* **Reliability**: Dead Letter Queues (DLQ) catch failed messages.

## 8. Caching Strategy

**Redis** is the backbone of our performance strategy.

* **Config**: `CachingConfig.java`.
* **Global TTL**: Defaults to 10 minutes (`600000ms`) to prevent data staleness.
* **Patterns**:
    * **Read-Through**: Critical read paths (e.g., `findByUrlArticleId`) are annotated with `@Cacheable`.
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

## 11. Financial Precision Architecture

To ensure Enterprise-grade accuracy in financial data (Stock Prices, Crypto), we strictly avoid floating-point arithmetic.

* **Java Layer**: All monetary fields use `java.math.BigDecimal`.
* **Python Layer**: The Market Data Engine uses `decimal.Decimal` with a precision context of 28 places.
* **Database**: Columns are defined as `DECIMAL(19, 4)` or higher.
* **Why?**: Prevents IEEE 754 errors (e.g., `0.1 + 0.2 = 0.30000000000000004`) ensuring exact penny-perfect calculations.