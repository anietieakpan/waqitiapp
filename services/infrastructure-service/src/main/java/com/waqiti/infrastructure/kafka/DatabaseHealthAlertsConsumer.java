package com.waqiti.infrastructure.kafka;

import com.waqiti.common.events.DatabaseHealthAlertEvent;
import com.waqiti.infrastructure.domain.DatabaseHealthRecord;
import com.waqiti.infrastructure.repository.DatabaseHealthRecordRepository;
import com.waqiti.infrastructure.service.DatabaseHealthService;
import com.waqiti.infrastructure.service.DatabaseRecoveryService;
import com.waqiti.infrastructure.service.DatabaseOptimizationService;
import com.waqiti.infrastructure.metrics.DatabaseMetricsService;
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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseHealthAlertsConsumer {

    private final DatabaseHealthRecordRepository healthRecordRepository;
    private final DatabaseHealthService healthService;
    private final DatabaseRecoveryService recoveryService;
    private final DatabaseOptimizationService optimizationService;
    private final DatabaseMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Database health tracking
    private final AtomicLong databaseIssueCount = new AtomicLong(0);
    private final AtomicLong databaseRecoveryCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter databaseIssueCounter;
    private Timer processingTimer;
    private Gauge databaseIssueGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("database_health_alerts_processed_total")
            .description("Total number of successfully processed database health alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("database_health_alerts_errors_total")
            .description("Total number of database health alert processing errors")
            .register(meterRegistry);
        databaseIssueCounter = Counter.builder("database_issues_total")
            .description("Total number of database health issues")
            .register(meterRegistry);
        processingTimer = Timer.builder("database_health_alerts_processing_duration")
            .description("Time taken to process database health alert events")
            .register(meterRegistry);
        databaseIssueGauge = Gauge.builder("database_issues_active")
            .description("Number of active database health issues")
            .register(meterRegistry, databaseIssueCount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"database-health-alerts", "database-performance-alerts", "database-availability-events"},
        groupId = "database-health-alerts-service-group",
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
    @CircuitBreaker(name = "database-health-alerts", fallbackMethod = "handleDatabaseHealthAlertFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleDatabaseHealthAlertEvent(
            @Payload DatabaseHealthAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("db-health-%s-p%d-o%d", event.getDatabaseId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDatabaseId(), event.getAlertType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing database health alert: databaseId={}, alertType={}, severity={}, metricValue={}",
                event.getDatabaseId(), event.getAlertType(), event.getSeverity(), event.getMetricValue());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Database recovery impact assessment
            assessDatabaseRecoveryImpact(event, correlationId);

            switch (event.getAlertType()) {
                case CONNECTION_POOL_EXHAUSTED:
                    handleConnectionPoolExhausted(event, correlationId);
                    break;

                case HIGH_CPU_UTILIZATION:
                    handleHighCpuUtilization(event, correlationId);
                    break;

                case HIGH_MEMORY_UTILIZATION:
                    handleHighMemoryUtilization(event, correlationId);
                    break;

                case SLOW_QUERIES:
                    handleSlowQueries(event, correlationId);
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

                case BACKUP_FAILURE:
                    handleBackupFailure(event, correlationId);
                    break;

                case INDEX_FRAGMENTATION:
                    handleIndexFragmentation(event, correlationId);
                    break;

                default:
                    log.warn("Unknown database health alert type: {}", event.getAlertType());
                    handleGenericDatabaseAlert(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDatabaseEvent("DATABASE_HEALTH_ALERT_PROCESSED", event.getDatabaseId(),
                Map.of("alertType", event.getAlertType(), "severity", event.getSeverity(),
                    "metricValue", event.getMetricValue(), "thresholdValue", event.getThresholdValue(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process database health alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("database-health-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleDatabaseHealthAlertFallback(
            DatabaseHealthAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("db-health-fallback-%s-p%d-o%d", event.getDatabaseId(), partition, offset);

        log.error("Circuit breaker fallback triggered for database health alert: databaseId={}, error={}",
            event.getDatabaseId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("database-health-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for database unavailability
        if ("DATABASE_UNAVAILABLE".equals(event.getAlertType()) || "CRITICAL".equals(event.getSeverity())) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Database Health Alert - Circuit Breaker Triggered",
                    String.format("Critical database health monitoring for %s failed: %s",
                        event.getDatabaseId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltDatabaseHealthAlertEvent(
            @Payload DatabaseHealthAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-db-health-%s-%d", event.getDatabaseId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Database health alert permanently failed: databaseId={}, topic={}, error={}",
            event.getDatabaseId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logDatabaseEvent("DATABASE_HEALTH_ALERT_DLT_EVENT", event.getDatabaseId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "severity", event.getSeverity(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Database Health Alert Dead Letter Event",
                String.format("Database health monitoring for %s sent to DLT: %s",
                    event.getDatabaseId(), exceptionMessage),
                Map.of("databaseId", event.getDatabaseId(), "topic", topic,
                    "correlationId", correlationId, "alertType", event.getAlertType())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessDatabaseRecoveryImpact(DatabaseHealthAlertEvent event, String correlationId) {
        if ("CRITICAL".equals(event.getSeverity()) || "DATABASE_UNAVAILABLE".equals(event.getAlertType())) {
            databaseIssueCount.incrementAndGet();
            databaseIssueCounter.increment();

            // Alert if too many database issues
            if (databaseIssueCount.get() > 3) {
                try {
                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Multiple Database Health Issues",
                        String.format("Database issues count: %d. Data infrastructure review required.",
                            databaseIssueCount.get()),
                        "CRITICAL"
                    );
                } catch (Exception ex) {
                    log.error("Failed to send database recovery impact alert: {}", ex.getMessage());
                }
            }
        }

        if ("HEALTHY".equals(event.getSeverity()) || "RESOLVED".equals(event.getAlertType())) {
            long currentIssues = databaseIssueCount.get();
            if (currentIssues > 0) {
                databaseIssueCount.decrementAndGet();
                databaseRecoveryCount.incrementAndGet();
            }
        }
    }

    private void handleConnectionPoolExhausted(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "CONNECTION_POOL_EXHAUSTED", correlationId);

        // Immediate connection pool scaling
        kafkaTemplate.send("connection-pool-scaling-requests", Map.of(
            "databaseId", event.getDatabaseId(),
            "scalingType", "POOL_EXHAUSTION_SCALING",
            "currentPoolSize", event.getCurrentConnectionPoolSize(),
            "targetPoolSize", event.getCurrentConnectionPoolSize() * 1.5,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Connection leak detection
        kafkaTemplate.send("connection-leak-detection", Map.of(
            "databaseId", event.getDatabaseId(),
            "detectionType", "POOL_EXHAUSTION_ANALYSIS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Circuit breaker activation for new connections
        kafkaTemplate.send("circuit-breaker-activation", Map.of(
            "databaseId", event.getDatabaseId(),
            "circuitType", "CONNECTION_PROTECTION",
            "duration", 300000, // 5 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Database Connection Pool Exhausted",
            String.format("Database %s connection pool exhausted: %d/%d connections",
                event.getDatabaseId(), event.getCurrentConnectionPoolSize(), event.getMaxConnectionPoolSize()),
            "CRITICAL");

        metricsService.recordDatabaseAlert("CONNECTION_POOL_EXHAUSTED", event.getDatabaseId());
    }

    private void handleHighCpuUtilization(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "HIGH_CPU", correlationId);

        // Query optimization analysis
        kafkaTemplate.send("query-optimization-analysis", Map.of(
            "databaseId", event.getDatabaseId(),
            "analysisType", "HIGH_CPU_ANALYSIS",
            "cpuUtilization", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Resource scaling
        if (event.getMetricValue() > 90.0) {
            kafkaTemplate.send("database-resource-scaling", Map.of(
                "databaseId", event.getDatabaseId(),
                "resourceType", "CPU",
                "scalingDirection", "UP",
                "urgency", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Kill expensive queries if CPU > 95%
        if (event.getMetricValue() > 95.0) {
            kafkaTemplate.send("expensive-query-termination", Map.of(
                "databaseId", event.getDatabaseId(),
                "terminationType", "HIGH_CPU_PROTECTION",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordDatabaseAlert("HIGH_CPU", event.getDatabaseId());
    }

    private void handleHighMemoryUtilization(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "HIGH_MEMORY", correlationId);

        // Memory optimization
        kafkaTemplate.send("memory-optimization-requests", Map.of(
            "databaseId", event.getDatabaseId(),
            "optimizationType", "HIGH_MEMORY_OPTIMIZATION",
            "memoryUtilization", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Cache clearing
        if (event.getMetricValue() > 90.0) {
            kafkaTemplate.send("cache-clearing-requests", Map.of(
                "databaseId", event.getDatabaseId(),
                "cacheType", "BUFFER_POOL",
                "clearingStrategy", "SELECTIVE",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordDatabaseAlert("HIGH_MEMORY", event.getDatabaseId());
    }

    private void handleSlowQueries(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "SLOW_QUERIES", correlationId);

        // Slow query analysis
        kafkaTemplate.send("slow-query-analysis", Map.of(
            "databaseId", event.getDatabaseId(),
            "analysisType", "SLOW_QUERY_INVESTIGATION",
            "averageQueryTime", event.getMetricValue(),
            "slowQueryThreshold", event.getThresholdValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Index recommendations
        kafkaTemplate.send("index-recommendation-requests", Map.of(
            "databaseId", event.getDatabaseId(),
            "recommendationType", "SLOW_QUERY_OPTIMIZATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Query plan analysis
        kafkaTemplate.send("query-plan-analysis", Map.of(
            "databaseId", event.getDatabaseId(),
            "analysisType", "SLOW_QUERY_PLANS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordDatabaseAlert("SLOW_QUERIES", event.getDatabaseId());
    }

    private void handleDeadlockDetected(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "DEADLOCK", correlationId);

        // Deadlock analysis
        kafkaTemplate.send("deadlock-analysis", Map.of(
            "databaseId", event.getDatabaseId(),
            "analysisType", "DEADLOCK_INVESTIGATION",
            "deadlockCount", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Transaction isolation adjustment
        kafkaTemplate.send("isolation-level-optimization", Map.of(
            "databaseId", event.getDatabaseId(),
            "optimizationType", "DEADLOCK_PREVENTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Database Deadlocks Detected",
            String.format("Database %s experiencing deadlocks: %.0f occurrences",
                event.getDatabaseId(), event.getMetricValue()),
            "HIGH");

        metricsService.recordDatabaseAlert("DEADLOCK", event.getDatabaseId());
    }

    private void handleReplicationLag(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "REPLICATION_LAG", correlationId);

        // Replication lag analysis
        kafkaTemplate.send("replication-lag-analysis", Map.of(
            "databaseId", event.getDatabaseId(),
            "analysisType", "REPLICATION_LAG_INVESTIGATION",
            "lagSeconds", event.getMetricValue(),
            "lagThreshold", event.getThresholdValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Network bandwidth check between primary and replica
        kafkaTemplate.send("replication-network-checks", Map.of(
            "databaseId", event.getDatabaseId(),
            "checkType", "REPLICATION_BANDWIDTH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Replica recovery if lag is too high
        if (event.getMetricValue() > 300) { // 5 minutes
            kafkaTemplate.send("replica-recovery-requests", Map.of(
                "databaseId", event.getDatabaseId(),
                "recoveryType", "HIGH_LAG_RECOVERY",
                "lagSeconds", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordDatabaseAlert("REPLICATION_LAG", event.getDatabaseId());
    }

    private void handleDiskSpaceLow(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "DISK_SPACE_LOW", correlationId);

        // Disk space optimization
        kafkaTemplate.send("disk-space-optimization", Map.of(
            "databaseId", event.getDatabaseId(),
            "optimizationType", "DISK_SPACE_CLEANUP",
            "availableSpacePercent", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Log file cleanup
        kafkaTemplate.send("log-file-cleanup", Map.of(
            "databaseId", event.getDatabaseId(),
            "cleanupType", "LOW_DISK_SPACE",
            "retentionDays", 7,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Emergency disk expansion
        if (event.getMetricValue() < 5.0) { // Less than 5% free
            kafkaTemplate.send("emergency-disk-expansion", Map.of(
                "databaseId", event.getDatabaseId(),
                "expansionType", "EMERGENCY",
                "expansionSize", "50GB",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendOperationalAlert("Database Disk Space Low",
            String.format("Database %s disk space low: %.1f%% available",
                event.getDatabaseId(), event.getMetricValue()),
            "HIGH");

        metricsService.recordDatabaseAlert("DISK_SPACE_LOW", event.getDatabaseId());
    }

    private void handleDatabaseUnavailable(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "UNAVAILABLE", correlationId);

        // Immediate failover to read replica
        kafkaTemplate.send("database-failover-requests", Map.of(
            "primaryDatabaseId", event.getDatabaseId(),
            "failoverType", "DATABASE_UNAVAILABLE",
            "urgency", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Database recovery
        kafkaTemplate.send("database-recovery-requests", Map.of(
            "databaseId", event.getDatabaseId(),
            "recoveryType", "UNAVAILABILITY_RECOVERY",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Health checks
        kafkaTemplate.send("database-health-checks", Map.of(
            "databaseId", event.getDatabaseId(),
            "checkType", "UNAVAILABILITY_DIAGNOSIS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Database Unavailable",
            String.format("Database %s is unavailable, initiating failover",
                event.getDatabaseId()),
            "CRITICAL");

        metricsService.recordDatabaseAlert("UNAVAILABLE", event.getDatabaseId());
    }

    private void handleBackupFailure(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "BACKUP_FAILURE", correlationId);

        // Retry backup
        kafkaTemplate.send("backup-retry-requests", Map.of(
            "databaseId", event.getDatabaseId(),
            "retryType", "BACKUP_FAILURE_RETRY",
            "backupType", event.getBackupType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Alternative backup location
        kafkaTemplate.send("alternative-backup-requests", Map.of(
            "databaseId", event.getDatabaseId(),
            "backupType", "ALTERNATIVE_LOCATION",
            "urgency", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Database Backup Failure",
            String.format("Database %s backup failed: %s",
                event.getDatabaseId(), event.getFailureReason()),
            "HIGH");

        metricsService.recordDatabaseAlert("BACKUP_FAILURE", event.getDatabaseId());
    }

    private void handleIndexFragmentation(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "INDEX_FRAGMENTATION", correlationId);

        // Index maintenance scheduling
        kafkaTemplate.send("index-maintenance-scheduling", Map.of(
            "databaseId", event.getDatabaseId(),
            "maintenanceType", "FRAGMENTATION_REPAIR",
            "fragmentationPercent", event.getMetricValue(),
            "schedulingPriority", event.getMetricValue() > 50 ? "HIGH" : "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Performance impact analysis
        kafkaTemplate.send("fragmentation-impact-analysis", Map.of(
            "databaseId", event.getDatabaseId(),
            "analysisType", "FRAGMENTATION_PERFORMANCE_IMPACT",
            "fragmentationPercent", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordDatabaseAlert("INDEX_FRAGMENTATION", event.getDatabaseId());
    }

    private void handleGenericDatabaseAlert(DatabaseHealthAlertEvent event, String correlationId) {
        createHealthRecord(event, "GENERIC", correlationId);

        // Log for investigation
        auditService.logDatabaseEvent("UNKNOWN_DATABASE_ALERT", event.getDatabaseId(),
            Map.of("alertType", event.getAlertType(), "severity", event.getSeverity(),
                "metricValue", event.getMetricValue(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Database Alert",
            String.format("Unknown database alert for %s: %s",
                event.getDatabaseId(), event.getAlertType()),
            "MEDIUM");

        metricsService.recordDatabaseAlert("GENERIC", event.getDatabaseId());
    }

    private void createHealthRecord(DatabaseHealthAlertEvent event, String alertType, String correlationId) {
        try {
            DatabaseHealthRecord record = DatabaseHealthRecord.builder()
                .databaseId(event.getDatabaseId())
                .databaseType(event.getDatabaseType())
                .alertType(alertType)
                .severity(event.getSeverity())
                .metricValue(event.getMetricValue())
                .thresholdValue(event.getThresholdValue())
                .alertTime(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

            healthRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to create database health record: {}", e.getMessage());
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
}