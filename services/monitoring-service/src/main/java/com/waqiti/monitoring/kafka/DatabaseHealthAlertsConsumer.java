package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.DatabaseHealthAlertEvent;
import com.waqiti.monitoring.service.DatabaseMonitoringService;
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
public class DatabaseHealthAlertsConsumer {

    private final DatabaseMonitoringService databaseService;
    private final AlertingService alertingService;
    private final InfrastructureMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("database_health_alerts_processed_total")
            .description("Total number of successfully processed database health alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("database_health_alerts_errors_total")
            .description("Total number of database health alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("database_health_alerts_processing_duration")
            .description("Time taken to process database health alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"database-health-alerts", "db-performance-alerts", "database-monitoring"},
        groupId = "database-health-group",
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
    @CircuitBreaker(name = "database-health-alerts", fallbackMethod = "handleDatabaseHealthAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleDatabaseHealthAlertEvent(
            @Payload DatabaseHealthAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("db-health-%s-p%d-o%d", event.getDatabaseName(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDatabaseName(), event.getAlertType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing database health alert: db={}, type={}, severity={}, metric={}",
                event.getDatabaseName(), event.getAlertType(), event.getSeverity(), event.getMetricName());

            cleanExpiredEntries();

            switch (event.getAlertType()) {
                case HIGH_CPU_USAGE:
                    handleHighCpuUsage(event, correlationId);
                    break;

                case HIGH_MEMORY_USAGE:
                    handleHighMemoryUsage(event, correlationId);
                    break;

                case SLOW_QUERIES:
                    handleSlowQueries(event, correlationId);
                    break;

                case CONNECTION_EXHAUSTION:
                    handleConnectionExhaustion(event, correlationId);
                    break;

                case DEADLOCK_DETECTED:
                    handleDeadlockDetected(event, correlationId);
                    break;

                case REPLICATION_LAG:
                    handleReplicationLag(event, correlationId);
                    break;

                case DISK_SPACE_LOW:
                    handleDiskSpaceLow(event, correlationId);
                    break;

                case DATABASE_UNAVAILABLE:
                    handleDatabaseUnavailable(event, correlationId);
                    break;

                case BACKUP_FAILED:
                    handleBackupFailed(event, correlationId);
                    break;

                case HEALTH_CHECK_FAILED:
                    handleHealthCheckFailed(event, correlationId);
                    break;

                case PERFORMANCE_DEGRADED:
                    handlePerformanceDegraded(event, correlationId);
                    break;

                case HEALTH_RESTORED:
                    handleHealthRestored(event, correlationId);
                    break;

                default:
                    log.warn("Unknown database health alert type: {}", event.getAlertType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logInfrastructureEvent("DATABASE_HEALTH_ALERT_PROCESSED", event.getDatabaseName(),
                Map.of("alertType", event.getAlertType(), "severity", event.getSeverity(),
                    "metricName", event.getMetricName(), "currentValue", event.getCurrentValue(),
                    "threshold", event.getThreshold(), "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process database health alert: {}", e.getMessage(), e);

            kafkaTemplate.send("database-health-alerts-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleDatabaseHealthAlertEventFallback(
            DatabaseHealthAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("db-health-fallback-%s-p%d-o%d", event.getDatabaseName(), partition, offset);

        log.error("Circuit breaker fallback for database health alert: db={}, error={}",
            event.getDatabaseName(), ex.getMessage());

        kafkaTemplate.send("database-health-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Database Health Alert Processing Failure",
                String.format("Database %s health alert processing failed: %s", event.getDatabaseName(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltDatabaseHealthAlertEvent(
            @Payload DatabaseHealthAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-db-health-%s-%d", event.getDatabaseName(), System.currentTimeMillis());

        log.error("DLT handler - Database health alert failed: db={}, topic={}, error={}",
            event.getDatabaseName(), topic, exceptionMessage);

        auditService.logInfrastructureEvent("DATABASE_HEALTH_ALERT_DLT_EVENT", event.getDatabaseName(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Database Health Alert DLT Event",
                String.format("Database %s health alert sent to DLT: %s", event.getDatabaseName(), exceptionMessage),
                Map.of("databaseName", event.getDatabaseName(), "topic", topic, "correlationId", correlationId)
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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void handleHighCpuUsage(DatabaseHealthAlertEvent event, String correlationId) {
        log.warn("High CPU usage detected: db={}, usage={}%, threshold={}%",
            event.getDatabaseName(), event.getCurrentValue(), event.getThreshold());

        databaseService.handleHighCpuUsage(event.getDatabaseName(), event.getCurrentValue(), event.getThreshold());

        alertingService.sendHighPriorityAlert(
            "Database High CPU Usage",
            String.format("Database %s CPU usage at %.1f%% (threshold: %.1f%%)",
                event.getDatabaseName(), event.getCurrentValue(), event.getThreshold()),
            correlationId
        );

        metricsService.recordDatabaseCpuAlert(event.getDatabaseName(), event.getCurrentValue());

        // Trigger performance analysis
        databaseService.analyzePerformanceBottlenecks(event.getDatabaseName());
    }

    private void handleHighMemoryUsage(DatabaseHealthAlertEvent event, String correlationId) {
        log.warn("High memory usage detected: db={}, usage={}%, threshold={}%",
            event.getDatabaseName(), event.getCurrentValue(), event.getThreshold());

        databaseService.handleHighMemoryUsage(event.getDatabaseName(), event.getCurrentValue(), event.getThreshold());

        alertingService.sendHighPriorityAlert(
            "Database High Memory Usage",
            String.format("Database %s memory usage at %.1f%% (threshold: %.1f%%)",
                event.getDatabaseName(), event.getCurrentValue(), event.getThreshold()),
            correlationId
        );

        metricsService.recordDatabaseMemoryAlert(event.getDatabaseName(), event.getCurrentValue());

        // Trigger memory optimization
        databaseService.optimizeMemoryUsage(event.getDatabaseName());
    }

    private void handleSlowQueries(DatabaseHealthAlertEvent event, String correlationId) {
        log.warn("Slow queries detected: db={}, avgTime={}ms, threshold={}ms, count={}",
            event.getDatabaseName(), event.getAvgQueryTime(), event.getSlowQueryThreshold(), event.getSlowQueryCount());

        databaseService.handleSlowQueries(
            event.getDatabaseName(), event.getAvgQueryTime(), event.getSlowQueryCount());

        alertingService.sendMediumPriorityAlert(
            "Database Slow Queries Detected",
            String.format("Database %s has %d slow queries (avg: %dms, threshold: %dms)",
                event.getDatabaseName(), event.getSlowQueryCount(), event.getAvgQueryTime(), event.getSlowQueryThreshold()),
            correlationId
        );

        metricsService.recordSlowQueries(event.getDatabaseName(), event.getSlowQueryCount());

        // Analyze and suggest query optimizations
        databaseService.analyzeSlowQueries(event.getDatabaseName());
    }

    private void handleConnectionExhaustion(DatabaseHealthAlertEvent event, String correlationId) {
        log.error("Connection exhaustion detected: db={}, active={}, max={}",
            event.getDatabaseName(), event.getActiveConnections(), event.getMaxConnections());

        databaseService.handleConnectionExhaustion(
            event.getDatabaseName(), event.getActiveConnections(), event.getMaxConnections());

        alertingService.sendCriticalAlert(
            "Database Connection Exhaustion",
            String.format("Database %s connection pool exhausted (%d/%d)",
                event.getDatabaseName(), event.getActiveConnections(), event.getMaxConnections()),
            correlationId
        );

        metricsService.recordConnectionExhaustion(event.getDatabaseName());

        // Trigger connection pool expansion
        databaseService.expandConnectionPool(event.getDatabaseName());
    }

    private void handleDeadlockDetected(DatabaseHealthAlertEvent event, String correlationId) {
        log.error("Deadlock detected: db={}, deadlockCount={}, affectedTables={}",
            event.getDatabaseName(), event.getDeadlockCount(), event.getAffectedTables());

        databaseService.handleDeadlock(
            event.getDatabaseName(), event.getDeadlockCount(), event.getAffectedTables());

        alertingService.sendHighPriorityAlert(
            "Database Deadlock Detected",
            String.format("Database %s deadlock detected (%d occurrences, tables: %s)",
                event.getDatabaseName(), event.getDeadlockCount(), String.join(", ", event.getAffectedTables())),
            correlationId
        );

        metricsService.recordDeadlock(event.getDatabaseName(), event.getDeadlockCount());

        // Send to deadlock recovery processor
        kafkaTemplate.send("deadlock-recovery-events", Map.of(
            "databaseName", event.getDatabaseName(),
            "deadlockCount", event.getDeadlockCount(),
            "affectedTables", event.getAffectedTables(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleReplicationLag(DatabaseHealthAlertEvent event, String correlationId) {
        log.warn("Replication lag detected: db={}, lag={}ms, threshold={}ms",
            event.getDatabaseName(), event.getReplicationLagMs(), event.getReplicationLagThreshold());

        databaseService.handleReplicationLag(
            event.getDatabaseName(), event.getReplicationLagMs(), event.getReplicationLagThreshold());

        alertingService.sendMediumPriorityAlert(
            "Database Replication Lag",
            String.format("Database %s replication lag: %dms (threshold: %dms)",
                event.getDatabaseName(), event.getReplicationLagMs(), event.getReplicationLagThreshold()),
            correlationId
        );

        metricsService.recordReplicationLag(event.getDatabaseName(), event.getReplicationLagMs());
    }

    private void handleDiskSpaceLow(DatabaseHealthAlertEvent event, String correlationId) {
        log.error("Low disk space: db={}, available={}GB, threshold={}GB",
            event.getDatabaseName(), event.getAvailableDiskSpaceGB(), event.getDiskSpaceThresholdGB());

        databaseService.handleLowDiskSpace(
            event.getDatabaseName(), event.getAvailableDiskSpaceGB(), event.getDiskSpaceThresholdGB());

        alertingService.sendCriticalAlert(
            "Database Low Disk Space",
            String.format("Database %s low disk space: %.1fGB available (threshold: %.1fGB)",
                event.getDatabaseName(), event.getAvailableDiskSpaceGB(), event.getDiskSpaceThresholdGB()),
            correlationId
        );

        metricsService.recordLowDiskSpace(event.getDatabaseName(), event.getAvailableDiskSpaceGB());

        // Trigger disk cleanup procedures
        databaseService.triggerDiskCleanup(event.getDatabaseName());
    }

    private void handleDatabaseUnavailable(DatabaseHealthAlertEvent event, String correlationId) {
        log.error("Database unavailable: db={}, downtime={}",
            event.getDatabaseName(), event.getDowntimeDuration());

        databaseService.handleDatabaseUnavailable(event.getDatabaseName(), event.getDowntimeDuration());

        alertingService.sendEmergencyAlert(
            "Database Unavailable",
            String.format("Database %s is unavailable (downtime: %s)",
                event.getDatabaseName(), event.getDowntimeDuration()),
            correlationId
        );

        metricsService.recordDatabaseUnavailable(event.getDatabaseName());

        // Trigger failover procedures
        databaseService.triggerFailover(event.getDatabaseName());

        // Send to disaster recovery
        kafkaTemplate.send("disaster-recovery-events", Map.of(
            "eventType", "DATABASE_UNAVAILABLE",
            "databaseName", event.getDatabaseName(),
            "downtime", event.getDowntimeDuration(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleBackupFailed(DatabaseHealthAlertEvent event, String correlationId) {
        log.error("Database backup failed: db={}, backupType={}, error={}",
            event.getDatabaseName(), event.getBackupType(), event.getBackupError());

        databaseService.handleBackupFailure(
            event.getDatabaseName(), event.getBackupType(), event.getBackupError());

        alertingService.sendCriticalAlert(
            "Database Backup Failed",
            String.format("Database %s backup failed (%s): %s",
                event.getDatabaseName(), event.getBackupType(), event.getBackupError()),
            correlationId
        );

        metricsService.recordBackupFailure(event.getDatabaseName(), event.getBackupType());

        // Retry backup with alternative method
        databaseService.retryBackup(event.getDatabaseName(), event.getBackupType());
    }

    private void handleHealthCheckFailed(DatabaseHealthAlertEvent event, String correlationId) {
        log.error("Database health check failed: db={}, checkType={}, error={}",
            event.getDatabaseName(), event.getHealthCheckType(), event.getHealthCheckError());

        databaseService.handleHealthCheckFailure(
            event.getDatabaseName(), event.getHealthCheckType(), event.getHealthCheckError());

        alertingService.sendHighPriorityAlert(
            "Database Health Check Failed",
            String.format("Database %s health check failed (%s): %s",
                event.getDatabaseName(), event.getHealthCheckType(), event.getHealthCheckError()),
            correlationId
        );

        metricsService.recordHealthCheckFailure(event.getDatabaseName(), event.getHealthCheckType());
    }

    private void handlePerformanceDegraded(DatabaseHealthAlertEvent event, String correlationId) {
        log.warn("Database performance degraded: db={}, metric={}, degradation={}%",
            event.getDatabaseName(), event.getPerformanceMetric(), event.getPerformanceDegradation());

        databaseService.handlePerformanceDegradation(
            event.getDatabaseName(), event.getPerformanceMetric(), event.getPerformanceDegradation());

        alertingService.sendMediumPriorityAlert(
            "Database Performance Degraded",
            String.format("Database %s performance degraded: %s (%.1f%% degradation)",
                event.getDatabaseName(), event.getPerformanceMetric(), event.getPerformanceDegradation()),
            correlationId
        );

        metricsService.recordPerformanceDegradation(event.getDatabaseName(), event.getPerformanceDegradation());
    }

    private void handleHealthRestored(DatabaseHealthAlertEvent event, String correlationId) {
        log.info("Database health restored: db={}, restoredMetric={}",
            event.getDatabaseName(), event.getRestoredMetric());

        databaseService.handleHealthRestored(event.getDatabaseName(), event.getRestoredMetric());

        alertingService.sendInfoAlert(
            "Database Health Restored",
            String.format("Database %s health restored: %s",
                event.getDatabaseName(), event.getRestoredMetric()),
            correlationId
        );

        metricsService.recordHealthRestored(event.getDatabaseName());

        // Clear any ongoing health alerts
        databaseService.clearHealthAlerts(event.getDatabaseName());
    }
}