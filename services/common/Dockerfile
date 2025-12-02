# Build stage for library
FROM maven:3.9.4-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent pom if exists
COPY ../../pom.xml /parent-pom.xml 2>/dev/null || true

# Copy library pom
COPY pom.xml .

# Copy source code
COPY src ./src

# Build and install library
RUN mvn clean install -DskipTests -B

# This is a library, so we only need the build stage
# The JAR will be copied from this stage by other services that depend on it
FROM alpine:latest
RUN apk add --no-cache ca-certificates

# Copy the built library JAR
COPY --from=build /app/target/*.jar /library.jar

# Add metadata
LABEL description="Waqiti Common Library" \
      version="1.0.0" \
      maintainer="Waqiti Team"

# This container is not meant to be run directly
CMD ["echo", "This is a library container - not meant to be run directly. Use as a build dependency."]