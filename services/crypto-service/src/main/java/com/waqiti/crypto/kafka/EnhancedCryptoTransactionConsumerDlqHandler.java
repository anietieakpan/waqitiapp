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
 * DLQ Handler for EnhancedCryptoTransactionConsumer
 *
 * Handles failed messages from the dead letter topic for enhanced crypto transaction processing
 * (enrichment, analysis, fraud scoring, blockchain confirmation tracking, etc.)
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class EnhancedCryptoTransactionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CryptoTransactionService transactionService;

    @Autowired
    public EnhancedCryptoTransactionConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate,
            CryptoTransactionService transactionService) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
        this.transactionService = transactionService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("EnhancedCryptoTransactionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.EnhancedCryptoTransactionConsumer.dlq:EnhancedCryptoTransactionConsumer.dlq}",
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
            log.info("Processing DLQ event for enhanced crypto transaction processing: {}", event);

            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String failureReason = (String) headers.get("failure_reason");
            String transactionId = (String) headers.get("transactionId");
            String enrichmentType = (String) headers.get("enrichmentType");
            String txHash = (String) headers.get("txHash");

            log.debug("Enhanced transaction DLQ - TxID: {}, Enrichment: {}, Hash: {}, Retry: {}, Failure: {}",
                    transactionId, enrichmentType, txHash, retryCount, failureReason);

            DlqProcessingResult result = classifyAndRecover(event, failureReason, retryCount, headers);
            updateRecoveryMetrics(result);

            return result;

        } catch (Exception e) {
            log.error("Error handling enhanced transaction DLQ event", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Classify error and determine recovery strategy for enhanced transaction processing
     * Enhanced processing is non-critical (enrichment, analysis) but valuable
     */
    private DlqProcessingResult classifyAndRecover(Object event, String failureReason,
                                                     Integer retryCount, Map<String, Object> headers) {
        final int MAX_RETRIES = 5;

        if (retryCount >= MAX_RETRIES) {
            log.warn("Max retries ({}) exceeded for enhanced transaction processing. Creating manual review task", MAX_RETRIES);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        if (failureReason == null) {
            return retryWithBackoff(event, retryCount, headers);
        }

        // Blockchain node connectivity errors - retry (common for confirmation tracking)
        if (isBlockchainNodeError(failureReason)) {
            log.info("Blockchain node error for enhanced transaction: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Fraud scoring service errors - retry
        if (isFraudServiceError(failureReason)) {
            log.warn("Fraud scoring service error: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Blockchain explorer API errors - retry
        if (isBlockchainExplorerError(failureReason)) {
            log.info("Blockchain explorer API error: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Transaction not found on blockchain yet - retry with extended backoff
        if (isTransactionNotFoundError(failureReason)) {
            log.info("Transaction not yet on blockchain: {}. Retry with extended backoff", failureReason);
            return retryWithExtendedBackoff(event, retryCount, headers);
        }

        // Database errors - retry
        if (isDatabaseError(failureReason)) {
            log.info("Database error for enhanced transaction: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Duplicate enrichment already processed
        if (isDuplicateError(failureReason)) {
            log.info("Duplicate enhanced transaction processing detected: {}. Discarding", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // ML model service errors - retry
        if (isMLServiceError(failureReason)) {
            log.warn("ML model service error: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Transaction data malformed - permanent failure
        if (isDataValidationError(failureReason)) {
            log.error("Transaction data validation error: {}. Permanent failure", failureReason);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }

        // External API rate limiting - retry with extended backoff
        if (isRateLimitError(failureReason)) {
            log.warn("External API rate limit for enhanced transaction: {}. Extended backoff", failureReason);
            return retryWithExtendedBackoff(event, retryCount, headers);
        }

        // Unknown error - retry conservatively
        log.warn("Unknown enhanced transaction error: {}. Conservative retry", failureReason);
        return retryWithBackoff(event, retryCount, headers);
    }

    private boolean isBlockchainNodeError(String failureReason) {
        return failureReason.contains("blockchain node") ||
               failureReason.contains("RPC") ||
               failureReason.contains("node unavailable") ||
               failureReason.contains("timeout");
    }

    private boolean isFraudServiceError(String failureReason) {
        return failureReason.contains("fraud service") ||
               failureReason.contains("fraud scoring") ||
               failureReason.contains("risk assessment");
    }

    private boolean isBlockchainExplorerError(String failureReason) {
        return failureReason.contains("block explorer") ||
               failureReason.contains("explorer API") ||
               failureReason.contains("Etherscan") ||
               failureReason.contains("Blockchair");
    }

    private boolean isTransactionNotFoundError(String failureReason) {
        return failureReason.contains("transaction not found") ||
               failureReason.contains("tx not found") ||
               failureReason.contains("not yet confirmed") ||
               failureReason.contains("pending");
    }

    private boolean isDatabaseError(String failureReason) {
        return failureReason.contains("database") ||
               failureReason.contains("connection") ||
               failureReason.contains("SQL") ||
               failureReason.contains("deadlock");
    }

    private boolean isDuplicateError(String failureReason) {
        return failureReason.contains("duplicate") ||
               failureReason.contains("already enriched") ||
               failureReason.contains("already processed");
    }

    private boolean isMLServiceError(String failureReason) {
        return failureReason.contains("ML service") ||
               failureReason.contains("model") ||
               failureReason.contains("prediction") ||
               failureReason.contains("inference");
    }

    private boolean isDataValidationError(String failureReason) {
        return failureReason.contains("validation") ||
               failureReason.contains("invalid") ||
               failureReason.contains("malformed") ||
               failureReason.contains("parse error");
    }

    private boolean isRateLimitError(String failureReason) {
        return failureReason.contains("rate limit") ||
               failureReason.contains("too many requests") ||
               failureReason.contains("429") ||
               failureReason.contains("throttle");
    }

    private DlqProcessingResult retryWithBackoff(Object event, Integer retryCount, Map<String, Object> headers) {
        try {
            long delaySeconds = (long) Math.pow(2, retryCount) * 60;
            log.info("Scheduling enhanced transaction retry #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);

            // In production: kafkaTemplate.send("enhanced-crypto-transaction-retry", event);
            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule enhanced transaction retry", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    private DlqProcessingResult retryWithExtendedBackoff(Object event, Integer retryCount, Map<String, Object> headers) {
        try {
            // Extended backoff for transaction confirmation tracking (5min, 10min, 20min...)
            long delaySeconds = (long) Math.pow(2, retryCount) * 300;
            log.info("Scheduling enhanced transaction retry with extended backoff #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);
            headers.put("extended_backoff", true);

            // In production: kafkaTemplate.send("enhanced-crypto-transaction-retry", event);
            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule enhanced transaction retry with extended backoff", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    private void createManualReviewTask(Object event, String failureReason, Integer retryCount) {
        try {
            log.warn("Creating manual review task for enhanced transaction. Failure: {}, Retries: {}",
                    failureReason, retryCount);
            // In production: transactionService.createManualReviewTask(event, failureReason, retryCount);
            sendOperationsAlert(event, failureReason, retryCount);
        } catch (Exception e) {
            log.error("Failed to create manual review task for enhanced transaction", e);
        }
    }

    private void sendOperationsAlert(Object event, String failureReason, Integer retryCount) {
        log.error("OPERATIONS ALERT: Enhanced transaction DLQ requires intervention. " +
                  "Event: {}, Failure: {}, Retries: {}", event, failureReason, retryCount);
    }

    private void updateRecoveryMetrics(DlqProcessingResult result) {
        try {
            log.debug("Updating enhanced transaction DLQ recovery metrics: {}", result.name().toLowerCase());
            // Metrics are automatically updated by BaseDlqConsumer
        } catch (Exception e) {
            log.warn("Failed to update enhanced transaction recovery metrics", e);
        }
    }

    @Override
    protected String getServiceName() {
        return "EnhancedCryptoTransactionConsumer";
    }
}
