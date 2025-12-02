package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.ConnectionPoolAlertEvent;
import com.waqiti.monitoring.service.ConnectionPoolMonitoringService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.InfrastructureMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectionPoolAlertsConsumer {

    private final ConnectionPoolMonitoringService connectionPoolService;
    private final AlertingService alertingService;
    private final InfrastructureMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("connection_pool_alerts_processed_total")
            .description("Total number of successfully processed connection pool alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("connection_pool_alerts_errors_total")
            .description("Total number of connection pool alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("connection_pool_alerts_processing_duration")
            .description("Time taken to process connection pool alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"connection-pool-alerts", "database-connection-alerts", "connection-pool-health"},
        groupId = "connection-pool-monitoring-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "connection-pool-alerts", fallbackMethod = "handleConnectionPoolAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleConnectionPoolAlertEvent(
            @Payload ConnectionPoolAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("pool-alert-%s-p%d-o%d", event.getPoolName(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getPoolName(), event.getAlertType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing connection pool alert: poolName={}, alertType={}, severity={}, activeConnections={}",
                event.getPoolName(), event.getAlertType(), event.getSeverity(), event.getActiveConnections());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getAlertType()) {
                case POOL_EXHAUSTED:
                    handlePoolExhausted(event, correlationId);
                    break;

                case HIGH_UTILIZATION:
                    handleHighUtilization(event, correlationId);
                    break;

                case CONNECTION_TIMEOUT:
                    handleConnectionTimeout(event, correlationId);
                    break;

                case SLOW_QUERIES:
                    handleSlowQueries(event, correlationId);
                    break;

                case CONNECTION_LEAKS:
                    handleConnectionLeaks(event, correlationId);
                    break;

                case POOL_VALIDATION_FAILED:
                    handlePoolValidationFailed(event, correlationId);
                    break;

                case DATABASE_UNAVAILABLE:
                    handleDatabaseUnavailable(event, correlationId);
                    break;

                case RECOVERY_COMPLETED:
                    handleRecoveryCompleted(event, correlationId);
                    break;

                default:
                    log.warn("Unknown connection pool alert type: {}", event.getAlertType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logInfrastructureEvent("CONNECTION_POOL_ALERT_PROCESSED", event.getPoolName(),
                Map.of("alertType", event.getAlertType(), "severity", event.getSeverity(),
                    "activeConnections", event.getActiveConnections(), "maxConnections", event.getMaxConnections(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process connection pool alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("connection-pool-alerts-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleConnectionPoolAlertEventFallback(
            ConnectionPoolAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("pool-alert-fallback-%s-p%d-o%d", event.getPoolName(), partition, offset);

        log.error("Circuit breaker fallback triggered for connection pool alert: poolName={}, error={}",
            event.getPoolName(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("connection-pool-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Connection Pool Alert Circuit Breaker Triggered",
                String.format("Pool %s alert processing failed: %s", event.getPoolName(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltConnectionPoolAlertEvent(
            @Payload ConnectionPoolAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-pool-alert-%s-%d", event.getPoolName(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Connection pool alert permanently failed: poolName={}, topic={}, error={}",
            event.getPoolName(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logInfrastructureEvent("CONNECTION_POOL_ALERT_DLT_EVENT", event.getPoolName(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Connection Pool Alert Dead Letter Event",
                String.format("Pool %s alert sent to DLT: %s", event.getPoolName(), exceptionMessage),
                Map.of("poolName", event.getPoolName(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void handlePoolExhausted(ConnectionPoolAlertEvent event, String correlationId) {
        log.error("Database connection pool exhausted: pool={}, active={}, max={}",
            event.getPoolName(), event.getActiveConnections(), event.getMaxConnections());

        connectionPoolService.handlePoolExhaustion(event.getPoolName(), event.getActiveConnections(), event.getMaxConnections());

        alertingService.sendCriticalAlert(
            "Connection Pool Exhausted",
            String.format("Pool %s has reached maximum capacity (%d/%d connections)",
                event.getPoolName(), event.getActiveConnections(), event.getMaxConnections()),
            correlationId
        );

        // Trigger immediate pool expansion if configured
        connectionPoolService.triggerPoolExpansion(event.getPoolName());

        metricsService.recordPoolExhaustion(event.getPoolName());

        // Send to infrastructure monitoring
        kafkaTemplate.send("infrastructure-alerts", Map.of(
            "alertType", "DATABASE_POOL_EXHAUSTED",
            "poolName", event.getPoolName(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleHighUtilization(ConnectionPoolAlertEvent event, String correlationId) {
        double utilizationPercent = (double) event.getActiveConnections() / event.getMaxConnections() * 100;

        log.warn("High connection pool utilization: pool={}, utilization={}%",
            event.getPoolName(), utilizationPercent);

        connectionPoolService.monitorHighUtilization(event.getPoolName(), utilizationPercent);

        if (utilizationPercent > 90) {
            alertingService.sendHighPriorityAlert(
                "High Connection Pool Utilization",
                String.format("Pool %s utilization at %.1f%% (%d/%d connections)",
                    event.getPoolName(), utilizationPercent, event.getActiveConnections(), event.getMaxConnections()),
                correlationId
            );
        }

        metricsService.recordPoolUtilization(event.getPoolName(), utilizationPercent);
    }

    private void handleConnectionTimeout(ConnectionPoolAlertEvent event, String correlationId) {
        log.warn("Connection timeout detected: pool={}, timeoutMs={}",
            event.getPoolName(), event.getTimeoutMs());

        connectionPoolService.handleConnectionTimeouts(event.getPoolName(), event.getTimeoutMs());

        alertingService.sendMediumPriorityAlert(
            "Connection Pool Timeout",
            String.format("Pool %s experiencing connection timeouts (timeout: %dms)",
                event.getPoolName(), event.getTimeoutMs()),
            correlationId
        );

        metricsService.recordConnectionTimeouts(event.getPoolName(), event.getTimeoutMs());
    }

    private void handleSlowQueries(ConnectionPoolAlertEvent event, String correlationId) {
        log.warn("Slow queries detected affecting connection pool: pool={}, avgQueryTime={}ms",
            event.getPoolName(), event.getAvgQueryTimeMs());

        connectionPoolService.analyzeSlowQueries(event.getPoolName(), event.getAvgQueryTimeMs());

        alertingService.sendMediumPriorityAlert(
            "Slow Queries Affecting Connection Pool",
            String.format("Pool %s experiencing slow queries (avg: %dms)",
                event.getPoolName(), event.getAvgQueryTimeMs()),
            correlationId
        );

        metricsService.recordSlowQueryImpact(event.getPoolName(), event.getAvgQueryTimeMs());
    }

    private void handleConnectionLeaks(ConnectionPoolAlertEvent event, String correlationId) {
        log.error("Connection leaks detected: pool={}, leakedConnections={}",
            event.getPoolName(), event.getLeakedConnections());

        connectionPoolService.handleConnectionLeaks(event.getPoolName(), event.getLeakedConnections());

        alertingService.sendHighPriorityAlert(
            "Connection Pool Leaks Detected",
            String.format("Pool %s has %d leaked connections",
                event.getPoolName(), event.getLeakedConnections()),
            correlationId
        );

        metricsService.recordConnectionLeaks(event.getPoolName(), event.getLeakedConnections());

        // Force connection cleanup
        connectionPoolService.forceConnectionCleanup(event.getPoolName());
    }

    private void handlePoolValidationFailed(ConnectionPoolAlertEvent event, String correlationId) {
        log.error("Connection pool validation failed: pool={}, failedValidations={}",
            event.getPoolName(), event.getFailedValidations());

        connectionPoolService.handleValidationFailures(event.getPoolName(), event.getFailedValidations());

        alertingService.sendHighPriorityAlert(
            "Connection Pool Validation Failed",
            String.format("Pool %s validation failed (%d failures)",
                event.getPoolName(), event.getFailedValidations()),
            correlationId
        );

        metricsService.recordValidationFailures(event.getPoolName(), event.getFailedValidations());
    }

    private void handleDatabaseUnavailable(ConnectionPoolAlertEvent event, String correlationId) {
        log.error("Database unavailable for connection pool: pool={}", event.getPoolName());

        connectionPoolService.handleDatabaseUnavailable(event.getPoolName());

        alertingService.sendCriticalAlert(
            "Database Unavailable",
            String.format("Database for pool %s is unavailable", event.getPoolName()),
            correlationId
        );

        metricsService.recordDatabaseUnavailable(event.getPoolName());

        // Trigger failover procedures
        connectionPoolService.triggerFailoverProcedures(event.getPoolName());

        // Send to disaster recovery
        kafkaTemplate.send("disaster-recovery-events", Map.of(
            "eventType", "DATABASE_UNAVAILABLE",
            "poolName", event.getPoolName(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleRecoveryCompleted(ConnectionPoolAlertEvent event, String correlationId) {
        log.info("Connection pool recovery completed: pool={}", event.getPoolName());

        connectionPoolService.handleRecoveryCompleted(event.getPoolName());

        alertingService.sendInfoAlert(
            "Connection Pool Recovery Completed",
            String.format("Pool %s has recovered successfully", event.getPoolName()),
            correlationId
        );

        metricsService.recordPoolRecovery(event.getPoolName());

        // Clear any ongoing incidents
        connectionPoolService.clearPoolIncidents(event.getPoolName());
    }
}