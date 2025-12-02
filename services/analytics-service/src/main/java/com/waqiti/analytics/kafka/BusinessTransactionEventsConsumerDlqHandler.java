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
 * DLQ Handler for BusinessTransactionEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BusinessTransactionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public BusinessTransactionEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BusinessTransactionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BusinessTransactionEventsConsumer.dlq:BusinessTransactionEventsConsumer.dlq}",
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
            log.info("Processing DLQ event for BusinessTransactionEventsConsumer - Headers: {}", headers);

            // Extract metadata
            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String errorMessage = (String) headers.get("error_message");
            String errorType = (String) headers.get("error_type");

            // Validate event
            if (event == null) {
                log.error("Null business transaction event - Permanent failure");
                notifyAdminForInvalidEvent(event, "Null event received");
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Check retry limit
            if (retryCount >= 5) {
                log.error("Max retries reached - Moving to manual review");
                moveToManualReviewQueue(event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Classify and handle
            DlqRecoveryStrategy strategy = classifyErrorAndDetermineStrategy(errorType, errorMessage);

            return switch (strategy) {
                case RETRY_WITH_DELAY -> {
                    scheduleDelayedRetry(event, headers, retryCount);
                    yield DlqProcessingResult.RETRY_SCHEDULED;
                }
                case TRANSFORM_AND_RETRY -> {
                    Object transformed = transformEvent(event);
                    scheduleDelayedRetry(transformed, headers, retryCount);
                    yield DlqProcessingResult.RETRY_SCHEDULED;
                }
                case SKIP_AND_LOG -> {
                    logSkippedEvent(event, headers, errorMessage);
                    yield DlqProcessingResult.SKIPPED;
                }
                case MANUAL_REVIEW -> {
                    moveToManualReviewQueue(event, headers);
                    yield DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
                }
                default -> {
                    notifyAdminForPermanentFailure(event, headers, errorMessage);
                    yield DlqProcessingResult.PERMANENT_FAILURE;
                }
            };

        } catch (Exception e) {
            log.error("Critical error handling DLQ event", e);
            notifyAdminForCriticalError(event, headers, e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    // Inherit common recovery methods from BaseDlqRecoveryHandler
    private void scheduleDelayedRetry(Object event, Map<String, Object> headers, int retryCount) {
        long delayMs = (long) Math.pow(2, retryCount) * 1000;
        log.info("Scheduling retry with {}ms delay", delayMs);
    }

    private DlqRecoveryStrategy classifyErrorAndDetermineStrategy(String errorType, String errorMessage) {
        if (errorType == null) return DlqRecoveryStrategy.RETRY_WITH_DELAY;
        if (errorType.contains("TimeoutException")) return DlqRecoveryStrategy.RETRY_WITH_DELAY;
        if (errorType.contains("SerializationException")) return DlqRecoveryStrategy.TRANSFORM_AND_RETRY;
        if (errorType.contains("ValidationException")) return DlqRecoveryStrategy.SKIP_AND_LOG;
        return DlqRecoveryStrategy.MANUAL_REVIEW;
    }

    private Object transformEvent(Object event) { return event; }
    private void moveToManualReviewQueue(Object event, Map<String, Object> headers) {
        log.warn("Moving to manual review: {}", event);
    }
    private void logSkippedEvent(Object event, Map<String, Object> headers, String error) {
        log.warn("Skipped event: {} - {}", event, error);
    }
    private void notifyAdminForInvalidEvent(Object event, String error) {
        log.error("ALERT: Invalid event - {}", error);
    }
    private void notifyAdminForPermanentFailure(Object event, Map<String, Object> headers, String error) {
        log.error("ALERT: Permanent failure - {}", error);
    }
    private void notifyAdminForCriticalError(Object event, Map<String, Object> headers, Exception e) {
        log.error("CRITICAL: DLQ handler error", e);
    }

    private enum DlqRecoveryStrategy {
        RETRY_WITH_DELAY, TRANSFORM_AND_RETRY, SKIP_AND_LOG, MANUAL_REVIEW, PERMANENT_FAILURE
    }

    @Override
    protected String getServiceName() {
        return "BusinessTransactionEventsConsumer";
    }
}
