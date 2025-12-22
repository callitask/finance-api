# ------------------------------------------------------------------------------
# STAGE 1: Build the Application
# ------------------------------------------------------------------------------
# We use a Docker image that HAS Maven and Java 21 to build the app.
# This solves the "Permission Denied" and "Java Version" errors on your Host.
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# 1. Copy configuration
COPY pom.xml .
# 2. Download dependencies (Cached if pom.xml doesn't change)
RUN mvn dependency:go-offline

# 3. Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# STAGE 2: Run the Application
# ------------------------------------------------------------------------------
# We use a lightweight Java 21 image for running the app.
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# 1. Install Python 3, Dependencies, AND Infisical CLI
# We combine apt-get commands to keep the image layer small.
RUN apt-get update && \
    apt-get install -y python3 python3-pip python-is-python3 curl && \
    curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh' | bash && \
    apt-get install -y infisical && \
    rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir \
    pandas \
    requests \
    yfinance \
    sqlalchemy \
    mysql-connector-python \
    wikipedia-api

# 2. Create necessary directories
RUN mkdir -p /app/uploads /app/sitemaps /app/logs /app/scripts

# 3. Copy the compiled WAR file from the 'builder' stage
COPY --from=builder /build/target/finance-api.war app.war

# 4. Expose Port
EXPOSE 8080

# 5. Start the App via Infisical
# "infisical run --" fetches secrets using the ENV vars provided in docker-compose,
# injects them into the process, and then starts the Java app.
ENTRYPOINT ["infisical", "run", "--", "java", "-jar", "app.war"]