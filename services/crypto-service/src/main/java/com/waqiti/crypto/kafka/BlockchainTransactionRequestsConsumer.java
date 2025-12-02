package com.waqiti.crypto.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.crypto.dto.BlockchainTransactionRequest;
import com.waqiti.crypto.service.BlockchainExecutionService;
import com.waqiti.crypto.service.TransactionMiningService;
import com.waqiti.common.exception.KafkaRetryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Blockchain Transaction Requests Consumer
 *
 * PURPOSE: Execute blockchain transactions (Bitcoin, Ethereum, Lightning)
 *
 * BUSINESS CRITICAL: Crypto transactions represent $500K-2M daily volume
 * Missing this consumer means:
 * - User crypto withdrawal requests stuck
 * - No on-chain transaction execution
 * - Failed Lightning channel operations
 * - Customer support escalations
 *
 * IMPLEMENTATION PRIORITY: P0 CRITICAL
 *
 * @author Waqiti Crypto Team
 * @version 1.0.0
 * @since 2025-10-13
 */
@Service
@Slf4j
public class BlockchainTransactionRequestsConsumer {

    private final BlockchainExecutionService executionService;
    private final TransactionMiningService miningService;
    private final Counter transactionsExecutedCounter;
    private final Counter transactionsFailedCounter;

    @Autowired
    public BlockchainTransactionRequestsConsumer(
            BlockchainExecutionService executionService,
            TransactionMiningService miningService,
            MeterRegistry meterRegistry) {

        this.executionService = executionService;
        this.miningService = miningService;

        this.transactionsExecutedCounter = Counter.builder("blockchain.transactions.executed")
                .description("Number of blockchain transactions executed")
                .register(meterRegistry);

        this.transactionsFailedCounter = Counter.builder("blockchain.transactions.failed")
                .description("Number of blockchain transactions that failed")
                .register(meterRegistry);
    }

    /**
     * Process blockchain transaction request
     */
    @RetryableKafkaListener(
        topics = "blockchain-transaction-requests",
        groupId = "crypto-service-blockchain-requests",
        containerFactory = "kafkaListenerContainerFactory",
        retries = 3, // Fewer retries for blockchain (gas fees)
        backoffMultiplier = 2.0,
        initialBackoff = 5000L // Longer initial backoff for blockchain
    )
    @Transactional
    public void handleBlockchainTransactionRequest(
            @Payload BlockchainTransactionRequest request,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();

        log.info("Processing blockchain transaction request: requestId={}, blockchain={}, type={}",
                request.getRequestId(),
                request.getBlockchain(),
                request.getTransactionType());

        try {
            // Step 1: Validate request
            validateRequest(request);

            // Step 2: Check idempotency (critical for blockchain to avoid double-spend)
            if (executionService.isTransactionAlreadySubmitted(request.getRequestId())) {
                log.info("Blockchain transaction already submitted (idempotent): requestId={}",
                        request.getRequestId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Check wallet balance and gas fees
            executionService.validateBalance(request);

            // Step 4: Execute based on blockchain type
            String txHash;
            switch (request.getBlockchain()) {
                case BITCOIN:
                    txHash = executionService.executeBitcoinTransaction(request);
                    break;
                case ETHEREUM:
                    txHash = executionService.executeEthereumTransaction(request);
                    break;
                case LIGHTNING:
                    txHash = executionService.executeLightningPayment(request);
                    break;
                case POLYGON:
                    txHash = executionService.executePolygonTransaction(request);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported blockchain: " + request.getBlockchain());
            }

            log.info("Blockchain transaction submitted: requestId={}, txHash={}",
                    request.getRequestId(), txHash);

            // Step 5: Monitor transaction mining/confirmation
            if (!request.getBlockchain().equals("LIGHTNING")) {
                miningService.monitorTransaction(txHash, request);
            }

            // Step 6: Mark as processed
            executionService.markTransactionSubmitted(request.getRequestId(), txHash);

            // Step 7: Acknowledge
            acknowledgment.acknowledge();

            // Metrics
            transactionsExecutedCounter.increment();

            long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Blockchain transaction processed: requestId={}, txHash={}, time={}ms",
                    request.getRequestId(), txHash, processingTime);

        } catch (InsufficientBalanceException e) {
            log.error("Insufficient balance for blockchain transaction: requestId={}",
                    request.getRequestId());
            // Don't retry - notify user of insufficient funds
            executionService.notifyUserInsufficientFunds(request);
            acknowledgment.acknowledge();
            transactionsFailedCounter.increment();

        } catch (GasPriceTooHighException e) {
            log.error("Gas price too high for blockchain transaction: requestId={}, retry later",
                    request.getRequestId());
            // Retry - gas prices fluctuate
            transactionsFailedCounter.increment();
            throw new KafkaRetryException(
                    "Gas price too high, retry later",
                    e,
                    request.getRequestId().toString()
            );

        } catch (Exception e) {
            log.error("Failed to execute blockchain transaction: requestId={}, will retry",
                    request.getRequestId(), e);

            transactionsFailedCounter.increment();

            throw new KafkaRetryException(
                    "Failed to execute blockchain transaction",
                    e,
                    request.getRequestId().toString()
            );
        }
    }

    /**
     * Validate request
     */
    private void validateRequest(BlockchainTransactionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if (request.getRequestId() == null) {
            throw new IllegalArgumentException("Request ID cannot be null");
        }

        if (request.getBlockchain() == null) {
            throw new IllegalArgumentException("Blockchain cannot be null");
        }

        if (request.getFromAddress() == null || request.getFromAddress().isBlank()) {
            throw new IllegalArgumentException("From address cannot be null or empty");
        }

        if (request.getToAddress() == null || request.getToAddress().isBlank()) {
            throw new IllegalArgumentException("To address cannot be null or empty");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    /**
     * Handle DLQ messages
     */
    @KafkaListener(topics = "blockchain-transaction-requests-crypto-service-dlq")
    public void handleDLQMessage(@Payload BlockchainTransactionRequest request) {
        log.error("Blockchain transaction in DLQ - user funds stuck: requestId={}, blockchain={}",
                request.getRequestId(), request.getBlockchain());

        try {
            // Log to persistent storage
            executionService.logDLQTransaction(
                    request.getRequestId(),
                    request,
                    "Blockchain transaction failed permanently"
            );

            // Alert crypto operations team (HIGH PRIORITY - user funds stuck)
            executionService.alertCryptoOps(
                    "CRITICAL",
                    "User crypto withdrawal stuck in DLQ - funds may be locked",
                    java.util.Map.of(
                            "requestId", request.getRequestId().toString(),
                            "blockchain", request.getBlockchain(),
                            "amount", request.getAmount().toString(),
                            "userId", request.getUserId().toString()
                    )
            );

            // Notify user
            executionService.notifyUserTransactionFailed(request);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process blockchain DLQ message: requestId={}",
                    request.getRequestId(), e);
        }
    }

    // Exception classes
    public static class InsufficientBalanceException extends RuntimeException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
    }

    public static class GasPriceTooHighException extends RuntimeException {
        public GasPriceTooHighException(String message) {
            super(message);
        }
    }
}
