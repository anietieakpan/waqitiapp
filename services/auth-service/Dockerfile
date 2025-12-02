# =============================================================================
# Multi-stage Dockerfile for Auth Service
# Optimized for production with security hardening and minimal image size
# =============================================================================

# Stage 1: Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder

# Set working directory
WORKDIR /build

# Install build dependencies
RUN apk add --no-cache maven

# Copy Maven files for dependency caching
COPY pom.xml .
COPY ../common/pom.xml ../common/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application (skip tests in Docker build - run in CI)
RUN mvn clean package -DskipTests -B && \
    mkdir -p target/dependency && \
    cd target/dependency && \
    jar -xf ../*.jar

# Stage 2: Runtime stage
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
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \

# Run the application using extracted layers
# Spring Boot will automatically use the extracted layers
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
