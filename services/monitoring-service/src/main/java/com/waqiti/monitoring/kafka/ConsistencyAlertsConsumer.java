package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.ConsistencyAlertEvent;
import com.waqiti.monitoring.service.DataConsistencyService;
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
public class ConsistencyAlertsConsumer {

    private final DataConsistencyService consistencyService;
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
        successCounter = Counter.builder("consistency_alerts_processed_total")
            .description("Total number of successfully processed consistency alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("consistency_alerts_errors_total")
            .description("Total number of consistency alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("consistency_alerts_processing_duration")
            .description("Time taken to process consistency alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"consistency-alerts", "data-consistency-alerts", "data-integrity-alerts"},
        groupId = "consistency-monitoring-group",
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
    @CircuitBreaker(name = "consistency-alerts", fallbackMethod = "handleConsistencyAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleConsistencyAlertEvent(
            @Payload ConsistencyAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("consistency-%s-p%d-o%d", event.getDataSource(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDataSource(), event.getInconsistencyType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing consistency alert: dataSource={}, inconsistencyType={}, severity={}, affectedRecords={}",
                event.getDataSource(), event.getInconsistencyType(), event.getSeverity(), event.getAffectedRecords());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getInconsistencyType()) {
                case DATA_MISMATCH:
                    handleDataMismatch(event, correlationId);
                    break;

                case REFERENTIAL_INTEGRITY_VIOLATION:
                    handleReferentialIntegrityViolation(event, correlationId);
                    break;

                case DUPLICATE_RECORDS:
                    handleDuplicateRecords(event, correlationId);
                    break;

                case ORPHANED_RECORDS:
                    handleOrphanedRecords(event, correlationId);
                    break;

                case CHECKSUM_MISMATCH:
                    handleChecksumMismatch(event, correlationId);
                    break;

                case CROSS_SYSTEM_INCONSISTENCY:
                    handleCrossSystemInconsistency(event, correlationId);
                    break;

                case TEMPORAL_INCONSISTENCY:
                    handleTemporalInconsistency(event, correlationId);
                    break;

                case SCHEMA_DRIFT:
                    handleSchemaDrift(event, correlationId);
                    break;

                case CONSISTENCY_RESTORED:
                    handleConsistencyRestored(event, correlationId);
                    break;

                default:
                    log.warn("Unknown consistency alert type: {}", event.getInconsistencyType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logInfrastructureEvent("CONSISTENCY_ALERT_PROCESSED", event.getDataSource(),
                Map.of("inconsistencyType", event.getInconsistencyType(), "severity", event.getSeverity(),
                    "affectedRecords", event.getAffectedRecords(), "detectionTime", event.getDetectionTime(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process consistency alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("consistency-alerts-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleConsistencyAlertEventFallback(
            ConsistencyAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("consistency-fallback-%s-p%d-o%d", event.getDataSource(), partition, offset);

        log.error("Circuit breaker fallback triggered for consistency alert: dataSource={}, error={}",
            event.getDataSource(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("consistency-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Consistency Alert Circuit Breaker Triggered",
                String.format("Data consistency alert processing failed for %s: %s", event.getDataSource(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltConsistencyAlertEvent(
            @Payload ConsistencyAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-consistency-%s-%d", event.getDataSource(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Consistency alert permanently failed: dataSource={}, topic={}, error={}",
            event.getDataSource(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logInfrastructureEvent("CONSISTENCY_ALERT_DLT_EVENT", event.getDataSource(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "inconsistencyType", event.getInconsistencyType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Consistency Alert Dead Letter Event",
                String.format("Data consistency alert for %s sent to DLT: %s", event.getDataSource(), exceptionMessage),
                Map.of("dataSource", event.getDataSource(), "topic", topic, "correlationId", correlationId)
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

    private void handleDataMismatch(ConsistencyAlertEvent event, String correlationId) {
        log.error("Data mismatch detected: dataSource={}, affectedRecords={}, mismatchDetails={}",
            event.getDataSource(), event.getAffectedRecords(), event.getInconsistencyDetails());

        consistencyService.analyzeDataMismatch(event.getDataSource(), event.getAffectedRecords(), event.getInconsistencyDetails());

        alertingService.sendHighPriorityAlert(
            "Data Mismatch Detected",
            String.format("Data mismatch in %s affecting %d records: %s",
                event.getDataSource(), event.getAffectedRecords(), event.getInconsistencyDetails()),
            correlationId
        );

        // Trigger data reconciliation
        consistencyService.triggerDataReconciliation(event.getDataSource(), event.getAffectedRecords());

        metricsService.recordDataMismatch(event.getDataSource(), event.getAffectedRecords());

        // Send to data quality monitoring
        kafkaTemplate.send("data-quality-events", Map.of(
            "eventType", "DATA_MISMATCH",
            "dataSource", event.getDataSource(),
            "affectedRecords", event.getAffectedRecords(),
            "severity", event.getSeverity(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleReferentialIntegrityViolation(ConsistencyAlertEvent event, String correlationId) {
        log.error("Referential integrity violation: dataSource={}, affectedRecords={}, violationType={}",
            event.getDataSource(), event.getAffectedRecords(), event.getViolationType());

        consistencyService.handleReferentialIntegrityViolation(
            event.getDataSource(), event.getAffectedRecords(), event.getViolationType());

        alertingService.sendCriticalAlert(
            "Referential Integrity Violation",
            String.format("Referential integrity violation in %s: %s (%d affected records)",
                event.getDataSource(), event.getViolationType(), event.getAffectedRecords()),
            correlationId
        );

        // Trigger integrity repair
        consistencyService.triggerIntegrityRepair(event.getDataSource(), event.getViolationType());

        metricsService.recordIntegrityViolation(event.getDataSource(), event.getViolationType());
    }

    private void handleDuplicateRecords(ConsistencyAlertEvent event, String correlationId) {
        log.warn("Duplicate records detected: dataSource={}, duplicateCount={}, duplicateField={}",
            event.getDataSource(), event.getDuplicateCount(), event.getDuplicateField());

        consistencyService.handleDuplicateRecords(event.getDataSource(), event.getDuplicateCount(), event.getDuplicateField());

        alertingService.sendMediumPriorityAlert(
            "Duplicate Records Detected",
            String.format("Found %d duplicate records in %s (field: %s)",
                event.getDuplicateCount(), event.getDataSource(), event.getDuplicateField()),
            correlationId
        );

        // Trigger deduplication process
        consistencyService.triggerDeduplication(event.getDataSource(), event.getDuplicateField());

        metricsService.recordDuplicateRecords(event.getDataSource(), event.getDuplicateCount());
    }

    private void handleOrphanedRecords(ConsistencyAlertEvent event, String correlationId) {
        log.warn("Orphaned records detected: dataSource={}, orphanedCount={}, parentTable={}",
            event.getDataSource(), event.getOrphanedCount(), event.getParentTable());

        consistencyService.handleOrphanedRecords(event.getDataSource(), event.getOrphanedCount(), event.getParentTable());

        alertingService.sendMediumPriorityAlert(
            "Orphaned Records Detected",
            String.format("Found %d orphaned records in %s (parent: %s)",
                event.getOrphanedCount(), event.getDataSource(), event.getParentTable()),
            correlationId
        );

        // Trigger orphan cleanup
        consistencyService.triggerOrphanCleanup(event.getDataSource(), event.getParentTable());

        metricsService.recordOrphanedRecords(event.getDataSource(), event.getOrphanedCount());
    }

    private void handleChecksumMismatch(ConsistencyAlertEvent event, String correlationId) {
        log.error("Checksum mismatch detected: dataSource={}, expectedChecksum={}, actualChecksum={}",
            event.getDataSource(), event.getExpectedChecksum(), event.getActualChecksum());

        consistencyService.handleChecksumMismatch(
            event.getDataSource(), event.getExpectedChecksum(), event.getActualChecksum());

        alertingService.sendCriticalAlert(
            "Data Checksum Mismatch",
            String.format("Checksum mismatch in %s: expected %s, got %s",
                event.getDataSource(), event.getExpectedChecksum(), event.getActualChecksum()),
            correlationId
        );

        // Trigger data integrity verification
        consistencyService.triggerIntegrityVerification(event.getDataSource());

        metricsService.recordChecksumMismatch(event.getDataSource());
    }

    private void handleCrossSystemInconsistency(ConsistencyAlertEvent event, String correlationId) {
        log.error("Cross-system inconsistency detected: primarySystem={}, secondarySystem={}, inconsistencyType={}",
            event.getPrimarySystem(), event.getSecondarySystem(), event.getInconsistencyType());

        consistencyService.handleCrossSystemInconsistency(
            event.getPrimarySystem(), event.getSecondarySystem(), event.getInconsistencyType());

        alertingService.sendHighPriorityAlert(
            "Cross-System Inconsistency",
            String.format("Inconsistency between %s and %s: %s",
                event.getPrimarySystem(), event.getSecondarySystem(), event.getInconsistencyType()),
            correlationId
        );

        // Trigger cross-system synchronization
        consistencyService.triggerCrossSystemSync(event.getPrimarySystem(), event.getSecondarySystem());

        metricsService.recordCrossSystemInconsistency(event.getPrimarySystem(), event.getSecondarySystem());

        // Send to integration monitoring
        kafkaTemplate.send("integration-monitoring", Map.of(
            "eventType", "CROSS_SYSTEM_INCONSISTENCY",
            "primarySystem", event.getPrimarySystem(),
            "secondarySystem", event.getSecondarySystem(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleTemporalInconsistency(ConsistencyAlertEvent event, String correlationId) {
        log.warn("Temporal inconsistency detected: dataSource={}, timeField={}, inconsistencyDetails={}",
            event.getDataSource(), event.getTimeField(), event.getInconsistencyDetails());

        consistencyService.handleTemporalInconsistency(
            event.getDataSource(), event.getTimeField(), event.getInconsistencyDetails());

        alertingService.sendMediumPriorityAlert(
            "Temporal Data Inconsistency",
            String.format("Temporal inconsistency in %s (field: %s): %s",
                event.getDataSource(), event.getTimeField(), event.getInconsistencyDetails()),
            correlationId
        );

        // Trigger temporal validation
        consistencyService.triggerTemporalValidation(event.getDataSource(), event.getTimeField());

        metricsService.recordTemporalInconsistency(event.getDataSource());
    }

    private void handleSchemaDrift(ConsistencyAlertEvent event, String correlationId) {
        log.warn("Schema drift detected: dataSource={}, schemaChanges={}, impactLevel={}",
            event.getDataSource(), event.getSchemaChanges(), event.getImpactLevel());

        consistencyService.handleSchemaDrift(event.getDataSource(), event.getSchemaChanges(), event.getImpactLevel());

        alertingService.sendMediumPriorityAlert(
            "Schema Drift Detected",
            String.format("Schema drift in %s: %s (impact: %s)",
                event.getDataSource(), event.getSchemaChanges(), event.getImpactLevel()),
            correlationId
        );

        // Trigger schema validation
        consistencyService.triggerSchemaValidation(event.getDataSource());

        metricsService.recordSchemaDrift(event.getDataSource(), event.getImpactLevel());
    }

    private void handleConsistencyRestored(ConsistencyAlertEvent event, String correlationId) {
        log.info("Data consistency restored: dataSource={}, resolutionMethod={}, restoredRecords={}",
            event.getDataSource(), event.getResolutionMethod(), event.getRestoredRecords());

        consistencyService.handleConsistencyRestored(
            event.getDataSource(), event.getResolutionMethod(), event.getRestoredRecords());

        alertingService.sendInfoAlert(
            "Data Consistency Restored",
            String.format("Consistency restored in %s using %s (%d records)",
                event.getDataSource(), event.getResolutionMethod(), event.getRestoredRecords()),
            correlationId
        );

        metricsService.recordConsistencyRestored(event.getDataSource(), event.getRestoredRecords());

        // Clear any ongoing consistency incidents
        consistencyService.clearConsistencyIncidents(event.getDataSource());
    }
}