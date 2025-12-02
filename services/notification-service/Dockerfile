FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /workspace/app

# Copy maven executables and configuration
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY notification-service/pom.xml ./notification-service/
COPY common/pom.xml ./common/

# Make the maven wrapper executable
RUN chmod +x ./mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY common/src ./common/src
COPY notification-service/src ./notification-service/src

# Build the application
RUN ./mvnw clean package -pl notification-service -am -DskipTests

FROM eclipse-temurin:17-jre-alpine

# Add application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Extract JAR layers for optimal Docker caching
# Each layer is cached separately - dependencies change less frequently than application code
RUN mkdir -p /app
WORKDIR /app

COPY --from=build /workspace/app/notification-service/target/*.jar app.jar

# Extract JAR layers using Spring Boot's layertools
RUN java -Djarmode=layertools -jar app.jar extract && \
    rm app.jar

# Set ownership to appuser
RUN chown -R appuser:appgroup /app

USER appuser

# Set health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 CMD wget -q --spider http://localhost:8084/actuator/health || exit 1

# Run the application using extracted layers
# Spring Boot will automatically use the extracted layers
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]