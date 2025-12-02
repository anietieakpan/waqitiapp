# Analytics Service Configuration Properties

## Overview

Production-grade, enterprise-scale configuration properties implementation for the Waqiti Analytics Service. This implementation provides comprehensive type safety, validation, documentation, and operational monitoring for all custom configuration properties.

## Architecture

### Core Components

1. **Configuration Properties Classes** (`properties/`)
   - `AnalyticsProperties` - Core analytics configuration (analytics.*)
   - `InfluxDBProperties` - Time-series database configuration (influxdb.*)
   - `SparkProperties` - Distributed computing configuration (spark.*)
   - `HealthProperties` - Resilience patterns configuration (health.*)
   - `FeatureFlagProperties` - Feature toggles configuration (feature.flags.*)

2. **Configuration Enablement** (`ConfigurationPropertiesConfig`)
   - Centralized enablement of all @ConfigurationProperties classes
   - Automatic bean registration and initialization

3. **Validation Infrastructure** (`validator/`)
   - `ConfigurationValidator` - Startup validation with detailed error reporting
   - Cross-property validation logic
   - Fail-fast behavior for critical misconfigurations

4. **Health Monitoring** (`health/`)
   - `ConfigurationHealthIndicator` - Actuator health indicator
   - Real-time configuration health status
   - Exposed via `/actuator/health/configurationHealth`

5. **IDE Integration**
   - `META-INF/additional-spring-configuration-metadata.json`
   - IntelliJ IDEA / VS Code autocomplete support
   - Property hints and documentation

## Features

### ✅ Bean Validation
- `@NotNull`, `@NotBlank` for required properties
- `@Min`, `@Max` for numeric constraints
- `@Pattern` for string format validation
- `@DecimalMin`, `@DecimalMax` for decimal ranges
- `@Valid` for nested object validation

### ✅ Security
- `@JsonIgnore` for sensitive properties (tokens, secrets)
- Masked logging for credentials
- Actuator endpoint exclusion for sensitive data
- WRITE_ONLY access mode for secrets

### ✅ Documentation
- Comprehensive JavaDoc with examples
- `@Schema` annotations for OpenAPI/Swagger
- Configuration metadata for IDE autocomplete
- Property hints for common values

### ✅ Validation
- Automatic startup validation
- Cross-property constraint checking
- Detailed error reporting with property paths
- Warning vs. error distinction

### ✅ Observability
- Health indicator for configuration status
- Validation warnings logged at startup
- Configuration details in health endpoint
- Operational monitoring support

## Configuration Properties

### Analytics Properties (analytics.*)

#### Processing Configuration
```yaml
analytics:
  processing:
    batch-size: 10000              # 100-100,000
    parallel-processing: true
    max-threads: 10                # 1-100
    chunk-size: 1000               # 100-50,000
    retention-days: 2555           # 1-3650 (10 years)
```

#### Real-time Analytics
```yaml
analytics:
  real-time:
    enabled: true
    window-size-minutes: 5         # 1-60
    aggregation-interval-seconds: 30  # 5-300
    alert-thresholds:
      transaction-volume: 1000     # >= 1
      error-rate: 0.05             # 0.0-1.0
      response-time-ms: 5000       # >= 100
```

#### Machine Learning
```yaml
analytics:
  ml:
    enabled: true
    model-training:
      auto-retrain: true
      retrain-interval-hours: 24   # 1-720 (30 days)
      min-data-points: 10000       # >= 1000
    fraud-detection:
      model-path: "/opt/ml/models/fraud-detection"  # absolute path
      threshold: 0.75              # 0.0-1.0
      feature-extraction: true
    recommendation:
      model-path: "/opt/ml/models/recommendation"   # absolute path
      collaborative-filtering: true
      content-based: true
```

#### ETL Configuration
```yaml
analytics:
  etl:
    enabled: true
    schedule: "0 0 2 * * ?"        # valid cron expression
    parallel-jobs: 5               # 1-20
    data-sources:
      user-service: true
      wallet-service: true
      payment-service: true
      security-service: true
      core-banking-service: true
    transformations:
      data-cleansing: true
      data-enrichment: true
      feature-engineering: true
```

#### Reporting Configuration
```yaml
analytics:
  reporting:
    enabled: true
    formats: ["PDF", "EXCEL", "CSV", "JSON"]  # at least one required
    scheduled-reports:
      daily-summary: "0 0 8 * * ?"
      weekly-report: "0 0 8 ? * MON"
      monthly-report: "0 0 8 1 * ?"
    distribution:
      email: true
      dashboard: true
      api: true
```

#### Dashboard Configuration
```yaml
analytics:
  dashboard:
    refresh-interval-seconds: 30   # 5-300
    cache-duration-minutes: 5      # 1-60
    real-time-updates: true
    max-data-points: 1000          # 100-10,000
    widgets:
      transaction-volume: true
      revenue-metrics: true
      user-growth: true
      fraud-alerts: true
      system-health: true
```

#### Data Retention
```yaml
analytics:
  retention:
    raw-data-days: 90              # 1-3650
    aggregated-data-days: 2555     # 1-3650
    ml-training-data-days: 365     # 1-3650
    audit-logs-days: 2555          # 1-3650
    cleanup-schedule: "0 0 3 * * ?"  # valid cron
```

#### Performance Optimization
```yaml
analytics:
  performance:
    query-timeout-seconds: 300     # 30-3600
    connection-pool-size: 20       # 5-100
    cache-size-mb: 512             # 64-4096
    index-optimization: true
    query-optimization: true
```

#### Data Quality
```yaml
analytics:
  quality:
    validation-enabled: true
    anomaly-detection: true
    data-profiling: true
    completeness-threshold: 0.95   # 0.0-1.0
    accuracy-threshold: 0.98       # 0.0-1.0
```

### InfluxDB Properties (influxdb.*)

```yaml
influxdb:
  url: http://localhost:8086       # http:// or https://
  token: ${INFLUXDB_TOKEN}         # sensitive, masked in logs
  org: waqiti                      # alphanumeric, -, _
  bucket: analytics                # alphanumeric, -, _
```

### Spark Properties (spark.*)

```yaml
spark:
  app-name: "Waqiti Analytics Service"
  master: local[*]                 # local[*], spark://, yarn, mesos://, k8s://
  sql:
    adaptive:
      enabled: true
      coalescePartitions:
        enabled: true
  executor:
    memory: "2g"                   # format: number + k/m/g/t
    cores: 2                       # >= 1
  driver:
    memory: "1g"                   # format: number + k/m/g/t
```

### Health Properties (health.*)

```yaml
health:
  circuit-breaker:
    enabled: true
  rate-limiter:
    enabled: true
```

### Feature Flags (feature.flags.*)

```yaml
feature:
  flags:
    dual-auth-mode:
      enabled: false
    keycloak-only:
      enabled: true
    legacy-jwt-deprecation-warning:
      enabled: true
```

## Validation

### Startup Validation

The `ConfigurationValidator` automatically validates all configuration properties on application startup:

```
========================================
Starting Configuration Validation
========================================
Analytics Configuration: ✓ Valid
InfluxDB Configuration: ✓ Valid
Spark Configuration: ✓ Valid
========================================
Configuration Validation PASSED
All configuration properties are valid
========================================
```

### Error Reporting

Invalid configurations produce detailed error messages:

```
----------------------------------------
Analytics Configuration Errors:
----------------------------------------
  Property: processing.maxThreads
  Error: Max threads must be at least 1
  Invalid Value: 0

  Property: ml.fraudDetection.threshold
  Error: Threshold must be greater than 0
  Invalid Value: -0.5
```

### Cross-Property Validation

Validates relationships between properties:

- Spark executor memory >= driver memory
- Raw data retention <= aggregated data retention
- InfluxDB configured if real-time analytics enabled

## Health Monitoring

### Actuator Endpoint

Access configuration health via:
```
GET /actuator/health/configurationHealth
```

Response:
```json
{
  "status": "UP",
  "details": {
    "influxdb.configured": true,
    "influxdb.url": "http://localhost:8086",
    "influxdb.token": "abcd...xyz",
    "spark.memory.valid": true,
    "analytics.realtime.enabled": true,
    "analytics.ml.enabled": true,
    "status": "All configuration properties are healthy"
  }
}
```

## IDE Integration

### IntelliJ IDEA / VS Code

The configuration metadata enables:
- ✅ Autocomplete for all custom properties
- ✅ Property hints and allowed values
- ✅ Property documentation on hover
- ✅ Validation warnings for invalid values
- ✅ Quick navigation to property definitions

### Example Autocomplete

Type `analytics.` in `application.yml`:
- `analytics.processing.batch-size` - Batch size for processing operations (100-100,000)
- `analytics.ml.enabled` - Enable machine learning features
- `analytics.retention.raw-data-days` - Raw data retention period (1-3650 days)

## Testing

### Programmatic Validation

```java
@Autowired
private ConfigurationValidator validator;

@Test
void testAnalyticsProperties() {
    var config = new AnalyticsProperties();
    config.getProcessing().setMaxThreads(0);  // Invalid

    Set<ConstraintViolation<AnalyticsProperties>> violations =
        validator.validate(config);

    assertFalse(violations.isEmpty());
}
```

### Integration Tests

```java
@SpringBootTest
@TestPropertySource(properties = {
    "analytics.processing.max-threads=10",
    "influxdb.url=http://test:8086"
})
class ConfigurationPropertiesTest {
    @Autowired
    private AnalyticsProperties properties;

    @Test
    void testPropertiesBinding() {
        assertEquals(10, properties.getProcessing().getMaxThreads());
    }
}
```

## Best Practices

### 1. Environment-Specific Configuration

Use Spring profiles for environment-specific overrides:

```yaml
# application-production.yml
analytics:
  processing:
    max-threads: 50
  retention:
    raw-data-days: 30
```

### 2. Sensitive Properties

Always use environment variables for sensitive data:

```yaml
influxdb:
  token: ${INFLUXDB_TOKEN}

keycloak:
  credentials:
    secret: ${KEYCLOAK_CLIENT_SECRET}
```

### 3. Validation Strategy

- Use `@NotNull` for required properties
- Use `@Min/@Max` for numeric bounds
- Use `@Pattern` for format validation
- Implement cross-property validation in `@PostConstruct`

### 4. Documentation

- Add detailed JavaDoc to all configuration classes
- Use `@Schema` for API documentation
- Include examples in property descriptions
- Maintain configuration metadata for IDE support

## Migration Guide

### From application.yml to Properties Class

**Before:**
```java
@Value("${analytics.processing.batch-size}")
private int batchSize;
```

**After:**
```java
@Autowired
private AnalyticsProperties properties;

int batchSize = properties.getProcessing().getBatchSize();
```

### Benefits of Migration

- ✅ Type safety at compile time
- ✅ Automatic validation
- ✅ IDE autocomplete
- ✅ Centralized configuration
- ✅ Easy testing with mocks

## Troubleshooting

### Issue: Configuration validation fails at startup

**Solution:** Check application logs for detailed error messages with property paths and invalid values.

### Issue: IDE doesn't show autocomplete

**Solution:**
1. Ensure `spring-boot-configuration-processor` is in dependencies
2. Rebuild project to generate metadata
3. Invalidate IDE caches and restart

### Issue: Health indicator shows DOWN

**Solution:** Check `/actuator/health/configurationHealth` for specific error details.

## Contributing

When adding new configuration properties:

1. Add property to appropriate `*Properties` class
2. Add validation annotations
3. Add JavaDoc and `@Schema` documentation
4. Update `additional-spring-configuration-metadata.json`
5. Add validation logic if cross-property constraints exist
6. Update this README

## See Also

- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html)
- [Bean Validation](https://beanvalidation.org/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
