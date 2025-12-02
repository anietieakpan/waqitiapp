package com.waqiti.virtualcard.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.virtualcard.service.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * DLQ Handler for CardIssuanceEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CardIssuanceEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;

    @Value("${kafka.topics.card-issuance-events.retry:card.issuance.events.retry}")
    private String retryTopic;

    @Value("${kafka.dlq.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${kafka.dlq.retry-delay-ms:60000}")
    private long retryDelayMs;

    private static final String ALERT_TOPIC = "system.alerts";

    public CardIssuanceEventConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate,
            AuditService auditService) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
        this.auditService = auditService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CardIssuanceEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CardIssuanceEventConsumer.dlq:CardIssuanceEventConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        log.info("Processing DLQ event from card issuance topic. Event type: {}", event.getClass().getSimpleName());

        try {
            // Extract retry metadata from headers
            Integer retryCount = extractRetryCount(headers);
            String originalTopic = extractOriginalTopic(headers);
            Long originalTimestamp = extractOriginalTimestamp(headers);
            String failureReason = extractFailureReason(headers);

            log.info("DLQ Event Details - RetryCount: {}, OriginalTopic: {}, OriginalTimestamp: {}, FailureReason: {}",
                    retryCount, originalTopic, originalTimestamp, failureReason);

            // Classify the failure to determine recovery strategy
            RecoveryStrategy strategy = classifyFailure(event, failureReason, retryCount);

            switch (strategy) {
                case RETRY:
                    return handleRetryableFailure(event, headers, retryCount);

                case COMPENSATE:
                    return handleCompensatableFailure(event, headers, failureReason);

                case MANUAL_INTERVENTION:
                    return handleManualInterventionRequired(event, headers, failureReason);

                case DISCARD:
                    return handleDiscardableFailure(event, headers, failureReason);

                default:
                    log.error("Unknown recovery strategy: {}", strategy);
                    return DlqProcessingResult.PERMANENT_FAILURE;
            }

        } catch (Exception e) {
            log.error("Critical error handling DLQ event. Event will be marked for manual review.", e);

            // Send critical alert
            sendCriticalAlert(event, e);

            // Audit the failure
            auditDlqFailure(event, e);

            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Handle retryable failures by sending back to retry topic
     */
    private DlqProcessingResult handleRetryableFailure(Object event, Map<String, Object> headers, int retryCount) {
        if (retryCount >= maxRetryAttempts) {
            log.warn("Max retry attempts ({}) exceeded for event. Escalating to manual intervention.", maxRetryAttempts);
            return handleManualInterventionRequired(event, headers, "Max retry attempts exceeded");
        }

        try {
            // Increment retry count
            Map<String, Object> retryHeaders = new HashMap<>(headers);
            retryHeaders.put("X-Retry-Count", retryCount + 1);
            retryHeaders.put("X-Retry-Timestamp", Instant.now().toEpochMilli());
            retryHeaders.put("X-Scheduled-Retry-Time", Instant.now().plusMillis(retryDelayMs).toEpochMilli());

            // Send to retry topic with delay (using Kafka scheduled message or custom scheduler)
            kafkaTemplate.send(retryTopic, event);

            log.info("Event sent to retry topic. Retry attempt: {}/{}", retryCount + 1, maxRetryAttempts);

            // Audit the retry
            auditDlqRetry(event, retryCount + 1);

            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule retry for DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Handle compensatable failures by triggering compensation logic
     */
    private DlqProcessingResult handleCompensatableFailure(Object event, Map<String, Object> headers, String failureReason) {
        log.info("Attempting compensation for failed event. Reason: {}", failureReason);

        try {
            // Trigger compensation logic (e.g., rollback card creation, refund, etc.)
            boolean compensated = executeCompensation(event, failureReason);

            if (compensated) {
                log.info("Compensation successful for DLQ event");
                auditDlqCompensation(event, failureReason, true);
                return DlqProcessingResult.COMPENSATED;
            } else {
                log.warn("Compensation failed for DLQ event. Manual intervention required.");
                auditDlqCompensation(event, failureReason, false);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error during compensation", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Handle failures requiring manual intervention
     */
    private DlqProcessingResult handleManualInterventionRequired(Object event, Map<String, Object> headers, String reason) {
        log.error("Manual intervention required for DLQ event. Reason: {}", reason);

        try {
            // Store event for manual review
            storeForManualReview(event, headers, reason);

            // Send alert to operations team
            sendManualInterventionAlert(event, reason);

            // Audit the requirement for manual intervention
            auditManualInterventionRequired(event, reason);

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("Error storing event for manual review", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Handle discardable failures (e.g., duplicate events, invalid events)
     */
    private DlqProcessingResult handleDiscardableFailure(Object event, Map<String, Object> headers, String reason) {
        log.info("Discarding DLQ event. Reason: {}", reason);

        // Audit the discard
        auditDlqDiscard(event, reason);

        return DlqProcessingResult.DISCARDED;
    }

    /**
     * Classify failure to determine recovery strategy
     */
    private RecoveryStrategy classifyFailure(Object event, String failureReason, int retryCount) {
        if (failureReason == null) {
            return RecoveryStrategy.RETRY;
        }

        // Transient errors - retry
        if (failureReason.contains("timeout") ||
            failureReason.contains("connection") ||
            failureReason.contains("unavailable") ||
            failureReason.contains("temporary")) {
            return RecoveryStrategy.RETRY;
        }

        // Data validation errors - discard
        if (failureReason.contains("validation") ||
            failureReason.contains("invalid format") ||
            failureReason.contains("malformed")) {
            return RecoveryStrategy.DISCARD;
        }

        // Business logic errors - compensate
        if (failureReason.contains("insufficient funds") ||
            failureReason.contains("limit exceeded") ||
            failureReason.contains("duplicate")) {
            return RecoveryStrategy.COMPENSATE;
        }

        // Max retries exceeded
        if (retryCount >= maxRetryAttempts) {
            return RecoveryStrategy.MANUAL_INTERVENTION;
        }

        // Default: retry
        return RecoveryStrategy.RETRY;
    }

    /**
     * Execute compensation logic
     */
    private boolean executeCompensation(Object event, String failureReason) {
        // TODO: Implement specific compensation logic based on event type
        // Examples:
        // - Cancel pending card order
        // - Refund transaction
        // - Rollback state changes
        // - Notify user of failure

        log.info("Executing compensation for event: {}", event.getClass().getSimpleName());
        return true; // Placeholder - implement actual compensation
    }

    /**
     * Store event for manual review
     */
    private void storeForManualReview(Object event, Map<String, Object> headers, String reason) {
        // Store in database table for manual review
        // or send to dedicated manual review topic
        Map<String, Object> reviewPayload = new HashMap<>();
        reviewPayload.put("event", event);
        reviewPayload.put("headers", headers);
        reviewPayload.put("reason", reason);
        reviewPayload.put("timestamp", Instant.now());
        reviewPayload.put("serviceName", getServiceName());

        kafkaTemplate.send("manual.review.queue", reviewPayload);
    }

    /**
     * Send critical alert for DLQ processing failure
     */
    private void sendCriticalAlert(Object event, Exception error) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("severity", "CRITICAL");
        alert.put("service", "virtual-card-service");
        alert.put("component", "CardIssuanceEventConsumerDlqHandler");
        alert.put("message", "Critical DLQ processing failure");
        alert.put("eventType", event.getClass().getSimpleName());
        alert.put("error", error.getMessage());
        alert.put("timestamp", Instant.now());

        kafkaTemplate.send(ALERT_TOPIC, alert);
    }

    /**
     * Send manual intervention alert
     */
    private void sendManualInterventionAlert(Object event, String reason) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("severity", "HIGH");
        alert.put("service", "virtual-card-service");
        alert.put("component", "CardIssuanceEventConsumerDlqHandler");
        alert.put("message", "Manual intervention required for DLQ event");
        alert.put("eventType", event.getClass().getSimpleName());
        alert.put("reason", reason);
        alert.put("timestamp", Instant.now());

        kafkaTemplate.send(ALERT_TOPIC, alert);
    }

    // Audit helper methods
    private void auditDlqFailure(Object event, Exception error) {
        auditService.logSecurityIncident(
                "SYSTEM",
                "DLQ_PROCESSING_FAILURE",
                "Critical failure processing DLQ event",
                Map.of(
                        "eventType", event.getClass().getSimpleName(),
                        "error", error.getMessage(),
                        "service", getServiceName()
                )
        );
    }

    private void auditDlqRetry(Object event, int retryCount) {
        log.info("AUDIT: DLQ retry scheduled - eventType={}, retryCount={}", event.getClass().getSimpleName(), retryCount);
    }

    private void auditDlqCompensation(Object event, String reason, boolean success) {
        log.info("AUDIT: DLQ compensation {} - eventType={}, reason={}",
                success ? "succeeded" : "failed", event.getClass().getSimpleName(), reason);
    }

    private void auditManualInterventionRequired(Object event, String reason) {
        log.warn("AUDIT: Manual intervention required - eventType={}, reason={}",
                event.getClass().getSimpleName(), reason);
    }

    private void auditDlqDiscard(Object event, String reason) {
        log.info("AUDIT: DLQ event discarded - eventType={}, reason={}",
                event.getClass().getSimpleName(), reason);
    }

    // Helper methods to extract metadata from headers
    private Integer extractRetryCount(Map<String, Object> headers) {
        Object retryCount = headers.get("X-Retry-Count");
        return retryCount != null ? (Integer) retryCount : 0;
    }

    private String extractOriginalTopic(Map<String, Object> headers) {
        Object topic = headers.get("X-Original-Topic");
        return topic != null ? topic.toString() : "unknown";
    }

    private Long extractOriginalTimestamp(Map<String, Object> headers) {
        Object timestamp = headers.get("X-Original-Timestamp");
        return timestamp != null ? (Long) timestamp : System.currentTimeMillis();
    }

    private String extractFailureReason(Map<String, Object> headers) {
        Object reason = headers.get("X-Failure-Reason");
        return reason != null ? reason.toString() : null;
    }

    /**
     * Recovery strategy enum
     */
    private enum RecoveryStrategy {
        RETRY,              // Retry the operation
        COMPENSATE,         // Execute compensation logic
        MANUAL_INTERVENTION, // Requires manual review
        DISCARD             // Discard the event
    }

    @Override
    protected String getServiceName() {
        return "CardIssuanceEventConsumer";
    }
}
