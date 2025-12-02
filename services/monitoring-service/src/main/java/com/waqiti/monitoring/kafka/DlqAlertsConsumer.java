package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.DlqAlertEvent;
import com.waqiti.monitoring.model.DlqAlert;
import com.waqiti.monitoring.service.DlqMonitoringService;
import com.waqiti.monitoring.service.IncidentManagementService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.MetricsCollectionService;
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
public class DlqAlertsConsumer {

    private final DlqMonitoringService dlqMonitoringService;
    private final IncidentManagementService incidentManagementService;
    private final AlertingService alertingService;
    private final MetricsCollectionService metricsCollectionService;
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
        successCounter = Counter.builder("dlq_alerts_processed_total")
            .description("Total number of successfully processed DLQ alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("dlq_alerts_errors_total")
            .description("Total number of DLQ alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("dlq_alerts_processing_duration")
            .description("Time taken to process DLQ alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"dlq-alerts"},
        groupId = "dlq-alerts-monitoring-service-group",
        containerFactory = "criticalMonitoringKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 2000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dlq-alerts", fallbackMethod = "handleDlqAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 1000))
    public void handleDlqAlertEvent(
            @Payload DlqAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("dlq-alert-%s-p%d-o%d", event.getAlertId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAlertId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing DLQ alert: alertId={}, eventType={}, dlqTopic={}, severity={}",
                event.getAlertId(), event.getEventType(), event.getDlqTopic(), event.getSeverity());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case DLQ_THRESHOLD_EXCEEDED:
                    processDlqThresholdExceeded(event, correlationId);
                    break;

                case DLQ_CRITICAL_VOLUME:
                    processDlqCriticalVolume(event, correlationId);
                    break;

                case DLQ_PATTERN_DETECTED:
                    processDlqPatternDetected(event, correlationId);
                    break;

                case DLQ_SERVICE_DEGRADATION:
                    processDlqServiceDegradation(event, correlationId);
                    break;

                case DLQ_RECOVERY_INITIATED:
                    processDlqRecoveryInitiated(event, correlationId);
                    break;

                case DLQ_RECOVERY_COMPLETED:
                    processDlqRecoveryCompleted(event, correlationId);
                    break;

                case DLQ_MONITORING_FAILURE:
                    processDlqMonitoringFailure(event, correlationId);
                    break;

                default:
                    log.warn("Unknown DLQ alert event type: {}", event.getEventType());
                    processUnknownDlqAlertEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logMonitoringEvent("DLQ_ALERT_EVENT_PROCESSED", event.getAlertId(),
                Map.of("eventType", event.getEventType(), "dlqTopic", event.getDlqTopic(),
                    "severity", event.getSeverity(), "messageCount", event.getMessageCount(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process DLQ alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("dlq-alerts-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 2));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleDlqAlertEventFallback(
            DlqAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("dlq-alert-fallback-%s-p%d-o%d", event.getAlertId(), partition, offset);

        log.error("Circuit breaker fallback triggered for DLQ alert: alertId={}, error={}",
            event.getAlertId(), ex.getMessage());

        // Create critical incident
        incidentManagementService.createCriticalIncident(
            "DLQ_ALERT_CIRCUIT_BREAKER",
            String.format("DLQ alert circuit breaker triggered for alert %s", event.getAlertId()),
            "CRITICAL",
            Map.of("alertId", event.getAlertId(), "eventType", event.getEventType(),
                "dlqTopic", event.getDlqTopic(), "severity", event.getSeverity(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to emergency monitoring queue
        kafkaTemplate.send("dlq-alerts-emergency", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "DLQ Alert Circuit Breaker",
                String.format("DLQ alert processing failed for topic %s: %s",
                    event.getDlqTopic(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltDlqAlertEvent(
            @Payload DlqAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-dlq-alert-%s-%d", event.getAlertId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - DLQ alert permanently failed: alertId={}, topic={}, error={}",
            event.getAlertId(), topic, exceptionMessage);

        // Create emergency incident
        incidentManagementService.createEmergencyIncident(
            "DLQ_ALERT_DLT_EVENT",
            String.format("EMERGENCY: DLQ alert sent to DLT for alert %s", event.getAlertId()),
            Map.of("alertId", event.getAlertId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "dlqTopic", event.getDlqTopic(),
                "correlationId", correlationId, "requiresImmediateAction", true)
        );

        // Save to emergency audit log
        auditService.logEmergencyMonitoringEvent("DLQ_ALERT_DLT_EVENT", event.getAlertId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresEmergencyIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "DLQ Alert Dead Letter Event",
                String.format("EMERGENCY: DLQ alert for topic %s sent to DLT: %s",
                    event.getDlqTopic(), exceptionMessage),
                Map.of("alertId", event.getAlertId(), "dlqTopic", event.getDlqTopic(),
                    "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void processDlqThresholdExceeded(DlqAlertEvent event, String correlationId) {
        // Create DLQ alert record
        DlqAlert alert = DlqAlert.builder()
            .alertId(event.getAlertId())
            .dlqTopic(event.getDlqTopic())
            .sourceTopic(event.getSourceTopic())
            .messageCount(event.getMessageCount())
            .threshold(event.getThreshold())
            .severity(event.getSeverity())
            .alertType("THRESHOLD_EXCEEDED")
            .detectedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .build();

        dlqMonitoringService.recordDlqAlert(alert);

        // Trigger immediate response based on severity
        if (event.getSeverity().equals("CRITICAL") || event.getSeverity().equals("HIGH")) {
            // Create incident
            String incidentId = incidentManagementService.createIncident(
                "DLQ_THRESHOLD_EXCEEDED",
                String.format("DLQ threshold exceeded for topic %s (%d messages)",
                    event.getDlqTopic(), event.getMessageCount()),
                event.getSeverity(),
                Map.of("dlqTopic", event.getDlqTopic(), "messageCount", event.getMessageCount(),
                    "threshold", event.getThreshold(), "correlationId", correlationId)
            );

            // Auto-assign to on-call engineer
            incidentManagementService.autoAssignToOnCallEngineer(incidentId, event.getDlqTopic());
        }

        // Start DLQ analysis
        dlqMonitoringService.startDlqAnalysis(event.getDlqTopic(), event.getMessageCount());

        // Send alert notification
        alertingService.sendDlqAlert(
            "DLQ Threshold Exceeded",
            String.format("DLQ topic %s has exceeded threshold: %d/%d messages",
                event.getDlqTopic(), event.getMessageCount(), event.getThreshold()),
            event.getSeverity()
        );

        // Update metrics
        metricsCollectionService.recordDlqThresholdExceeded(event.getDlqTopic(), event.getMessageCount());

        log.warn("DLQ threshold exceeded: topic={}, count={}, threshold={}",
            event.getDlqTopic(), event.getMessageCount(), event.getThreshold());
    }

    private void processDlqCriticalVolume(DlqAlertEvent event, String correlationId) {
        // Record critical volume alert
        dlqMonitoringService.recordCriticalVolumeAlert(event.getDlqTopic(), event.getMessageCount(),
            event.getVolumeRate());

        // Create critical incident
        String incidentId = incidentManagementService.createCriticalIncident(
            "DLQ_CRITICAL_VOLUME",
            String.format("CRITICAL: DLQ topic %s experiencing critical volume: %d messages",
                event.getDlqTopic(), event.getMessageCount()),
            "CRITICAL",
            Map.of("dlqTopic", event.getDlqTopic(), "messageCount", event.getMessageCount(),
                "volumeRate", event.getVolumeRate(), "correlationId", correlationId)
        );

        // Trigger emergency response
        dlqMonitoringService.triggerEmergencyResponse(event.getDlqTopic(), event.getMessageCount());

        // Implement circuit breaker if needed
        if (event.getMessageCount() > 10000) {
            dlqMonitoringService.activateCircuitBreaker(event.getSourceTopic(),
                "CRITICAL_DLQ_VOLUME");
        }

        // Send critical alert
        notificationService.sendCriticalAlert(
            "CRITICAL DLQ Volume",
            String.format("CRITICAL: DLQ topic %s has critical message volume: %d",
                event.getDlqTopic(), event.getMessageCount()),
            "CRITICAL"
        );

        // Update metrics
        metricsCollectionService.recordDlqCriticalVolume(event.getDlqTopic(), event.getMessageCount());

        log.error("CRITICAL DLQ volume: topic={}, count={}, rate={}",
            event.getDlqTopic(), event.getMessageCount(), event.getVolumeRate());
    }

    private void processDlqPatternDetected(DlqAlertEvent event, String correlationId) {
        // Analyze DLQ pattern
        dlqMonitoringService.analyzeDlqPattern(event.getDlqTopic(), event.getPatternType(),
            event.getPatternDetails());

        // Create incident for pattern investigation
        String incidentId = incidentManagementService.createIncident(
            "DLQ_PATTERN_DETECTED",
            String.format("DLQ pattern detected for topic %s: %s",
                event.getDlqTopic(), event.getPatternType()),
            "HIGH",
            Map.of("dlqTopic", event.getDlqTopic(), "patternType", event.getPatternType(),
                "patternDetails", event.getPatternDetails(), "correlationId", correlationId)
        );

        // Trigger pattern-specific response
        switch (event.getPatternType()) {
            case "ERROR_BURST":
                dlqMonitoringService.handleErrorBurstPattern(event.getDlqTopic(), event.getPatternDetails());
                break;
            case "RECURRING_FAILURE":
                dlqMonitoringService.handleRecurringFailurePattern(event.getDlqTopic(), event.getPatternDetails());
                break;
            case "SERVICE_DEGRADATION":
                dlqMonitoringService.handleServiceDegradationPattern(event.getDlqTopic(), event.getPatternDetails());
                break;
            default:
                dlqMonitoringService.handleUnknownPattern(event.getDlqTopic(), event.getPatternType());
                break;
        }

        // Send pattern alert
        alertingService.sendPatternAlert(
            "DLQ Pattern Detected",
            String.format("Pattern detected in DLQ topic %s: %s",
                event.getDlqTopic(), event.getPatternType()),
            "HIGH"
        );

        // Update metrics
        metricsCollectionService.recordDlqPatternDetected(event.getDlqTopic(), event.getPatternType());

        log.warn("DLQ pattern detected: topic={}, pattern={}, details={}",
            event.getDlqTopic(), event.getPatternType(), event.getPatternDetails());
    }

    private void processDlqServiceDegradation(DlqAlertEvent event, String correlationId) {
        // Record service degradation
        dlqMonitoringService.recordServiceDegradation(event.getSourceService(),
            event.getDegradationMetrics());

        // Create high priority incident
        String incidentId = incidentManagementService.createIncident(
            "DLQ_SERVICE_DEGRADATION",
            String.format("Service degradation detected via DLQ monitoring: %s",
                event.getSourceService()),
            "HIGH",
            Map.of("sourceService", event.getSourceService(),
                "degradationMetrics", event.getDegradationMetrics(),
                "dlqTopic", event.getDlqTopic(), "correlationId", correlationId)
        );

        // Trigger service health check
        dlqMonitoringService.triggerServiceHealthCheck(event.getSourceService());

        // Implement load balancing adjustments
        dlqMonitoringService.adjustLoadBalancing(event.getSourceService(),
            event.getDegradationMetrics());

        // Send degradation alert
        alertingService.sendServiceDegradationAlert(
            "Service Degradation Detected",
            String.format("Service %s degradation detected via DLQ analysis",
                event.getSourceService()),
            "HIGH"
        );

        // Update metrics
        metricsCollectionService.recordServiceDegradation(event.getSourceService(),
            event.getDegradationMetrics());

        log.warn("Service degradation detected via DLQ: service={}, metrics={}",
            event.getSourceService(), event.getDegradationMetrics());
    }

    private void processDlqRecoveryInitiated(DlqAlertEvent event, String correlationId) {
        // Update DLQ alert status
        dlqMonitoringService.updateAlertStatus(event.getAlertId(), "RECOVERY_INITIATED");

        // Start recovery monitoring
        dlqMonitoringService.startRecoveryMonitoring(event.getDlqTopic(), event.getRecoveryStrategy());

        // Track recovery progress
        dlqMonitoringService.trackRecoveryProgress(event.getDlqTopic(), event.getRecoveryMetrics());

        // Send recovery notification
        alertingService.sendRecoveryNotification(
            "DLQ Recovery Initiated",
            String.format("Recovery initiated for DLQ topic %s using strategy: %s",
                event.getDlqTopic(), event.getRecoveryStrategy()),
            "MEDIUM"
        );

        // Update metrics
        metricsCollectionService.recordDlqRecoveryInitiated(event.getDlqTopic(), event.getRecoveryStrategy());

        log.info("DLQ recovery initiated: topic={}, strategy={}", event.getDlqTopic(), event.getRecoveryStrategy());
    }

    private void processDlqRecoveryCompleted(DlqAlertEvent event, String correlationId) {
        // Update DLQ alert status
        dlqMonitoringService.updateAlertStatus(event.getAlertId(), "RECOVERED");

        // Record recovery completion
        dlqMonitoringService.recordRecoveryCompletion(event.getDlqTopic(),
            event.getRecoveryDuration(), event.getRecoveredMessageCount());

        // Close related incidents
        incidentManagementService.closeRelatedIncidents(event.getDlqTopic(), "DLQ_RECOVERED");

        // Generate recovery report
        String reportId = dlqMonitoringService.generateRecoveryReport(event.getDlqTopic(),
            event.getRecoveryMetrics(), event.getRecoveryDuration());

        // Send recovery completion notification
        alertingService.sendRecoveryCompletionNotification(
            "DLQ Recovery Completed",
            String.format("Recovery completed for DLQ topic %s. Duration: %d minutes, Messages recovered: %d",
                event.getDlqTopic(), event.getRecoveryDuration(), event.getRecoveredMessageCount()),
            "LOW"
        );

        // Update metrics
        metricsCollectionService.recordDlqRecoveryCompleted(event.getDlqTopic(),
            event.getRecoveryDuration(), event.getRecoveredMessageCount());

        log.info("DLQ recovery completed: topic={}, duration={}min, recovered={}",
            event.getDlqTopic(), event.getRecoveryDuration(), event.getRecoveredMessageCount());
    }

    private void processDlqMonitoringFailure(DlqAlertEvent event, String correlationId) {
        // Create critical incident for monitoring failure
        String incidentId = incidentManagementService.createCriticalIncident(
            "DLQ_MONITORING_FAILURE",
            String.format("DLQ monitoring failure for topic %s", event.getDlqTopic()),
            "CRITICAL",
            Map.of("dlqTopic", event.getDlqTopic(), "failureReason", event.getFailureReason(),
                "correlationId", correlationId)
        );

        // Activate backup monitoring
        dlqMonitoringService.activateBackupMonitoring(event.getDlqTopic());

        // Restart monitoring services
        dlqMonitoringService.restartMonitoringServices(event.getDlqTopic());

        // Send critical alert
        notificationService.sendCriticalAlert(
            "DLQ Monitoring Failure",
            String.format("CRITICAL: DLQ monitoring failed for topic %s: %s",
                event.getDlqTopic(), event.getFailureReason()),
            "CRITICAL"
        );

        // Update metrics
        metricsCollectionService.recordDlqMonitoringFailure(event.getDlqTopic(), event.getFailureReason());

        log.error("DLQ monitoring failure: topic={}, reason={}", event.getDlqTopic(), event.getFailureReason());
    }

    private void processUnknownDlqAlertEvent(DlqAlertEvent event, String correlationId) {
        // Create incident for unknown event type
        incidentManagementService.createIncident(
            "UNKNOWN_DLQ_ALERT_EVENT",
            String.format("Unknown DLQ alert event type %s for topic %s",
                event.getEventType(), event.getDlqTopic()),
            "MEDIUM",
            Map.of("dlqTopic", event.getDlqTopic(), "unknownEventType", event.getEventType(),
                "correlationId", correlationId)
        );

        log.warn("Unknown DLQ alert event: topic={}, eventType={}",
            event.getDlqTopic(), event.getEventType());
    }
}