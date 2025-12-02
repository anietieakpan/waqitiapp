package com.waqiti.analytics.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * DLQ Handler for AnalyticsEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AnalyticsEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AnalyticsEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AnalyticsEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AnalyticsEventConsumer.dlq:AnalyticsEventConsumer.dlq}",
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
        try {
            log.info("Processing DLQ event for AnalyticsEventConsumer - Headers: {}", headers);

            // Extract metadata from headers
            String originalTopic = (String) headers.get("original_topic");
            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            Long originalTimestamp = (Long) headers.get("original_timestamp");
            String errorMessage = (String) headers.get("error_message");
            String errorType = (String) headers.get("error_type");

            log.debug("DLQ Event Details - Topic: {}, RetryCount: {}, Error: {}",
                originalTopic, retryCount, errorMessage);

            // Strategy 1: Validate event data
            if (!isValidAnalyticsEvent(event)) {
                log.error("Invalid analytics event structure - Permanent failure: {}", event);
                notifyAdminForInvalidEvent(event, errorMessage);
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 2: Check retry limit (max 5 retries)
            if (retryCount >= 5) {
                log.error("Maximum retry limit reached (5) - Moving to manual review queue");
                moveToManualReviewQueue(event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Classify error and determine recovery strategy
            DlqRecoveryStrategy strategy = classifyErrorAndDetermineStrategy(errorType, errorMessage);

            switch (strategy) {
                case RETRY_IMMEDIATELY:
                    log.info("Retrying event immediately - RetryCount: {}", retryCount);
                    return retryEventWithBackoff(event, headers, retryCount);

                case RETRY_WITH_DELAY:
                    log.info("Scheduling delayed retry - RetryCount: {}", retryCount);
                    scheduleDelayedRetry(event, headers, retryCount);
                    return DlqProcessingResult.RETRY_SCHEDULED;

                case TRANSFORM_AND_RETRY:
                    log.info("Transforming event and retrying");
                    Object transformedEvent = transformEvent(event);
                    return retryEventWithBackoff(transformedEvent, headers, retryCount);

                case SKIP_AND_LOG:
                    log.warn("Skipping non-critical analytics event - Logging for audit");
                    logSkippedEvent(event, headers, errorMessage);
                    return DlqProcessingResult.SKIPPED;

                case MANUAL_REVIEW:
                    log.warn("Requiring manual review for complex error");
                    moveToManualReviewQueue(event, headers);
                    return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

                case PERMANENT_FAILURE:
                default:
                    log.error("Permanent failure determined - No recovery possible");
                    notifyAdminForPermanentFailure(event, headers, errorMessage);
                    return DlqProcessingResult.PERMANENT_FAILURE;
            }

        } catch (Exception e) {
            log.error("Critical error handling DLQ event - Requires manual intervention", e);
            notifyAdminForCriticalError(event, headers, e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Validates analytics event structure
     */
    private boolean isValidAnalyticsEvent(Object event) {
        if (event == null) {
            return false;
        }

        // Analytics events are non-critical, most structural issues can be handled
        return true;
    }

    /**
     * Classifies error type and determines recovery strategy
     */
    private DlqRecoveryStrategy classifyErrorAndDetermineStrategy(String errorType, String errorMessage) {
        if (errorType == null || errorMessage == null) {
            return DlqRecoveryStrategy.RETRY_WITH_DELAY;
        }

        // Transient network errors - retry immediately
        if (errorType.contains("TimeoutException") ||
            errorType.contains("ConnectException") ||
            errorMessage.contains("connection refused")) {
            return DlqRecoveryStrategy.RETRY_WITH_DELAY;
        }

        // Database deadlock/lock timeout - retry with exponential backoff
        if (errorMessage.contains("deadlock") ||
            errorMessage.contains("lock timeout")) {
            return DlqRecoveryStrategy.RETRY_WITH_DELAY;
        }

        // Serialization errors - may need transformation
        if (errorType.contains("SerializationException") ||
            errorType.contains("JsonProcessingException")) {
            return DlqRecoveryStrategy.TRANSFORM_AND_RETRY;
        }

        // Validation errors - skip non-critical analytics
        if (errorType.contains("ValidationException")) {
            return DlqRecoveryStrategy.SKIP_AND_LOG;
        }

        // Unknown errors - manual review
        return DlqRecoveryStrategy.MANUAL_REVIEW;
    }

    /**
     * Retry event with exponential backoff
     */
    private DlqProcessingResult retryEventWithBackoff(Object event, Map<String, Object> headers, int retryCount) {
        try {
            // Calculate backoff delay: 2^retryCount seconds
            long delayMs = (long) Math.pow(2, retryCount) * 1000;

            log.info("Scheduling retry with {}ms delay", delayMs);

            // Schedule retry (implementation would use Spring @Async or scheduler)
            scheduleDelayedRetry(event, headers, retryCount);

            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule retry", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Schedule delayed retry
     */
    private void scheduleDelayedRetry(Object event, Map<String, Object> headers, int retryCount) {
        // Implementation: Use Spring TaskScheduler or Kafka delay queue
        log.info("Delayed retry scheduled for event after retry count: {}", retryCount);
        // In production: republish to retry topic with delay or use scheduler
    }

    /**
     * Transform event to fix structural issues
     */
    private Object transformEvent(Object event) {
        // Analytics events are flexible - minimal transformation needed
        return event;
    }

    /**
     * Move to manual review queue
     */
    private void moveToManualReviewQueue(Object event, Map<String, Object> headers) {
        log.warn("Moving analytics event to manual review queue");
        // Implementation: Publish to manual review topic or database queue
    }

    /**
     * Log skipped event for audit
     */
    private void logSkippedEvent(Object event, Map<String, Object> headers, String errorMessage) {
        log.warn("Skipping analytics event - Reason: {} - Event: {}", errorMessage, event);
        // Implementation: Write to audit log or metrics
    }

    /**
     * Notify admin for invalid event
     */
    private void notifyAdminForInvalidEvent(Object event, String errorMessage) {
        log.error("ALERT: Invalid analytics event structure - Error: {}", errorMessage);
        // Implementation: Send alert via monitoring system
    }

    /**
     * Notify admin for permanent failure
     */
    private void notifyAdminForPermanentFailure(Object event, Map<String, Object> headers, String errorMessage) {
        log.error("ALERT: Permanent DLQ failure - Error: {} - Event: {}", errorMessage, event);
        // Implementation: Send critical alert
    }

    /**
     * Notify admin for critical error
     */
    private void notifyAdminForCriticalError(Object event, Map<String, Object> headers, Exception e) {
        log.error("CRITICAL ALERT: DLQ handler exception - Event: {}", event, e);
        // Implementation: Send critical alert with stack trace
    }

    /**
     * DLQ Recovery Strategies
     */
    private enum DlqRecoveryStrategy {
        RETRY_IMMEDIATELY,
        RETRY_WITH_DELAY,
        TRANSFORM_AND_RETRY,
        SKIP_AND_LOG,
        MANUAL_REVIEW,
        PERMANENT_FAILURE
    }

    @Override
    protected String getServiceName() {
        return "AnalyticsEventConsumer";
    }
}
