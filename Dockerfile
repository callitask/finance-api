# /**
#  * AI-CONTEXT:
#  *
#  * Purpose:
#  * - Enterprise Runtime Dockerfile for the Treishvaam Finance Backend API.
#  *
#  * Scope:
#  * - Executes the pre-compiled WAR application using a minimal, secure JRE environment.
#  *
#  * Critical Dependencies:
#  * - Backend: `backend-app.war` (Provided by Engine A GitHub Actions Builder)
#  *
#  * Security Constraints:
#  * - The final container must run as the non-root `spring` user.
#  * - Secrets must be injected at runtime, NEVER baked into the image.
#  *
#  * Non-Negotiables:
#  * - Do not revert to root execution.
#  * - Do not re-introduce a multi-stage Maven builder. The artifact is built natively on the host.
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
#  * - EDITED (OOM Killer Mitigation):
#  * • Injected `ENV MAVEN_OPTS="-Xmx512m -XX:MaxMetaspaceSize=128m"` into the builder stage.
#  * • Why: Restricts the active memory allocation during Maven's compilation and protobuf stub generation, preventing the Git Runner from exceeding the VirtualBox 4.8GB threshold and getting assassinated by the Linux OOM Killer.
#  *
#  * - EDITED (Build Heap Space Fix):
#  * • Increased `MAVEN_OPTS` to `-Xmx1024m -XX:MaxMetaspaceSize=256m`.
#  * • Reason: The `maven-war-plugin` was failing with `Java heap space` during artifact assembly. The 512m limit caused the JVM to thrash, which dropped the Git Runner connection and triggered a catastrophic `.env` wipe. 1024m provides the necessary headroom for compilation while remaining safe for the host.
#  *
#  * - EDITED (Single-Stage Artifact Architecture Pivot):
#  * • Removed the multi-stage Maven builder entirely. 
#  * • Reason: Engine A (`deploy.yml`) already natively builds the artifact to `/opt/treishvaam/backend-app.war`. Executing a secondary Maven compilation inside Docker caused catastrophic Java Heap OOMs, crashing the Git Runner and wiping the `.env` vault. The Dockerfile now strictly consumes the pre-built WAR artifact, eliminating the double-build starvation.
#  *
#  * - DO-NOT-DELETE RULE (ABSOLUTE):
#  * This IMMUTABLE CHANGE HISTORY section acts as the institutional memory for future AI sessions.
#  * It must never be deleted, truncated, rewritten, or regenerated. Future AI must append only.
#  */

# ------------------------------------------------------------------------------
# RUNTIME ENVIRONMENT (Single Stage)
# ------------------------------------------------------------------------------
# We use a lightweight Java 21 image for running the app.
# The WAR file is provided natively by Engine A (deploy.yml).
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

# 3. Copy the compiled WAR file explicitly built by Engine A on the host
COPY --chown=spring:spring backend-app.war app.war

# 4. Expose Port
EXPOSE 8080

# 5. Switch to Non-Root User
USER spring:spring

# 6. Start the App
# Secrets are injected via environment variables from docker-compose/.env
ENTRYPOINT ["java", "-jar", "app.war"]