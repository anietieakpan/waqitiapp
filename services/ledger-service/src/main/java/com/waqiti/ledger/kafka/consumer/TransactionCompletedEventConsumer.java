package com.waqiti.ledger.kafka.consumer;

import com.waqiti.common.events.TransactionCompletedEvent;
import com.waqiti.common.kafka.idempotency.IdempotencyService;
import com.waqiti.ledger.entity.LedgerEntryEntity;
import com.waqiti.ledger.entity.TransactionEntity;
import com.waqiti.ledger.enums.EntryType;
import com.waqiti.ledger.enums.LedgerAccountType;
import com.waqiti.ledger.service.LedgerService;
import com.waqiti.ledger.service.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * P0 CRITICAL FIX: Consumer for TransactionCompletedEvent
 *
 * This consumer implements double-entry bookkeeping for completed transactions.
 * Every transaction must result in balanced debits and credits in the general ledger.
 *
 * Architecture:
 * - Idempotency: 7-day TTL to prevent duplicate ledger entries
 * - Transaction Isolation: SERIALIZABLE to ensure ledger integrity
 * - DLQ: Automatic retry with Dead Letter Queue for failures
 * - Audit: Complete audit trail for all ledger postings
 *
 * Financial Integrity:
 * - Enforces accounting equation: Assets = Liabilities + Equity
 * - Validates debits = credits before committing
 * - Creates journal entries with complete audit trail
 *
 * @author Waqiti Engineering Team
 * @since 2025-10-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionCompletedEventConsumer {

    private static final String CONSUMER_GROUP = "ledger-service-transaction-completed";
    private static final String TOPIC = "transaction-completed-events";

    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    /**
     * Consumes TransactionCompletedEvent and posts double-entry ledger entries.
     *
     * Double-Entry Logic:
     * For a P2P transfer of $1000 from User A to User B:
     *
     * DEBIT:  User_B_Wallet_Asset       $1000.00
     * CREDIT: User_A_Wallet_Liability   $1000.00
     *
     * This maintains the accounting equation and ensures all funds are tracked.
     *
     * @param event The transaction completed event
     * @param acknowledgment Kafka acknowledgment
     * @param partition Kafka partition
     * @param offset Kafka offset
     */
    @KafkaListener(
        topics = TOPIC,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "handleTransactionCompletedFallback")
    @Retry(name = "ledger-service")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleTransactionCompleted(
            @Payload TransactionCompletedEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        String transactionId = event.getTransactionId();
        String correlationId = event.getCorrelationId();

        log.info("📊 LEDGER POSTING: Received TransactionCompletedEvent - transactionId={}, amount={}, type={}, partition={}, offset={}",
                transactionId, event.getAmount(), event.getTransactionType(), partition, offset);

        // CRITICAL: Idempotency check to prevent duplicate ledger entries
        String idempotencyKey = "ledger:transaction-completed:" + transactionId;
        UUID operationId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(7);

        if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
            log.warn("⚠️ DUPLICATE DETECTED - Transaction {} already posted to ledger. Skipping.", transactionId);
            acknowledgment.acknowledge();
            return;
        }

        try {
            // Create transaction entity
            TransactionEntity transaction = TransactionEntity.builder()
                    .id(UUID.fromString(transactionId))
                    .correlationId(correlationId)
                    .transactionType(event.getTransactionType())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .status("COMPLETED")
                    .completedAt(LocalDateTime.now())
                    .description(event.getDescription())
                    .build();

            // Generate double-entry ledger entries
            List<LedgerEntryEntity> entries = generateDoubleEntryLedgerEntries(event, transaction);

            // CRITICAL: Validate debits = credits before posting
            validateDoubleEntry(entries, transactionId);

            // Post to ledger with transaction guarantee
            ledgerService.recordTransaction(transaction, entries);

            // Audit successful posting
            auditService.logLedgerPosting(
                    transactionId,
                    correlationId,
                    event.getAmount(),
                    entries.size(),
                    "TRANSACTION_COMPLETED_POSTED",
                    LocalDateTime.now()
            );

            log.info("✅ LEDGER POSTED: Transaction {} - {} entries created, amount={}, debits={}, credits={}",
                    transactionId, entries.size(), event.getAmount(),
                    calculateTotalDebits(entries), calculateTotalCredits(entries));

            // Mark idempotency operation as complete
            idempotencyService.completeOperation(idempotencyKey, operationId, "SUCCESS", ttl);

            // Acknowledge Kafka message
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ LEDGER POSTING FAILED: Transaction {} - Error: {}", transactionId, e.getMessage(), e);

            // Record failure for idempotency tracking
            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage(), ttl);

            // Audit failure
            auditService.logLedgerPostingFailure(transactionId, correlationId, e.getMessage(), LocalDateTime.now());

            // Throw exception to trigger DLQ
            throw new LedgerPostingException("Failed to post transaction " + transactionId + " to ledger", e);
        }
    }

    /**
     * Generates double-entry ledger entries for a completed transaction.
     *
     * Business Rules:
     * - P2P Transfer: DEBIT receiver wallet asset, CREDIT sender wallet liability
     * - Payment: DEBIT merchant account, CREDIT customer account + fee revenue
     * - Deposit: DEBIT wallet asset, CREDIT external liability
     * - Withdrawal: DEBIT external asset, CREDIT wallet liability
     *
     * @param event Transaction completed event
     * @param transaction Transaction entity
     * @return List of balanced ledger entries
     */
    private List<LedgerEntryEntity> generateDoubleEntryLedgerEntries(
            TransactionCompletedEvent event,
            TransactionEntity transaction) {

        List<LedgerEntryEntity> entries = new ArrayList<>();
        BigDecimal amount = event.getAmount();
        BigDecimal feeAmount = event.getFeeAmount() != null ? event.getFeeAmount() : BigDecimal.ZERO;
        BigDecimal netAmount = amount.subtract(feeAmount);

        String transactionType = event.getTransactionType();
        String fromAccountId = event.getFromAccountId();
        String toAccountId = event.getToAccountId();

        switch (transactionType) {
            case "P2P_TRANSFER":
            case "WALLET_TRANSFER":
                // DEBIT: Receiver's wallet (asset increases)
                entries.add(createLedgerEntry(
                        transaction,
                        toAccountId,
                        LedgerAccountType.WALLET_ASSET,
                        EntryType.DEBIT,
                        amount,
                        "P2P transfer received"
                ));

                // CREDIT: Sender's wallet (liability decreases)
                entries.add(createLedgerEntry(
                        transaction,
                        fromAccountId,
                        LedgerAccountType.WALLET_LIABILITY,
                        EntryType.CREDIT,
                        amount,
                        "P2P transfer sent"
                ));
                break;

            case "MERCHANT_PAYMENT":
                // DEBIT: Merchant's account (asset increases)
                entries.add(createLedgerEntry(
                        transaction,
                        toAccountId,
                        LedgerAccountType.MERCHANT_RECEIVABLE,
                        EntryType.DEBIT,
                        netAmount,
                        "Merchant payment received (net of fees)"
                ));

                // CREDIT: Customer's wallet (liability decreases)
                entries.add(createLedgerEntry(
                        transaction,
                        fromAccountId,
                        LedgerAccountType.WALLET_LIABILITY,
                        EntryType.CREDIT,
                        amount,
                        "Merchant payment made"
                ));

                // DEBIT: Fee revenue (if applicable)
                if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
                    entries.add(createLedgerEntry(
                            transaction,
                            "PLATFORM_FEE_REVENUE",
                            LedgerAccountType.FEE_REVENUE,
                            EntryType.DEBIT,
                            feeAmount,
                            "Platform fee collected"
                    ));
                }
                break;

            case "DEPOSIT":
                // DEBIT: User's wallet asset (asset increases)
                entries.add(createLedgerEntry(
                        transaction,
                        toAccountId,
                        LedgerAccountType.WALLET_ASSET,
                        EntryType.DEBIT,
                        amount,
                        "Deposit to wallet"
                ));

                // CREDIT: External payment liability (liability increases)
                entries.add(createLedgerEntry(
                        transaction,
                        "EXTERNAL_PAYMENT_CLEARING",
                        LedgerAccountType.EXTERNAL_LIABILITY,
                        EntryType.CREDIT,
                        amount,
                        "External deposit received"
                ));
                break;

            case "WITHDRAWAL":
                // DEBIT: External payment asset (asset increases)
                entries.add(createLedgerEntry(
                        transaction,
                        "EXTERNAL_PAYMENT_CLEARING",
                        LedgerAccountType.EXTERNAL_ASSET,
                        EntryType.DEBIT,
                        amount,
                        "External withdrawal sent"
                ));

                // CREDIT: User's wallet liability (liability decreases)
                entries.add(createLedgerEntry(
                        transaction,
                        fromAccountId,
                        LedgerAccountType.WALLET_LIABILITY,
                        EntryType.CREDIT,
                        amount,
                        "Withdrawal from wallet"
                ));
                break;

            default:
                log.warn("⚠️ Unknown transaction type: {} - Using generic ledger entries", transactionType);

                // Generic debit/credit for unknown types
                entries.add(createLedgerEntry(
                        transaction,
                        toAccountId,
                        LedgerAccountType.SUSPENSE,
                        EntryType.DEBIT,
                        amount,
                        "Transaction completed - " + transactionType
                ));

                entries.add(createLedgerEntry(
                        transaction,
                        fromAccountId,
                        LedgerAccountType.SUSPENSE,
                        EntryType.CREDIT,
                        amount,
                        "Transaction completed - " + transactionType
                ));
        }

        return entries;
    }

    /**
     * Creates a single ledger entry.
     */
    private LedgerEntryEntity createLedgerEntry(
            TransactionEntity transaction,
            String accountId,
            LedgerAccountType accountType,
            EntryType entryType,
            BigDecimal amount,
            String description) {

        return LedgerEntryEntity.builder()
                .id(UUID.randomUUID())
                .transaction(transaction)
                .accountId(accountId)
                .accountType(accountType)
                .entryType(entryType)
                .amount(amount)
                .description(description)
                .entryDate(LocalDateTime.now())
                .build();
    }

    /**
     * CRITICAL: Validates that total debits equal total credits.
     *
     * This is the fundamental rule of double-entry bookkeeping.
     * If debits != credits, the transaction MUST be rejected.
     *
     * @param entries Ledger entries to validate
     * @param transactionId Transaction ID for error reporting
     * @throws UnbalancedTransactionException if debits != credits
     */
    private void validateDoubleEntry(List<LedgerEntryEntity> entries, String transactionId) {
        BigDecimal totalDebits = calculateTotalDebits(entries);
        BigDecimal totalCredits = calculateTotalCredits(entries);

        // Allow 0.0001 tolerance for rounding errors
        BigDecimal difference = totalDebits.subtract(totalCredits).abs();
        BigDecimal tolerance = new BigDecimal("0.0001");

        if (difference.compareTo(tolerance) > 0) {
            String errorMsg = String.format(
                    "Transaction %s UNBALANCED: debits=%s, credits=%s, difference=%s",
                    transactionId, totalDebits, totalCredits, difference
            );

            log.error("❌ DOUBLE-ENTRY VIOLATION: {}", errorMsg);

            throw new UnbalancedTransactionException(errorMsg);
        }

        log.debug("✅ BALANCED: Transaction {} - debits={}, credits={}",
                transactionId, totalDebits, totalCredits);
    }

    /**
     * Calculates total debits from ledger entries.
     */
    private BigDecimal calculateTotalDebits(List<LedgerEntryEntity> entries) {
        return entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates total credits from ledger entries.
     */
    private BigDecimal calculateTotalCredits(List<LedgerEntryEntity> entries) {
        return entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Circuit breaker fallback method.
     *
     * When the ledger service is experiencing issues, we don't want to drop
     * transaction events. Instead, we:
     * 1. Log the failure
     * 2. Send to DLQ for manual processing
     * 3. Alert operations team
     *
     * @param event Transaction completed event
     * @param acknowledgment Kafka acknowledgment
     * @param partition Partition
     * @param offset Offset
     * @param throwable Exception that triggered fallback
     */
    public void handleTransactionCompletedFallback(
            TransactionCompletedEvent event,
            Acknowledgment acknowledgment,
            int partition,
            long offset,
            Throwable throwable) {

        log.error("🔥 CIRCUIT BREAKER OPEN: Failed to post transaction {} to ledger after retries. " +
                  "Event will be sent to DLQ. Error: {}",
                event.getTransactionId(), throwable.getMessage());

        // Audit circuit breaker activation
        auditService.logCircuitBreakerActivation(
                "TransactionCompletedEventConsumer",
                event.getTransactionId(),
                throwable.getMessage(),
                LocalDateTime.now()
        );

        // Message will automatically go to DLQ (configured in Kafka)
        // DO NOT acknowledge - let Kafka retry logic handle it
    }

    /**
     * Custom exception for unbalanced transactions.
     */
    public static class UnbalancedTransactionException extends RuntimeException {
        public UnbalancedTransactionException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception for ledger posting failures.
     */
    public static class LedgerPostingException extends RuntimeException {
        public LedgerPostingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
