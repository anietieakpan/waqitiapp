# Multi-stage build for family-account-service
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy dependencies
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Production image  
FROM eclipse-temurin:17-jre-alpine

# Add application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Extract JAR layers for optimal Docker caching
# Each layer is cached separately - dependencies change less frequently than application code
RUN mkdir -p /app
WORKDIR /app

COPY --from=build /workspace/app/*/target/*.jar app.jar

# Extract JAR layers using Spring Boot's layertools
RUN java -Djarmode=layertools -jar app.jar extract && \
    rm app.jar

# Set ownership to appuser
RUN chown -R appuser:appgroup /app

USER appuser

# Set health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \

# Run the application using extracted layers
# Spring Boot will automatically use the extracted layers
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
