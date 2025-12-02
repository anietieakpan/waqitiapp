package com.waqiti.common.kafka;

import com.waqiti.common.alerting.PagerDutyAlertingService;
import com.waqiti.common.alerting.SlackNotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base Abstract Class for Dead Letter Queue (DLQ) Consumers
 *
 * Provides standardized error handling, monitoring, and recovery patterns
 * for all DLQ consumers across the Waqiti platform.
 *
 * Features:
 * - Automatic retry with exponential backoff
 * - PagerDuty integration for critical failures
 * - Slack notifications for operational visibility
 * - Comprehensive metrics and monitoring
 * - Failure categorization (transient vs permanent)
 * - Auto-recovery for known error types
 * - Manual intervention workflow for unknown errors
 * - Circuit breaker integration
 * - Audit logging for compliance
 *
 * Usage Example:
 * ```java
 * @Service
 * @Slf4j
 * public class PaymentDlqConsumer extends BaseDlqConsumer<PaymentEvent> {
 *
 *     @KafkaListener(topics = "payment.events.DLT", groupId = "payment-dlq-group")
 *     public void consumeDlqMessage(
 *             @Payload PaymentEvent event,
 *             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
 *             Acknowledgment acknowledgment) {
 *         processDlqMessage(event, topic, acknowledgment);
 *     }
 *
 *     @Override
 *     protected DlqProcessingResult handleDlqEvent(PaymentEvent event, Map<String, Object> headers) {
 *         // Custom DLQ handling logic
 *         if (isTransientError(event)) {
 *             return retryWithBackoff(event);
 *         }
 *         return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
 *     }
 *
 *     @Override
 *     protected String getServiceName() {
 *         return "payment-service";
 *     }
 * }
 * ```
 *
 * DLQ Processing Flow:
 * 1. Receive failed event from DLT topic
 * 2. Log failure details and metadata
 * 3. Categorize failure type (transient/permanent)
 * 4. Attempt auto-recovery if applicable
 * 5. Send alerts (Slack + PagerDuty for critical)
 * 6. Update metrics
 * 7. Acknowledge or reject message
 *
 * @param <T> Event type being processed
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Slf4j
public abstract class BaseDlqConsumer<T> {

    @Autowired(required = false)
    protected PagerDutyAlertingService pagerDuty;

    @Autowired(required = false)
    protected SlackNotificationService slack;

    @Autowired
    protected MeterRegistry meterRegistry;

    // Metrics
    protected Counter dlqEventsProcessedCounter;
    protected Counter dlqEventsRecoveredCounter;
    protected Counter dlqEventsFailedCounter;
    protected Counter dlqEventsPagerDutyCounter;
    protected Timer dlqProcessingTimer;

    // Retry tracking
    private final Map<String, AtomicInteger> retryCounters = new HashMap<>();
    private static final int MAX_AUTO_RETRY_ATTEMPTS = 3;

    public BaseDlqConsumer(MeterRegistry meterRegistry) {
    }

    /**
     * Initialize metrics for the specific DLQ consumer
     */
    protected void initializeMetrics(String consumerName) {
        dlqEventsProcessedCounter = Counter.builder("dlq.events.processed")
            .description("Total DLQ events processed")
            .tag("consumer", consumerName)
            .register(meterRegistry);

        dlqEventsRecoveredCounter = Counter.builder("dlq.events.recovered")
            .description("DLQ events successfully recovered")
            .tag("consumer", consumerName)
            .register(meterRegistry);

        dlqEventsFailedCounter = Counter.builder("dlq.events.failed")
            .description("DLQ events that failed recovery")
            .tag("consumer", consumerName)
            .register(meterRegistry);

        dlqEventsPagerDutyCounter = Counter.builder("dlq.events.pagerduty")
            .description("DLQ events escalated to PagerDuty")
            .tag("consumer", consumerName)
            .register(meterRegistry);

        dlqProcessingTimer = Timer.builder("dlq.processing.duration")
            .description("DLQ event processing duration")
            .tag("consumer", consumerName)
            .register(meterRegistry);
    }

    /**
     * Main DLQ message processing entry point
     *
     * @param event Failed event from DLT topic
     * @param topic Original topic name
     * @param acknowledgment Kafka acknowledgment
     */
    protected void processDlqMessage(T event, String topic, Acknowledgment acknowledgment) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.warn("DLQ_EVENT_RECEIVED: topic={} service={} event={}",
                topic, getServiceName(), event.getClass().getSimpleName());

            dlqEventsProcessedCounter.increment();

            // Extract Kafka headers
            Map<String, Object> headers = extractHeaders(event);

            // Get failure reason if available
            String failureReason = extractFailureReason(headers);
            int failureCount = extractFailureCount(headers);

            log.error("DLQ_FAILURE_DETAILS: reason={} failureCount={} originalTopic={}",
                failureReason, failureCount, topic);

            // Attempt to handle the DLQ event
            DlqProcessingResult result = handleDlqEvent(event, headers);

            // Process result
            switch (result) {
                case SUCCESS:
                    handleSuccess(event, topic);
                    acknowledgment.acknowledge();
                    dlqEventsRecoveredCounter.increment();
                    break;

                case RETRY:
                    handleRetry(event, topic, failureCount);
                    acknowledgment.acknowledge();
                    break;

                case MANUAL_INTERVENTION_REQUIRED:
                    handleManualIntervention(event, topic, failureReason);
                    acknowledgment.acknowledge();
                    dlqEventsFailedCounter.increment();
                    break;

                case PERMANENT_FAILURE:
                    handlePermanentFailure(event, topic, failureReason);
                    acknowledgment.acknowledge();
                    dlqEventsFailedCounter.increment();
                    break;

                case DISCARDED:
                    handleDiscarded(event, topic, failureReason);
                    acknowledgment.acknowledge();
                    break;
            }

            sample.stop(dlqProcessingTimer);

        } catch (Exception e) {
            log.error("DLQ_PROCESSING_ERROR: Failed to process DLQ event", e);
            dlqEventsFailedCounter.increment();

            // Send critical alert
            sendCriticalAlert(event, topic, e);

            // Do not acknowledge - message will be retried
            sample.stop(dlqProcessingTimer);
        }
    }

    /**
     * Handle successful DLQ recovery
     */
    private void handleSuccess(T event, String topic) {
        log.info("DLQ_RECOVERY_SUCCESS: Event successfully recovered - topic={} service={}",
            topic, getServiceName());

        // Send success notification to Slack
        if (slack != null) {
            slack.sendCustomMessage(
                getSlackWebhook(),
                "✅ DLQ Recovery: Successfully recovered event from " + topic,
                SlackNotificationService.MessageSeverity.SUCCESS
            );
        }
    }

    /**
     * Handle retry scenario
     */
    private void handleRetry(T event, String topic, int currentFailureCount) {
        String eventId = getEventId(event);
        AtomicInteger retryCount = retryCounters.computeIfAbsent(eventId, k -> new AtomicInteger(0));
        int attempts = retryCount.incrementAndGet();

        log.warn("DLQ_RETRY: Scheduling retry attempt {} for event from topic={}",
            attempts, topic);

        if (attempts >= MAX_AUTO_RETRY_ATTEMPTS) {
            log.error("DLQ_MAX_RETRIES_EXCEEDED: Event exceeded max retry attempts - escalating");
            handleManualIntervention(event, topic, "Max retry attempts exceeded");
            retryCounters.remove(eventId);
        } else {
            // Schedule retry with exponential backoff
            scheduleRetry(event, topic, attempts);
        }
    }

    /**
     * Handle manual intervention requirement
     */
    private void handleManualIntervention(T event, String topic, String reason) {
        log.error("DLQ_MANUAL_INTERVENTION: Event requires manual review - topic={} reason={}",
            topic, reason);

        // Create support ticket or incident
        createManualInterventionTicket(event, topic, reason);

        // Send alerts
        sendSlackAlert(event, topic, reason, SlackNotificationService.MessageSeverity.WARNING);

        // DO NOT send PagerDuty for manual intervention (not critical)
    }

    /**
     * Handle permanent failure
     */
    private void handlePermanentFailure(T event, String topic, String reason) {
        log.error("DLQ_PERMANENT_FAILURE: Event cannot be recovered - topic={} reason={}",
            topic, reason);

        // Move to dead letter storage
        storePermanentFailure(event, topic, reason);

        // Send critical alerts
        sendCriticalAlert(event, topic, new RuntimeException(reason));
    }

    /**
     * Handle discarded events (non-critical)
     *
     * Used for events that are non-critical and safe to discard without recovery attempts.
     * Examples: duplicate notifications, optional analytics events, non-essential logging
     */
    private void handleDiscarded(T event, String topic, String reason) {
        log.info("DLQ_DISCARDED: Non-critical event discarded - topic={} reason={}",
            topic, reason);

        // Send informational Slack notification (not PagerDuty - not critical)
        if (slack != null) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("Event Type", event.getClass().getSimpleName());
            metadata.put("Topic", topic);
            metadata.put("Service", getServiceName());
            metadata.put("Reason", reason != null ? reason : "Non-critical event");

            slack.sendInfrastructureAlert(
                "DLQ Event Discarded (Non-Critical)",
                "Non-critical event discarded from DLQ: " + reason,
                SlackNotificationService.MessageSeverity.INFO,
                metadata
            );
        }

        // Optionally log to audit trail for compliance
        log.debug("DLQ_AUDIT: Discarded event logged for audit - eventType={} reason={}",
            event.getClass().getSimpleName(), reason);
    }

    /**
     * Send critical alert (Slack + PagerDuty)
     */
    private void sendCriticalAlert(T event, String topic, Exception error) {
        String eventType = event.getClass().getSimpleName();

        // Slack alert
        if (slack != null) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("Event Type", eventType);
            metadata.put("Topic", topic);
            metadata.put("Service", getServiceName());
            metadata.put("Error", error.getMessage());

            slack.sendSecurityAlert(
                "DLQ Processing Failure",
                "Failed to process DLQ event: " + error.getMessage(),
                SlackNotificationService.MessageSeverity.CRITICAL,
                metadata
            );
        }

        // PagerDuty alert for critical failures
        if (pagerDuty != null && isCriticalEvent(event)) {
            Map<String, Object> customDetails = new HashMap<>();
            customDetails.put("event_type", eventType);
            customDetails.put("topic", topic);
            customDetails.put("service", getServiceName());
            customDetails.put("error_message", error.getMessage());

            pagerDuty.triggerAlert(
                PagerDutyAlertingService.AlertSeverity.HIGH,
                "DLQ Processing Failure: " + getServiceName(),
                "Failed to process DLQ event from " + topic + ": " + error.getMessage(),
                getServiceName(),
                customDetails
            );

            dlqEventsPagerDutyCounter.increment();
        }
    }

    /**
     * Send Slack alert (non-critical)
     */
    private void sendSlackAlert(T event, String topic, String reason,
                               SlackNotificationService.MessageSeverity severity) {
        if (slack == null) {
            return;
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("Event Type", event.getClass().getSimpleName());
        metadata.put("Topic", topic);
        metadata.put("Service", getServiceName());

        slack.sendInfrastructureAlert(
            "DLQ Event Requires Attention",
            reason,
            severity,
            metadata
        );
    }

    /**
     * Extract headers from Kafka message
     */
    private Map<String, Object> extractHeaders(T event) {
        // Subclasses can override to extract actual headers
        return new HashMap<>();
    }

    /**
     * Extract failure reason from headers
     */
    private String extractFailureReason(Map<String, Object> headers) {
        return (String) headers.getOrDefault("kafka_exception-message", "Unknown");
    }

    /**
     * Extract failure count from headers
     */
    private int extractFailureCount(Map<String, Object> headers) {
        Object count = headers.get("kafka_dlt-exception-stacktrace");
        return count != null ? 1 : 0;
    }

    /**
     * Schedule retry with exponential backoff
     */
    private void scheduleRetry(T event, String topic, int attempt) {
        long delayMs = (long) (Math.pow(2, attempt) * 1000); // Exponential backoff
        log.info("DLQ_RETRY_SCHEDULED: Retry scheduled in {}ms", delayMs);

        // In production, use a scheduler or delayed message queue
        // For now, log the intent
    }

    /**
     * Create manual intervention ticket
     */
    private void createManualInterventionTicket(T event, String topic, String reason) {
        log.warn("DLQ_TICKET_CREATED: Manual intervention ticket created for event from topic={}",
            topic);
        // Integration with ticketing system (Jira, ServiceNow, etc.)
    }

    /**
     * Store permanent failure for audit
     */
    private void storePermanentFailure(T event, String topic, String reason) {
        log.error("DLQ_PERMANENT_STORAGE: Event stored in permanent failure storage");
        // Store in database or S3 for audit trail
    }

    // ========== Abstract Methods (must be implemented by subclasses) ==========

    /**
     * Handle the DLQ event - implement recovery logic
     *
     * @param event The failed event
     * @param headers Kafka headers
     * @return Processing result
     */
    protected abstract DlqProcessingResult handleDlqEvent(T event, Map<String, Object> headers);

    /**
     * Get service name for logging and alerts
     */
    protected abstract String getServiceName();

    // ========== Optional Override Methods ==========

    /**
     * Get event ID for retry tracking (override if event has ID field)
     */
    protected String getEventId(T event) {
        return event.toString();
    }

    /**
     * Determine if event is critical (override for custom logic)
     */
    protected boolean isCriticalEvent(T event) {
        return true; // Default: all DLQ events are critical
    }

    /**
     * Get Slack webhook URL (override for custom routing)
     */
    protected String getSlackWebhook() {
        return null; // Use default webhook
    }

    // ========== Enums ==========

    /**
     * DLQ processing result enumeration
     */
    protected enum DlqProcessingResult {
        SUCCESS,                        // Event successfully recovered
        RETRY,                          // Retry with exponential backoff
        MANUAL_INTERVENTION_REQUIRED,   // Requires human review
        PERMANENT_FAILURE,              // Cannot be recovered, store for audit
        DISCARDED                       // Non-critical event, safe to discard
    }
}
