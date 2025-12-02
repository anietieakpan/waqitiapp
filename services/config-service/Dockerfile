FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /workspace/app

# Copy maven executables and configuration
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY services/config-service/pom.xml ./config-service/
COPY services/common/pom.xml ./common/

# Make the maven wrapper executable
RUN chmod +x ./mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY services/common/src ./common/src
COPY services/config-service/src ./config-service/src

# Build the application
RUN ./mvnw clean package -pl config-service -am -DskipTests

FROM eclipse-temurin:17-jre-alpine

# Add application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Extract JAR layers for optimal Docker caching
# Each layer is cached separately - dependencies change less frequently than application code
RUN mkdir -p /app
WORKDIR /app

COPY --from=build /workspace/app/config-service/target/*.jar app.jar

# Extract JAR layers using Spring Boot's layertools
RUN java -Djarmode=layertools -jar app.jar extract && \
    rm app.jar

# Set ownership to appuser
RUN chown -R appuser:appgroup /app

USER appuser

# Set health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 CMD curl -f http://localhost:8888/actuator/health || exit 1

# Run the application using extracted layers
# Spring Boot will automatically use the extracted layers
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]