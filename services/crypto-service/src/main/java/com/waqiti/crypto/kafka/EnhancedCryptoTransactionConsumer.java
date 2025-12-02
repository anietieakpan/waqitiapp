package com.waqiti.crypto.kafka;

import com.waqiti.crypto.events.model.CryptoEvent;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.service.CryptoPriceOracleService;
import com.waqiti.crypto.blockchain.BlockchainService;
import com.waqiti.crypto.compliance.ComplianceService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class EnhancedCryptoTransactionConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoWalletService cryptoWalletService;
    private final CryptoPriceOracleService priceOracleService;
    private final BlockchainService blockchainService;
    private final ComplianceService complianceService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("crypto_transaction_processed_total")
            .description("Total number of successfully processed crypto transaction events")
            .register(meterRegistry);
        errorCounter = Counter.builder("crypto_transaction_errors_total")
            .description("Total number of crypto transaction processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("crypto_transaction_processing_duration")
            .description("Time taken to process crypto transaction events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"crypto-transaction", "crypto-transaction-events", "blockchain-transactions"},
        groupId = "enhanced-crypto-transaction-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "crypto-transaction", fallbackMethod = "handleCryptoTransactionEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCryptoTransactionEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("crypto-tx-%s-p%d-o%d", event.getTransactionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTransactionId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing crypto transaction: txId={}, type={}, amount={} {}",
                event.getTransactionId(), event.getEventType(), event.getAmount(), event.getCryptoSymbol());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case "TRANSACTION_INITIATED":
                    processTransactionInitiated(event, correlationId);
                    break;

                case "TRANSACTION_VALIDATED":
                    processTransactionValidated(event, correlationId);
                    break;

                case "COMPLIANCE_CHECK_COMPLETED":
                    processComplianceCheckCompleted(event, correlationId);
                    break;

                case "BLOCKCHAIN_SUBMITTED":
                    processBlockchainSubmitted(event, correlationId);
                    break;

                case "BLOCKCHAIN_CONFIRMED":
                    processBlockchainConfirmed(event, correlationId);
                    break;

                case "TRANSACTION_COMPLETED":
                    processTransactionCompleted(event, correlationId);
                    break;

                case "TRANSACTION_FAILED":
                    processTransactionFailed(event, correlationId);
                    break;

                case "TRANSACTION_REVERSED":
                    processTransactionReversed(event, correlationId);
                    break;

                default:
                    log.warn("Unknown crypto transaction event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("CRYPTO_TRANSACTION_EVENT_PROCESSED", event.getUserId(),
                Map.of("eventType", event.getEventType(), "transactionId", event.getTransactionId(),
                    "amount", event.getAmount(), "currency", event.getCryptoSymbol(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process crypto transaction event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("crypto-transaction-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCryptoTransactionEventFallback(
            CryptoEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("crypto-tx-fallback-%s-p%d-o%d", event.getTransactionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for crypto transaction: txId={}, error={}",
            event.getTransactionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("crypto-transaction-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Crypto Transaction Circuit Breaker Triggered",
                String.format("Transaction %s processing failed: %s", event.getTransactionId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCryptoTransactionEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-crypto-tx-%s-%d", event.getTransactionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Crypto transaction permanently failed: txId={}, topic={}, error={}",
            event.getTransactionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("CRYPTO_TRANSACTION_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "transactionId", event.getTransactionId(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Crypto Transaction Dead Letter Event",
                String.format("Transaction %s sent to DLT: %s", event.getTransactionId(), exceptionMessage),
                Map.of("transactionId", event.getTransactionId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processTransactionInitiated(CryptoEvent event, String correlationId) {
        log.info("Processing transaction initiated: txId={}", event.getTransactionId());

        // Create transaction record
        cryptoTransactionService.createTransaction(
            event.getTransactionId(),
            event.getUserId(),
            event.getCryptoWalletId(),
            event.getCryptoSymbol(),
            event.getAmount(),
            event.getOrderType(),
            correlationId
        );

        // Validate wallet balance and limits
        if (!cryptoWalletService.validateTransactionLimits(event.getCryptoWalletId(), event.getAmount())) {
            throw new RuntimeException("Transaction exceeds wallet limits");
        }

        // Get current price and calculate total value
        BigDecimal currentPrice = priceOracleService.getCurrentPrice(event.getCryptoSymbol());
        BigDecimal totalValue = event.getAmount().multiply(currentPrice);

        // Update transaction with current pricing
        cryptoTransactionService.updateTransactionPricing(event.getTransactionId(), currentPrice, totalValue, correlationId);

        // Trigger validation workflow
        kafkaTemplate.send("crypto-transaction-workflow", Map.of(
            "transactionId", event.getTransactionId(),
            "eventType", "VALIDATION_REQUESTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Transaction initiated: txId={}, amount={} {}, value={}",
            event.getTransactionId(), event.getAmount(), event.getCryptoSymbol(), totalValue);
    }

    private void processTransactionValidated(CryptoEvent event, String correlationId) {
        log.info("Processing transaction validated: txId={}", event.getTransactionId());

        // Update transaction status
        cryptoTransactionService.markTransactionValidated(event.getTransactionId(), correlationId);

        // Reserve funds in wallet
        cryptoWalletService.reserveFunds(event.getCryptoWalletId(), event.getAmount(), event.getTransactionId(), correlationId);

        // Trigger compliance check
        kafkaTemplate.send("crypto-compliance-workflow", Map.of(
            "transactionId", event.getTransactionId(),
            "userId", event.getUserId(),
            "walletId", event.getCryptoWalletId(),
            "amount", event.getAmount(),
            "cryptoSymbol", event.getCryptoSymbol(),
            "eventType", "COMPLIANCE_CHECK_REQUESTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Transaction validated and funds reserved: txId={}", event.getTransactionId());
    }

    private void processComplianceCheckCompleted(CryptoEvent event, String correlationId) {
        log.info("Processing compliance check completed: txId={}, status={}", event.getTransactionId(), event.getStatus());

        if ("APPROVED".equals(event.getStatus())) {
            // Update transaction status
            cryptoTransactionService.markComplianceApproved(event.getTransactionId(), correlationId);

            // Submit to blockchain
            kafkaTemplate.send("blockchain-transaction-requests", Map.of(
                "transactionId", event.getTransactionId(),
                "eventType", "BLOCKCHAIN_SUBMISSION_REQUESTED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

        } else if ("REJECTED".equals(event.getStatus())) {
            // Mark transaction as failed
            cryptoTransactionService.markTransactionFailed(event.getTransactionId(), "COMPLIANCE_REJECTED", correlationId);

            // Release reserved funds
            cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

            // Notify user
            notificationService.sendNotification(event.getUserId(),
                "Transaction Rejected",
                "Your cryptocurrency transaction was rejected during compliance review.",
                correlationId);

        } else if ("REQUIRES_MANUAL_REVIEW".equals(event.getStatus())) {
            // Mark for manual review
            cryptoTransactionService.markForManualReview(event.getTransactionId(), event.getReason(), correlationId);

            // Notify compliance team
            notificationService.sendOperationalAlert(
                "Crypto Transaction Manual Review Required",
                String.format("Transaction %s requires manual compliance review", event.getTransactionId()),
                "MEDIUM"
            );
        }

        log.info("Compliance check completed: txId={}, status={}", event.getTransactionId(), event.getStatus());
    }

    private void processBlockchainSubmitted(CryptoEvent event, String correlationId) {
        log.info("Processing blockchain submitted: txId={}, hash={}", event.getTransactionId(), event.getTransactionHash());

        // Update transaction with blockchain hash
        cryptoTransactionService.updateTransactionHash(event.getTransactionId(), event.getTransactionHash(), correlationId);

        // Mark as pending confirmation
        cryptoTransactionService.markTransactionPending(event.getTransactionId(), correlationId);

        // Notify user of blockchain submission
        notificationService.sendNotification(event.getUserId(),
            "Transaction Submitted to Blockchain",
            String.format("Your %s transaction has been submitted to the blockchain. Hash: %s",
                event.getCryptoSymbol(), event.getTransactionHash()),
            correlationId);

        // Start monitoring for confirmations
        kafkaTemplate.send("blockchain-confirmation-monitor", Map.of(
            "transactionId", event.getTransactionId(),
            "blockchainHash", event.getTransactionHash(),
            "network", event.getBlockchainNetwork(),
            "eventType", "CONFIRMATION_MONITORING_STARTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Transaction submitted to blockchain: txId={}, hash={}", event.getTransactionId(), event.getTransactionHash());
    }

    private void processBlockchainConfirmed(CryptoEvent event, String correlationId) {
        log.info("Processing blockchain confirmed: txId={}, confirmations={}", event.getTransactionId(),
            event.getMetadata() != null ? event.getMetadata().get("confirmations") : "unknown");

        // Update transaction confirmation status
        cryptoTransactionService.markTransactionConfirmed(event.getTransactionId(),
            (Integer) event.getMetadata().getOrDefault("confirmations", 1), correlationId);

        // Check if required confirmations are met
        int requiredConfirmations = blockchainService.getRequiredConfirmations(event.getBlockchainNetwork());
        int currentConfirmations = (Integer) event.getMetadata().getOrDefault("confirmations", 0);

        if (currentConfirmations >= requiredConfirmations) {
            // Mark transaction as completed
            kafkaTemplate.send("crypto-transaction-workflow", Map.of(
                "transactionId", event.getTransactionId(),
                "eventType", "TRANSACTION_COMPLETION_REQUESTED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Blockchain confirmation updated: txId={}, confirmations={}/{}",
            event.getTransactionId(), currentConfirmations, requiredConfirmations);
    }

    private void processTransactionCompleted(CryptoEvent event, String correlationId) {
        log.info("Processing transaction completed: txId={}", event.getTransactionId());

        // Mark transaction as completed
        cryptoTransactionService.markTransactionCompleted(event.getTransactionId(), LocalDateTime.now(), correlationId);

        // Update wallet balances
        cryptoWalletService.updateBalanceAfterTransaction(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Release any remaining reserved funds
        cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Send completion notification
        notificationService.sendNotification(event.getUserId(),
            "Transaction Completed",
            String.format("Your %s %s transaction has been completed successfully.",
                event.getAmount(), event.getCryptoSymbol()),
            correlationId);

        // Trigger analytics events
        kafkaTemplate.send("crypto-analytics-events", Map.of(
            "transactionId", event.getTransactionId(),
            "userId", event.getUserId(),
            "walletId", event.getCryptoWalletId(),
            "cryptoSymbol", event.getCryptoSymbol(),
            "amount", event.getAmount(),
            "eventType", "TRANSACTION_COMPLETED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Transaction completed successfully: txId={}, amount={} {}",
            event.getTransactionId(), event.getAmount(), event.getCryptoSymbol());
    }

    private void processTransactionFailed(CryptoEvent event, String correlationId) {
        log.warn("Processing transaction failed: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Mark transaction as failed
        cryptoTransactionService.markTransactionFailed(event.getTransactionId(), event.getReason(), correlationId);

        // Release reserved funds
        cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Send failure notification
        notificationService.sendNotification(event.getUserId(),
            "Transaction Failed",
            String.format("Your cryptocurrency transaction failed: %s", event.getReason()),
            correlationId);

        // Log failure for analytics
        kafkaTemplate.send("crypto-analytics-events", Map.of(
            "transactionId", event.getTransactionId(),
            "userId", event.getUserId(),
            "walletId", event.getCryptoWalletId(),
            "eventType", "TRANSACTION_FAILED",
            "reason", event.getReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.warn("Transaction failed: txId={}, reason={}", event.getTransactionId(), event.getReason());
    }

    private void processTransactionReversed(CryptoEvent event, String correlationId) {
        log.warn("Processing transaction reversed: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Mark transaction as reversed
        cryptoTransactionService.markTransactionReversed(event.getTransactionId(), event.getReason(), correlationId);

        // Reverse wallet balance changes
        cryptoWalletService.reverseTransaction(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Send reversal notification
        notificationService.sendNotification(event.getUserId(),
            "Transaction Reversed",
            String.format("Your cryptocurrency transaction has been reversed: %s", event.getReason()),
            correlationId);

        // Alert operations team if reversal was due to fraud
        if (event.getReason() != null && event.getReason().toLowerCase().contains("fraud")) {
            notificationService.sendOperationalAlert(
                "Fraudulent Transaction Reversed",
                String.format("Transaction %s reversed due to fraud: %s", event.getTransactionId(), event.getReason()),
                "HIGH"
            );
        }

        log.warn("Transaction reversed: txId={}, reason={}", event.getTransactionId(), event.getReason());
    }
}