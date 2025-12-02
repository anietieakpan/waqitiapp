package com.waqiti.payment.kafka;

import com.waqiti.common.events.BackupStatusEvent;
import com.waqiti.payment.domain.BackupStatus;
import com.waqiti.payment.repository.BackupStatusRepository;
import com.waqiti.payment.service.BackupService;
import com.waqiti.payment.service.PaymentDataService;
import com.waqiti.payment.metrics.PaymentMetricsService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class BackupStatusEventsConsumer {

    private final BackupStatusRepository backupStatusRepository;
    private final BackupService backupService;
    private final PaymentDataService paymentDataService;
    private final PaymentMetricsService metricsService;
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
        successCounter = Counter.builder("backup_status_events_processed_total")
            .description("Total number of successfully processed backup status events")
            .register(meterRegistry);
        errorCounter = Counter.builder("backup_status_events_errors_total")
            .description("Total number of backup status event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("backup_status_events_processing_duration")
            .description("Time taken to process backup status events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"backup-status-events", "payment-backup-status", "data-backup-events"},
        groupId = "backup-status-events-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "backup-status-events", fallbackMethod = "handleBackupStatusEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBackupStatusEvent(
            @Payload BackupStatusEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("backup-%s-p%d-o%d", event.getBackupId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getBackupId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing backup status event: backupId={}, status={}, type={}",
                event.getBackupId(), event.getBackupStatus(), event.getBackupType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case BACKUP_STARTED:
                    processBackupStarted(event, correlationId);
                    break;

                case BACKUP_COMPLETED:
                    processBackupCompleted(event, correlationId);
                    break;

                case BACKUP_FAILED:
                    processBackupFailed(event, correlationId);
                    break;

                case BACKUP_PROGRESS_UPDATE:
                    processBackupProgressUpdate(event, correlationId);
                    break;

                case BACKUP_VALIDATION_COMPLETED:
                    processBackupValidationCompleted(event, correlationId);
                    break;

                case BACKUP_VALIDATION_FAILED:
                    processBackupValidationFailed(event, correlationId);
                    break;

                case BACKUP_RETENTION_EXPIRED:
                    processBackupRetentionExpired(event, correlationId);
                    break;

                case BACKUP_RESTORE_INITIATED:
                    processBackupRestoreInitiated(event, correlationId);
                    break;

                case BACKUP_RESTORE_COMPLETED:
                    processBackupRestoreCompleted(event, correlationId);
                    break;

                case BACKUP_RESTORE_FAILED:
                    processBackupRestoreFailed(event, correlationId);
                    break;

                default:
                    log.warn("Unknown backup status event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BACKUP_STATUS_EVENT_PROCESSED", event.getBackupId(),
                Map.of("eventType", event.getEventType(), "backupStatus", event.getBackupStatus(),
                    "backupType", event.getBackupType(), "dataSize", event.getDataSize(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process backup status event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("backup-status-events-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBackupStatusEventFallback(
            BackupStatusEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("backup-fallback-%s-p%d-o%d", event.getBackupId(), partition, offset);

        log.error("Circuit breaker fallback triggered for backup status: backupId={}, error={}",
            event.getBackupId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("backup-status-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Backup Status Circuit Breaker Triggered",
                String.format("Backup %s status processing failed: %s", event.getBackupId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBackupStatusEvent(
            @Payload BackupStatusEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-backup-%s-%d", event.getBackupId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Backup status permanently failed: backupId={}, topic={}, error={}",
            event.getBackupId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("BACKUP_STATUS_DLT_EVENT", event.getBackupId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Backup Status Dead Letter Event",
                String.format("Backup %s status sent to DLT: %s", event.getBackupId(), exceptionMessage),
                Map.of("backupId", event.getBackupId(), "topic", topic, "correlationId", correlationId)
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

    private void processBackupStarted(BackupStatusEvent event, String correlationId) {
        // Create backup status record
        BackupStatus backupStatus = BackupStatus.builder()
            .backupId(event.getBackupId())
            .backupType(event.getBackupType())
            .status("STARTED")
            .startTime(LocalDateTime.now())
            .expectedCompletionTime(event.getExpectedCompletionTime())
            .dataSize(event.getDataSize())
            .correlationId(correlationId)
            .build();

        backupStatusRepository.save(backupStatus);

        // Start monitoring backup progress
        backupService.startBackupMonitoring(event.getBackupId(), correlationId);

        // Record metrics
        metricsService.recordBackupStarted(event.getBackupType(), event.getDataSize());

        // Send notification for critical payment data backups
        if (event.isCriticalData()) {
            notificationService.sendOperationalAlert(
                "Critical Payment Data Backup Started",
                String.format("Backup %s started for critical payment data: %s",
                    event.getBackupId(), event.getBackupType()),
                "INFO"
            );
        }

        log.info("Backup started: backupId={}, type={}, size={}",
            event.getBackupId(), event.getBackupType(), event.getDataSize());
    }

    private void processBackupCompleted(BackupStatusEvent event, String correlationId) {
        // Update backup status
        BackupStatus backupStatus = backupStatusRepository.findByBackupId(event.getBackupId())
            .orElseThrow(() -> new RuntimeException("Backup status not found"));

        backupStatus.setStatus("COMPLETED");
        backupStatus.setCompletionTime(LocalDateTime.now());
        backupStatus.setActualDataSize(event.getActualDataSize());
        backupStatus.setChecksum(event.getChecksum());
        backupStatus.setStorageLocation(event.getStorageLocation());
        backupStatusRepository.save(backupStatus);

        // Validate backup integrity
        backupService.validateBackupIntegrity(event.getBackupId(), event.getChecksum(), correlationId);

        // Update backup catalog
        backupService.updateBackupCatalog(
            event.getBackupId(),
            event.getBackupType(),
            event.getStorageLocation(),
            event.getActualDataSize(),
            correlationId
        );

        // Record metrics
        metricsService.recordBackupCompleted(event.getBackupType(), event.getActualDataSize(),
            event.getDurationMinutes());

        // Send notification for successful completion
        notificationService.sendOperationalAlert(
            "Payment Data Backup Completed",
            String.format("Backup %s completed successfully: %s (%d MB)",
                event.getBackupId(), event.getBackupType(), event.getActualDataSize() / 1024 / 1024),
            "INFO"
        );

        log.info("Backup completed: backupId={}, type={}, size={}, duration={}min",
            event.getBackupId(), event.getBackupType(), event.getActualDataSize(), event.getDurationMinutes());
    }

    private void processBackupFailed(BackupStatusEvent event, String correlationId) {
        // Update backup status
        BackupStatus backupStatus = backupStatusRepository.findByBackupId(event.getBackupId())
            .orElseThrow(() -> new RuntimeException("Backup status not found"));

        backupStatus.setStatus("FAILED");
        backupStatus.setFailureTime(LocalDateTime.now());
        backupStatus.setFailureReason(event.getFailureReason());
        backupStatus.setErrorDetails(event.getErrorDetails());
        backupStatusRepository.save(backupStatus);

        // Record failure metrics
        metricsService.recordBackupFailed(event.getBackupType(), event.getFailureReason());

        // Check if retry is appropriate
        boolean shouldRetry = backupService.shouldRetryBackup(
            event.getBackupId(),
            event.getFailureReason(),
            event.getRetryCount()
        );

        if (shouldRetry) {
            // Schedule backup retry
            backupService.scheduleBackupRetry(
                event.getBackupId(),
                event.getBackupType(),
                event.getRetryCount() + 1,
                correlationId
            );
        } else {
            // Send critical alert for backup failure
            notificationService.sendCriticalAlert(
                "Payment Data Backup Failed",
                String.format("Backup %s failed permanently: %s", event.getBackupId(), event.getFailureReason()),
                Map.of("backupId", event.getBackupId(), "backupType", event.getBackupType(),
                    "correlationId", correlationId)
            );
        }

        log.error("Backup failed: backupId={}, type={}, reason={}, retry={}",
            event.getBackupId(), event.getBackupType(), event.getFailureReason(), shouldRetry);
    }

    private void processBackupProgressUpdate(BackupStatusEvent event, String correlationId) {
        // Update backup progress
        backupService.updateBackupProgress(
            event.getBackupId(),
            event.getProgressPercentage(),
            event.getProcessedDataSize(),
            correlationId
        );

        // Check for backup performance issues
        if (event.getProgressPercentage() > 50 && event.getDurationMinutes() > event.getExpectedDurationMinutes() * 1.5) {
            // Backup is taking longer than expected
            notificationService.sendOperationalAlert(
                "Backup Performance Warning",
                String.format("Backup %s is taking longer than expected: %d%% complete in %d minutes",
                    event.getBackupId(), event.getProgressPercentage(), event.getDurationMinutes()),
                "MEDIUM"
            );
        }

        log.debug("Backup progress update: backupId={}, progress={}%, processed={}",
            event.getBackupId(), event.getProgressPercentage(), event.getProcessedDataSize());
    }

    private void processBackupValidationCompleted(BackupStatusEvent event, String correlationId) {
        // Update backup validation status
        backupService.updateBackupValidationStatus(
            event.getBackupId(),
            "VALIDATION_COMPLETED",
            event.getValidationResults(),
            correlationId
        );

        // Check validation results
        if (event.getValidationResults().isValid()) {
            // Mark backup as verified
            backupService.markBackupAsVerified(event.getBackupId(), correlationId);

            // Record successful validation
            metricsService.recordBackupValidationSuccess(event.getBackupType());
        } else {
            // Handle validation failure
            processBackupValidationFailed(event, correlationId);
        }

        log.info("Backup validation completed: backupId={}, valid={}",
            event.getBackupId(), event.getValidationResults().isValid());
    }

    private void processBackupValidationFailed(BackupStatusEvent event, String correlationId) {
        // Update backup validation status
        backupService.updateBackupValidationStatus(
            event.getBackupId(),
            "VALIDATION_FAILED",
            event.getValidationResults(),
            correlationId
        );

        // Record validation failure
        metricsService.recordBackupValidationFailure(event.getBackupType());

        // Mark backup as invalid
        backupService.markBackupAsInvalid(
            event.getBackupId(),
            event.getValidationResults().getFailureReason(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Backup Validation Failed",
            String.format("Backup %s validation failed: %s",
                event.getBackupId(), event.getValidationResults().getFailureReason()),
            Map.of("backupId", event.getBackupId(), "backupType", event.getBackupType(),
                "correlationId", correlationId)
        );

        // Schedule backup recreation if appropriate
        boolean shouldRecreate = backupService.shouldRecreateBackup(
            event.getBackupId(),
            event.getValidationResults().getFailureReason()
        );

        if (shouldRecreate) {
            backupService.scheduleBackupRecreation(event.getBackupId(), event.getBackupType(), correlationId);
        }

        log.error("Backup validation failed: backupId={}, reason={}, recreate={}",
            event.getBackupId(), event.getValidationResults().getFailureReason(), shouldRecreate);
    }

    private void processBackupRetentionExpired(BackupStatusEvent event, String correlationId) {
        // Update backup retention status
        backupService.markBackupRetentionExpired(event.getBackupId(), correlationId);

        // Check if backup should be archived or deleted
        boolean shouldArchive = backupService.shouldArchiveExpiredBackup(
            event.getBackupId(),
            event.getBackupType()
        );

        if (shouldArchive) {
            // Archive expired backup
            backupService.archiveExpiredBackup(event.getBackupId(), correlationId);
        } else {
            // Schedule backup deletion
            backupService.scheduleBackupDeletion(event.getBackupId(), correlationId);
        }

        // Record retention metrics
        metricsService.recordBackupRetentionExpired(event.getBackupType());

        log.info("Backup retention expired: backupId={}, archive={}", event.getBackupId(), shouldArchive);
    }

    private void processBackupRestoreInitiated(BackupStatusEvent event, String correlationId) {
        // Record restore initiation
        backupService.recordRestoreInitiation(
            event.getBackupId(),
            event.getRestoreType(),
            event.getTargetEnvironment(),
            correlationId
        );

        // Validate restore prerequisites
        backupService.validateRestorePrerequisites(
            event.getBackupId(),
            event.getTargetEnvironment(),
            correlationId
        );

        // Start restore monitoring
        backupService.startRestoreMonitoring(event.getBackupId(), correlationId);

        // Record metrics
        metricsService.recordRestoreInitiated(event.getBackupType(), event.getRestoreType());

        // Send notification for critical restore operations
        if (event.getTargetEnvironment().equals("PRODUCTION")) {
            notificationService.sendCriticalAlert(
                "Production Payment Data Restore Initiated",
                String.format("Production restore initiated from backup %s", event.getBackupId()),
                Map.of("backupId", event.getBackupId(), "targetEnvironment", event.getTargetEnvironment(),
                    "correlationId", correlationId)
            );
        }

        log.info("Backup restore initiated: backupId={}, target={}, type={}",
            event.getBackupId(), event.getTargetEnvironment(), event.getRestoreType());
    }

    private void processBackupRestoreCompleted(BackupStatusEvent event, String correlationId) {
        // Update restore status
        backupService.updateRestoreStatus(
            event.getBackupId(),
            "RESTORE_COMPLETED",
            event.getRestoredDataSize(),
            correlationId
        );

        // Validate restored data integrity
        backupService.validateRestoredDataIntegrity(
            event.getBackupId(),
            event.getTargetEnvironment(),
            correlationId
        );

        // Record metrics
        metricsService.recordRestoreCompleted(event.getBackupType(), event.getRestoredDataSize(),
            event.getRestoreDurationMinutes());

        // Send notification
        notificationService.sendOperationalAlert(
            "Payment Data Restore Completed",
            String.format("Restore completed from backup %s to %s: %d MB in %d minutes",
                event.getBackupId(), event.getTargetEnvironment(),
                event.getRestoredDataSize() / 1024 / 1024, event.getRestoreDurationMinutes()),
            "INFO"
        );

        log.info("Backup restore completed: backupId={}, target={}, size={}, duration={}min",
            event.getBackupId(), event.getTargetEnvironment(), event.getRestoredDataSize(),
            event.getRestoreDurationMinutes());
    }

    private void processBackupRestoreFailed(BackupStatusEvent event, String correlationId) {
        // Update restore status
        backupService.updateRestoreStatus(
            event.getBackupId(),
            "RESTORE_FAILED",
            0L,
            correlationId
        );

        // Record restore failure
        backupService.recordRestoreFailure(
            event.getBackupId(),
            event.getFailureReason(),
            event.getErrorDetails(),
            correlationId
        );

        // Record metrics
        metricsService.recordRestoreFailed(event.getBackupType(), event.getFailureReason());

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Payment Data Restore Failed",
            String.format("Restore from backup %s failed: %s", event.getBackupId(), event.getFailureReason()),
            Map.of("backupId", event.getBackupId(), "targetEnvironment", event.getTargetEnvironment(),
                "correlationId", correlationId)
        );

        log.error("Backup restore failed: backupId={}, target={}, reason={}",
            event.getBackupId(), event.getTargetEnvironment(), event.getFailureReason());
    }
}