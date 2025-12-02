##############################################################################
# Multi-Stage Dockerfile for Waqiti Microservice
#
# Stage 1: Build - Compile and package Java application
# Stage 2: Runtime - Minimal JRE image for production deployment
#
# Security Features:
# - Non-root user (appuser)
# - Minimal base image (Alpine)
# - Health check endpoint
# - Security scanning compatible
#
# Build: docker build -t waqiti/SERVICE_NAME:VERSION .
# Run:   docker run -p 8080:8080 waqiti/SERVICE_NAME:VERSION
##############################################################################

# =============================================================================
# Stage 1: Build Stage
# =============================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

LABEL maintainer="Waqiti Platform Engineering <engineering@example.com>"
LABEL stage="build"

WORKDIR /app

# Copy parent POM first for better caching
COPY ../../pom.xml /app/parent-pom.xml 2>/dev/null || true

# Copy common modules if they exist
COPY ../../services/common /app/services/common 2>/dev/null || true
COPY ../../services/payment-commons /app/services/payment-commons 2>/dev/null || true

# Copy service POM
COPY pom.xml .

# Download dependencies (cached layer if pom.xml unchanged)
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY src ./src

# Build application
# Skip tests in Docker build (run separately in CI/CD)
RUN mvn clean package -DskipTests -B

# Verify JAR was created
RUN ls -lh target/*.jar

# =============================================================================
# Stage 2: Runtime Stage
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Waqiti Platform Engineering <engineering@example.com>"
LABEL stage="runtime"
LABEL description="Waqiti Microservice - Production Runtime"

# Install required packages
RUN apk add --no-cache \
    curl \
    tzdata \
    && rm -rf /var/cache/apk/*

# Set timezone
ENV TZ=UTC

# Create app directory
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

# Copy JAR from build stage
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

# JVM Configuration
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Application Configuration
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=production

# Expose application port
EXPOSE 8080

# Health check
# Checks /actuator/health endpoint every 30 seconds
# Waits 60 seconds before first check (startup time)
# Fails after 3 consecutive failures
HEALTHCHECK --interval=30s \
            --timeout=3s \
            --start-period=60s \
            --retries=3 \
            CMD curl -f http://localhost:8080/actuator/health || exit 1

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Metadata
ARG BUILD_DATE
ARG VERSION
ARG VCS_REF

LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${VCS_REF}"
LABEL org.opencontainers.image.source="https://github.com/waqiti/waqiti-app"
LABEL org.opencontainers.image.vendor="Waqiti Financial Services"
