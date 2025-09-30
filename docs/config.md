Treishvaam Finance Backend: In-Depth Developer Guide


1) Executive summary / project one-pager 


1) Executive summary / project one‑pager
Short description — purpose & business problem
Treishvaam Finance Backend is a Java Spring Boot backend API that provides REST endpoints and background services for the Treishvaam Finance application. It handles user authentication (JWT), authorization, data persistence (accounts/transactions/portfolios), market-data integration, file uploads, scheduled tasks, and caching; the service is designed to be deployed as a WAR to an application server (external Tomcat). The backend solves the business problem of centralizing finance application logic and integrations so the frontend(s) can consume a secure, consistent API for user-facing features (portfolio views, transactions, market data, reports and file assets).
(Observed from project metadata: artifactId "finance-api", description "Backend API for Treishvaam Finance", and code/config that enable JWT auth, market-data API key, file storage, caching, scheduling, and DB migrations.)
High-level architecture summary (1–2 paragraphs)
	•  Backend: A Spring Boot 3.x application packaged as a WAR (maven packaging: war) intended for deployment to an app server (Tomcat is used as the provided servlet container). The app exposes REST endpoints (spring-boot-starter-web) and also includes WebFlux (reactive components) and OAuth2 client support for external auth providers. The codebase registers an ObjectMapper with JavaTime support, enables scheduling for background jobs, and enables Spring caching for in‑process caches (Caffeine).
	•  Data & integrations: Persistent storage is managed via Spring Data JPA with Liquibase-based database migrations; the runtime DB driver is MariaDB (mariadb-java-client). There are external integrations (market data API key configured — likely Financial Modeling Prep or similar), file storage for uploads (configurable path), and JWT-based authentication with configurable secret and expiration. The app is configured to run behind a frontend (base URL / CORS configured) and is structured to exclude some packages (marketdata) from certain scans, indicating modularization of responsibilities.
Primary technologies & versions
	•  Java / JDK: Java 21 (project property java.version=21)
	•  Spring Boot: 3.2.5 (parent in pom.xml)
	•  Servlet container: Apache Tomcat (spring-boot-starter-tomcat, scope provided — WAR deployable)
	•  Build tool: Maven (spring-boot-maven-plugin present)
	•  Persistence: Spring Data JPA + Hibernate (hibernate dialect set to MySQLDialect)
	•  DB driver / engine: MariaDB JDBC driver for runtime; Liquibase for migrations
	•  Caching: Spring Cache abstraction + Caffeine
	•  Security / Auth:
	•  Spring Security
	•  JWT via io.jsonwebtoken (jjwt) 0.11.5
	•  OAuth2 client (spring-boot-starter-oauth2-client) for external providers
	•  Reactive: spring-boot-starter-webflux included (mix of blocking/reactive support)
	•  API docs: springdoc-openapi-starter-webmvc-ui 2.5.0 (OpenAPI/Swagger UI)
	•  Image/file libs: thumbnailator, webp-imageio, imageio-webp
	•  Testing: spring-boot-starter-test, H2 database for tests
	•  Other: Lombok (optional), Gson
	•  Packaging: WAR (so expected to run in external servlet container)
	•  Scheduling: @EnableScheduling enabled in main app
	•  Containerization / caching / external infra: not present in repo (no Dockerfile found during the quick scan)
Intended audience
	•  Developers: implement/extend APIs, add features, fix bugs, and write tests
	•  DevOps / Release engineers: build, package (WAR), deploy to Tomcat/Java 21 runtime, manage environment variables and secrets, configure DB and file storage
	•  QA / Testers: exercise REST endpoints, integration tests, validate DB migrations and scheduled jobs
	•  New hires / Onboarding: overview of architecture, conventions, environment variables, and how to run locally / run tests
Notes from inspection
	•  The main application class enables scheduling and caching and provides a custom ObjectMapper (JavaTimeModule).
	•  Configuration uses environment variables for sensitive values (DB URL/credentials, JWT secret, market-data API key) in src/main/resources/application.properties — good practice, but some test/bin resource files in the repository contain secrets or sample secrets; these should be audited and rotated if they are real secrets.
	•  The project is structured to validate the JPA schema at runtime (spring.jpa.hibernate.ddl-auto=validate) and relies on Liquibase as the authoritative schema tool — follow Liquibase migrations for schema changes.
	•  File uploads are configured to write to a local folder (storage.upload-dir) by default; consider externalizing to object storage for cloud deployments.						



2) Quick start (developer setup)


2) Quick start (developer setup)
Prerequisites
	•  OS: Windows (project workspace shows Windows paths). Linux/macOS also supported.
	•  JDK: Java 21 (project pom property java.version=21). Install JDK 21 and set JAVA_HOME.
	•  Build tool: Maven (wrapper included). Use the included wrappers (mvnw / mvnw.cmd) to avoid installing Maven system-wide.
	•  Node / frontend: No frontend code in this repo. If you have the frontend, typical stacks use Node 16+ / npm or yarn — adjust to that repo’s README.
	•  Docker (optional but recommended for local DB): Docker Desktop (for running MariaDB container).
	•  DB client: MariaDB/MySQL client or MySQL Workbench to inspect local DB. H2 is used for tests (no client required).
Repository / branches
	•  Repo root contains pom.xml and maven wrapper. Main branch policy: not enforced in repo files — follow your org’s branching rules (e.g., main/master protected, feature branches, PRs).
	•  If you need the exact remote URL, check your git config (not included here). Typical workflow: clone, create feature branch, open PR to main.
Environment files & locations
Configuration lives in Spring Boot properties files under:
	•  src/main/resources/application.properties (production template that references environment variables)
	•  src/main/resources/application-dev.properties (dev overrides)
	•  src/main/resources/application-prod.properties (prod overrides)
	•  src/test/resources/application.properties (test settings)
Secrets and runtime overrides are passed via environment variables referenced in application.properties:
	•  PROD_DB_URL (spring.datasource.url)
	•  PROD_DB_USERNAME
	•  PROD_DB_PASSWORD
	•  JWT_SECRET_KEY
	•  INTERNAL_API_SECRET_KEY
	•  MARKET_DATA_API_KEY
Recommended: keep local secrets out of repo and use a .env file or OS environment variables. Example .env.example below.
.env.example
Create a local .env (or populate your environment) based on this template:


# Database (MariaDB)
PROD_DB_URL=jdbc:mariadb://localhost:3306/treishvaam_dev
PROD_DB_USERNAME=treishvaam
PROD_DB_PASSWORD=change_me

# JWT & internal secrets
JWT_SECRET_KEY=replace-with-long-random-secret
INTERNAL_API_SECRET_KEY=replace-with-internal-secret

# External APIs
MARKET_DATA_API_KEY=replace-with-market-data-key

# Optional overrides
APP_BASE_URL=http://localhost:3000
STORAGE_UPLOAD_DIR=C:/treishvaam-uploads-dev
CORS_ALLOWED_ORIGINS=http://localhost:3000

(Variable names match references in src/main/resources/application.properties — use appropriate casing when exporting to environment.)
### Minimum steps to get running locally
1) Clone (example)
- git clone <repo-url>
- cd finance-api
2) Start a local MariaDB (recommended) — Docker command:
- Docker (Windows PowerShell / CMD):
 - docker run --name treish-mariadb -e MYSQL_ROOT_PASSWORD=rootpw -e MYSQL_DATABASE=treishvaam_dev -e MYSQL_USER=treishvaam -e MYSQL_PASSWORD=change_me -p 3306:3306 -d mariadb:10.11
- Or run MariaDB/MySQL using your preferred installer.
3) Populate .env or export env vars (PowerShell example):
- $env:PROD_DB_URL="jdbc:mariadb://localhost:3306/treishvaam_dev"
- $env:PROD_DB_USERNAME="treishvaam"
- $env:PROD_DB_PASSWORD="change_me"
- $env:JWT_SECRET_KEY="your_local_jwt_secret"
- $env:INTERNAL_API_SECRET_KEY="internal_secret"
- $env:MARKET_DATA_API_KEY="market_api_key"
4) Build and run (use the wrapper to ensure consistent Maven):
- Windows (PowerShell/CMD):
 - Build: mvnw.cmd clean package
 - Run (with dev profile): mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
 - Or run packaged app: java -jar target/finance-api-0.0.1-SNAPSHOT.war
- Linux / macOS:
 - Build: ./mvnw clean package
 - Run: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 - Or: java -jar target/finance-api-0.0.1-SNAPSHOT.war
Notes:
- The project is packaged as a WAR but uses the Spring Boot Maven plugin: running with spring-boot:run is the fastest for dev.
- To run from IDE: import as Maven project, set JDK 21, run com.treishvaam.financeapi.FinanceApiApplication main class. Use program args or VM/env variables to set profiles / env vars.
5) Run tests
- mvnw.cmd test (Windows) or ./mvnw test (Unix). Tests use H2 in-memory based on test resources.
### Ports used
- Backend server: 8080 (default in src/main/resources/application.properties `server.port=8080`)
- Database (MariaDB): 3306 (default)
- Frontend (if present): commonly 3000 (not part of this repo — adapt to frontend repo)
- Tests: H2 in-memory (no external port)
### Quick smoke tests
- After starting the backend, confirm the app is listening:
 - curl (Unix / PowerShell):
   - curl -i http://localhost:8080/v3/api-docs
     - If OpenAPI is enabled (springdoc present), this should return JSON.
   - OR open Swagger UI:
     - http://localhost:8080/swagger-ui/index.html
 - Basic HTTP check:
   - curl -I http://localhost:8080/  (may return 404 if no root mapping configured)
- Health endpoints: actuator is not included by default in pom.xml; use the OpenAPI endpoint or any public REST endpoint implemented in the project (e.g., /api/auth/login — inspect controllers under src/main/java/com/treishvaam/financeapi).
### Common troubleshooting
- Java version mismatch: ensure JDK 21 is active (mvn -v shows JAVA_HOME and Java version).
- DB connection errors: verify PROD_DB_URL and credentials, ensure MariaDB container is up and accessible (use telnet localhost 3306 or mysql client).
- Liquibase / schema validation: application uses `spring.jpa.hibernate.ddl-auto=validate` and Liquibase; ensure migrations have run and schema matches JPA entities before startup.
- Missing secrets: set JWT_SECRET_KEY and other required env vars or application will fail to initialize security components.

3) Repo layout & file map (top-level)

/ (finance-api)
├─ .git/                          # Git repository metadata
├─ .mvn/
│  └─ wrapper/
│     └─ maven-wrapper.properties # Maven wrapper configuration
├─ .settings/                     # IDE (Eclipse) settings
├─ bin/                           # Compiled output directory (IDE-specific)
├─ build/                         # Build artifacts
├─ target/                        # Maven build output (contains the final .war file)
├─ test-uploads/                  # Assets for testing file uploads
│  └─ logo.webp
├─ user-uploads/                  # Default directory for runtime file uploads
│  └─ logo.webp
├─ .classpath                     # IDE (Eclipse) project metadata
├─ .factorypath                   # IDE (Eclipse) tooling metadata
├─ .gitattributes                 # Git file-specific settings (e.g., line endings)
├─ .gitignore                     # Files and directories ignored by Git
├─ .project                       # IDE (Eclipse) project configuration
├─ mvnw                           # Maven wrapper script for Unix-like systems
├─ mvnw.cmd                       # Maven wrapper script for Windows
├─ pom.xml                        # Project Object Model: Core Maven configuration
│
└─ src/                           # Source code root
   ├─ main/                       # Main application source
   │  ├─ java/
   │  │  └─ com/
   │  │     └─ treishvaam/
   │  │        └─ financeapi/     # Root package for the application
   │  │           ├─ FinanceApiApplication.java
   │  │           ├─ ServletInitializer.java
   │  │           │
   │  │           ├─ apistatus/
   │  │           │  ├─ ApiFetchStatus.java
   │  │           │  ├─ ApiFetchStatusRepository.java
   │  │           │  ├─ ApiStatusController.java
   │  │           │  └─ PasswordDto.java
   │  │           │
   │  │           ├─ common/
   │  │           │  ├─ SystemProperty.java
   │  │           │  └─ SystemPropertyRepository.java
   │  │           │
   │  │           ├─ config/
   │  │           │  ├─ CachingConfig.java
   │  │           │  ├─ DataInitializer.java
   │  │           │  ├─ MarketDataInitializer.java
   │  │           │  ├─ OpenApiConfig.java
   │  │           │  ├─ SecurityConfig.java
   │  │           │  ├─ WebConfig.java
   │  │           │  └─ tenant/
   │  │           │     ├─ TenantContext.java
   │  │           │     └─ TenantInterceptor.java
   │  │           │
   │  │           ├─ controller/
   │  │           │  ├─ AuthController.java
   │  │           │  ├─ BlogPostController.java
   │  │           │  ├─ CategoryController.java
   │  │           │  ├─ FileController.java
   │  │           │  ├─ HealthCheckController.java
   │  │           │  ├─ MarketDataController.java
   │  │           │  ├─ OAuth2Controller.java
   │  │           │  ├─ SearchController.java
   │  │           │  └─ SitemapController.java
   │  │           │
   │  │           ├─ dto/
   │  │           │  ├─ AuthResponse.java
   │  │           │  ├─ BlogPostDto.java
   │  │           │  ├─ BlogPostSuggestionDto.java
   │  │           │  ├─ LoginRequest.java
   │  │           │  ├─ PostThumbnailDto.java
   │  │           │  └─ ShareRequest.java
   │  │           │
   │  │           ├─ marketdata/
   │  │           │  ├─ AlphaVantageProvider.java
   │  │           │  ├─ BreezeProvider.java
   │  │           │  ├─ FmpProvider.java
   │  │           │  ├─ MarketData.java
   │  │           │  ├─ MarketDataFactory.java
   │  │           │  ├─ MarketDataProvider.java
   │  │           │  ├─ MarketDataRepository.java
   │  │           │  ├─ MarketDataScheduler.java
   │  │           │  ├─ MarketDataService.java
   │  │           │  └─ package-info.java
   │  │           │
   │  │           ├─ model/
   │  │           │  ├─ BlogPost.java
   │  │           │  ├─ Category.java
   │  │           │  ├─ ERole.java
   │  │           │  ├─ PostStatus.java
   │  │           │  ├─ PostThumbnail.java
   │  │           │  ├─ Role.java
   │  │           │  └─ User.java
   │  │           │
   │  │           ├─ newshighlight/
   │  │           │  ├─ NewsApiResponseDto.java
   │  │           │  ├─ NewsArticleDto.java
   │  │           │  ├─ NewsHighlight.java
   │  │           │  ├─ NewsHighlightController.java
   │  │           │  ├─ NewsHighlightRepository.java
   │  │           │  └─ NewsHighlightService.java
   │  │           │
   │  │           ├─ repository/
   │  │           │  ├─ BlogPostRepository.java
   │  │           │  ├─ CategoryRepository.java
   │  │           │  ├─ PostThumbnailRepository.java
   │  │           │  ├─ RoleRepository.java
   │  │           │  └─ UserRepository.java
   │  │           │
   │  │           ├─ security/
   │  │           │  ├─ InternalSecretFilter.java
   │  │           │  ├─ JwtTokenFilter.java
   │  │           │  └─ JwtTokenProvider.java
   │  │           │
   │  │           └─ service/
   │  │              ├─ BlogPostService.java
   │  │              ├─ BlogPostServiceImpl.java
   │  │              ├─ CustomUserDetailsService.java
   │  │              ├─ FileStorageService.java
   │  │              ├─ ImageService.java
   │  │              └─ LinkedInService.java
   │  │
   │  └─ resources/
   │     ├─ application.properties
   │     ├─ application-dev.properties
   │     ├─ application-prod.properties
   │     │
   │     ├─ db/
   │     │  └─ changelog/
   │     │     ├─ 008-add-scheduling-to-posts.xml
   │     │     ├─ 010-add-story-thumbnails.xml
   │     │     ├─ db.changelog-master.xml
   │     │     ├─ db.changelog-master.yaml
   │     │     ├─ V1__init.xml
   │     │     ├─ V2__add_users_table.xml
   │     │     ├─ V3__add_roles_table.xml
   │     │     ├─ V4__add_user_roles_table.xml
   │     │     ├─ V5__add_categories_table.xml
   │     │     ├─ V6__add_tenant_id_to_posts.xml
   │     │     ├─ V7__add_category_to_posts_table.xml
   │     │     ├─ V8__add_image_metadata_to_posts.xml
   │     │     ├─ V9__add_author_to_posts.xml
   │     │     ├─ V10__add_linkedin_token_to_users.xml
   │     │     └─ V11__add_market_data_table.xml
   │     │
   │     └─ static/
   │        ├─ images/
   │        │  └─ logo.png
   │        └─ robots.txt
   │
   └─ test/
      ├─ java/
      │  └─ com/
      │     └─ treishvaam/
      │        └─ financeapi/
      │           ├─ FinanceApiApplicationTests.java
      │           └─ config/
      │              └─ TestConfig.java
      │
      └─ resources/
         ├─ application-test.properties
         └─ application.properties


Important generated / build artifacts to know
	•  target/finance-api-0.0.1-SNAPSHOT.war
	•  The packaged deployable (WAR). Deploy to Tomcat/Java 21.
	•  target/classes/application-*.properties
	•  Packaged runtime configuration (watch for environment overrides).
	•  target/surefire-reports/
	•  Test outputs for CI debugging.
Files you should inspect first
	•  pom.xml
	•  Dependency versions, Java target (21), packaging=war, plugins (spring-boot-maven-plugin).
	•  src/main/java/com/treishvaam/financeapi/FinanceApiApplication.java
	•  Application entrypoint, component-scan behavior, caching & scheduling flags.
	•  src/main/resources/application.properties
	•  Runtime configuration keys and env var references (DB, JWT, API keys, storage path).
	•  src/main/java/com/treishvaam/financeapi/config/SecurityConfig.java
	•  Web security config and auth filter chain.
	•  src/main/java/com/treishvaam/financeapi/security/JwtTokenProvider.java
	•  JWT generation/validation — key for authentication flows.
	•  src/main/resources/db/
	•  Liquibase changelogs — canonical DB migration source. Follow these for schema changes.
	•  src/main/java/com/treishvaam/financeapi/controller/
	•  Controller entry points — good starting points to understand HTTP API surface.
Notes and conventions
	•  Packaging: WAR (tomcat provided scope) — the project is intended for deployment to an external servlet container or as a WAR-run Spring Boot app.
	•  Profiles: dev/prod/test properties exist; application.properties references environment variables for secrets (JWT, DB creds). Use the dev profile locally.
	•  Some packages (marketdata) are excluded from component scanning — they may be used separately or loaded conditionally.
	•  Tests use H2 (in test resources) — run mvn test to exercise unit/integration tests without external DB.


4) Backend — in-depth
Document each of these (for each package/module):


Below is Section 4 — Backend — in‑depth. It documents each package/module found under com.treishvaam.financeapi and related runtime/config artifacts. Each package description includes purpose, key classes/files, responsibilities, extension points, and important runtime considerations.
4) Backend — in‑depth
Notes applicable across packages
	•  Base package: com.treishvaam.financeapi
	•  App entry: FinanceApiApplication.java — enables scheduling and caching, registers JavaTimeModule, configures component scan (excludes marketdata package by regex).
	•  Packaging: WAR (pom.xml packaging=war); Tomcat provided scope; run as Spring Boot app or deploy to servlet container.
	•  Profiles / config: main properties in src/main/resources/application.properties with profile overrides (application-dev.properties, application-prod.properties). Many values reference environment variables (DB, JWT, API keys).
	•  DB migrations: src/main/resources/db/ — Liquibase changelogs are authoritative. spring.jpa.hibernate.ddl-auto=validate in production.
	•  Caching: Spring Cache + Caffeine configured (CachingConfig).
	•  Scheduling: @EnableScheduling present (FinanceApiApplication). Some initializers / runners exist (CacheWarmupRunner, DataInitializer, MarketDataInitializer).
Package: com.treishvaam.financeapi.config
	•  Purpose: Central Spring configuration for web, security, caching, OpenAPI, data initialization, and tenant concerns.
	•  Key files:
	•  SecurityConfig.java — configures Spring Security filter chain, authentication/authorization rules, integration points for JwtTokenFilter and InternalSecretFilter, CORS and CSRF handling. Primary place to adjust authentication/authorization rules and endpoint permit/deny lists.
	•  WebConfig.java — MVC settings, resource handlers, CORS defaults, static resource mapping, view resolvers.
	•  CachingConfig.java — configures Spring Cache manager with Caffeine settings (cache sizes, TTL). Modify to adjust eviction, TTL or add named caches.
	•  OpenApiConfig.java — OpenAPI/Swagger configuration for API docs (springdoc). Adjust metadata, servers, and groupings here.
	•  DataInitializer.java — startup logic to seed roles, admin user, sample content. Runs on application startup (ApplicationRunner/CommandLineRunner). Update when default data needs changes.
	•  MarketDataInitializer.java — initializes market-data caches/integrations. Note: marketdata package is excluded from component scan but this initializer may selectively start market data tasks.
	•  tenant/ — multi-tenant configuration helpers (tenant resolver, tenant-aware datasources, interceptors) if multi-tenancy is required.
	•  Extension points: add additional @Configuration classes, override beans for ObjectMapper or CacheManager, add profile‑specific beans with @Profile.
Package: com.treishvaam.financeapi.security
	•  Purpose: JWT authentication, request filtering, and internal API secret enforcement.
	•  Key files:
	•  JwtTokenProvider.java — creates and validates JWT tokens (uses jjwt library). Source of token settings: jwt.secret, jwt.expiration.ms. Change here to alter token claims, signing algorithm, or validation rules.
	•  JwtTokenFilter.java — OncePerRequest filter that extracts JWT from Authorization header, validates and sets SecurityContext. Hook point for custom claim checks, token header name, or multi-source token extraction.
	•  InternalSecretFilter.java — validates an internal-secret header for protected internal endpoints (app.security.internal-secret). Useful for inter-service internal calls; adjust header name or placement here.
	•  Runtime notes:
	•  Ensure JWT secret is kept out of repo; production value must be set via env var JWT_SECRET_KEY.
	•  Authentication integrates with CustomUserDetailsService (service layer) for loading users and roles.
Package: com.treishvaam.financeapi.controller
	•  Purpose: HTTP API surface — REST endpoints for authentication, content, assets, search, sitemap and health.
	•  Key controllers:
	•  AuthController.java — login, token issuance, possibly registration and refresh endpoints. Uses JwtTokenProvider to generate tokens.
	•  BlogPostController.java — CRUD for blog posts, pagination, publish/unpublish.
	•  CategoryController.java — category CRUD used by blog posts/pages.
	•  ContactController.java — receives contact form submissions and persists them (ContactMessage).
	•  FileController.java — upload and download endpoints; delegates to FileStorageService for writing/reading files. Ensure storage.upload-dir config is set for environment.
	•  HealthCheckController.java — lightweight health/status probe used by external monitors.
	•  LogoController.java — serves logo assets (from user-uploads/ or packaged static/).
	•  OAuth2Controller.java — endpoints to handle OAuth2 client callbacks / flows.
	•  SearchController.java — search and suggestions (integrates with repositories or external search service).
	•  SitemapController.java — dynamic sitemap generation.
	•  ViewController.java — server side views or generic redirect endpoints.
	•  Extension & testing:
	•  Controllers use DTOs under dto/ and entities under model/.
	•  Add request validation (javax.validation) on DTOs for robust input handling.
	•  Secure endpoints via SecurityConfig; annotate methods with @PreAuthorize if needed.
Package: com.treishvaam.financeapi.dto
	•  Purpose: DTOs used by controllers and services for request/response payloads.
	•  Files:
	•  LoginRequest.java — login payload (username/password).
	•  AuthResponse.java — token and user info response.
	•  BlogPostDto.java, BlogPostSuggestionDto.java — blog post payloads for API and suggestions.
	•  ContactInfoDTO.java — contact submission payload.
	•  PostThumbnailDto.java — thumbnail metadata for posts.
	•  ShareRequest.java — request payload for sharing a post.
	•  Best practices:
	•  Keep DTOs validated (use @Valid in controller method params and validation annotations).
	•  Use mapping utilities (MapStruct or manual mappers) when converting between entity and DTO to centralize mapping logic.
Package: com.treishvaam.financeapi.model
	•  Purpose: JPA entity models representing DB tables.
	•  Key entities:
	•  User.java — user account model (username/email, password hash, roles relation). Used by authentication and CustomUserDetailsService.
	•  Role.java, ERole.java — roles enumeration and entity mapping.
	•  BlogPost.java — stores blog content, status, author, categories, timestamps.
	•  Category.java — categories taxonomy for posts.
	•  ContactMessage.java — persisted contact messages from site visitors.
	•  PageContent.java — generic page content sections.
	•  PostThumbnail.java — metadata for post thumbnails (file path, mime, width/height).
	•  PostStatus.java — enumeration for post lifecycle (DRAFT, PUBLISHED, etc.).
	•  DB notes:
	•  Entities map to Liquibase-managed schema; never change entity table/column names without adding corresponding Liquibase changelog.
	•  spring.jpa.hibernate.ddl-auto=validate in production — schema must match entity mappings.
Package: com.treishvaam.financeapi.repository
	•  Purpose: Spring Data JPA repositories for entity persistence and queries.
	•  Files:
	•  UserRepository.java — user lookup by username/email; used by authentication flows.
	•  RoleRepository.java — role lookup/seed operations.
	•  BlogPostRepository.java, CategoryRepository.java, PageContentRepository.java, PostThumbnailRepository.java, ContactMessageRepository.java
	•  Extension:
	•  Add query methods with derived queries or @Query for custom SQL.
	•  For complex queries, add repository custom implementations or use JPA Criteria / QueryDSL.
Package: com.treishvaam.financeapi.service
	•  Purpose: Business logic, file/image handling, user details loader, scheduled or startup runners.
	•  Files:
	•  CustomUserDetailsService.java — implements UserDetailsService; loads User entity and maps to Spring Security authorities.
	•  BlogPostService + BlogPostServiceImpl.java — domain logic for creating/updating/publishing posts.
	•  FileStorageService.java — read/write file system operations (uses storage.upload-dir config). Responsible for safe filename handling, path traversal protection, and serving files.
	•  ImageService.java — processing images (thumbnail creation, webp handling) using thumbnailator and imageio plugins.
	•  CacheWarmupRunner.java — warms caches on startup (named caches defined in CachingConfig).
	•  LinkedInService.java — integration to post/share to LinkedIn (background or sync).
	•  impl/ — additional service implementations or helpers.
	•  Extension & safety:
	•  File uploads must validate content type and size; sanitize filenames and configure storage path per environment.
	•  ImageService should be resilient against malicious images (use libraries that mitigate BOM/metadata attacks).
Package: com.treishvaam.financeapi.apistatus
	•  Purpose: Track and expose status of external API fetches and health of integration jobs.
	•  Files:
	•  ApiFetchStatus.java — entity to persist last fetch time, status, error messages.
	•  ApiFetchStatusRepository.java — repository for above.
	•  ApiStatusController.java — controller to view API status.
	•  PasswordDto.java — helper DTO used for internal status changes (if needed).
	•  Usage:
	•  MarketDataInitializer and scheduled tasks can update ApiFetchStatus for observability.
Package: com.treishvaam.financeapi.marketdata
	•  Purpose: Market data ingestion/processing (external API integrations). Note: explicitly excluded from component scan by FinanceApiApplication component filter.
	•  Why excluded:
	•  May be loaded conditionally, used as a separate module, or disabled in certain deployments.
	•  Files: (module contents present in workspace) — contains services/classes to fetch and cache market data using fmp.api.key (MARKET_DATA_API_KEY).
	•  Runtime considerations:
	•  If enabling, ensure API keys are provided and component scanning includes this package or beans are registered conditionally.
Package: com.treishvaam.financeapi.newshighlight
	•  Purpose: Feature area for news highlights; contains model/service/controller responsible for curated news content and highlights on the site.
	•  Files: controllers/services/models for news highlights. Integrates with external news or market-data modules.
Package: com.treishvaam.financeapi.web
	•  Purpose: Web layer helpers, possibly MVC view controllers or static resource mapping used for server-rendered pages.
	•  Files: helper controllers and view resolvers.
Top-level classes
	•  FinanceApiApplication.java
	•  Main boot class. Notes:
		•  @EnableScheduling and @EnableCaching are enabled.
		•  Custom ObjectMapper bean registered with JavaTimeModule.
		•  ComponentScan includes com.treishvaam.financeapi and com.treishvaam.finance but excludes marketdata.* via regex.
	•  ServletInitializer.java
	•  SpringBootServletInitializer subclass used when packaged as WAR — necessary for external servlet container deployments.
Resources and support files
	•  src/main/resources/application.properties — base config: server.port=8080, DB datasource configured via PROD_DB_URL/USERNAME/PASSWORD, jwt.secret referenced via JWT_SECRET_KEY, storage.upload-dir default, logging, CORS allowed origins.
	•  src/main/resources/application-dev.properties / application-prod.properties — environment specific overrides; inspect before deploying.
	•  src/main/resources/db/ — Liquibase changelogs. Add new changelog files here and register them in master changelog when introducing schema changes.
	•  src/main/resources/static/ — static web assets (served from classpath).
Testing
	•  Tests use H2 (in test resources). Test application.properties includes a test jwt.secret (safe test-only value) and H2 datasource.
	•  Unit/integration tests mirror main package structure under src/test/java; run with mvn test.
Common developer tasks & where to change code
	•  Add new entity/table:
	•  Create JPA entity in model/, add repository in repository/, add Liquibase changelog under src/main/resources/db/ and register it, add service logic and controller endpoints as required.
	•  Add new secure endpoint:
	•  Add controller method in controller/, secure via SecurityConfig (paths or method-level @PreAuthorize), ensure JWT claims/roles mapping in JwtTokenProvider and CustomUserDetailsService.
	•  Modify JWT behavior:
	•  Update JwtTokenProvider (signing algorithm, claims) and application properties (JWT_SECRET_KEY, jwt.expiration.ms).
	•  Change cache behavior:
	•  Update CachingConfig to change Caffeine spec (maximumSize, expireAfterWrite), and list cache names used by CacheWarmupRunner.
	•  Change file storage:
	•  Update storage.upload-dir in properties; modify FileStorageService for cloud storage (S3 or blob) and ensure upload/download permissions & validation.
Operational concerns & security notes
	•  Secrets: Do not commit JWT_SECRET_KEY, DB passwords, or API keys. The repo contains test/dev sample secrets — rotate any real secrets.
	•  Schema changes: Use Liquibase for DB migrations; do not rely on hibernate ddl-auto=update in prod.
	•  File uploads: Storage path is filesystem by default; for cloud deployments, replace FileStorageService with an object-storage backed impl and store path references in DB.
	•  Component exclusions: marketdata package is excluded from scan — check FinanceApiApplication component-scan filter if you expect market data beans to load.
Pointers for further exploration
	•  Inspect SecurityConfig.java, JwtTokenProvider.java and CustomUserDetailsService.java to fully understand authentication flow and how roles/authorities are mapped.
	•  Inspect DataInitializer and Liquibase changelogs to see initial data seeding and schema evolution.
	•  Review CachingConfig and CacheWarmupRunner for cache naming and warmup strategy.
	•  Review FileController and FileStorageService for file path handling and security checks.
If you want, next deliverables can include:
	•  A sequence diagram (Markdown-friendly) showing request flow for authentication and a blog-post CRUD request.
	•  A short checklist for adding a new JPA entity + migration with exact file examples.
	•  A sample migration (Liquibase XML) and the code changes needed to wire it in.


4.a) Build & run
Build tool & commands (Maven/Gradle + args).
How to run in Eclipse (run config), how to run from CLI.
How to create a runnable JAR/WAR and where it is generated.


Build & run — concise reference
Summary
- Build system: Maven (project includes mvnw / mvnw.cmd wrapper).
- Packaging: WAR (pom.xml packaging=war). Intended for external servlet container (Tomcat). Use spring-boot:run for local dev.
- JDK: Java 21 required.
1) Build commands
- Run tests and package (use wrapper to ensure consistent Maven):
 - Windows (PowerShell / CMD):
   - mvnw.cmd clean package
 - macOS / Linux:
   - ./mvnw clean package
- Run tests only:
 - mvnw.cmd test  (Windows)
 - ./mvnw test    (Unix)
- Skip tests during package:
 - mvnw.cmd clean package -DskipTests
 - ./mvnw clean package -DskipTests
2) Run from CLI (dev)
- Preferred for development (uses embedded Spring Boot runtime via plugin):
 - Windows (PowerShell):
   - $env:PROD_DB_URL="jdbc:mariadb://localhost:3306/treishvaam_dev"
   - $env:PROD_DB_USERNAME="treishvaam"
   - $env:PROD_DB_PASSWORD="change_me"
   - $env:JWT_SECRET_KEY="local_jwt_secret"
   - ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 - Windows (CMD):
   - set PROD_DB_URL=jdbc:mariadb://localhost:3306/treishvaam_dev && set PROD_DB_USERNAME=treishvaam && set PROD_DB_PASSWORD=change_me && set JWT_SECRET_KEY=local_jwt_secret && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
 - macOS/Linux:
   - export PROD_DB_URL=jdbc:mariadb://localhost:3306/treishvaam_dev
   - export PROD_DB_USERNAME=treishvaam
   - export PROD_DB_PASSWORD=change_me
   - export JWT_SECRET_KEY=local_jwt_secret
   - ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
Notes:
- spring-boot:run respects the main class com.treishvaam.financeapi.FinanceApiApplication.
- Use the dev profile (application-dev.properties) locally.
3) Running the packaged artifact
- Build creates WAR under target/:
 - target/finance-api-0.0.1-SNAPSHOT.war (or finance-api.war depending on build)
 - exploded folder: target/finance-api-0.0.1-SNAPSHOT/
- Deploy WAR to external Tomcat (recommended because pom sets Tomcat dependency scope to provided):
 - Copy the WAR to <TOMCAT_HOME>/webapps/ and start Tomcat (Tomcat 10+ recommended for Spring Boot 3 / Jakarta namespaces).
 - Tomcat start: <TOMCAT_HOME>/bin/startup.bat (Windows) or startup.sh (Unix).
- Executable java -jar:
 - Might not work if spring-boot-starter-tomcat is provided-scoped (WAR intended for external container). If you need a runnable jar/war, change packaging and dependencies (see notes below).
 - You can attempt:
   - java -jar target/finance-api-0.0.1-SNAPSHOT.war
   - If it fails with missing container classes, deploy to Tomcat or repackage with embedded Tomcat.
4) Creating a runnable JAR/WAR (options)
- Current repo is WAR for external container. Two options:
 - Option A (recommended for production on app server): keep WAR and deploy to Tomcat 10+.
 - Option B (for single-file deployment):
   - Change pom.xml: remove provided scope on spring-boot-starter-tomcat OR change packaging to jar and ensure embedded container included.
   - Re-run: mvnw clean package → produces an executable jar/war.
- Note: Changing packaging or tomcat scope requires testing and review of webapp resources.
5) Run in Eclipse (import & run)
- Import:
 - File → Import → Existing Maven Projects → select project root (finance-api).
 - Ensure Project → Properties → Java Build Path → JRE System Library points to Java 21 (install if needed).
 - Add Maven nature / update project (Right-click project → Maven → Update Project).
 - If you plan to run as a WAR on Tomcat: enable Dynamic Web Module facet and set Target runtime to Tomcat 10 in Project Facets (or create a Server in Servers view).
- Run as Spring Boot app (IDE run configuration):
 - Run → Run Configurations → Java Application
   - Name: finance-api (dev)
   - Project: finance-api (select)
   - Main class: com.treishvaam.financeapi.FinanceApiApplication
   - VM arguments: (optional) -Dspring.profiles.active=dev
   - Environment: add required env vars (PROD_DB_URL, PROD_DB_USERNAME, PROD_DB_PASSWORD, JWT_SECRET_KEY, etc.)
 - Apply → Run
- Run on Tomcat in Eclipse:
 - Window → Show View → Servers → New → Apache Tomcat v10.x → point to Tomcat installation
 - Right-click server → Add and Remove... → add the finance-api project (it should appear as a Web App)
 - Start server → Tomcat will deploy WAR to its webapps automatically.
6) Useful build & run tips
- Use mvnw / mvnw.cmd to avoid Maven version issues.
- Use -Dspring-boot.run.profiles=dev for local profile or set SPRING_PROFILES_ACTIVE.
- For CI: mvn -B -DskipTests clean package
- For debugging: run spring-boot:run with debug port:
 - ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
- Make sure required env vars (DB, JWT secret, API keys) are set in IDE Run configuration or system environment.
7) Where artifacts are generated
- Primary build artifact(s):
 - target/finance-api-0.0.1-SNAPSHOT.war
 - target/finance-api-0.0.1-SNAPSHOT.war.original (maven plugin output)
 - target/finance-api-0.0.1-SNAPSHOT/ (exploded content)
- Additional outputs:
 - target/classes/ (compiled classes & packaged resources)
 - target/surefire-reports/ (test reports)
Concise operational note
- Because pom sets spring-boot-starter-tomcat scope=provided, the intended deployment model is external Tomcat (Tomcat 10+ for Jakarta EE compatibility). For simple local dev, use spring-boot:run or run from the IDE with the main class.


4.b) Entry points & app bootstrap
Main class (e.g., com.example.Application) and what it wires.
Framework specifics (Spring Boot @SpringBootApplication, controllers auto-scan).
4.b) Entry points & app bootstrap
Main entry point
	•  Class: com.treishvaam.financeapi.FinanceApiApplication
	•  Annotation: @SpringBootApplication — enables component scan, auto-configuration and property binding for the application.
	•  Additional annotations:
		•  @EnableScheduling — enables scheduled tasks (@Scheduled) across the app.
		•  @EnableCaching — enables Spring Cache abstraction (Caffeine backing configured in CachingConfig).
		•  @ComponentScan(basePackages = {"com.treishvaam.financeapi", "com.treishvaam.finance"}, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\.treishvaam\.finance\.marketdata\..*"))
		•  Scans application beans in com.treishvaam.financeapi plus com.treishvaam.finance, explicitly excludes any marketdata subpackages by regex (beans in marketdata are not auto-registered).
	•  Beans defined:
		•  ObjectMapper objectMapper() — registers JavaTimeModule for Jackson date/time support (used by HTTP message converters).
	•  Behavior:
		•  Running SpringApplication.run(FinanceApiApplication.class, args) boots the embedded Spring container (when using spring-boot:run) or prepares the app for WAR deployment.
Servlet bootstrap (WAR support)
	•  Class: com.treishvaam.financeapi.ServletInitializer
	•  Extends SpringBootServletInitializer and overrides configure(SpringApplicationBuilder) to return application.sources(FinanceApiApplication.class).
	•  Purpose: enables traditional WAR deployment to an external servlet container (Tomcat). Tomcat dependency is provided-scoped in pom.xml.
What the app wires at startup (high level)
	•  Configuration classes discovered by component-scan (src/main/java/com/treishvaam/financeapi/config):
	•  SecurityConfig — defines the SecurityFilterChain, registers JwtTokenFilter and InternalSecretFilter, configures endpoint permit/deny rules, CORS and CSRF.
	•  WebConfig — MVC resource handlers, CORS mapping, view resolvers.
	•  OpenApiConfig — OpenAPI metadata and Swagger UI setup.
	•  CachingConfig — CacheManager bean configured with Caffeine.
	•  DataInitializer, MarketDataInitializer — ApplicationRunner/CommandLineRunner beans that seed data and initialize integrations on startup.
	•  Security wiring:
	•  JwtTokenProvider, JwtTokenFilter, InternalSecretFilter are discovered/registered and wired into the security filter chain defined by SecurityConfig.
	•  CustomUserDetailsService implements UserDetailsService and is used by SecurityConfig for auth lookups.
	•  Persistence & migrations:
	•  Liquibase changelogs under src/main/resources/db are applied (if Liquibase auto-run enabled) and JPA repositories (spring-data) are initialized; spring.jpa.hibernate.ddl-auto is set to validate to ensure schema matches entities.
	•  Startup runners & cache:
	•  CacheWarmupRunner and other runners run after context refresh to warm caches and seed runtime state.
	•  Component exclusions and conditional modules:
	•  marketdata package is excluded from auto-scan; enabling market-data requires removing the exclusion or registering beans conditionally.
Framework specifics to note
	•  Spring Boot 3.2.5 + Java 21: uses Jakarta EE namespaces (Tomcat 10+ compatibility).
	•  Controllers in com.treishvaam.financeapi.controller are auto-detected as @RestController/@Controller due to component-scan.
	•  Property resolution: application.properties + profile-specific files (application-dev.properties / application-prod.properties) and environment variables (JWT_SECRET_KEY, PROD_DB_* etc.) are used for wiring configuration values into beans (@Value or @ConfigurationProperties).
	•  Build plugin spring-boot-maven-plugin is present to enable spring-boot:run and packaging.
Quick pointers
	•  To change which packages are scanned, edit the @ComponentScan in FinanceApiApplication.
	•  To add a startup job, implement ApplicationRunner/CommandLineRunner and annotate as @Component or register as a @Bean.
	•  To alter JSON serialization, update the ObjectMapper bean in FinanceApiApplication or provide additional Jackson modules via @Configuration.



4.c) Package / module map
List of packages and responsibilities. e.g.:


controller / web — REST controllers (routes)
service — business logic
repository / dao — DB access
model / entity — JPA Entities / domain models
dto — request/response objects
config — config classes (security, CORS, beans)
exception — custom exceptions & handlers
scheduler — scheduled jobs / cron
integration — external APIs
4.c) Package / module map — responsibilities and key files
	•  com.treishvaam.financeapi (root)
	•  Responsibility: application entrypoint & overall component-scan configuration.
	•  Key: FinanceApiApplication.java (main, @EnableScheduling, @EnableCaching, ObjectMapper bean), ServletInitializer.java (WAR bootstrap).
	•  com.treishvaam.financeapi.controller
	•  Responsibility: HTTP API surface (REST controllers, view endpoints).
	•  Key controllers: AuthController, BlogPostController, CategoryController, ContactController, FileController, HealthCheckController, LogoController, OAuth2Controller, SearchController, SitemapController, ViewController.
	•  Notes: Controllers use DTOs and services; secure endpoints via SecurityConfig.
	•  com.treishvaam.financeapi.service
	•  Responsibility: business logic, orchestration, image & file handling, startup runners.
	•  Key: BlogPostService / BlogPostServiceImpl, FileStorageService, ImageService, CustomUserDetailsService, CacheWarmupRunner, DataInitializer, LinkedInService.
	•  Extension: add domain logic classes and background runners here.
	•  com.treishvaam.financeapi.repository
	•  Responsibility: persistence layer (Spring Data JPA repositories).
	•  Key: UserRepository, RoleRepository, BlogPostRepository, CategoryRepository, PageContentRepository, PostThumbnailRepository, ContactMessageRepository.
	•  Notes: Use derived query methods or @Query for custom queries.
	•  com.treishvaam.financeapi.model
	•  Responsibility: JPA entities / domain models mapped to DB tables.
	•  Key entities: User, Role, ERole, BlogPost, Category, PageContent, ContactMessage, PostThumbnail, PostStatus.
	•  Important: Schema is Liquibase-managed; do not change entities without adding migrations.
	•  com.treishvaam.financeapi.dto
	•  Responsibility: request/response payload objects (API surface).
	•  Key DTOs: LoginRequest, AuthResponse, BlogPostDto, BlogPostSuggestionDto, ContactInfoDTO, PostThumbnailDto, ShareRequest.
	•  Best practice: validate with javax.validation and map to entities in service layer.
	•  com.treishvaam.financeapi.config
	•  Responsibility: Spring configuration (security, web, cache, OpenAPI, data init, tenant config).
	•  Key: SecurityConfig, WebConfig, CachingConfig, OpenApiConfig, DataInitializer, MarketDataInitializer, tenant/*.
	•  Extension points: change cache settings, security rules, CORS, OpenAPI metadata here.
	•  com.treishvaam.financeapi.security
	•  Responsibility: authentication/authorization filters and JWT handling.
	•  Key: JwtTokenProvider (create/validate tokens), JwtTokenFilter (security filter chain integration), InternalSecretFilter (internal API secret enforcement).
	•  Notes: Secrets read from environment (JWT_SECRET_KEY). Map roles in CustomUserDetailsService.
	•  com.treishvaam.financeapi.apistatus
	•  Responsibility: track and expose external API fetch/health status.
	•  Key: ApiFetchStatus (entity), ApiFetchStatusRepository, ApiStatusController, PasswordDto.
	•  com.treishvaam.financeapi.marketdata
	•  Responsibility: market data ingestion & caching (external API integration).
	•  Key: market-data services (found in module). Note: excluded from component-scan by FinanceApiApplication (regex exclusion) — enable explicitly if required.
	•  com.treishvaam.financeapi.newshighlight
	•  Responsibility: news highlight feature area (models/services/controllers for curated news).
	•  Notes: feature-specific domain logic and UI feed generation.
	•  com.treishvaam.financeapi.web
	•  Responsibility: web MVC helpers, server-side view controllers, and static resource mappings.
	•  src/main/resources
	•  Responsibility: runtime configuration and packaged resources.
	•  Files:
		•  application.properties (base; references env vars like PROD_DB_URL, JWT_SECRET_KEY, MARKET_DATA_API_KEY)
		•  application-dev.properties / application-prod.properties (profile overrides)
		•  db/ (Liquibase changelogs — canonical DB migrations)
		•  static/ (static assets packaged in WAR)
		•  webapp/ (WAR web resources, if present)
	•  Notes: Use env vars for secrets; follow profiles for dev/prod/test.
	•  src/test/java & src/test/resources
	•  Responsibility: unit & integration tests.
	•  Notes: tests use H2 (test properties include test DB and test jwt secret). Keep test fixtures isolated.
	•  build artifacts & helper folders
	•  mvnw / mvnw.cmd / .mvn/ — Maven wrapper & config (use to build).
	•  pom.xml — Maven descriptor (Java 21, Spring Boot 3.2.5, packaging=war).
	•  target/ — build outputs: compiled classes, packaged WAR(s) (finance-api-*.war), surefire reports.
	•  bin/ — duplicate/backup project copy (ignore for development).
	•  user-uploads/ & test-uploads/ — local filesystem upload area and test assets (logo.webp); FileController/FileStorageService interact with storage.upload-dir.
	•  Cross-cutting modules & concerns
	•  Exception handling: global exception handlers (if present) should live in a dedicated package (not found explicitly — add one at com.treishvaam.financeapi.exception).
	•  Scheduler: scheduled jobs and initializers are implemented via @Scheduled and ApplicationRunner/CommandLineRunner (CacheWarmupRunner, MarketDataInitializer).
	•  Integration: external API clients (marketdata, LinkedInService) live under marketdata/ and service/; keys read from properties (fmp.api.key / MARKET_DATA_API_KEY).
Recommendations for contributors
	•  New REST endpoint: add DTO -> service -> repository -> controller; update OpenAPI config if needed.
	•  New entity: add model, repository, Liquibase changelog under src/main/resources/db/, then run migrations and keep spring.jpa.hibernate.ddl-auto=validate for prod.
	•  Enable market data: remove or modify the component-scan exclusion and ensure env var MARKET_DATA_API_KEY is provided.


4.d) Routes / API endpoints 
A table of all endpoints (method, path, description, auth required, request body, response, status codes).


Link to controller class + filename and method where the endpoint is implemented.


Example requests/responses (curl + example JSON).
Below is the complete routes / API endpoints inventory derived from the controllers in the codebase. For each endpoint I list: HTTP method, path, short description, whether auth is required, expected request body / params, response shape, typical status codes, and the controller file + method that implements it. Example curl requests/responses are provided for the most commonly used endpoints. Note: two controllers both map /api/logo (LogoController and FileController); this causes a duplicate mapping at runtime — see the note at the end.
Format: Controller → endpoints (table-like rows)
---
AuthController (src/main/java/com/treishvaam/financeapi/controller/AuthController.java)
- POST /api/auth/login
 - Description: Authenticate user (email/password) and return JWT + user info.
 - Auth required: No
 - Request body: JSON LoginRequest { "email": "user@example.com", "password": "secret" }
 - Response: AuthResponse { jwt, id, username, email, roles: [..], isLinkedinConnected: boolean }
 - Status codes: 200 OK (success), 401 Unauthorized (invalid credentials)
 - Implementation: AuthController.authenticateUser()
Example:
- Request:
 curl -X POST http://localhost:8080/api/auth/login \
   -H "Content-Type: application/json" \
   -d '{"email":"admin@example.com","password":"password"}'
- Example success response (200):
 {
   "token": "eyJhbGciOiJ...",
   "id": 1,
   "username": "admin@example.com",
   "email": "admin@example.com",
   "roles": ["ROLE_ADMIN"],
   "linkedinConnected": false
 }
---
BlogPostController (src/main/java/com/treishvaam/financeapi/controller/BlogPostController.java)
- GET /api/posts
 - Description: Paginated list of published posts (public listing).
 - Auth required: No
 - Query params: page (default 0), size (default 9)
 - Response: Page<BlogPost> (Spring Page object containing BlogPost entities)
 - Status codes: 200 OK
 - Implementation: BlogPostController.getAllPosts()
- GET /api/posts/admin/all
 - Description: Return all posts (admin view).
 - Auth required: Yes — ROLE_ADMIN
 - Response: List<BlogPost>
 - Status codes: 200 OK, 403 Forbidden (no role)
 - Implementation: BlogPostController.getAllPostsForAdmin()
- GET /api/posts/{id}
 - Description: Fetch a single post by numeric id.
 - Auth required: No
 - Path params: id (Long)
 - Response: BlogPost or 404 Not Found
 - Status codes: 200, 404
 - Implementation: BlogPostController.getPostById()
- GET /api/posts/url/{urlArticleId}
 - Description: Fetch a post by urlArticleId (alternate identifier).
 - Auth required: No
 - Path params: urlArticleId (String)
 - Response: BlogPost or 404
 - Status codes: 200, 404
 - Implementation: BlogPostController.getPostByUrlArticleId()
- GET /api/posts/category/{categorySlug}/{userFriendlySlug}/{id}
 - Description: Fetch post by full URL slug parts (used by public site routing).
 - Auth required: No
 - Path params: categorySlug, userFriendlySlug, id
 - Response: BlogPost or 404
 - Status codes: 200, 404
 - Implementation: BlogPostController.getPostByFullSlug()
- POST /api/posts/admin/backfill-slugs
 - Description: Admin task to backfill slugs on posts.
 - Auth required: Yes — ROLE_ADMIN
 - Response: Map message with counts
 - Status codes: 200, 403
 - Implementation: BlogPostController.backfillSlugs()
- GET /api/posts/admin/drafts
 - Description: List draft posts for admin.
 - Auth required: Yes — ROLE_ADMIN
 - Response: List<BlogPost>
 - Status codes: 200, 403
 - Implementation: BlogPostController.getDrafts()
- POST /api/posts/draft
 - Description: Create a draft post (admin).
 - Auth required: Yes — ROLE_ADMIN
 - Request body: BlogPostDto JSON
 - Response: created BlogPost
 - Status codes: 201 Created, 400 Bad Request, 403
 - Implementation: BlogPostController.createDraft()
- PUT /api/posts/draft/{id}
 - Description: Update a draft (admin).
 - Auth required: Yes — ROLE_ADMIN
 - Request body: BlogPostDto JSON
 - Response: updated BlogPost or 404
 - Status codes: 200, 404, 403
 - Implementation: BlogPostController.updateDraft()
- POST /api/posts
 - Description: Create a new post (admin). Accepts multipart form (text fields + images).
 - Auth required: Yes — ROLE_ADMIN
 - Request: multipart/form-data with many fields:
   - title (string), content (string)
   - optional: userFriendlySlug, customSnippet, metaDescription, keywords (string), tags (list), featured (boolean)
   - category (string), layoutStyle (string), layoutGroupId (string)
   - scheduledTime (ISO datetime) optional
   - newThumbnails (files[]), thumbnailMetadata (JSON string), coverImage (file), coverImageAltText (string), thumbnailOrientation
 - Response: created BlogPost (JSON)
 - Status codes: 201 Created, 400 Bad Request, 403
 - Implementation: BlogPostController.createPost()
- POST /api/posts/{id}/duplicate
 - Description: Duplicate a post by id (admin).
 - Auth required: Yes — ROLE_ADMIN
 - Response: created duplicated BlogPost or 404
 - Status codes: 201, 404, 403
 - Implementation: BlogPostController.duplicatePost()
- PUT /api/posts/{id}
 - Description: Update an existing post (admin). Multipart fields same as create.
 - Auth required: Yes — ROLE_ADMIN
 - Request: multipart/form-data (see POST /api/posts)
 - Response: updated BlogPost or 404
 - Status codes: 200, 404, 403
 - Implementation: BlogPostController.updatePost()
- DELETE /api/posts/{id}
 - Description: Delete a post by id (admin).
 - Auth required: Yes — ROLE_ADMIN
 - Response: No content
 - Status codes: 204 No Content, 403
 - Implementation: BlogPostController.deletePost()
- DELETE /api/posts/bulk
 - Description: Delete multiple posts by id list (admin).
 - Auth required: Yes — ROLE_ADMIN
 - Request body: JSON array of ids [1,2,3]
 - Response: No content
 - Status codes: 204, 400, 403
 - Implementation: BlogPostController.deleteMultiplePosts()
- POST /api/posts/{id}/share
 - Description: Share the post to LinkedIn via LinkedInService (admin). Reactive Mono result.
 - Auth required: Yes — ROLE_ADMIN
 - Request body: ShareRequest JSON { "message": "...", "tags": ["#tag1", ...] }
 - Response: 200 OK with success message OR 404 Not Found OR 500 Internal Server Error OR 501 Not Implemented (if LinkedIn integration disabled)
 - Status codes: 200, 404, 500, 501, 403
 - Implementation: BlogPostController.sharePost()
---
CategoryController (src/main/java/com/treishvaam/financeapi/controller/CategoryController.java)
- GET /api/categories
 - Description: List all categories.
 - Auth required: No
 - Response: List<Category>
 - Status codes: 200
 - Implementation: CategoryController.getAllCategories()
- POST /api/categories
 - Description: Create a new category (admin).
 - Auth required: Yes — ROLE_ADMIN
 - Request body: JSON { "name": "Category Name" }
 - Response: saved Category
 - Status codes: 200 (created), 400 (bad input), 403
 - Implementation: CategoryController.createCategory()
- POST /api/categories/admin/backfill-slugs
 - Description: Backfill missing category slugs.
 - Auth required: Yes — ROLE_ADMIN
 - Response: Map message with count
 - Status codes: 200, 403
 - Implementation: CategoryController.backfillCategorySlugs()
---
ContactController (src/main/java/com/treishvaam/financeapi/controller/ContactController.java)
- POST /api/contact
 - Description: Submit contact message (persisted).
 - Auth required: No
 - Request body: ContactMessage JSON (entity shape: name, email, message, etc.)
 - Response: 200 OK with "Message received successfully!"
 - Status codes: 200, 400
 - Implementation: ContactController.submitContactForm()
- GET /api/contact/info
 - Description: Get contact information for the site (static DTO).
 - Auth required: No
 - Response: ContactInfoDTO { email, phone, address }
 - Status codes: 200
 - Implementation: ContactController.getContactInfo()
---
FileController (src/main/java/com/treishvaam/financeapi/controller/FileController.java)
- GET /api/logo
 - Description: Serve cached logo.webp from storage.upload-dir (content-type image/webp). NOTE: LogoController also maps /api/logo — duplicate mapping conflict (see note).
 - Auth required: No
 - Response: binary image (image/webp) or 404
 - Status codes: 200, 404, 500
 - Implementation: FileController.serveLogo()
- POST /api/files/upload
 - Description: Upload a file (image); server processes to multiple .webp sizes and returns URLs/meta.
 - Auth required: (not protected in code) — public endpoint
 - Request: multipart/form-data; param name: file (single file)
 - Response: JSON { "result": [ { "url": "/api/uploads/<base>.webp", "urls": { "large": "...", "medium": "...", "small": "..." }, "name": "<base>", "size": <bytes> } ] }
 - Status codes: 200 OK, 400 Bad Request (empty file), 500 Internal Server Error
 - Implementation: FileController.handleFileUpload()
Notes: The controller returns upload URLs in the form /api/uploads/<name>.webp and /api/uploads/<name>-medium.webp etc. Actual serving of /api/uploads/* may be implemented by a resource handler (WebConfig) or FileStorageService — search WebConfig/resource handlers if you need explicit endpoint.
---
LogoController (src/main/java/com/treishvaam/financeapi/controller/LogoController.java)
- GET /api/logo, /logo512.png, /logo.png, /favicon.ico
 - Description: Serve classpath static/logo512.png as image/png for multiple common logo paths.
 - Auth required: No
 - Response: image/png bytes (static file) or 404
 - Status codes: 200, 404
 - Implementation: LogoController.getLogo()
Conflict: /api/logo is mapped by both FileController and LogoController. At runtime Spring will fail to start or choose one mapping depending on bean ordering — this must be resolved (remove or rename one mapping).
---
OAuth2Controller (src/main/java/com/treishvaam/financeapi/controller/OAuth2Controller.java)
- GET /api/login/oauth2/code/linkedin
 - Description: OAuth2 callback endpoint for LinkedIn — stores access token and expiry on the user record and redirects to frontend dashboard.
 - Auth required: implicit OAuth2 authenticated flow
 - Request: OAuth2 redirect from provider (handled by Spring Security OAuth2 client)
 - Response: HTTP redirect to frontend (http://localhost:3000/dashboard in code)
 - Status codes: 302 redirect (or 400/500 on error)
 - Implementation: OAuth2Controller.handleLinkedInCallback()
---
SearchController (src/main/java/com/treishvaam/financeapi/controller/SearchController.java)
- GET /api/search?q=<term>
 - Description: Search posts by title for suggestions (returns minimal suggestion DTO).
 - Auth required: No
 - Query params: q (string)
 - Response: List<BlogPostSuggestionDto> [{ id, title, slug }, ...] or [] when q empty
 - Status codes: 200
 - Implementation: SearchController.searchPosts()
Example:
- curl "http://localhost:8080/api/search?q=market"
- Example response:
 [
   { "id": 12, "title": "Market update", "slug": "market-update" },
   { "id": 15, "title": "How markets reacted", "slug": "how-markets-reacted" }
 ]
---
SitemapController (src/main/java/com/treishvaam/financeapi/controller/SitemapController.java)
- GET /sitemap.xml
 - Description: Generate dynamic XML sitemap including static pages and published posts.
 - Auth required: No
 - Response: sitemap XML (Content-Type: application/xml)
 - Status codes: 200
 - Implementation: SitemapController.getSitemap()
---
ViewController (src/main/java/com/treishvaam/financeapi/controller/ViewController.java)
- GET /category/{categorySlug}/{userFriendlySlug}/{urlArticleId}
 - Description: Server-side rendered page HTML for a post with SEO meta replaced (caches entries).
 - Auth required: No
 - Response: HTML (text/html) — index.html with SEO replacements
 - Status codes: 200, 404 (if index missing)
 - Implementation: ViewController.getPostView()
- GET /, /about, /services, /vision, /education, /contact, /login
 - Description: Serve SPA index page with page-specific SEO meta when PageContent exists.
 - Auth required: No
 - Response: HTML
 - Status codes: 200
 - Implementation: ViewController.serveStaticPage()
- GET /dashboard/**
 - Description: Serve SPA dashboard route (index.html) for frontend routing.
 - Auth required: No (frontend handles auth)
 - Response: HTML
 - Status codes: 200
 - Implementation: ViewController.forwardToDashboard()
---
ApiStatusController (src/main/java/com/treishvaam/financeapi/apistatus/ApiStatusController.java)
Class-level @PreAuthorize("hasAuthority('ROLE_ADMIN')") — all endpoints require ROLE_ADMIN.
- GET /api/status
 - Description: Return the latest API fetch statuses for external integrations.
 - Auth required: Yes — ROLE_ADMIN
 - Response: List<ApiFetchStatus>
 - Status codes: 200, 403
 - Implementation: ApiStatusController.getLatestApiStatuses()
- GET /api/status/history
 - Description: Return full history of API fetch logs.
 - Auth required: Yes — ROLE_ADMIN
 - Response: List<ApiFetchStatus>
 - Status codes: 200, 403
 - Implementation: ApiStatusController.getFullApiStatusHistory()
---
HealthCheckController (src/main/java/com/treishvaam/financeapi/controller/HealthCheckController.java)
- GET /health
 - Description: Lightweight health endpoint returning "OK".
 - Auth required: No
 - Response: text/plain "OK"
 - Status codes: 200
 - Implementation: HealthCheckController.checkHealth()
Example:
- curl http://localhost:8080/health
 - returns: OK
---
Files / uploads note
- Uploaded images URLs returned use /api/uploads/<name>.webp and variants. Serving of these path(s) is handled by a resource handler or FileStorageService; check WebConfig and FileStorageService to confirm static resource mapping. If not present, the app may expose them via an endpoint or static resource folder under target/classes/static or user-uploads folder configured by storage.upload-dir.
Authentication & Authorization
- JWT tokens: endpoints that require auth (ROLE_ADMIN) are protected by Spring Security configuration. To call protected endpoints:
 - Obtain token from /api/auth/login
 - Include header: Authorization: Bearer <token>
- OAuth2 endpoints require proper OAuth2 client configuration (linkedin client configured in application properties).
Duplicate mapping warning
- There are two controllers that map /api/logo:
 - FileController.serveLogo() (serves logo.webp from configured storage directory)
 - LogoController.getLogo() (serves static/logo512.png from classpath as image/png)
- This is a conflicting mapping. At runtime Spring will raise an ambiguous mapping error or undefined behavior. Fix by renaming or removing one of the mappings (suggestion: use /api/logo.webp for webp file and keep /api/logo for PNG, or consolidate logic into a single controller that picks the correct content-type).
---


4.e) Business logic & flow mapping
For key workflows (e.g., signup, login, order processing) include sequence diagrams or step-by-step flow:
Controller → Service → Repo → External API → Response
For each key flow map to files / classes that implement it.

4.e) Business logic & flow mapping
Below are concise step‑by‑step flows and simple ASCII sequence diagrams for the key workflows implemented in the codebase. Each step maps to the controller / service / repository / external classes and methods that implement it so a developer can quickly locate the code.
Note: method names are taken from the repository files scanned earlier.
1) Login (JWT issuance)
- Purpose: authenticate user via email/password and return JWT + user info.
Flow (steps)
1. Client POST /api/auth/login with JSON { email, password }
  - Controller: com.treishvaam.financeapi.controller.AuthController.authenticateUser()
2. Controller calls AuthenticationManager.authenticate(...)
  - Under the hood: Spring Security uses CustomUserDetailsService to load user details:
    - com.treishvaam.financeapi.service.CustomUserDetailsService.loadUserByUsername()
    - Repository: com.treishvaam.financeapi.repository.UserRepository.findByEmail(...)
3. If authentication succeeds, SecurityContextHolder is set.
  - Controller then calls JwtTokenProvider.createToken(authentication)
    - com.treishvaam.financeapi.security.JwtTokenProvider.createToken()
    - Uses property jwt.secret and jwt.expiration.ms
4. Controller fetches the User entity to include extra info (LinkedIn token, roles)
  - UserRepository.findByEmail(...)
5. Controller returns AuthResponse (JWT + user data)
  - com.treishvaam.financeapi.dto.AuthResponse
ASCII sequence
Client -> AuthController.authenticateUser -> AuthenticationManager.authenticate
AuthenticationManager -> CustomUserDetailsService.loadUserByUsername -> UserRepository.findByEmail
AuthController -> JwtTokenProvider.createToken -> (returns token)
AuthController -> Response (AuthResponse with token)
Where to edit
- change JWT claims/expiry: JwtTokenProvider.java
- change user lookup or extra returned fields: AuthController.java / UserRepository.java / User entity
2) Protected request (JWT validation on later requests)
- Purpose: validate incoming JWT and populate SecurityContext.
Flow (steps)
1. Client sends request to protected endpoint with header Authorization: Bearer <token>.
2. JwtTokenFilter intercepts request before controller:
  - com.treishvaam.financeapi.security.JwtTokenFilter.doFilterInternal(...) (filter class)
  - Calls JwtTokenProvider.validateToken(...) and JwtTokenProvider.getAuthentication(...)
3. If valid, JwtTokenFilter sets SecurityContext authentication and request proceeds to controller.
4. Controller executes (e.g., BlogPostController.admin endpoints) and can use SecurityContextHolder.getContext().getAuthentication() for username/roles.
ASCII sequence
Client -> HTTP Request (Authorization: Bearer token) -> JwtTokenFilter -> JwtTokenProvider.validateToken
If valid -> JwtTokenProvider.getAuthentication -> SecurityContext set -> Controller method
Where to edit
- token parsing/validation: JwtTokenProvider.java
- filter behaviour (header name, error responses): JwtTokenFilter.java
- role mapping: CustomUserDetailsService.java
3) OAuth2 LinkedIn connect (callback flow)
- Purpose: receive OAuth2 callback, store LinkedIn access token on User, redirect to frontend.
Flow (steps)
1. OAuth2 provider redirects user to configured callback: GET /api/login/oauth2/code/linkedin
  - Controller: com.treishvaam.financeapi.controller.OAuth2Controller.handleLinkedInCallback()
  - Method parameters: @RegisteredOAuth2AuthorizedClient("linkedin") OAuth2AuthorizedClient and @AuthenticationPrincipal OAuth2User
2. Controller extracts OAuth2AuthorizedClient.getAccessToken().getTokenValue() and expiresAt.
3. Controller locates User by email:
  - UserRepository.findByEmail(userEmail)
4. Controller persists linkedinAccessToken, linkedinTokenExpiry, linkedinUrn on User and saves:
  - UserRepository.save(user)
5. Controller redirects user to frontend (hardcoded redirect in code to http://localhost:3000/dashboard)
ASCII sequence
LinkedIn -> /api/login/oauth2/code/linkedin -> OAuth2Controller.handleLinkedInCallback
handleLinkedInCallback -> UserRepository.findByEmail -> user.setLinkedinAccessToken / setExpiry -> UserRepository.save -> redirect to frontend
Where to edit
- OAuth2 client config: application-*.properties and Spring Security OAuth2 config
- redirect target: OAuth2Controller.java
4) File upload (image conversion + URLs)
- Purpose: upload image, convert/generate multiple webp sizes, return URLs metadata.
Flow (steps)
1. Client POST multipart/form-data to /api/files/upload with param file
  - Controller: com.treishvaam.financeapi.controller.FileController.handleFileUpload()
2. Controller calls FileStorageService.storeFile(MultipartFile)
  - com.treishvaam.financeapi.service.FileStorageService.storeFile()
3. FileStorageService delegates image processing to ImageService (thumbnail creation & webp conversion)
  - com.treishvaam.financeapi.service.ImageService (uses Thumbnailator and webp imageio libs)
4. FileStorageService persists files to disk under storage.upload-dir and returns base file name
5. Controller constructs response JSON listing URLs (/api/uploads/<base>.webp, -medium, -small) and returns result.
ASCII sequence
Client -> FileController.handleFileUpload -> FileStorageService.storeFile -> ImageService.process/convert -> filesystem (storage.upload-dir)
FileController -> Response with URLs
Where to edit
- file destination/validation: FileStorageService.java
- image processing details: ImageService.java
- response shape or URLs: FileController.java
5) Create post (admin multipart create with thumbnails & cover)
- Purpose: create a BlogPost with optional thumbnails and cover image; store media and persist post.
Flow (steps)
1. Admin client POST multipart/form-data to /api/posts with text fields, files (newThumbnails[], coverImage), thumbnailMetadata JSON.
  - Controller: com.treishvaam.financeapi.controller.BlogPostController.createPost(...)
2. Controller builds a BlogPost entity (sets author from SecurityContext), resolves Category:
  - blogPostService.findCategoryByName(categoryName)
3. Controller reads thumbnailMetadata JSON via ObjectMapper (injected).
4. Controller calls blogPostService.save(newPost, newThumbnails, thumbnailDtos, coverImage)
  - com.treishvaam.financeapi.service.BlogPostService.save(...)
  - Implementation: com.treishvaam.financeapi.service.BlogPostServiceImpl.save(...) (persists entity)
5. Save process:
  - Save BlogPost via BlogPostRepository.save(...)
  - For each uploaded file: FileStorageService.storeFile(...) -> ImageService.process -> save PostThumbnail entity via PostThumbnailRepository
  - Update BlogPost.coverImageUrl if cover image saved
6. blogPostService.save returns saved BlogPost entity
7. Controller returns 201 Created with saved BlogPost JSON
ASCII sequence
Admin -> BlogPostController.createPost -> blogPostService.save -> BlogPostRepository.save
blogPostService.save -> for each file -> FileStorageService.storeFile -> ImageService -> PostThumbnailRepository.save
blogPostService.save -> return saved BlogPost -> Controller -> 201 Created
Where to edit
- post persistence logic: BlogPostServiceImpl.java
- thumbnail entity behavior: PostThumbnail.java and PostThumbnailRepository.java
- slug generation: BlogPostService.generateUserFriendlySlug (used by CategoryController too)
6) Duplicate post
- Purpose: create a copy of an existing post.
Flow (steps)
1. Admin POST /api/posts/{id}/duplicate
  - Controller: BlogPostController.duplicatePost()
2. Controller calls blogPostService.duplicatePost(id)
  - Implementation in BlogPostServiceImpl.duplicatePost(...) clones fields, creates new entity, persists via BlogPostRepository
3. Return created BlogPost 201 or 404 when original not found
Where to edit
- BlogPostServiceImpl.duplicatePost(...)
7) Search suggestions
- Purpose: return lightweight suggestions matching title for published posts.
Flow (steps)
1. Client GET /api/search?q=term
  - Controller: com.treishvaam.financeapi.controller.SearchController.searchPosts()
2. Controller calls BlogPostRepository.findByTitleContainingIgnoreCaseAndStatus(q, PostStatus.PUBLISHED)
  - Repository: com.treishvaam.financeapi.repository.BlogPostRepository
3. Map results to BlogPostSuggestionDto and return 200 OK
ASCII sequence
Client -> SearchController.searchPosts -> BlogPostRepository.findByTitleContainingIgnoreCaseAndStatus -> map to DTO -> Response 200
8) Sitemap generation
- Purpose: dynamic /sitemap.xml that includes static pages and published posts.
Flow (steps)
1. Client GET /sitemap.xml
  - Controller: SitemapController.getSitemap()
2. Controller obtains published posts:
  - BlogPostService.findAllByStatus(PostStatus.PUBLISHED)
3. Controller obtains category slug map:
  - CategoryRepository.findAll()
4. Controller builds XML (loc, lastmod, changefreq) and returns application/xml
Where to edit
- Sitemap formatting: SitemapController.getSitemap()
- Source rules for pages/posts: BlogPostService, CategoryRepository
9) Health check
- Purpose: quick ping for monitoring.
Flow (steps)
1. Client GET /health
  - Controller: HealthCheckController.checkHealth()
2. Controller returns "OK" 200
10) Startup tasks: Data initializer, Market data initializer, Cache warmup
- Purpose: seed roles/users, prime caches, fetch initial market data.
Flow (steps)
1. Spring context refresh triggers ApplicationRunner / CommandLineRunner beans:
  - com.treishvaam.financeapi.config.DataInitializer (seeds roles/users/pages)
  - com.treishvaam.financeapi.config.MarketDataInitializer (fetches market data or config)
  - com.treishvaam.financeapi.service.CacheWarmupRunner (pre-populate caches)
2. DataInitializer calls repositories (RoleRepository, UserRepository, PageContentRepository) to seed rows.
3. MarketDataInitializer calls market data client code (marketdata package) — if enabled — to call external API with fmp.api.key (PROPERTY).
4. CacheWarmupRunner calls service methods (e.g., blogPostService) to populate named caches (configured in CachingConfig).
ASCII sequence (startup)
SpringBoot -> DataInitializer.run -> RoleRepository.save / UserRepository.save
SpringBoot -> MarketDataInitializer.run -> MarketDataClient.fetch -> ApiFetchStatusRepository.save
SpringBoot -> CacheWarmupRunner.run -> blogPostService.find... -> CacheManager.put
Where to edit
- startup seeding: DataInitializer.java
- cache names & TTL: CachingConfig.java and CacheWarmupRunner.java
- market data fetch logic: marketdata package & MarketDataInitializer.java
11) Share post to LinkedIn
- Purpose: post content to LinkedIn using stored LinkedIn access token.
Flow (steps)
1. Admin POST /api/posts/{id}/share with ShareRequest { message, tags }
  - Controller: BlogPostController.sharePost()
2. Controller fetches post: blogPostService.findById(id)
3. Controller calls linkedInService.sharePost(post, message, tags)
  - com.treishvaam.financeapi.service.LinkedInService.sharePost(...)
  - Uses User.linkedinAccessToken from User entity saved earlier
  - Makes external HTTP call to LinkedIn API (OAuth2 bearer token)
4. Service returns Mono/Reactive result; Controller maps success -> 200, errors -> 500
Where to edit
- LinkedIn API details & endpoints: LinkedInService.java
- token expiry handling: code that checks linkedinTokenExpiry in AuthController and LinkedInService
General mapping table (quick reference)
- Controllers:
 - AuthController.authenticateUser() — auth flow
 - BlogPostController.* — posts CRUD, share
 - FileController.handleFileUpload() — uploads
 - OAuth2Controller.handleLinkedInCallback() — OAuth2 callback
 - SearchController.searchPosts() — search
 - SitemapController.getSitemap() — sitemap
 - ViewController.* — SSR page serving
 - ApiStatusController.* — API fetch statuses
 - HealthCheckController.checkHealth() — health
- Services:
 - BlogPostService / BlogPostServiceImpl — posts domain logic and persistence orchestration
 - FileStorageService — file persistence and base filename
 - ImageService — image conversion & resizing
 - CustomUserDetailsService — Spring Security user lookup
 - LinkedInService — LinkedIn API integration
 - CacheWarmupRunner — cache pre-warm
- Repositories:
 - UserRepository, RoleRepository, BlogPostRepository, CategoryRepository, PostThumbnailRepository, ApiFetchStatusRepository, ContactMessageRepository, PageContentRepository
- Security:
 - JwtTokenProvider, JwtTokenFilter, InternalSecretFilter, SecurityConfig
- Startup config:



4.j) Background jobs, scheduling & queues (APPENDED DUPLICATE)

- Scheduling in this project
  - Scheduling is enabled application-wide via @EnableScheduling on com.treishvaam.financeapi.FinanceApiApplication.
  - Observed scheduled jobs:
    - com.treishvaam.financeapi.marketdata.MarketDataScheduler
      - Cron jobs:
        - fetchIndianMarketMovers(): cron "0 30 16 * * MON-FRI" (zone Asia/Kolkata) — fetches Indian market movers via MarketDataService.fetchAndStoreMarketData("IN", "AUTOMATIC").
        - fetchUsMarketMovers(): cron "0 0 22 * * MON-FRI" (zone UTC) — fetches US market movers via MarketDataService.fetchAndStoreMarketData("US", "AUTOMATIC").
    - com.treishvaam.financeapi.service.BlogPostServiceImpl
      - checkAndPublishScheduledPosts(): @Scheduled(fixedRate = 60000) — runs once a minute to publish posts whose scheduledTime has passed.
    - Application runners / initializers that run on startup (non-scheduled but important):
      - com.treishvaam.financeapi.config.MarketDataInitializer (CommandLineRunner) — performs an initial market-data fetch on application start (profile !test).
      - com.treishvaam.financeapi.config.DataInitializer / CacheWarmupRunner — seed data and warm caches on startup.

- Workers / external queues
  - There are no RabbitMQ, Kafka or other external queue client dependencies in the repository. No message listeners or queue consumers were detected.
  - If you need asynchronous/queued processing, add a messaging dependency (spring-boot-starter-amqp for RabbitMQ or spring-kafka) and implement message listeners/producer beans. Also add durable queue configuration and credentials as environment variables.

- How to trigger jobs locally
  - Automatic scheduling: simply run the application (./mvnw spring-boot:run or start the packaged WAR in Tomcat); scheduled jobs will run according to their configured cron/fixedRate expressions.
  - Market data (manual trigger): the project exposes admin endpoints to trigger market fetches manually (requires ROLE_ADMIN):
    - POST /api/market/admin/refresh-movers — triggers marketDataService.fetchAndStoreMarketData("US", "MANUAL").
    - POST /api/market/admin/refresh-indices — triggers marketDataService.refreshIndices().
  - Blog post schedule (manual trigger): there is no public HTTP endpoint to invoke checkAndPublishScheduledPosts(). To run it manually you can:
    - Call the service method from a unit/integration test (recommended for one-off runs).
    - Start the application in the debugger/IDE and invoke blogPostService.checkAndPublishScheduledPosts() programmatically.
    - Temporarily add a protected admin endpoint that calls BlogPostService.checkAndPublishScheduledPosts() and remove it after use.
  - Startup-initializers run automatically when the Spring context is started (DataInitializer, MarketDataInitializer).

4.k) Caching & performance (APPENDED DUPLICATE)

- Cache locations & types
  - In‑process (local) cache: the app uses Spring Cache backed by Caffeine. Configuration is in com.treishvaam.financeapi.config.CachingConfig:
    - Named caches: "blogPostHtml" (CachingConfig.BLOG_POST_CACHE).
    - Caffeine configuration: expireAfterWrite(1 day), maximumSize(500).
  - Persistent/DB cache: market historical data uses a DB cache table (com.treishvaam.financeapi.marketdata.HistoricalDataCache) with repository HistoricalDataCacheRepository. MarketDataService checks lastFetched timestamp and treats cached entries younger than 30 minutes as fresh.
  - No Redis/remote cache dependency was found in the repo.

- Cache invalidation rules (observed)
  - Blog post caches are evicted via annotations in BlogPostServiceImpl:
    - @CacheEvict(value = BLOG_POST_CACHE, key = "#result.slug", condition = "#result.slug != null and #result.status.name() == 'PUBLISHED'") on save — evicts the cache entry for the newly published post slug when a post is saved and published.
    - @CacheEvict(value = BLOG_POST_CACHE, allEntries = true) on delete operations — clears all blog post HTML cache pages on deletes/bulk deletes.
  - Historical market data: invalidated by time check (minutes since lastFetched) performed inside MarketDataService; entries older than CACHE_DURATION_MINUTES (30) are refreshed.

- DB connection pool / tuning
  - HikariCP is on the classpath (auto-configured by Spring Boot). The project does not provide explicit Hikari configuration in application properties; therefore the Hikari defaults are used unless overridden by environment properties.
  - To tune Hikari, set these properties (application-*.properties or env vars):
    - spring.datasource.hikari.maximum-pool-size (recommended: tune based on CPU and DB capacity, e.g. 10–50 for medium apps)
    - spring.datasource.hikari.minimum-idle
    - spring.datasource.hikari.connection-timeout
    - spring.datasource.hikari.idle-timeout
    - spring.datasource.hikari.max-lifetime
  - Example env var style (Windows PowerShell):
    - $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20

- Thread pool / scheduler tuning
  - No custom TaskScheduler or ThreadPoolTaskExecutor bean is defined in the repo. That means scheduling and any @Async work uses Spring Boot defaults (single-thread scheduled executor for @Scheduled annotations and default task executors for async calls).
  - If you have multiple or long-running scheduled jobs, add a ThreadPoolTaskScheduler bean or implement SchedulingConfigurer, for example:
    - Define a ThreadPoolTaskScheduler with a pool size (e.g., 5–20) and register it as the scheduler used by @Scheduled tasks.
  - Useful properties to control executors when added:
    - spring.task.execution.pool.core-size
    - spring.task.execution.pool.max-size
    - spring.task.execution.pool.queue-capacity

- Performance notes
  - Heavy scheduled work (market fetch + DB saves) can block other scheduled tasks because the default scheduler is not tuned. Add a multi-thread scheduler before adding CPU/IO-heavy scheduled jobs.
  - Caffeine is in-process: it scales with the app JVM and does not provide shared caching across multiple instances. For multi-instance deployments consider Redis or a distributed cache.

4.l) Tests (unit/integration) (APPENDED DUPLICATE)

- Organization
  - Tests live under src/test/java with a mirror of main package structure (com.treishvaam.financeapi.*). Test configuration files live in src/test/resources.
  - Tests use H2 in-memory database for fast execution. Test properties are visible under target/test-classes/application-test.properties and src/test/resources/application-test.properties.

- How to run
  - Run unit and integration tests with Maven (wrapper):
    - Windows: mvnw.cmd test
    - macOS / Linux: ./mvnw test
  - CI/Headless: mvn -B -DskipTests=false test
  - Test reports: target/surefire-reports/ (XML and dumps) for CI diagnostics.

- Test data generation & fixtures
  - Some tests rely on in-repo test fixtures and TestConfig helper classes. DataInitializer is profile-aware and is excluded when running tests (seeds are usually controlled by test fixtures).
  - There is no Testcontainers usage detected in the repository. Integration tests that need a real MariaDB (or other infra) should either use Testcontainers or an externally provided test database.
  - Recommendation: add Testcontainers for MariaDB or Redis if you introduce external infra in integration tests (use @Testcontainers and container lifecycle management in tests).

4.m) Docker & deployment packages (APPENDED DUPLICATE)

- Current repo state
  - No Dockerfile was found in the repository.
  - Packaging: the project is built as a WAR (pom.xml packaging=war) with spring-boot-starter-tomcat set to provided — the intended deployment model is a WAR deployed to an external Tomcat (Tomcat 10+ for Spring Boot 3 / Jakarta namespaces).
  - No docker-compose.yml or Kubernetes manifests were found in the repo.

- How to containerize (recommended minimal options)
  - Option A — WAR deployed on Tomcat container (recommended when you must match existing app-server deployments):
    - Build the WAR: ./mvnw clean package
    - Use an official Tomcat 10 image and COPY the WAR into /usr/local/tomcat/webapps/
    - Provide environment variables (PROD_DB_URL, PROD_DB_USERNAME, PROD_DB_PASSWORD, JWT_SECRET_KEY, INTERNAL_API_SECRET_KEY, MARKET_DATA_API_KEY) to the container at runtime.
  - Option B — runnable fat JAR (single container):
    - Change packaging to jar and remove provided scope for spring-boot-starter-tomcat in pom.xml, or add an alternative profile that builds an executable artifact with embedded Tomcat.
    - Build: ./mvnw clean package
    - Run with java -jar target/finance-api-*.jar in a container.

- Example docker-compose (development) — minimal stack (MariaDB + app)
  - The repo has no docker-compose; suggested compose for local dev:
    - services:
      - db: mariadb:10.11
        environment: MYSQL_ROOT_PASSWORD, MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD
        ports: 3306:3306
      - app: build: . (if Dockerfile present) or image: finance-api:dev
        environment: PROD_DB_URL=jdbc:mariadb://db:3306/treishvaam_dev, PROD_DB_USERNAME, PROD_DB_PASSWORD, JWT_SECRET_KEY, INTERNAL_API_SECRET_KEY, MARKET_DATA_API_KEY
        ports: 8080:8080
      - optional: redis: for a distributed cache if you adopt it later
  - Persist volume for DB and map user-uploads/storage path for file uploads when testing locally.

- Kubernetes / manifests
  - There are no Kubernetes YAML manifests in the repository. If you introduce k8s artifacts, place them under a top-level directory such as /deploy/k8s/ or /k8s/ with separate manifests for:
    - Deployment, Service, ConfigMap (for non-secret config), Secret (for credentials), PersistentVolumeClaim (for file uploads if needed), and HorizontalPodAutoscaler if required.

4.n) Third-party integrations (APPENDED DUPLICATE)

- Present integrations (observed in code)
  - Market-data providers
    - Property: fmp.api.key (mapped to env var MARKET_DATA_API_KEY in documentation). Used by classes under com.treishvaam.financeapi.marketdata and MarketDataFactory/providers.
  - News API (dev)
    - Dev property: newsdata.api.key in application-dev.properties (example key placeholder).
  - LinkedIn
    - com.treishvaam.financeapi.service.LinkedInService uses WebClient and user-linked LinkedIn tokens (code and DB columns reference linkedin token fields in changelogs). LinkedIn integration uses OAuth2 flows (the repo includes spring-boot-starter-oauth2-client) and per-user tokens stored in User records (see Liquibase changelog that adds linkedin token to users).
  - OAuth2 client / SSO
    - spring-boot-starter-oauth2-client is present — SSO provider(s) can be configured via standard Spring properties (spring.security.oauth2.client.registration.* and spring.security.oauth2.client.provider.*).

- Not present (no code detected)
  - No Stripe or PayPal SDKs or integration code detected.
  - No SMS provider (Twilio, AWS SNS) dependency or code detected.
  - No webhook-specific controller for external payments or third-party webhooks discovered.

- Environment variables used for integrations & credentials (observed)
  - PROD_DB_URL, PROD_DB_USERNAME, PROD_DB_PASSWORD — database
  - JWT_SECRET_KEY — JWT signing secret
  - INTERNAL_API_SECRET_KEY (app.security.internal-secret) — used by InternalSecretFilter to secure internal endpoints
  - MARKET_DATA_API_KEY — market data API key (fmp.api.key)
  - newsdata.api.key — development news API key (application-dev.properties)
  - storage.upload-dir / STORAGE_UPLOAD_DIR — filesystem path used by FileStorageService for uploads
  - app.base-url — base URL used to build absolute URLs
  - CORS/CORS_ALLOWED_ORIGINS / cors.allowed-origins — CORS configuration

- Webhook & security considerations
  - Internal secret enforcement: InternalSecretFilter enforces an app.security.internal-secret header for internal endpoints — implement the same pattern for incoming webhooks if you need to verify sender authenticity.
  - For any webhook endpoints you add (payment providers, SMS callbacks, etc.):
    - Use a signature verification approach (HMAC with a shared secret) or provider-supplied signature headers.
    - Validate the request body and record the event in a dedicated table (for audit & replay/diagnostics).
    - Protect endpoints with a dedicated path and IP/ACLs if possible; log and monitor failures.

- Recommendations before enabling new providers
  - Add explicit configuration properties (and a .env.example) for each provider (e.g., STRIPE_API_KEY, PAYPAL_CLIENT_ID/SECRET, TWILIO_SID/AUTH_TOKEN, EMAIL_SMTP_HOST/USERNAME/PASSWORD).
  - Avoid committing provider credentials in code or test resources.
  - Add integration tests (use Testcontainers or sandbox/test keys) and record webhooks to a persistent store for replay and debugging.


Append summary (DUPLICATE)
- The repository currently supports scheduled market-data fetch and blog-post publishing (in-process scheduler), in-memory Caffeine caching and a DB-based short-lived historical-data cache. There is no external queue, Redis, Dockerfile, docker-compose, or k8s manifests in the repo — the appended documentation above describes the current state and recommended steps to add these capabilities.
