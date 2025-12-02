# =============================================================================
# Production Multi-stage Dockerfile for Payment Service
# Optimized for security, performance, and minimal image size
# Base: Eclipse Temurin 21 (LTS) → Distroless for production
# =============================================================================

# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-jammy AS builder

# Install build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    maven=3.8.7-1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copy parent POM and settings
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./

# Copy all module POMs for dependency resolution
COPY services/payment-service/pom.xml services/payment-service/
COPY services/common/pom.xml services/common/
COPY services/payment-commons/pom.xml services/payment-commons/

# Download dependencies (cached layer - only rebuilds when POMs change)
RUN ./mvnw dependency:go-offline -B -pl services/payment-service

# Copy source code
COPY services/payment-service/src services/payment-service/src
COPY services/common/src services/common/src
COPY services/payment-commons/src services/payment-commons/src

# Build application with all tests
RUN ./mvnw clean package -B -pl services/payment-service -am \
    && mkdir -p target \
    && cp services/payment-service/target/payment-service-*.jar target/app.jar

# Extract JAR layers for better Docker caching
RUN java -Djarmode=layertools -jar target/app.jar extract --destination target/extracted

# Stage 2: Production runtime (Distroless - minimal attack surface)
FROM gcr.io/distroless/java21-debian12:nonroot

# Metadata labels
LABEL maintainer="DevOps Team <devops@example.com>" \
      application="waqiti-payment-service" \
      version="1.0.0" \
      description="Waqiti Payment Processing Service - PCI DSS Compliant" \
      org.opencontainers.image.source="https://github.com/waqiti/waqiti-app" \
      org.opencontainers.image.vendor="Waqiti Inc"

WORKDIR /app

# Copy extracted layers from builder (order optimized for caching)
COPY --from=builder --chown=nonroot:nonroot /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=nonroot:nonroot /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=nonroot:nonroot /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=nonroot:nonroot /build/target/extracted/application/ ./

# Expose ports
EXPOSE 8080 8081

# Environment variables (overridable at runtime)
ENV SPRING_PROFILES_ACTIVE="production" \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

# Run as non-root user (distroless provides nonroot user UID 65532)
USER nonroot:nonroot

# Entry point with optimized JVM flags
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+UseG1GC", \
            "-XX:+UseStringDeduplication", \
            "-XX:+OptimizeStringConcat", \
            "-XX:+UseCompressedOops", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", \
            "org.springframework.boot.loader.launch.JarLauncher"]