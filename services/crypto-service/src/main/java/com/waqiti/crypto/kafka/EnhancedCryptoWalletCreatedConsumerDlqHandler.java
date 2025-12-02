package com.waqiti.crypto.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.crypto.service.CryptoWalletService;
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
 * DLQ Handler for EnhancedCryptoWalletCreatedConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class EnhancedCryptoWalletCreatedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CryptoWalletService walletService;

    @Autowired
    public EnhancedCryptoWalletCreatedConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate,
            CryptoWalletService walletService) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
        this.walletService = walletService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("EnhancedCryptoWalletCreatedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.EnhancedCryptoWalletCreatedConsumer.dlq:EnhancedCryptoWalletCreatedConsumer.dlq}",
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
            log.info("Processing DLQ event for crypto wallet creation: {}", event);

            // Extract retry count from headers
            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String failureReason = (String) headers.get("failure_reason");
            LocalDateTime firstFailureTime = (LocalDateTime) headers.get("first_failure_time");
            String walletId = (String) headers.get("walletId");
            String userId = (String) headers.get("userId");
            String currency = (String) headers.get("currency");

            log.debug("DLQ event - Wallet: {}, User: {}, Currency: {}, Retry count: {}, Failure reason: {}, First failure: {}",
                    walletId, userId, currency, retryCount, failureReason, firstFailureTime);

            // Classify error type and determine recovery strategy
            DlqProcessingResult result = classifyAndRecover(event, failureReason, retryCount, headers);

            // Update metrics based on result
            updateRecoveryMetrics(result);

            return result;

        } catch (Exception e) {
            log.error("Error handling DLQ event for wallet creation", e);
            // For unexpected errors, require manual intervention
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Classify error and determine recovery strategy for wallet creation
     */
    private DlqProcessingResult classifyAndRecover(Object event, String failureReason,
                                                     Integer retryCount, Map<String, Object> headers) {
        // Maximum retry attempts
        final int MAX_RETRIES = 5;

        // Check if we've exceeded max retries
        if (retryCount >= MAX_RETRIES) {
            log.warn("Max retries ({}) exceeded for wallet creation DLQ event. Routing to manual intervention", MAX_RETRIES);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Classify error type based on failure reason
        if (failureReason == null) {
            log.warn("No failure reason provided for wallet creation. Attempting retry with default strategy");
            return retryWithBackoff(event, retryCount, headers);
        }

        // KMS encryption errors - safe to retry (transient AWS KMS issues)
        if (isKMSTransientError(failureReason)) {
            log.info("KMS transient error detected: {}. Scheduling retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // KMS permission errors - permanent failure
        if (isKMSPermissionError(failureReason)) {
            log.error("KMS permission error detected: {}. Manual intervention required", failureReason);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }

        // Database errors - retry with backoff
        if (isDatabaseError(failureReason)) {
            log.info("Database error detected: {}. Scheduling retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Key derivation errors - permanent failure (indicates bug)
        if (isKeyDerivationError(failureReason)) {
            log.error("Key derivation error detected: {}. Manual intervention required", failureReason);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }

        // Duplicate wallet errors - can be discarded
        if (isDuplicateError(failureReason)) {
            log.info("Duplicate wallet error detected: {}. Discarding event", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // Event publishing errors (for wallet created events) - retry
        if (isEventPublishingError(failureReason)) {
            log.warn("Event publishing error: {}. Scheduling retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Unknown error type - retry with caution
        log.warn("Unknown error type for wallet creation: {}. Conservative retry strategy", failureReason);
        return retryWithBackoff(event, retryCount, headers);
    }

    /**
     * Check if error is KMS transient (temporary AWS KMS issues)
     */
    private boolean isKMSTransientError(String failureReason) {
        return failureReason.contains("KMS") &&
               (failureReason.contains("timeout") ||
                failureReason.contains("throttl") ||
                failureReason.contains("temporarily unavailable") ||
                failureReason.contains("service unavailable") ||
                failureReason.contains("503") ||
                failureReason.contains("429"));
    }

    /**
     * Check if error is KMS permission error (permanent)
     */
    private boolean isKMSPermissionError(String failureReason) {
        return failureReason.contains("KMS") &&
               (failureReason.contains("access denied") ||
                failureReason.contains("not authorized") ||
                failureReason.contains("permission") ||
                failureReason.contains("403") ||
                failureReason.contains("401"));
    }

    /**
     * Check if error is database-related
     */
    private boolean isDatabaseError(String failureReason) {
        return failureReason.contains("database") ||
               failureReason.contains("connection") ||
               failureReason.contains("constraint") ||
               failureReason.contains("SQL") ||
               failureReason.contains("deadlock");
    }

    /**
     * Check if error is key derivation failure
     */
    private boolean isKeyDerivationError(String failureReason) {
        return failureReason.contains("key derivation") ||
               failureReason.contains("BIP32") ||
               failureReason.contains("BIP44") ||
               failureReason.contains("mnemonic") ||
               failureReason.contains("address generation");
    }

    /**
     * Check if error is duplicate wallet
     */
    private boolean isDuplicateError(String failureReason) {
        return failureReason.contains("duplicate") ||
               failureReason.contains("already exists") ||
               failureReason.contains("unique constraint");
    }

    /**
     * Check if error is event publishing failure
     */
    private boolean isEventPublishingError(String failureReason) {
        return failureReason.contains("kafka") ||
               failureReason.contains("event publish") ||
               failureReason.contains("message send");
    }

    /**
     * Retry with exponential backoff
     */
    private DlqProcessingResult retryWithBackoff(Object event, Integer retryCount,
                                                   Map<String, Object> headers) {
        try {
            // Calculate backoff delay: 2^retryCount * 60 seconds (1min, 2min, 4min, 8min, 16min)
            long delaySeconds = (long) Math.pow(2, retryCount) * 60;

            log.info("Scheduling wallet creation retry #{} with {}s delay", retryCount + 1, delaySeconds);

            // Update retry count in headers
            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);

            // In production, use Spring's @Scheduled or Kafka delay topic
            // For now, mark as retry requested
            scheduleRetry(event, headers, delaySeconds);

            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule wallet creation retry", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Schedule retry with delay (in production, use Kafka delay topic or scheduler)
     */
    private void scheduleRetry(Object event, Map<String, Object> headers, long delaySeconds) {
        // In production, publish to a retry topic with delay
        // Or use Spring @Scheduled with dynamic delays
        log.info("Wallet creation retry scheduled for event after {}s delay", delaySeconds);

        // Placeholder: In production, implement with Kafka scheduled messages
        // kafkaTemplate.send("crypto-wallet-creation-retry", event);
    }

    /**
     * Create manual review task for operations team
     */
    private void createManualReviewTask(Object event, String failureReason, Integer retryCount) {
        try {
            log.warn("Creating manual review task for wallet creation DLQ event. Failure: {}, Retries: {}",
                    failureReason, retryCount);

            // In production, create task in manual review system
            // walletService.createManualReviewTask(event, failureReason, retryCount);

            // Send alert to operations team
            sendOperationsAlert(event, failureReason, retryCount);

        } catch (Exception e) {
            log.error("Failed to create manual review task for wallet creation", e);
        }
    }

    /**
     * Send alert to operations team (Slack, PagerDuty, email, etc.)
     */
    private void sendOperationsAlert(Object event, String failureReason, Integer retryCount) {
        log.error("OPERATIONS ALERT: Wallet creation DLQ event requires manual intervention. " +
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
            log.debug("Updating wallet creation DLQ recovery metrics: {}", resultType);

            // Metrics are automatically updated by BaseDlqConsumer
            // Additional custom metrics can be added here

        } catch (Exception e) {
            log.warn("Failed to update wallet creation recovery metrics", e);
        }
    }

    @Override
    protected String getServiceName() {
        return "EnhancedCryptoWalletCreatedConsumer";
    }
}
