# /**
#  * AI-CONTEXT:
#  *
#  * Purpose:
#  * - Multi-stage Docker build for the Treishvaam Finance Backend API.
#  *
#  * Scope:
#  * - Stage 1: Builds the WAR file natively using Maven/Java 21 to bypass GHCR network denials.
#  * - Stage 2: Executes the application using a minimal, secure JRE environment.
#  *
#  * Critical Dependencies:
#  * - Backend: `pom.xml`, `src/`, `checkstyle.xml`, `dependency-check-suppressions.xml`, `aegis/`.
#  *
#  * Security Constraints:
#  * - The final container must run as the non-root `spring` user.
#  * - Secrets must be injected at runtime, NEVER baked into the image.
#  *
#  * Non-Negotiables:
#  * - Do not revert to root execution.
#  * - Maintain the multi-stage build to keep the final image size minimal and secure.
#  *
#  * Change Intent:
#  * - Resolving the Maven gRPC pipeline failure. The builder stage lacked the `aegis` directory, 
#  * blinding the protobuf compiler to the `.proto` files required to generate stub classes.
#  *
#  * Future AI Guidance:
#  * - If new root-level configuration files or external proto definitions are added, 
#  * they MUST be explicitly copied into the `builder` stage.
#  *
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - EDITED:
#  * • Added `COPY checkstyle.xml .` and `COPY dependency-check-suppressions.xml .` to the builder stage.
#  * • Why: The `maven-checkstyle-plugin` and `dependency-check-maven` plugins require these files to run natively inside the isolated Docker build context.
#  *
#  * - EDITED:
#  * • Added `COPY aegis ./aegis` to the builder stage.
#  * • Why: Fixed `com.treishvaam.financeapi.security.aegis.zkp does not exist` build error. Protobuf plugin needs the `.proto` files to synthesize the Java stubs during `mvn compile`.
#  *
#  * - EDITED:
#  * • Enforced explicit `--uid 1000` and `--gid 1000` during the `spring` system user creation.
#  * • Why: To resolve `java.io.FileNotFoundException (Permission denied)` on mapped volumes. By locking the internal user to a deterministic UID, the infrastructure can enforce strict `chown` ownership on host volumes, replacing the insecure `chmod 777` workaround.
#  *
#  * - EDITED (Anti-Stale Cache Injection):
#  * • Injected `ARG CACHE_BUST` before the `spring` user creation.
#  * • Why: To forcefully invalidate the server-side Docker daemon build cache, ensuring the Git Runner compiles the new UID rules rather than recycling the broken image layer.
#  *
#  * - EDITED (Enterprise BuildKit Caching):
#  * • Added `--mount=type=cache,target=/root/.m2` to `mvn dependency:go-offline` and `mvn clean package`.
#  * • Why: Prevents redundant Maven dependency downloads across CI/CD deployments. By persisting the local repository outside the image layer filesystem, deployments become near-instantaneous despite aggressive Docker cleanup policies.
#  *
#  * - DO-NOT-DELETE RULE (ABSOLUTE):
#  * This IMMUTABLE CHANGE HISTORY section acts as the institutional memory for future AI sessions. 
#  * It must never be deleted, truncated, rewritten, or regenerated. Future AI must append only. 
#  */

# ------------------------------------------------------------------------------
# STAGE 1: Build the Application
# ------------------------------------------------------------------------------
# We use a Docker image that HAS Maven and Java 21 to build the app.
# This solves the "Permission Denied" and "Java Version" errors on your Host.
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# 1. Copy configuration
COPY pom.xml .
# 2. Download dependencies (Cached if pom.xml doesn't change via BuildKit)
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline

# 3. Copy validation configurations and source code, then build
COPY checkstyle.xml .
COPY dependency-check-suppressions.xml .
COPY aegis ./aegis
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# STAGE 2: Run the Application
# ------------------------------------------------------------------------------
# We use a lightweight Java 21 image for running the app.
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# 1. Install Python 3 and Dependencies
# We removed 'infisical' to prevent authentication errors inside the container.
RUN apt-get update && \
    apt-get install -y python3 python3-pip python-is-python3 && \
    rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir \
    pandas \
    requests \
    yfinance \
    sqlalchemy \
    mysql-connector-python \
    wikipedia-api

# 2. Create necessary directories and Non-Root User (Fort Knox)
# We create a system user 'spring' to run the app safely with a deterministic UID/GID.
# Injected CACHE_BUST to prevent the Git Runner from using stale layers without the UID rule.
ARG CACHE_BUST=1
RUN addgroup --system --gid 1000 spring && adduser --system --uid 1000 --ingroup spring spring

RUN mkdir -p /app/uploads /app/sitemaps /app/logs /app/scripts && \
    chown -R spring:spring /app

# 3. Copy the compiled WAR file from the 'builder' stage with permissions
COPY --chown=spring:spring --from=builder /build/target/finance-api.war app.war

# 4. Expose Port
EXPOSE 8080

# 5. Switch to Non-Root User
USER spring:spring

# 6. Start the App
# Secrets are injected via environment variables from docker-compose/.env
ENTRYPOINT ["java", "-jar", "app.war"]