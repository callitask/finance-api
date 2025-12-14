# 09-DEPLOYMENT-OPS.md

## 1. Build Process
- Use Maven to build the project:
  ```sh
  ./mvnw clean package
  ```
- Key dependencies:
  - Spring Boot Web, WebFlux
  - Spring Security, OAuth2 Resource Server
  - Spring Data JPA (MariaDB)
  - Spring Data Redis
  - Liquibase (DB migrations)
  - MinIO (S3-compatible storage)
  - Google Analytics Data
  - Logback encoder for JSON logs

## 2. Security Audits & Code Quality
- **OWASP Dependency-Check**: Configured in `pom.xml` to scan for vulnerable dependencies. The build fails if any dependency has a CVSS score ≥ 7.0. Suppressions and NVD API key are supported.
- **Checkstyle**: Enforces code style and quality using `checkstyle.xml`. The build fails on style violations.

## 3. Docker Deployment
- The generated `.war` file (`target/finance-api.war`) is mounted into a Docker container.
- The backend runs with the production profile:
  ```sh
  java -jar finance-api.war --spring.profiles.active=prod
  ```
- Environment variables and secrets are injected at runtime (see Vault integration).

## 4. Logging
- Logback is configured (see `logback-spring.xml`) to output JSON logs using the `logstash-logback-encoder` dependency.
- These logs are compatible with Loki and other observability stacks for centralized log management.

---
This guide summarizes the build, security, deployment, and logging practices for Treishvaam Finance backend operations.