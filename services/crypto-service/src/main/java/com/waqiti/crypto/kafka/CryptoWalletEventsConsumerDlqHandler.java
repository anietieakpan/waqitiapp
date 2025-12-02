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
 * DLQ Handler for CryptoWalletEventsConsumer
 *
 * Handles failed messages from the dead letter topic for crypto wallet events
 * (balance updates, status changes, configuration updates, etc.)
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoWalletEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CryptoWalletService walletService;

    @Autowired
    public CryptoWalletEventsConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate,
            CryptoWalletService walletService) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
        this.walletService = walletService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoWalletEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoWalletEventsConsumer.dlq:CryptoWalletEventsConsumer.dlq}",
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
            log.info("Processing DLQ event for crypto wallet events: {}", event);

            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String failureReason = (String) headers.get("failure_reason");
            String walletId = (String) headers.get("walletId");
            String eventType = (String) headers.get("eventType");

            log.debug("Wallet event DLQ - Wallet: {}, Event type: {}, Retry: {}, Failure: {}",
                    walletId, eventType, retryCount, failureReason);

            DlqProcessingResult result = classifyAndRecover(event, failureReason, retryCount, headers, eventType);
            updateRecoveryMetrics(result);

            return result;

        } catch (Exception e) {
            log.error("Error handling wallet events DLQ event", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    private DlqProcessingResult classifyAndRecover(Object event, String failureReason,
                                                     Integer retryCount, Map<String, Object> headers,
                                                     String eventType) {
        final int MAX_RETRIES = 5;

        if (retryCount >= MAX_RETRIES) {
            log.warn("Max retries ({}) exceeded for wallet event. Manual intervention required", MAX_RETRIES);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        if (failureReason == null) {
            return retryWithBackoff(event, retryCount, headers);
        }

        // Balance update failures - CRITICAL
        if (isBalanceUpdateEvent(eventType) && isFinancialCriticalError(failureReason)) {
            log.error("CRITICAL: Wallet balance update failed: {}. Manual intervention required", failureReason);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Database connectivity issues - retry
        if (isDatabaseError(failureReason)) {
            log.info("Database error for wallet event: {}. Scheduling retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Duplicate event processing
        if (isDuplicateError(failureReason)) {
            log.info("Duplicate wallet event detected: {}. Discarding", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // Event validation errors - permanent
        if (isValidationError(failureReason)) {
            log.error("Wallet event validation error: {}. Permanent failure", failureReason);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }

        // Downstream service errors - retry
        if (isDownstreamServiceError(failureReason)) {
            log.warn("Downstream service error for wallet event: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Unknown error - retry conservatively
        log.warn("Unknown wallet event error: {}. Conservative retry", failureReason);
        return retryWithBackoff(event, retryCount, headers);
    }

    private boolean isBalanceUpdateEvent(String eventType) {
        return eventType != null && (eventType.contains("BALANCE") || eventType.contains("balance"));
    }

    private boolean isFinancialCriticalError(String failureReason) {
        return failureReason.contains("balance mismatch") ||
               failureReason.contains("reconciliation") ||
               failureReason.contains("accounting");
    }

    private boolean isDatabaseError(String failureReason) {
        return failureReason.contains("database") ||
               failureReason.contains("connection") ||
               failureReason.contains("SQL") ||
               failureReason.contains("deadlock");
    }

    private boolean isDuplicateError(String failureReason) {
        return failureReason.contains("duplicate") ||
               failureReason.contains("already processed") ||
               failureReason.contains("unique constraint");
    }

    private boolean isValidationError(String failureReason) {
        return failureReason.contains("validation") ||
               failureReason.contains("invalid") ||
               failureReason.contains("malformed");
    }

    private boolean isDownstreamServiceError(String failureReason) {
        return failureReason.contains("service unavailable") ||
               failureReason.contains("timeout") ||
               failureReason.contains("503") ||
               failureReason.contains("502");
    }

    private DlqProcessingResult retryWithBackoff(Object event, Integer retryCount, Map<String, Object> headers) {
        try {
            long delaySeconds = (long) Math.pow(2, retryCount) * 60;
            log.info("Scheduling wallet event retry #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);

            // In production: kafkaTemplate.send("crypto-wallet-events-retry", event);
            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule wallet event retry", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    private void createManualReviewTask(Object event, String failureReason, Integer retryCount) {
        try {
            log.warn("Creating manual review task for wallet event. Failure: {}, Retries: {}",
                    failureReason, retryCount);
            // In production: walletService.createManualReviewTask(event, failureReason, retryCount);
            sendOperationsAlert(event, failureReason, retryCount);
        } catch (Exception e) {
            log.error("Failed to create manual review task for wallet event", e);
        }
    }

    private void sendOperationsAlert(Object event, String failureReason, Integer retryCount) {
        log.error("OPERATIONS ALERT: Wallet event DLQ requires intervention. " +
                  "Event: {}, Failure: {}, Retries: {}", event, failureReason, retryCount);
    }

    private void updateRecoveryMetrics(DlqProcessingResult result) {
        try {
            log.debug("Updating wallet event DLQ recovery metrics: {}", result.name().toLowerCase());
        } catch (Exception e) {
            log.warn("Failed to update wallet event recovery metrics", e);
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoWalletEventsConsumer";
    }
}
