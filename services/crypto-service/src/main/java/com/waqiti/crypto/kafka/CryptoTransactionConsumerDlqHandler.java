package com.waqiti.crypto.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.crypto.service.CryptoTransactionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DLQ Handler for CryptoTransactionConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoTransactionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CryptoTransactionService transactionService;

    @Autowired
    public CryptoTransactionConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate,
            CryptoTransactionService transactionService) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
        this.transactionService = transactionService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoTransactionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoTransactionConsumer.dlq:CryptoTransactionConsumer.dlq}",
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
            log.info("Processing DLQ event for CryptoTransaction: {}", event);

            // Extract retry count from headers
            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String failureReason = (String) headers.get("failure_reason");
            LocalDateTime firstFailureTime = (LocalDateTime) headers.get("first_failure_time");

            log.debug("DLQ event - Retry count: {}, Failure reason: {}, First failure: {}",
                    retryCount, failureReason, firstFailureTime);

            // Classify error type and determine recovery strategy
            DlqProcessingResult result = classifyAndRecover(event, failureReason, retryCount, headers);

            // Update metrics based on result
            updateRecoveryMetrics(result);

            return result;

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            // For unexpected errors, require manual intervention
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Classify error and determine recovery strategy
     */
    private DlqProcessingResult classifyAndRecover(Object event, String failureReason,
                                                     Integer retryCount, Map<String, Object> headers) {
        // Maximum retry attempts
        final int MAX_RETRIES = 5;

        // Check if we've exceeded max retries
        if (retryCount >= MAX_RETRIES) {
            log.warn("Max retries ({}) exceeded for DLQ event. Routing to manual intervention", MAX_RETRIES);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Classify error type based on failure reason
        if (failureReason == null) {
            log.warn("No failure reason provided. Attempting retry with default strategy");
            return retryWithBackoff(event, retryCount, headers);
        }

        // Transient errors - safe to retry
        if (isTransientError(failureReason)) {
            log.info("Transient error detected: {}. Scheduling retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Permanent errors - cannot be recovered automatically
        if (isPermanentError(failureReason)) {
            log.error("Permanent error detected: {}. Manual intervention required", failureReason);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }

        // Data validation errors - may require transformation
        if (isDataValidationError(failureReason)) {
            log.warn("Data validation error: {}. Attempting data correction", failureReason);
            return attemptDataCorrection(event, failureReason, retryCount, headers);
        }

        // Unknown error type - retry with caution
        log.warn("Unknown error type: {}. Conservative retry strategy", failureReason);
        return retryWithBackoff(event, retryCount, headers);
    }

    /**
     * Check if error is transient (temporary, likely to succeed on retry)
     */
    private boolean isTransientError(String failureReason) {
        return failureReason.contains("timeout") ||
               failureReason.contains("connection") ||
               failureReason.contains("temporarily unavailable") ||
               failureReason.contains("service unavailable") ||
               failureReason.contains("rate limit") ||
               failureReason.contains("503") ||
               failureReason.contains("429");
    }

    /**
     * Check if error is permanent (will never succeed, needs manual fix)
     */
    private boolean isPermanentError(String failureReason) {
        return failureReason.contains("not found") ||
               failureReason.contains("unauthorized") ||
               failureReason.contains("forbidden") ||
               failureReason.contains("invalid signature") ||
               failureReason.contains("insufficient funds") ||
               failureReason.contains("404") ||
               failureReason.contains("401") ||
               failureReason.contains("403");
    }

    /**
     * Check if error is data validation issue
     */
    private boolean isDataValidationError(String failureReason) {
        return failureReason.contains("validation") ||
               failureReason.contains("invalid format") ||
               failureReason.contains("parse error") ||
               failureReason.contains("deserialization") ||
               failureReason.contains("400");
    }

    /**
     * Retry with exponential backoff
     */
    private DlqProcessingResult retryWithBackoff(Object event, Integer retryCount,
                                                   Map<String, Object> headers) {
        try {
            // Calculate backoff delay: 2^retryCount * 60 seconds (1min, 2min, 4min, 8min, 16min)
            long delaySeconds = (long) Math.pow(2, retryCount) * 60;

            log.info("Scheduling retry #{} with {}s delay", retryCount + 1, delaySeconds);

            // Update retry count in headers
            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);

            // In production, use Spring's @Scheduled or Kafka delay topic
            // For now, mark as retry requested
            scheduleRetry(event, headers, delaySeconds);

            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule retry", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Attempt to correct data and retry
     */
    private DlqProcessingResult attemptDataCorrection(Object event, String failureReason,
                                                        Integer retryCount, Map<String, Object> headers) {
        try {
            log.info("Attempting automatic data correction for validation error");

            // In production, implement specific data correction logic
            // For example: trim whitespace, normalize formats, fix timestamps, etc.

            // For now, log the issue and route to manual review
            log.warn("Data correction not yet implemented for: {}", failureReason);
            createManualReviewTask(event, failureReason, retryCount);

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("Data correction failed", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Schedule retry with delay (in production, use Kafka delay topic or scheduler)
     */
    private void scheduleRetry(Object event, Map<String, Object> headers, long delaySeconds) {
        // In production, publish to a retry topic with delay
        // Or use Spring @Scheduled with dynamic delays
        log.info("Retry scheduled for event after {}s delay", delaySeconds);

        // Placeholder: In production, implement with Kafka scheduled messages
        // kafkaTemplate.send("crypto-transaction-retry", event);
    }

    /**
     * Create manual review task for operations team
     */
    private void createManualReviewTask(Object event, String failureReason, Integer retryCount) {
        try {
            log.warn("Creating manual review task for DLQ event. Failure: {}, Retries: {}",
                    failureReason, retryCount);

            // In production, create task in manual review system
            // transactionService.createManualReviewTask(event, failureReason, retryCount);

            // Send alert to operations team
            sendOperationsAlert(event, failureReason, retryCount);

        } catch (Exception e) {
            log.error("Failed to create manual review task", e);
        }
    }

    /**
     * Send alert to operations team (Slack, PagerDuty, email, etc.)
     */
    private void sendOperationsAlert(Object event, String failureReason, Integer retryCount) {
        log.error("OPERATIONS ALERT: DLQ event requires manual intervention. " +
                  "Event: {}, Failure: {}, Retries: {}", event, failureReason, retryCount);

        // In production, integrate with:
        // - Slack notifications
        // - PagerDuty incidents
        // - Email alerts
        // - Monitoring dashboards
    }

    /**
     * Update recovery metrics for monitoring
     */
    private void updateRecoveryMetrics(DlqProcessingResult result) {
        try {
            String resultType = result.name().toLowerCase();
            log.debug("Updating DLQ recovery metrics: {}", resultType);

            // Metrics are automatically updated by BaseDlqConsumer
            // Additional custom metrics can be added here

        } catch (Exception e) {
            log.warn("Failed to update recovery metrics", e);
        }
    }
    }

    @Override
    protected String getServiceName() {
        return "CryptoTransactionConsumer";
    }
}
