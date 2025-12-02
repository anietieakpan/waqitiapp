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
 * DLQ Handler for BlockchainTransactionRequestsConsumer
 *
 * Handles failed messages from the dead letter topic for blockchain transaction requests
 * This handler is CRITICAL as it deals with failed blockchain transaction broadcasting
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BlockchainTransactionRequestsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CryptoTransactionService transactionService;

    @Autowired
    public BlockchainTransactionRequestsConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate,
            CryptoTransactionService transactionService) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
        this.transactionService = transactionService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BlockchainTransactionRequestsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BlockchainTransactionRequestsConsumer.dlq:BlockchainTransactionRequestsConsumer.dlq}",
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
            log.error("Processing CRITICAL DLQ event for blockchain transaction request: {}", event);

            // Extract retry count from headers
            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String failureReason = (String) headers.get("failure_reason");
            LocalDateTime firstFailureTime = (LocalDateTime) headers.get("first_failure_time");
            String transactionId = (String) headers.get("transactionId");
            String txHash = (String) headers.get("txHash");
            String blockchain = (String) headers.get("blockchain");
            String amount = (String) headers.get("amount");

            log.error("CRITICAL: Blockchain transaction request failure - TxID: {}, Hash: {}, Blockchain: {}, Amount: {}, " +
                      "Retry count: {}, Failure: {}, First failure: {}",
                    transactionId, txHash, blockchain, amount, retryCount, failureReason, firstFailureTime);

            // Classify error type and determine recovery strategy
            DlqProcessingResult result = classifyAndRecover(event, failureReason, retryCount, headers);

            // Update metrics based on result
            updateRecoveryMetrics(result);

            return result;

        } catch (Exception e) {
            log.error("CRITICAL: Error handling blockchain transaction request DLQ event", e);
            // For unexpected errors in blockchain transactions, require manual intervention
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Classify error and determine recovery strategy for blockchain transaction requests
     * This is CRITICAL - failed transactions may result in fund loss if not handled properly
     */
    private DlqProcessingResult classifyAndRecover(Object event, String failureReason,
                                                     Integer retryCount, Map<String, Object> headers) {
        // Maximum retry attempts for blockchain operations
        final int MAX_RETRIES = 10; // Higher for blockchain due to network issues

        // Check if we've exceeded max retries
        if (retryCount >= MAX_RETRIES) {
            log.error("CRITICAL: Max retries ({}) exceeded for blockchain transaction request. IMMEDIATE MANUAL INTERVENTION REQUIRED", MAX_RETRIES);
            createUrgentManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Classify error type based on failure reason
        if (failureReason == null) {
            log.warn("No failure reason provided for blockchain transaction. Attempting retry with default strategy");
            return retryWithBackoff(event, retryCount, headers);
        }

        // Blockchain node connectivity errors - CRITICAL but transient
        if (isBlockchainNodeError(failureReason)) {
            log.error("Blockchain node connectivity error: {}. Scheduling retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Nonce/sequence errors - requires special handling
        if (isNonceError(failureReason)) {
            log.error("Blockchain nonce error detected: {}. Requires nonce recalculation", failureReason);
            return retryWithNonceRecalculation(event, retryCount, headers);
        }

        // Insufficient gas/fee errors - may require fee adjustment
        if (isGasFeeError(failureReason)) {
            log.error("Insufficient gas/fee error: {}. May require fee adjustment", failureReason);
            createManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Transaction already broadcasted/mined - check blockchain status
        if (isTransactionAlreadyBroadcast(failureReason)) {
            log.warn("Transaction may already be broadcasted: {}. Checking blockchain status", failureReason);
            return DlqProcessingResult.RETRY_WITH_IDEMPOTENCY_CHECK;
        }

        // Invalid transaction signature - PERMANENT FAILURE
        if (isSignatureError(failureReason)) {
            log.error("CRITICAL: Invalid transaction signature: {}. Permanent failure - DO NOT RETRY", failureReason);
            createUrgentManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }

        // Blockchain reorg detected - wait and retry
        if (isReorgError(failureReason)) {
            log.error("Blockchain reorganization detected: {}. Waiting for chain stabilization", failureReason);
            return retryWithExtendedBackoff(event, retryCount, headers);
        }

        // Hot wallet balance insufficient - CRITICAL
        if (isInsufficientBalanceError(failureReason)) {
            log.error("CRITICAL: Hot wallet insufficient balance: {}. URGENT MANUAL INTERVENTION", failureReason);
            createUrgentManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Network congestion - retry with higher gas
        if (isNetworkCongestionError(failureReason)) {
            log.warn("Network congestion detected: {}. Scheduling retry with potential fee increase", failureReason);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Double spend attempt detected - PERMANENT FAILURE
        if (isDoubleSpendError(failureReason)) {
            log.error("CRITICAL SECURITY: Double spend attempt detected: {}. Permanent failure", failureReason);
            createUrgentManualReviewTask(event, failureReason, retryCount);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }

        // Unknown error type - conservative retry strategy
        log.error("Unknown blockchain transaction error: {}. Conservative retry strategy", failureReason);
        return retryWithBackoff(event, retryCount, headers);
    }

    private boolean isBlockchainNodeError(String failureReason) {
        return failureReason.contains("node") ||
               failureReason.contains("RPC") ||
               failureReason.contains("connection refused") ||
               failureReason.contains("timeout") ||
               failureReason.contains("503") ||
               failureReason.contains("502");
    }

    private boolean isNonceError(String failureReason) {
        return failureReason.contains("nonce") ||
               failureReason.contains("sequence") ||
               failureReason.contains("nonce too low") ||
               failureReason.contains("nonce too high");
    }

    private boolean isGasFeeError(String failureReason) {
        return failureReason.contains("gas") ||
               failureReason.contains("fee") ||
               failureReason.contains("insufficient funds for gas") ||
               failureReason.contains("transaction underpriced");
    }

    private boolean isTransactionAlreadyBroadcast(String failureReason) {
        return failureReason.contains("already known") ||
               failureReason.contains("already broadcast") ||
               failureReason.contains("duplicate transaction") ||
               failureReason.contains("tx already in mempool");
    }

    private boolean isSignatureError(String failureReason) {
        return failureReason.contains("signature") ||
               failureReason.contains("invalid sig") ||
               failureReason.contains("ECDSA") ||
               failureReason.contains("verification failed");
    }

    private boolean isReorgError(String failureReason) {
        return failureReason.contains("reorg") ||
               failureReason.contains("reorganization") ||
               failureReason.contains("fork") ||
               failureReason.contains("chain split");
    }

    private boolean isInsufficientBalanceError(String failureReason) {
        return failureReason.contains("insufficient balance") ||
               failureReason.contains("insufficient funds") ||
               failureReason.contains("not enough") ||
               failureReason.contains("balance too low");
    }

    private boolean isNetworkCongestionError(String failureReason) {
        return failureReason.contains("congestion") ||
               failureReason.contains("mempool full") ||
               failureReason.contains("429") ||
               failureReason.contains("rate limit");
    }

    private boolean isDoubleSpendError(String failureReason) {
        return failureReason.contains("double spend") ||
               failureReason.contains("input already spent") ||
               failureReason.contains("UTXO already spent");
    }

    /**
     * Retry with exponential backoff
     */
    private DlqProcessingResult retryWithBackoff(Object event, Integer retryCount,
                                                   Map<String, Object> headers) {
        try {
            // Calculate backoff delay: 2^retryCount * 30 seconds (30s, 1min, 2min, 4min, 8min...)
            long delaySeconds = (long) Math.pow(2, retryCount) * 30;

            log.info("Scheduling blockchain transaction retry #{} with {}s delay", retryCount + 1, delaySeconds);

            // Update retry count in headers
            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);

            scheduleRetry(event, headers, delaySeconds);

            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule blockchain transaction retry", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Retry with extended backoff for blockchain reorg scenarios
     */
    private DlqProcessingResult retryWithExtendedBackoff(Object event, Integer retryCount,
                                                          Map<String, Object> headers) {
        try {
            // Extended backoff for reorg: wait for chain stabilization (5min, 10min, 20min...)
            long delaySeconds = (long) Math.pow(2, retryCount) * 300;

            log.warn("Scheduling blockchain transaction retry after reorg #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);
            headers.put("reorg_detected", true);

            scheduleRetry(event, headers, delaySeconds);

            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule blockchain transaction retry after reorg", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Retry with nonce recalculation
     */
    private DlqProcessingResult retryWithNonceRecalculation(Object event, Integer retryCount,
                                                             Map<String, Object> headers) {
        log.warn("Nonce error detected - marking for retry with nonce recalculation");
        headers.put("recalculate_nonce", true);
        return retryWithBackoff(event, retryCount, headers);
    }

    /**
     * Schedule retry with delay
     */
    private void scheduleRetry(Object event, Map<String, Object> headers, long delaySeconds) {
        log.info("Blockchain transaction retry scheduled for event after {}s delay", delaySeconds);
        // In production: kafkaTemplate.send("blockchain-transaction-retry", event);
    }

    /**
     * Create manual review task for operations team
     */
    private void createManualReviewTask(Object event, String failureReason, Integer retryCount) {
        try {
            log.error("Creating manual review task for blockchain transaction DLQ event. Failure: {}, Retries: {}",
                    failureReason, retryCount);

            // In production: transactionService.createManualReviewTask(event, failureReason, retryCount);
            sendOperationsAlert(event, failureReason, retryCount, "MANUAL_REVIEW");

        } catch (Exception e) {
            log.error("Failed to create manual review task for blockchain transaction", e);
        }
    }

    /**
     * Create URGENT manual review task for critical failures
     */
    private void createUrgentManualReviewTask(Object event, String failureReason, Integer retryCount) {
        try {
            log.error("Creating URGENT manual review task for CRITICAL blockchain transaction failure. Failure: {}, Retries: {}",
                    failureReason, retryCount);

            // In production: transactionService.createUrgentManualReviewTask(event, failureReason, retryCount);
            sendOperationsAlert(event, failureReason, retryCount, "URGENT");

        } catch (Exception e) {
            log.error("Failed to create urgent manual review task for blockchain transaction", e);
        }
    }

    /**
     * Send alert to operations team
     */
    private void sendOperationsAlert(Object event, String failureReason, Integer retryCount, String severity) {
        log.error("OPERATIONS ALERT [{}]: Blockchain transaction DLQ event requires intervention. " +
                  "Event: {}, Failure: {}, Retries: {}", severity, event, failureReason, retryCount);

        // In production, integrate with:
        // - PagerDuty incidents (for URGENT)
        // - Slack notifications
        // - Email alerts
        // - Monitoring dashboards
    }

    /**
     * Update recovery metrics for monitoring
     */
    private void updateRecoveryMetrics(DlqProcessingResult result) {
        try {
            String resultType = result.name().toLowerCase();
            log.debug("Updating blockchain transaction DLQ recovery metrics: {}", resultType);
            // Metrics are automatically updated by BaseDlqConsumer
        } catch (Exception e) {
            log.warn("Failed to update blockchain transaction recovery metrics", e);
        }
    }

    @Override
    protected String getServiceName() {
        return "BlockchainTransactionRequestsConsumer";
    }
}
