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

### 2.4. Internal Service Locking (The Internal Lock)
To protect sensitive endpoints from internal threats or misconfigured gateways, we implement a secondary authentication layer.

* **Filter**: `InternalSecretFilter`.
* **Mechanism**: Inspects the `X-Internal-Secret` header on specific POST endpoints (e.g., `/api/v1/posts`).
* **Validation**: The header value is compared against the `${INTERNAL_API_SECRET_KEY}` injected by Infisical.
* **Effect**: If valid, the request is granted the `ROLE_INTERNAL` authority, bypassing standard user checks.

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

## 5. Transactional Integrity & I/O Strategy

To prevent database connection pool exhaustion—a common failure mode in Enterprise apps—we enforce a **Strict Separation of Concerns**.

### 5.1. The "Plan First, Commit Later" Pattern
* **Rule**: Network I/O (e.g., MinIO Uploads, Third-party API calls) is **FORBIDDEN** inside `@Transactional` methods.
* **Reasoning**: If a network call takes 2 seconds inside a transaction, it holds a database connection for 2 seconds. Under load (e.g., 50 users uploading images), this starves the DB pool and freezes the app.
* **Implementation**:
    1.  **Phase 1 (Non-Transactional)**: Perform all heavy lifting (Image resizing, MinIO uploads) first. Get the resulting URLs.
    2.  **Phase 2 (Transactional)**: Pass the URLs to a dedicated `persistPost()` method annotated with `@Transactional`. This method does nothing but fast SQL inserts.
    3.  **Result**: Database lock time is reduced from seconds to milliseconds.

## 6. Resilience & Reliability

To prevent cascading failures when external APIs (AlphaVantage, Finnhub) go down, we use **Resilience4j**.

### 6.1. Circuit Breakers
* **Configuration**: Defined in `application-prod.properties`.
* **Behavior**:
    * If 50% of requests to an external provider fail within a sliding window, the circuit opens.
    * **Open State**: Requests are rejected immediately (Fast Fail) without calling the external service.
    * **Half-Open**: After a wait duration, a few probe requests are allowed to check if the service has recovered.

### 6.2. Rate Limiting (Bucket4j)
* **Purpose**: Protects the API from abuse and DDoS attempts.
* **Filter**: `RateLimitingFilter` checks the user's IP or User ID against a token bucket backed by **Redis**.
* **Headers**: Returns `X-RateLimit-Remaining` and `X-RateLimit-Retry-After` to the client.
* **Fail-Open Strategy (Resilience)**:
    * In the event of a Redis failure (Connection Refused/Timeout), the filter is designed to **Fail Open**.
    * **Logic**: We prioritize Application Availability over strict Rate Limiting during infrastructure outages. Errors are logged, but the request is allowed to proceed.

## 7. Async Processing & Event Bus

The application avoids blocking the main HTTP threads for long-running tasks.

### 7.1. Task Execution
* **Config**: `AsyncConfig.java` defines a `ThreadPoolTaskExecutor`.
* **Usage**: Methods annotated with `@Async` (e.g., sending emails, generating sitemaps) run in a separate thread pool.
* **Pool Sizing**:
    * **Core Pool**: 5 threads (always alive).
    * **Max Pool**: 10 threads (burst capacity).
    * **Queue**: 25 tasks (buffer).

### 7.2. Messaging (RabbitMQ)
* **Publisher**: `MessagePublisher` sends events to the `internal-events` exchange.
* **Consumer**: `MessageListener` processes events asynchronously (e.g., audit logging, search indexing).
* **Reliability**: Dead Letter Queues (DLQ) are configured to catch failed messages for later inspection.

## 8. Caching Strategy

**Redis** is the backbone of our performance strategy.

* **Config**: `CachingConfig.java`.
* **Annotations**:
    * `@Cacheable`: Caches results of expensive calls (e.g., `getMarketData`).
    * `@CacheEvict`: Clears cache when data changes (e.g., publishing a new post).
* **TTL**: Different Time-To-Live values for different data types (e.g., Market Data = 10 mins, Static Content = 24 hours).

## 9. Audit Logging

All critical actions are audited for security and compliance.

* **Aspect**: `AuditAspect.java` uses AOP to intercept methods annotated with `@LogAudit`.
* **Async Logging**: The actual database write to the `audit_logs` table happens asynchronously to ensure zero latency impact on the user.
* **Data Captured**:
    * **Actor**: User ID / Email.
    * **Action**: Method name (e.g., `DELETE_POST`).
    * **Resource**: ID of the entity affected.
    * **Outcome**: SUCCESS or FAILURE.

## 10. Configuration Management (Infisical)

We strictly adhere to the 12-Factor App methodology.

### 10.1. Secrets Injection Strategy
* **Source of Truth**: Infisical (External Secrets Manager).
* **Mechanism**:
    1.  The `auto_deploy.sh` script fetches secrets from Infisical securely.
    2.  Secrets are written to a temporary `.env` file.
    3.  `docker-compose` reads `.env` and injects variables (e.g., `MINIO_ACCESS_KEY`, `SPRING_RABBITMQ_PASSWORD`) into containers.
    4.  **Flash & Wipe**: The `.env` file is stripped of secrets immediately after deployment.

### 10.2. Critical Configurations
The following properties in `application-prod.properties` are **Dynamic**:

| Property | Environment Variable | Description |
| :--- | :--- | :--- |
| `spring.datasource.password` | `PROD_DB_PASSWORD` | Database Access |
| `storage.s3.secret-key` | `MINIO_SECRET_KEY` | File Storage Access |
| `spring.rabbitmq.password` | `SPRING_RABBITMQ_PASSWORD` | Event Bus Access |
| `jwt.secret` | `JWT_SECRET_KEY` | Token Signing |

*Note: HashiCorp Vault has been explicitly disabled (`spring.cloud.vault.enabled=false`) in favor of this simpler, robust Docker injection model.*