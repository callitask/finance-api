# ------------------------------------------------------------------------------
# STAGE 1: Build the Application
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# 1. Copy Project Configuration
COPY pom.xml .
# 2. Download Dependencies (Cached if pom.xml is unchanged)
RUN mvn dependency:go-offline

# 3. Copy Source & Build
COPY src ./src
# Skip tests to speed up production deployment
RUN mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# STAGE 2: Run the Application
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# 1. Install Runtime Dependencies (Python for your scripts)
# Note: We do NOT need Infisical inside the container because 
# your auto_deploy.sh injects the secrets before the container starts.
RUN apt-get update && \
    apt-get install -y python3 python3-pip python-is-python3 curl && \
    rm -rf /var/lib/apt/lists/*

# 2. Install Python Packages required by your scripts
RUN pip3 install --no-cache-dir \
    pandas \
    requests \
    yfinance \
    sqlalchemy \
    mysql-connector-python \
    wikipedia-api

# 3. Create required directories
RUN mkdir -p /app/uploads /app/sitemaps /app/logs /app/scripts

# 4. Copy the compiled WAR from the builder stage
# We use a wildcard *.war to automatically find the generated file
COPY --from=builder /build/target/*.war app.war

# 5. Expose Application Port
EXPOSE 8080

# 6. Start the Application
# Variables are already provided by Docker Compose from the Host
ENTRYPOINT ["java", "-jar", "app.war"]