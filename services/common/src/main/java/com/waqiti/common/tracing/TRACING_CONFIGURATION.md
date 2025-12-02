# Waqiti Distributed Tracing Configuration Guide

## Overview

This document provides comprehensive configuration and usage instructions for the Waqiti distributed tracing implementation using OpenTelemetry.

## Table of Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Configuration](#configuration)
4. [Usage Examples](#usage-examples)
5. [Integration Guide](#integration-guide)
6. [Performance Tuning](#performance-tuning)
7. [Troubleshooting](#troubleshooting)

---

## Features

### Core Capabilities

- **OpenTelemetry Integration**: Full W3C Trace Context compliance
- **Multiple Exporters**: Support for Zipkin, Jaeger, and OTLP
- **Custom Trace ID Generation**: Waqiti correlation IDs with timestamp and sequence info
- **Automatic Instrumentation**:
  - HTTP requests/responses
  - Database queries
  - Kafka messages
  - External API calls
  - Method-level tracing with `@Traced` annotation
- **Baggage Propagation**: Cross-cutting concerns across service boundaries
- **Performance Metrics**: Per-trace performance tracking
- **Error Tracking**: Automatic exception capture in spans
- **MDC Integration**: Logging correlation with trace/span IDs
- **Service Mesh Ready**: Compatible with Istio, Linkerd, etc.
- **Advanced Sampling**: Error-aware sampling strategies

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────┐
│              Waqiti Tracing Architecture                │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐    ┌──────────────┐                  │
│  │ TracingFilter│───▶│TraceIdGenerator│                 │
│  └──────────────┘    └──────────────┘                  │
│         │                    │                          │
│         ▼                    ▼                          │
│  ┌─────────────────────────────────────┐               │
│  │   DistributedTracingConfig          │               │
│  │  - OpenTelemetry SDK                │               │
│  │  - Multiple Exporters               │               │
│  │  - Context Propagators              │               │
│  │  - Sampling Strategies              │               │
│  └─────────────────────────────────────┘               │
│         │                                               │
│    ┌────┴────┬────────────┬──────────┐                │
│    ▼         ▼            ▼          ▼                 │
│  ┌────┐  ┌────┐      ┌────┐      ┌────┐              │
│  │Zipkin│ │Jaeger│   │OTLP│     │Logs│              │
│  └────┘  └────┘      └────┘      └────┘              │
└─────────────────────────────────────────────────────────┘
```

### Key Classes

1. **DistributedTracingConfig**: Main configuration class
2. **TraceIdGenerator**: Custom trace ID generation
3. **TracingFilter**: HTTP request/response tracing
4. **@Traced**: Method-level tracing annotation

---

## Configuration

### application.yml Configuration

```yaml
# ========================================
# OpenTelemetry Distributed Tracing
# ========================================

spring:
  application:
    name: waqiti-payment-service

service:
  version: 2.0.0
  namespace: waqiti

deployment:
  environment: production

# Tracing Configuration
tracing:
  enabled: true

  # Zipkin Exporter Configuration
  zipkin:
    enabled: true
    endpoint: http://zipkin:9411/api/v2/spans

  # Jaeger Exporter Configuration
  jaeger:
    enabled: true
    endpoint: http://jaeger:14250

  # OTLP Exporter Configuration (for OpenTelemetry Collector)
  otlp:
    enabled: true
    endpoint: http://otel-collector:4317

  # Sampling Configuration
  sampling:
    # Default sampling rate for successful requests (0.0 to 1.0)
    default-rate: 0.1  # 10% of successful requests

    # Sampling rate for errors (typically 1.0 to capture all errors)
    error-rate: 1.0  # 100% of errors

    # Sampling rate for critical operations
    critical-operations-rate: 1.0  # 100% of critical ops

  # Export Configuration
  export:
    timeout: 30  # seconds
    batch:
      max-queue-size: 2048
      max-export-batch-size: 512
      schedule-delay: 5  # seconds
      timeout: 30  # seconds

  # Feature Flags
  baggage:
    enabled: true  # Enable baggage propagation

  mdc:
    enabled: true  # Enable MDC logging integration

  metrics:
    enabled: true  # Enable metrics collection

  database:
    enabled: true  # Enable database query tracing

  kafka:
    enabled: true  # Enable Kafka message tracing

  http-client:
    enabled: true  # Enable HTTP client tracing

  # Service Mesh Integration
  service-mesh:
    enabled: false  # Enable when using Istio/Linkerd
    headers: "x-b3-traceid,x-b3-spanid,x-b3-sampled"

# Logging Configuration (with trace correlation)
logging:
  level:
    com.waqiti: INFO
    io.opentelemetry: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [traceId=%X{traceId:-}, spanId=%X{spanId:-}, correlationId=%X{correlationId:-}] - %msg%n"
```

### Environment-Specific Configurations

#### Development

```yaml
tracing:
  enabled: true
  zipkin:
    enabled: true
    endpoint: http://localhost:9411/api/v2/spans
  jaeger:
    enabled: false
  otlp:
    enabled: false
  sampling:
    default-rate: 1.0  # Sample all requests in dev
    error-rate: 1.0
```

#### Staging

```yaml
tracing:
  enabled: true
  zipkin:
    enabled: true
  jaeger:
    enabled: true
  sampling:
    default-rate: 0.5  # 50% sampling in staging
    error-rate: 1.0
```

#### Production

```yaml
tracing:
  enabled: true
  zipkin:
    enabled: false  # Disable if not needed
  jaeger:
    enabled: true
    endpoint: http://jaeger-collector.observability.svc.cluster.local:14250
  otlp:
    enabled: true
    endpoint: http://otel-collector.observability.svc.cluster.local:4317
  sampling:
    default-rate: 0.1  # 10% sampling to reduce overhead
    error-rate: 1.0    # Always sample errors
    critical-operations-rate: 1.0  # Always sample critical operations
```

---

## Usage Examples

### 1. Method-Level Tracing with @Traced

```java
import com.example.common.tracing.Traced;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    /**
     * Basic tracing
     */
    @Traced
    public Payment processPayment(PaymentRequest request) {
        // Method automatically traced
        return paymentRepository.save(payment);
    }

    /**
     * Tracing with custom operation name
     */
    @Traced(operationName = "payment.process.card")
    public Payment processCardPayment(CardPaymentRequest request) {
        // Traced with custom name
        return processPayment(request);
    }

    /**
     * Tracing with business operation and priority
     */
    @Traced(
        operationName = "payment.refund.high-value",
        businessOperation = "REFUND",
        priority = Traced.TracingPriority.CRITICAL,
        includeParameters = true,
        includeResult = true,
        tags = {"payment.type=refund", "priority=critical"}
    )
    public RefundResult processRefund(RefundRequest request) {
        // Critical operation with full tracing
        return refundService.process(request);
    }

    /**
     * Tracing async operations
     */
    @Async
    @Traced(
        operationName = "notification.send.async",
        priority = Traced.TracingPriority.LOW
    )
    public CompletableFuture<NotificationResult> sendNotification(User user) {
        // Async operation traced
        return CompletableFuture.completedFuture(result);
    }
}
```

### 2. Manual Span Creation

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class TransactionService {

    @Autowired
    private Tracer tracer;

    public void processTransaction(Transaction transaction) {
        // Create a new span
        Span span = tracer.spanBuilder("transaction.process")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("transaction.id", transaction.getId())
                .setAttribute("transaction.amount", transaction.getAmount())
                .setAttribute("transaction.currency", transaction.getCurrency())
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Your business logic here
            validateTransaction(transaction);

            // Add custom events
            span.addEvent("transaction.validated");

            saveTransaction(transaction);

            span.addEvent("transaction.saved");

            // Mark as successful
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            // Record exception
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### 3. Baggage Propagation

```java
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;

@Service
public class UserService {

    public void processUserRequest(String userId, String tenantId) {
        // Set baggage items
        Baggage baggage = Baggage.builder()
                .put("user.id", userId)
                .put("tenant.id", tenantId)
                .put("request.source", "web")
                .build();

        // Make baggage current
        try (Scope scope = baggage.storeInContext(Context.current()).makeCurrent()) {
            // Baggage automatically propagates to:
            // - Child spans
            // - HTTP requests
            // - Kafka messages
            // - Any downstream service calls

            callDownstreamService();
        }
    }

    public String getBaggageValue(String key) {
        return Baggage.current().getEntryValue(key);
    }
}
```

### 4. Database Query Tracing

Database queries are automatically traced when database tracing is enabled:

```java
@Repository
public class PaymentRepository extends JpaRepository<Payment, Long> {

    // Automatically traced
    Payment findByTransactionId(String transactionId);

    // Custom query - automatically traced
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status = :status")
    List<Payment> findUserPayments(@Param("userId") String userId,
                                   @Param("status") PaymentStatus status);
}
```

### 5. Kafka Message Tracing

Kafka messages are automatically traced when Kafka tracing is enabled:

```java
@Service
public class PaymentEventPublisher {

    @Autowired
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publishPaymentEvent(PaymentEvent event) {
        // Trace context automatically injected into Kafka headers
        kafkaTemplate.send("payment-events", event.getId(), event);
    }
}

@Component
public class PaymentEventConsumer {

    @KafkaListener(topics = "payment-events")
    public void consumePaymentEvent(PaymentEvent event) {
        // Trace context automatically extracted from Kafka headers
        processEvent(event);
    }
}
```

### 6. HTTP Client Tracing

```java
@Service
public class ExternalApiClient {

    @Autowired
    private RestTemplate tracedRestTemplate;  // Auto-configured with tracing

    public UserData fetchUserData(String userId) {
        // HTTP request automatically traced
        // Trace context injected into headers
        ResponseEntity<UserData> response = tracedRestTemplate.getForEntity(
                "https://api.example.com/users/" + userId,
                UserData.class
        );

        return response.getBody();
    }
}
```

### 7. Accessing Current Trace Context

```java
@Service
public class ContextService {

    public void logCurrentTraceInfo() {
        Span currentSpan = Span.current();

        String traceId = currentSpan.getSpanContext().getTraceId();
        String spanId = currentSpan.getSpanContext().getSpanId();
        boolean sampled = currentSpan.getSpanContext().isSampled();

        log.info("Current trace - TraceId: {}, SpanId: {}, Sampled: {}",
                 traceId, spanId, sampled);
    }
}
```

---

## Integration Guide

### 1. Add Dependencies (pom.xml)

```xml
<dependencies>
    <!-- OpenTelemetry -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
        <version>1.32.0</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk</artifactId>
        <version>1.32.0</version>
    </dependency>

    <!-- Exporters -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-zipkin</artifactId>
        <version>1.32.0</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-jaeger</artifactId>
        <version>1.32.0</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
        <version>1.32.0</version>
    </dependency>

    <!-- Semantic Conventions -->
    <dependency>
        <groupId>io.opentelemetry.semconv</groupId>
        <artifactId>opentelemetry-semconv</artifactId>
        <version>1.23.1-alpha</version>
    </dependency>
</dependencies>
```

### 2. Enable Tracing in Your Service

```java
@SpringBootApplication
@Import(DistributedTracingConfig.class)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

### 3. Configure Logback for Trace Correlation

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [traceId=%X{traceId:-}, spanId=%X{spanId:-}, correlationId=%X{correlationId:-}] - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

---

## Performance Tuning

### Sampling Strategies

**Production Recommendation:**
```yaml
tracing:
  sampling:
    default-rate: 0.1      # 10% for normal traffic
    error-rate: 1.0        # 100% for errors
    critical-operations-rate: 1.0  # 100% for critical ops
```

### Batch Configuration

**High-Throughput Services:**
```yaml
tracing:
  export:
    batch:
      max-queue-size: 4096
      max-export-batch-size: 1024
      schedule-delay: 2  # Export more frequently
```

**Low-Latency Services:**
```yaml
tracing:
  export:
    batch:
      max-queue-size: 1024
      max-export-batch-size: 256
      schedule-delay: 1  # Export very frequently
```

### Resource Limits

Monitor and adjust based on your service requirements:

```yaml
# JVM Options
-XX:MaxDirectMemorySize=512m
-Dio.opentelemetry.javaagent.shaded.io.netty.maxDirectMemory=512m
```

---

## Troubleshooting

### Common Issues

#### 1. No Traces Appearing

**Check:**
- Tracing is enabled: `tracing.enabled=true`
- At least one exporter is enabled and reachable
- Sampling rate is not 0
- Check logs for connection errors

**Solution:**
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Enable debug logging
logging.level.io.opentelemetry: DEBUG
```

#### 2. High Memory Usage

**Cause:** Large batch queue sizes or too many attributes

**Solution:**
```yaml
tracing:
  export:
    batch:
      max-queue-size: 1024  # Reduce queue size
      max-export-batch-size: 256  # Reduce batch size
```

#### 3. Missing Trace Context

**Cause:** Context not propagated correctly

**Solution:**
- Ensure `@Async` methods use traced executors
- Verify HTTP client is using traced RestTemplate
- Check Kafka interceptors are configured

#### 4. Performance Degradation

**Cause:** Too high sampling rate

**Solution:**
```yaml
tracing:
  sampling:
    default-rate: 0.05  # Reduce to 5%
```

### Monitoring Tracing Health

```java
@RestController
public class TracingHealthController {

    @Autowired
    private HealthIndicator tracingHealthIndicator;

    @GetMapping("/tracing/health")
    public Health getTracingHealth() {
        return tracingHealthIndicator.health();
    }
}
```

### Debugging Tips

1. **Enable Debug Logging:**
   ```yaml
   logging:
     level:
       io.opentelemetry: DEBUG
       com.example.common.tracing: DEBUG
   ```

2. **Check Exporter Connectivity:**
   ```bash
   # Test Zipkin
   curl http://zipkin:9411/api/v2/services

   # Test Jaeger
   curl http://jaeger:16686/api/services
   ```

3. **Verify Trace IDs:**
   ```java
   log.info("Current trace ID: {}", Span.current().getSpanContext().getTraceId());
   ```

---

## Best Practices

1. **Use Meaningful Operation Names:**
   ```java
   @Traced(operationName = "payment.card.authorize")  // Good
   @Traced(operationName = "process")  // Bad
   ```

2. **Add Business Context:**
   ```java
   span.setAttribute("payment.method", "credit_card");
   span.setAttribute("payment.amount", amount);
   span.setAttribute("payment.currency", currency);
   ```

3. **Don't Over-Instrument:**
   - Focus on important operations
   - Use appropriate priority levels
   - Avoid tracing getter/setter methods

4. **Handle Sensitive Data:**
   - Never log PII in spans
   - Mask sensitive attributes
   - Use baggage carefully

5. **Test Sampling in Production:**
   - Start with low sampling rates
   - Gradually increase based on traffic
   - Monitor resource usage

---

## Support

For issues or questions:
- **Internal Wiki:** [Waqiti Observability Guide](https://wiki.example.com/observability)
- **Slack Channel:** #platform-observability
- **Email:** platform-team@example.com

---

**Version:** 2.0
**Last Updated:** 2024-10-18
**Maintained By:** Waqiti Platform Team
