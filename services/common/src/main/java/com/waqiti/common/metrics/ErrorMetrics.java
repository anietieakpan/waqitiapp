package com.waqiti.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Error Metrics - Production-Ready Error Tracking and Monitoring
 *
 * Comprehensive error metrics collection for monitoring system health,
 * identifying reliability issues, and tracking SLA compliance.
 *
 * METRICS COLLECTED:
 * - DLQ message counts by topic and service
 * - Critical error counts by topic and service
 * - Error rates per service and operation
 * - Error duration/latency tracking
 * - Error types and classifications
 *
 * FEATURES:
 * - Real-time error tracking with Micrometer
 * - Dimensional metrics (tags for topic, service, error type)
 * - Thread-safe counter management
 * - Automatic metric registration
 * - Integration with Prometheus/Grafana
 *
 * BUSINESS VALUE:
 * - Early detection of system degradation
 * - SLA compliance monitoring
 * - Root cause analysis support
 * - Capacity planning data
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ErrorMetrics {

    private final MeterRegistry meterRegistry;

    // Thread-safe cache of metric counters
    private final Map<String, Counter> dlqCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> errorTimers = new ConcurrentHashMap<>();

    @PostConstruct
    public void initMetrics() {
        log.info("Initializing ErrorMetrics with MeterRegistry: {}", meterRegistry.getClass().getSimpleName());
    }

    /**
     * Increment DLQ count for a specific topic and service
     *
     * Used to track messages that failed processing and were sent to Dead Letter Queue.
     * High DLQ counts indicate processing issues requiring investigation.
     *
     * @param topic Kafka topic name (e.g., "payment-events", "account-freeze-requests")
     * @param service Service name (e.g., "wallet-service", "payment-service")
     * @param errorClass Error class name for categorization
     */
    public void incrementDlqCount(String topic, String service, String errorClass) {
        try {
            String metricKey = String.format("dlq.%s.%s.%s", topic, service, errorClass);

            Counter counter = dlqCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("kafka.dlq.messages.total")
                            .description("Total number of messages sent to DLQ")
                            .tag("topic", topic)
                            .tag("service", service)
                            .tag("error_class", errorClass)
                            .register(meterRegistry)
            );

            counter.increment();

            log.debug("DLQ_METRIC: Incremented DLQ count - Topic: {}, Service: {}, ErrorClass: {}, Total: {}",
                    topic, service, errorClass, counter.count());

        } catch (Exception e) {
            log.error("Failed to increment DLQ metric - Topic: {}, Service: {}, ErrorClass: {}",
                    topic, service, errorClass, e);
        }
    }

    /**
     * Record a critical error occurrence
     *
     * Used to track critical errors that require immediate attention.
     * Examples: payment processing failures, compliance violations, security breaches.
     *
     * @param topic Event topic where error occurred
     * @param service Service where error occurred
     * @param error The exception/error that occurred
     */
    public void recordCriticalError(String topic, String service, Throwable error) {
        try {
            String errorType = error.getClass().getSimpleName();
            String metricKey = String.format("critical.%s.%s.%s", topic, service, errorType);

            Counter counter = errorCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("errors.critical.total")
                            .description("Total number of critical errors")
                            .tag("topic", topic)
                            .tag("service", service)
                            .tag("error_type", errorType)
                            .tag("severity", "CRITICAL")
                            .register(meterRegistry)
            );

            counter.increment();

            log.error("CRITICAL_ERROR_METRIC: Recorded critical error - Topic: {}, Service: {}, ErrorType: {}, Total: {}",
                    topic, service, errorType, counter.count(), error);

        } catch (Exception e) {
            log.error("Failed to record critical error metric - Topic: {}, Service: {}",
                    topic, service, e);
        }
    }

    /**
     * Record an error with specific error type
     *
     * @param service Service name
     * @param operation Operation name (e.g., "processPayment", "freezeAccount")
     * @param errorType Error classification (e.g., "VALIDATION_ERROR", "TIMEOUT", "DATABASE_ERROR")
     */
    public void recordError(String service, String operation, String errorType) {
        try {
            String metricKey = String.format("error.%s.%s.%s", service, operation, errorType);

            Counter counter = errorCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("service.errors.total")
                            .description("Total number of errors by service and operation")
                            .tag("service", service)
                            .tag("operation", operation)
                            .tag("error_type", errorType)
                            .register(meterRegistry)
            );

            counter.increment();

            log.debug("ERROR_METRIC: Recorded error - Service: {}, Operation: {}, ErrorType: {}, Total: {}",
                    service, operation, errorType, counter.count());

        } catch (Exception e) {
            log.error("Failed to record error metric - Service: {}, Operation: {}, ErrorType: {}",
                    service, operation, errorType, e);
        }
    }

    /**
     * Record error processing duration
     *
     * Tracks how long it takes to handle errors, useful for identifying performance bottlenecks
     * in error handling logic (e.g., retry mechanisms, DLQ processing).
     *
     * @param service Service name
     * @param operation Operation name
     * @param durationMs Duration in milliseconds
     */
    public void recordErrorDuration(String service, String operation, long durationMs) {
        try {
            String metricKey = String.format("duration.%s.%s", service, operation);

            Timer timer = errorTimers.computeIfAbsent(metricKey, key ->
                    Timer.builder("service.error.duration")
                            .description("Error processing duration")
                            .tag("service", service)
                            .tag("operation", operation)
                            .register(meterRegistry)
            );

            timer.record(durationMs, TimeUnit.MILLISECONDS);

            log.debug("ERROR_DURATION_METRIC: Recorded error duration - Service: {}, Operation: {}, Duration: {}ms",
                    service, operation, durationMs);

        } catch (Exception e) {
            log.error("Failed to record error duration metric - Service: {}, Operation: {}, Duration: {}ms",
                    service, operation, durationMs, e);
        }
    }

    /**
     * Record retry attempt
     *
     * Tracks the number of retry attempts for operations, useful for identifying
     * flaky integrations or unstable components.
     *
     * @param service Service name
     * @param operation Operation being retried
     * @param attemptNumber Current attempt number
     */
    public void recordRetryAttempt(String service, String operation, int attemptNumber) {
        try {
            Counter counter = errorCounters.computeIfAbsent(
                    String.format("retry.%s.%s", service, operation),
                    key -> Counter.builder("service.retry.attempts.total")
                            .description("Total number of retry attempts")
                            .tag("service", service)
                            .tag("operation", operation)
                            .register(meterRegistry)
            );

            counter.increment();

            // Record attempt number as a gauge
            meterRegistry.gauge(
                    "service.retry.attempt.number",
                    Arrays.asList(
                            io.micrometer.core.instrument.Tag.of("service", service),
                            io.micrometer.core.instrument.Tag.of("operation", operation)
                    ),
                    attemptNumber
            );

            log.debug("RETRY_METRIC: Recorded retry attempt - Service: {}, Operation: {}, Attempt: {}",
                    service, operation, attemptNumber);

        } catch (Exception e) {
            log.error("Failed to record retry metric - Service: {}, Operation: {}, Attempt: {}",
                    service, operation, attemptNumber, e);
        }
    }

    /**
     * Get current DLQ count for a specific topic and service
     *
     * @param topic Kafka topic name
     * @param service Service name
     * @return Current DLQ count, or 0 if not found
     */
    public double getDlqCount(String topic, String service) {
        try {
            return dlqCounters.values().stream()
                    .filter(counter -> {
                        String topicTag = counter.getId().getTag("topic");
                        String serviceTag = counter.getId().getTag("service");
                        return topic.equals(topicTag) && service.equals(serviceTag);
                    })
                    .mapToDouble(Counter::count)
                    .sum();
        } catch (Exception e) {
            log.error("Failed to get DLQ count - Topic: {}, Service: {}", topic, service, e);
            return 0.0;
        }
    }

    /**
     * Get current error count for a specific service
     *
     * @param service Service name
     * @return Current error count, or 0 if not found
     */
    public double getErrorCount(String service) {
        try {
            return errorCounters.values().stream()
                    .filter(counter -> {
                        String serviceTag = counter.getId().getTag("service");
                        return service.equals(serviceTag);
                    })
                    .mapToDouble(Counter::count)
                    .sum();
        } catch (Exception e) {
            log.error("Failed to get error count - Service: {}", service, e);
            return 0.0;
        }
    }
}
