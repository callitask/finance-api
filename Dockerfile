# TARGET: Backend Docker Image
# PURPOSE: Java 21 Runtime + Python 3 Environment for Market Data Scripts
# GOAL: Single container that runs Spring Boot AND the Python Data Pipeline

# 1. Use Java 21 Base Image (Matching your compilation target)
FROM eclipse-temurin:21-jdk-jammy

# 2. Install Python 3, Pip, and build dependencies
# We use Debian-based 'jammy' so 'apt-get' is the correct package manager
RUN apt-get update && \
    apt-get install -y python3 python3-pip python-is-python3 && \
    rm -rf /var/lib/apt/lists/*

# 3. Install Python Libraries (YFinance, SQLAlchemy, etc.)
# FIX: Removed --break-system-packages as it is not supported/needed in this base image
RUN pip3 install --no-cache-dir \
    pandas \
    requests \
    yfinance \
    sqlalchemy \
    mysql-connector-python \
    wikipedia-api

# 4. Set working directory
WORKDIR /app

# 5. Create necessary directories for file storage and logs
RUN mkdir -p /app/uploads /app/sitemaps /app/logs /app/scripts

# 6. Copy the compiled WAR file
# Ensure the source matches what Maven generates (check target/ folder name)
COPY target/finance-api.war app.war

# 7. Expose the application port
EXPOSE 8080

# 8. Run the application
ENTRYPOINT ["java", "-jar", "app.war"]