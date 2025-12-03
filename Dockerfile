# TARGET: Backend Docker Image
# PURPOSE: Java 17 Runtime + Python 3 Environment for Market Data Scripts
# GOAL: Single container that runs Spring Boot AND the Python Data Pipeline

# 1. Use a lightweight Java 17 Runtime (Alpine Linux)
FROM eclipse-temurin:17-jre-alpine

# 2. Install Python 3, Pip, and build dependencies
# We install py3-pandas/numpy via APK because building them from source takes too long
RUN apk add --no-cache python3 py3-pip py3-pandas py3-numpy py3-mysqlclient

# 3. Install Python Libraries (YFinance, SQLAlchemy, etc.)
# --break-system-packages is required on newer Alpine versions
RUN pip3 install --no-cache-dir --break-system-packages \
    yfinance \
    sqlalchemy \
    mysql-connector-python \
    requests

# 4. Set working directory
WORKDIR /app

# 5. Copy the compiled WAR file
COPY target/*.war app.war

# 6. Expose the application port
EXPOSE 8080

# 7. Run the application
ENTRYPOINT ["java", "-jar", "app.war"]