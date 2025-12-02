# Waqiti Shared Configuration Guide

This guide explains how to use the shared configuration template to ensure consistency across all Waqiti microservices.

## Overview

The shared configuration provides:
- Standardized database connection pooling
- Redis caching configuration  
- Kafka messaging setup
- Resilience patterns (Circuit Breaker, Retry, Rate Limiting)
- Security settings
- Monitoring and metrics
- HTTP client configuration
- Async processing setup

## Usage Instructions

### 1. Add Dependency

Add the payment-commons dependency to your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.waqiti</groupId>
    <artifactId>payment-commons</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Import Configuration

In your main Spring Boot application class, import the shared configuration:

```java
@SpringBootApplication
@Import(WaqitiServiceConfiguration.class)
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

### 3. Copy Configuration Template

Copy the `application-waqiti-template.yml` to your service's `src/main/resources/application.yml` and customize:

```yaml
# Copy content from application-waqiti-template.yml
# Then override service-specific settings:

spring:
  application:
    name: your-service-name

server:
  port: 8085

# Service-specific configurations
your-service:
  specific:
    setting: value
```

### 4. Environment Configuration

Set up environment variables for different deployments:

#### Development
```bash
export SERVICE_NAME=your-service
export DATABASE_URL=jdbc:postgresql://localhost:5432/waqiti_dev
export REDIS_HOST=localhost
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

#### Production
```bash
export SERVICE_NAME=your-service
export DATABASE_URL=jdbc:postgresql://prod-db:5432/waqiti_prod
export REDIS_HOST=prod-redis
export KAFKA_BOOTSTRAP_SERVERS=prod-kafka1:9092,prod-kafka2:9092
```

## Configuration Components

### Database Configuration
```yaml
waqiti:
  datasource:
    hikari:
      maximum-pool-size: 20      # Max connections
      minimum-idle: 5            # Min idle connections
      connection-timeout: 30000  # Connection timeout (ms)
      idle-timeout: 600000       # Idle timeout (ms)
      max-lifetime: 1800000      # Max connection lifetime (ms)
```

### Redis Configuration
```yaml
waqiti:
  redis:
    host: localhost
    port: 6379
    timeout: 5s
    max-active: 20
    max-idle: 10
    min-idle: 2
```

### Kafka Configuration
```yaml
waqiti:
  kafka:
    bootstrap-servers: localhost:9092
    acks: all
    retries: 3
    group-id: waqiti-fintech
```

### Resilience Configuration
```yaml
waqiti:
  resilience:
    circuit-breaker:
      failure-rate-threshold: 50.0
      wait-duration-in-open-state: 30s
    retry:
      max-attempts: 3
      wait-duration: 500ms
```

## Service Client Configuration

Use the standardized Feign clients from payment-commons:

```java
@RestController
public class YourController {
    
    @Autowired
    private UnifiedWalletServiceClient walletClient;
    
    @Autowired  
    private UserServiceClient userClient;
    
    // Use the clients with built-in resilience patterns
}
```

## Security Configuration

The template includes standardized Keycloak OAuth2 configuration:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com:8180/realms/waqiti-fintech
```

## Monitoring Configuration

Actuator endpoints are pre-configured:
- `/actuator/health` - Health checks
- `/actuator/metrics` - Metrics
- `/actuator/prometheus` - Prometheus metrics

## Database Migration

Use Flyway for database migrations. Place your migration files in:
```
src/main/resources/db/migration/
```

Example migration file naming:
- `V1__Create_payment_tables.sql`
- `V2__Add_payment_status_column.sql`

## Best Practices

### 1. Environment-specific Configuration
- Use environment variables for all environment-specific values
- Never hardcode credentials or URLs
- Use different profiles for dev/staging/prod

### 2. Service Discovery
- Register with Eureka for service discovery
- Use service names instead of hardcoded URLs
- Configure health checks properly

### 3. Logging
- Use structured logging with correlation IDs
- Set appropriate log levels for different environments
- Include service name and version in logs

### 4. Error Handling
- Use the provided exception handling patterns
- Return consistent error responses
- Log errors with sufficient context

### 5. Testing
- Test with the same configuration used in production
- Use testcontainers for integration tests
- Mock external service calls appropriately

## Troubleshooting

### Common Issues

#### Database Connection Issues
```yaml
# Check these settings:
spring:
  datasource:
    url: # Verify database URL
    username: # Verify credentials
    password: # Verify credentials
```

#### Redis Connection Issues
```yaml
# Check Redis connectivity:
waqiti:
  redis:
    host: # Verify Redis host
    port: # Verify Redis port
```

#### Service Discovery Issues
```yaml
# Check Eureka configuration:
eureka:
  client:
    service-url:
      defaultZone: # Verify Eureka server URL
```

### Health Checks

Monitor service health via:
- `GET /actuator/health` - Overall health
- `GET /actuator/health/db` - Database health  
- `GET /actuator/health/redis` - Redis health
- `GET /actuator/health/diskSpace` - Disk space

### Performance Monitoring

Monitor performance via:
- `GET /actuator/metrics` - All metrics
- `GET /actuator/prometheus` - Prometheus format
- Check circuit breaker states
- Monitor database connection pool usage

## Support

For questions about the shared configuration:
1. Check this documentation first
2. Review the `WaqitiServiceConfiguration.java` source code
3. Contact the platform team
4. Update this documentation with any new patterns

## Version History

- v1.0.0 - Initial shared configuration template
  - Database connection pooling
  - Redis caching
  - Kafka messaging
  - Resilience patterns
  - Security configuration
  - Monitoring setup