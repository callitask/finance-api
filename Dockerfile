# TARGET: Backend Docker Image
# PURPOSE: Packages the Spring Boot WAR file into a runnable Java container
# GOAL: Run on any server with Java 17

# 1. Use a lightweight Java 17 Runtime (Alpine Linux)
FROM eclipse-temurin:17-jre-alpine

# 2. Set working directory
WORKDIR /app

# 3. Copy the compiled WAR file from the Maven build target folder
# Note: The CI pipeline runs 'mvn package' before this, creating the file in /target
COPY target/*.war app.war

# 4. Expose the application port
EXPOSE 8080

# 5. Run the application
ENTRYPOINT ["java", "-jar", "app.war"]
