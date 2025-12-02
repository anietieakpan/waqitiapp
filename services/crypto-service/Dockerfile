FROM openjdk:17-jdk-slim

LABEL maintainer="Waqiti Team <dev@example.com>"
LABEL service="crypto-service"
LABEL version="1.0.0"

# Install required packages
RUN apt-get update && apt-get install -y \
    curl \
    netcat-traditional \
    && rm -rf /var/lib/apt/lists/*

# Create app directory and user
RUN groupadd -r waqiti && useradd -r -g waqiti waqiti
RUN mkdir -p /app && chown waqiti:waqiti /app

# Set working directory
WORKDIR /app

# Copy Maven dependencies (for better caching)
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./

# Make mvnw executable
RUN chmod +x ./mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Create final image
FROM openjdk:17-jre-slim

# Install required packages for runtime
RUN apt-get update && apt-get install -y \
    curl \
    netcat-traditional \
    && rm -rf /var/lib/apt/lists/*

# Create app directory and user
RUN groupadd -r waqiti && useradd -r -g waqiti waqiti
RUN mkdir -p /app/logs && chown -R waqiti:waqiti /app

# Set working directory
WORKDIR /app

# Copy built jar from previous stage
COPY --from=0 /app/target/crypto-service-*.jar app.jar

# Create health check script
RUN echo '#!/bin/bash\ncurl -f http://localhost:8087/api/v1/crypto/actuator/health || exit 1' > health-check.sh && \
    chmod +x health-check.sh

# Switch to non-root user
USER waqiti

# Expose port
EXPOSE 8087

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD ./health-check.sh

# JVM options for containerized environment
ENV JVM_OPTS="-XX:+UseContainerSupport \
              -XX:MaxRAMPercentage=75.0 \
              -XX:+UseG1GC \
              -XX:+UseStringDeduplication \
              -XX:+OptimizeStringConcat \
              -XX:+UseCompressedOops \
              -Djava.security.egd=file:/dev/./urandom \
              -Dspring.profiles.active=prod"

# Application entry point
ENTRYPOINT ["sh", "-c", "java $JVM_OPTS -jar app.jar"]