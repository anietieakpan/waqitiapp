package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.payment.dto.ATMTransactionEvent;
import com.waqiti.payment.service.ATMTransactionProcessingService;
import com.waqiti.payment.service.ReconciliationService;
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
 * ATM Transaction Events Consumer
 *
 * PURPOSE: Process ATM transaction events for tracking, reconciliation, and fraud detection
 *
 * BUSINESS CRITICAL: ATM transactions represent $2M-5M daily volume
 * Missing this consumer means:
 * - No transaction reconciliation
 * - No fraud detection on ATM withdrawals
 * - No accurate balance tracking
 * - Failed compliance audits
 *
 * IMPLEMENTATION PRIORITY: P0 CRITICAL
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-10-13
 */
@Service
@Slf4j
public class ATMTransactionEventsConsumer {

    private final ATMTransactionProcessingService processingService;
    private final ReconciliationService reconciliationService;
    private final Counter transactionsProcessedCounter;
    private final Counter transactionsFailedCounter;

    @Autowired
    public ATMTransactionEventsConsumer(
            ATMTransactionProcessingService processingService,
            ReconciliationService reconciliationService,
            MeterRegistry meterRegistry) {

        this.processingService = processingService;
        this.reconciliationService = reconciliationService;

        this.transactionsProcessedCounter = Counter.builder("atm.transactions.processed")
                .description("Number of ATM transactions processed")
                .register(meterRegistry);

        this.transactionsFailedCounter = Counter.builder("atm.transactions.failed")
                .description("Number of ATM transactions that failed processing")
                .register(meterRegistry);
    }

    /**
     * Process ATM transaction event
     */
    @RetryableKafkaListener(
        topics = "atm-transaction-events",
        groupId = "payment-service-atm-events",
        containerFactory = "kafkaListenerContainerFactory",
        retries = 5,
        backoffMultiplier = 2.0,
        initialBackoff = 1000L
    )
    @Transactional
    public void handleATMTransaction(
            @Payload ATMTransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();

        log.info("Processing ATM transaction: transactionId={}, atmId={}, amount={}, type={}",
                event.getTransactionId(),
                event.getAtmId(),
                event.getAmount(),
                event.getTransactionType());

        try {
            // Step 1: Validate event
            validateEvent(event);

            // Step 2: Check idempotency
            if (processingService.isATMTransactionAlreadyProcessed(event.getTransactionId())) {
                log.info("ATM transaction already processed (idempotent): transactionId={}",
                        event.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Process transaction based on type
            switch (event.getTransactionType()) {
                case WITHDRAWAL:
                    processingService.processWithdrawal(event);
                    break;
                case BALANCE_INQUIRY:
                    processingService.processBalanceInquiry(event);
                    break;
                case DEPOSIT:
                    processingService.processDeposit(event);
                    break;
                case TRANSFER:
                    processingService.processTransfer(event);
                    break;
                default:
                    log.warn("Unknown ATM transaction type: {}", event.getTransactionType());
            }

            // Step 4: Reconcile transaction
            reconciliationService.reconcileATMTransaction(event);

            // Step 5: Check fraud indicators
            if (event.getAmount().compareTo(new java.math.BigDecimal("5000.00")) > 0) {
                processingService.triggerFraudCheck(event);
            }

            // Step 6: Mark as processed
            processingService.markATMTransactionProcessed(event.getTransactionId());

            // Step 7: Acknowledge
            acknowledgment.acknowledge();

            // Metrics
            transactionsProcessedCounter.increment();

            long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("ATM transaction processed successfully: transactionId={}, processingTime={}ms",
                    event.getTransactionId(), processingTime);

        } catch (Exception e) {
            log.error("Failed to process ATM transaction: transactionId={}, will retry",
                    event.getTransactionId(), e);

            transactionsFailedCounter.increment();

            throw new KafkaRetryException(
                    "Failed to process ATM transaction",
                    e,
                    event.getTransactionId().toString()
            );
        }
    }

    /**
     * Validate event
     */
    private void validateEvent(ATMTransactionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }

        if (event.getAtmId() == null) {
            throw new IllegalArgumentException("ATM ID cannot be null");
        }

        if (event.getUserId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        if (event.getAmount() == null || event.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
    }

    /**
     * Handle DLQ messages
     */
    @KafkaListener(topics = "atm-transaction-events-payment-service-dlq")
    public void handleDLQMessage(@Payload ATMTransactionEvent event) {
        log.error("ATM transaction in DLQ - manual intervention required: transactionId={}, atmId={}",
                event.getTransactionId(), event.getAtmId());

        try {
            // Log to persistent storage for manual review
            processingService.logDLQATMTransaction(
                    event.getTransactionId(),
                    event,
                    "ATM transaction processing failed permanently"
            );

            // Alert operations team
            processingService.alertOperations(
                    "HIGH",
                    "ATM transaction stuck in DLQ - reconciliation gap",
                    java.util.Map.of(
                            "transactionId", event.getTransactionId().toString(),
                            "atmId", event.getAtmId(),
                            "amount", event.getAmount().toString()
                    )
            );

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process ATM DLQ message: transactionId={}",
                    event.getTransactionId(), e);
        }
    }
}
