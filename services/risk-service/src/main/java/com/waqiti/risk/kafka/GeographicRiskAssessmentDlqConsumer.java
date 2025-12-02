package com.waqiti.risk.kafka;

import com.waqiti.common.events.GeographicRiskAssessmentEvent;
import com.waqiti.risk.domain.DeadLetterEvent;
import com.waqiti.risk.repository.DeadLetterEventRepository;
import com.waqiti.risk.service.GeographicRiskService;
import com.waqiti.risk.service.RiskMetricsService;
import com.waqiti.risk.service.DeadLetterProcessingService;
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
public class GeographicRiskAssessmentDlqConsumer {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final GeographicRiskService geographicRiskService;
    private final RiskMetricsService metricsService;
    private final DeadLetterProcessingService deadLetterProcessingService;
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
    private Counter dlqEventCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("geographic_risk_assessment_dlq_processed_total")
            .description("Total number of successfully processed geographic risk assessment DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("geographic_risk_assessment_dlq_errors_total")
            .description("Total number of geographic risk assessment DLQ processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("geographic_risk_assessment_dlq_processing_duration")
            .description("Time taken to process geographic risk assessment DLQ events")
            .register(meterRegistry);
        dlqEventCounter = Counter.builder("geographic_risk_assessment_dlq_events_total")
            .description("Total number of geographic risk assessment events sent to DLQ")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"geographic-risk-assessment.dlq", "geographic-risk-assessment-dlq"},
        groupId = "geographic-risk-assessment-dlq-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "3", // Reduced attempts for DLQ processing
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "geographic-risk-assessment-dlq", fallbackMethod = "handleGeographicRiskAssessmentDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000))
    public void handleGeographicRiskAssessmentDlqEvent(
            @Payload Object event, // Use Object to handle various event types in DLQ
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String originalTopic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("geo-risk-dlq-p%d-o%d-%d", partition, offset, System.currentTimeMillis());
        String eventKey = String.format("dlq-%d-%d-%d", partition, offset, System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing geographic risk assessment DLQ event: topic={}, partition={}, offset={}, error={}",
                originalTopic, partition, offset, exceptionMessage);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Extract entity ID if possible
            String entityId = extractEntityId(event);

            // Process the DLQ event
            processDlqEvent(event, entityId, originalTopic, exceptionMessage, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("GEOGRAPHIC_RISK_ASSESSMENT_DLQ_PROCESSED", entityId,
                Map.of("originalTopic", originalTopic, "exceptionMessage", exceptionMessage,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            dlqEventCounter.increment();
            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process geographic risk assessment DLQ event: {}", e.getMessage(), e);

            // Send to final fallback for manual intervention
            kafkaTemplate.send("manual-intervention-queue", Map.of(
                "originalEvent", event, "dlqError", e.getMessage(),
                "originalException", exceptionMessage, "originalTopic", originalTopic,
                "correlationId", correlationId, "timestamp", Instant.now(),
                "requiresManualProcessing", true));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleGeographicRiskAssessmentDlqEventFallback(
            Object event,
            int partition,
            long offset,
            String exceptionMessage,
            String originalTopic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("geo-risk-dlq-fallback-p%d-o%d-%d", partition, offset, System.currentTimeMillis());

        log.error("Circuit breaker fallback triggered for geographic risk assessment DLQ: topic={}, error={}",
            originalTopic, ex.getMessage());

        // Send to manual intervention queue
        kafkaTemplate.send("manual-intervention-queue", Map.of(
            "originalEvent", event,
            "dlqError", ex.getMessage(),
            "originalException", exceptionMessage,
            "originalTopic", originalTopic,
            "errorType", "DLQ_CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now(),
            "requiresImmediateAttention", true));

        // Send critical alert to operations team
        try {
            notificationService.sendCriticalAlert(
                "Geographic Risk Assessment DLQ Circuit Breaker Triggered",
                String.format("CRITICAL: DLQ processing failed for topic %s: %s", originalTopic, ex.getMessage()),
                Map.of("originalTopic", originalTopic, "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical DLQ alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleFinalDltGeographicRiskAssessmentEvent(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("final-dlt-geo-risk-%d", System.currentTimeMillis());

        log.error("FINAL DLT - Geographic risk assessment DLQ processing permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        // This is the final stop - save for manual investigation
        auditService.logRiskEvent("GEOGRAPHIC_RISK_ASSESSMENT_FINAL_DLT", "SYSTEM",
            Map.of("finalDltTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresCriticalIntervention", true,
                "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Geographic Risk Assessment Final DLT Event",
                String.format("EMERGENCY: Geographic risk assessment DLQ processing permanently failed: %s", exceptionMessage),
                Map.of("topic", topic, "correlationId", correlationId, "severity", "EMERGENCY")
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency final DLT alert: {}", ex.getMessage());
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

    private String extractEntityId(Object event) {
        try {
            // Try to extract entity ID from the event using reflection
            if (event instanceof Map) {
                Map<?, ?> eventMap = (Map<?, ?>) event;
                Object entityId = eventMap.get("entityId");
                return entityId != null ? entityId.toString() : "UNKNOWN";
            }

            // Try to call getEntityId() method if it exists
            try {
                var method = event.getClass().getMethod("getEntityId");
                Object entityId = method.invoke(event);
                return entityId != null ? entityId.toString() : "UNKNOWN";
            } catch (Exception ignored) {
                // Fallback approaches
            }

            return "UNKNOWN";
        } catch (Exception e) {
            log.warn("Failed to extract entity ID from DLQ event: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    private void processDlqEvent(Object event, String entityId, String originalTopic, String exceptionMessage, String correlationId) {
        // Store the dead letter event for manual processing
        DeadLetterEvent deadLetterEvent = DeadLetterEvent.builder()
            .entityId(entityId)
            .originalTopic(originalTopic)
            .errorMessage(exceptionMessage)
            .eventPayload(convertEventToString(event))
            .status("PENDING_MANUAL_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .eventType("GEOGRAPHIC_RISK_ASSESSMENT")
            .severity(determineDlqSeverity(originalTopic, exceptionMessage))
            .retryCount(0)
            .maxRetries(3)
            .build();

        deadLetterEventRepository.save(deadLetterEvent);

        // Attempt automated recovery based on error type
        attemptAutomatedRecovery(deadLetterEvent, event, exceptionMessage, correlationId);

        // Send notifications based on severity
        sendDlqNotifications(deadLetterEvent, correlationId);

        // Update risk metrics
        metricsService.recordDlqEvent("GEOGRAPHIC_RISK_ASSESSMENT", deadLetterEvent.getSeverity());

        log.error("Dead letter event stored for manual review: entityId={}, topic={}, correlationId={}",
            entityId, originalTopic, correlationId);
    }

    private String convertEventToString(Object event) {
        try {
            if (event instanceof String) {
                return (String) event;
            } else if (event instanceof Map) {
                return event.toString();
            } else {
                // Use reflection to convert object to string representation
                return event.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to convert event to string: {}", e.getMessage());
            return "FAILED_TO_SERIALIZE: " + e.getMessage();
        }
    }

    private String determineDlqSeverity(String originalTopic, String exceptionMessage) {
        if (exceptionMessage == null) {
            return "MEDIUM";
        }

        String lowerError = exceptionMessage.toLowerCase();

        // Critical errors that require immediate attention
        if (lowerError.contains("sanctions") || lowerError.contains("compliance") ||
            lowerError.contains("security") || lowerError.contains("fraud")) {
            return "CRITICAL";
        }

        // High priority errors
        if (lowerError.contains("timeout") || lowerError.contains("connection") ||
            lowerError.contains("serialization") || lowerError.contains("data")) {
            return "HIGH";
        }

        // Medium priority errors
        if (lowerError.contains("validation") || lowerError.contains("format") ||
            lowerError.contains("parsing")) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private void attemptAutomatedRecovery(DeadLetterEvent deadLetterEvent, Object originalEvent,
                                        String exceptionMessage, String correlationId) {
        try {
            boolean recoveryAttempted = deadLetterProcessingService.attemptRecovery(
                deadLetterEvent.getId(), originalEvent, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automated recovery attempted for DLQ event: correlationId={}", correlationId);

                // Update the dead letter event status
                deadLetterEvent.setStatus("RECOVERY_ATTEMPTED");
                deadLetterEvent.setLastAttemptAt(LocalDateTime.now());
                deadLetterEvent.setRetryCount(deadLetterEvent.getRetryCount() + 1);
                deadLetterEventRepository.save(deadLetterEvent);

                // Send notification about recovery attempt
                kafkaTemplate.send("dlq-recovery-events", Map.of(
                    "deadLetterEventId", deadLetterEvent.getId(),
                    "entityId", deadLetterEvent.getEntityId(),
                    "recoveryStatus", "ATTEMPTED",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        } catch (Exception e) {
            log.error("Failed to attempt automated recovery for DLQ event: {}", e.getMessage());

            // Update status to indicate recovery failure
            deadLetterEvent.setStatus("RECOVERY_FAILED");
            deadLetterEvent.setLastAttemptAt(LocalDateTime.now());
            deadLetterEvent.setRecoveryError(e.getMessage());
            deadLetterEventRepository.save(deadLetterEvent);
        }
    }

    private void sendDlqNotifications(DeadLetterEvent deadLetterEvent, String correlationId) {
        try {
            switch (deadLetterEvent.getSeverity()) {
                case "CRITICAL":
                    notificationService.sendCriticalAlert(
                        "Critical Geographic Risk Assessment DLQ Event",
                        String.format("CRITICAL: Geographic risk assessment failed for entity %s and requires immediate manual intervention",
                            deadLetterEvent.getEntityId()),
                        Map.of("entityId", deadLetterEvent.getEntityId(),
                               "deadLetterEventId", deadLetterEvent.getId(),
                               "correlationId", correlationId)
                    );
                    // Also send to on-call team
                    notificationService.sendPagerDutyAlert(
                        "Geographic Risk Assessment Critical DLQ",
                        String.format("Entity: %s, Error: %s",
                            deadLetterEvent.getEntityId(), deadLetterEvent.getErrorMessage()),
                        correlationId
                    );
                    break;

                case "HIGH":
                    notificationService.sendHighPriorityNotification("risk-ops-team",
                        "High Priority Geographic Risk Assessment DLQ Event",
                        String.format("High priority DLQ event for entity %s requires attention within 4 hours",
                            deadLetterEvent.getEntityId()),
                        correlationId);
                    break;

                case "MEDIUM":
                    notificationService.sendNotification("risk-support-team",
                        "Geographic Risk Assessment DLQ Event",
                        String.format("DLQ event for entity %s requires review within 24 hours",
                            deadLetterEvent.getEntityId()),
                        correlationId);
                    break;

                default:
                    // Low priority - just log for batch processing
                    log.info("Low priority DLQ event logged for batch processing: entityId={}, correlationId={}",
                        deadLetterEvent.getEntityId(), correlationId);
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to send DLQ notification: {}", e.getMessage());
        }
    }
}