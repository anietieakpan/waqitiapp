package com.waqiti.ledger.kafka.consumer;

import com.waqiti.common.events.PaymentInitiatedEvent;
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
 * P0 CRITICAL FIX: Consumer for PaymentInitiatedEvent
 *
 * This consumer creates PENDING ledger entries when a payment is initiated,
 * providing immediate visibility into in-flight transactions.
 *
 * Business Flow:
 * 1. Payment initiated → Create PENDING ledger entries
 * 2. Payment completed → Update entries to COMPLETED (via TransactionCompletedEvent)
 * 3. Payment failed → Reverse entries (via PaymentFailedEvent)
 *
 * Accounting Treatment:
 * Payment initiation creates entries in clearing accounts:
 * - DEBIT: Payment_Clearing_Asset
 * - CREDIT: Customer_Wallet_Liability
 *
 * Upon completion, these are transferred to final accounts.
 *
 * @author Waqiti Engineering Team
 * @since 2025-10-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentInitiatedEventConsumer {

    private static final String CONSUMER_GROUP = "ledger-service-payment-initiated";
    private static final String TOPIC = "payment-initiated-events";

    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    /**
     * Consumes PaymentInitiatedEvent and creates pending ledger entries.
     *
     * Double-Entry for Payment Initiation ($1000 payment):
     *
     * DEBIT:  Payment_Clearing_Asset     $1000.00
     * CREDIT: Customer_Wallet_Liability  $1000.00
     *
     * This ensures the payment is tracked immediately, providing real-time
     * visibility into pending transactions for reconciliation.
     *
     * @param event Payment initiated event
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
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "handlePaymentInitiatedFallback")
    @Retry(name = "ledger-service")
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void handlePaymentInitiated(
            @Payload PaymentInitiatedEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        String paymentId = event.getPaymentId();
        String correlationId = event.getCorrelationId();

        log.info("📝 LEDGER PENDING: Received PaymentInitiatedEvent - paymentId={}, amount={}, type={}, partition={}, offset={}",
                paymentId, event.getAmount(), event.getPaymentType(), partition, offset);

        // CRITICAL: Idempotency check
        String idempotencyKey = "ledger:payment-initiated:" + paymentId;
        UUID operationId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(7);

        if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
            log.warn("⚠️ DUPLICATE DETECTED - Payment {} already in ledger (PENDING). Skipping.", paymentId);
            acknowledgment.acknowledge();
            return;
        }

        try {
            // Create transaction entity with PENDING status
            TransactionEntity transaction = TransactionEntity.builder()
                    .id(UUID.fromString(paymentId))
                    .correlationId(correlationId)
                    .transactionType("PAYMENT_INITIATED")
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .status("PENDING")
                    .initiatedAt(LocalDateTime.now())
                    .description(event.getDescription())
                    .metadata(buildMetadata(event))
                    .build();

            // Generate pending ledger entries
            List<LedgerEntryEntity> entries = generatePendingLedgerEntries(event, transaction);

            // Validate double-entry
            validateDoubleEntry(entries, paymentId);

            // Record pending transaction
            ledgerService.recordPendingTransaction(transaction, entries);

            // Audit
            auditService.logLedgerPosting(
                    paymentId,
                    correlationId,
                    event.getAmount(),
                    entries.size(),
                    "PAYMENT_INITIATED_PENDING",
                    LocalDateTime.now()
            );

            log.info("✅ LEDGER PENDING POSTED: Payment {} - {} entries created, amount={}, status=PENDING",
                    paymentId, entries.size(), event.getAmount());

            // Mark operation complete
            idempotencyService.completeOperation(idempotencyKey, operationId, "SUCCESS", ttl);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ LEDGER PENDING FAILED: Payment {} - Error: {}", paymentId, e.getMessage(), e);

            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage(), ttl);

            auditService.logLedgerPostingFailure(paymentId, correlationId, e.getMessage(), LocalDateTime.now());

            throw new LedgerPostingException("Failed to post pending payment " + paymentId + " to ledger", e);
        }
    }

    /**
     * Generates pending ledger entries for initiated payment.
     *
     * Clearing Account Pattern:
     * - All initiated payments go to clearing accounts
     * - Upon completion, transferred to final accounts
     * - Upon failure, reversed from clearing accounts
     *
     * This provides real-time visibility into in-flight transactions.
     */
    private List<LedgerEntryEntity> generatePendingLedgerEntries(
            PaymentInitiatedEvent event,
            TransactionEntity transaction) {

        List<LedgerEntryEntity> entries = new ArrayList<>();
        BigDecimal amount = event.getAmount();

        String paymentType = event.getPaymentType();
        String fromUserId = event.getFromUserId();
        String toUserId = event.getToUserId();
        String merchantId = event.getMerchantId();

        switch (paymentType) {
            case "P2P_TRANSFER":
                // DEBIT: Payment clearing (asset increases - funds in transit)
                entries.add(createLedgerEntry(
                        transaction,
                        "PAYMENT_CLEARING",
                        LedgerAccountType.CLEARING_ASSET,
                        EntryType.DEBIT,
                        amount,
                        "P2P payment initiated - pending completion",
                        "PENDING"
                ));

                // CREDIT: Sender wallet liability (funds reserved)
                entries.add(createLedgerEntry(
                        transaction,
                        fromUserId,
                        LedgerAccountType.WALLET_LIABILITY,
                        EntryType.CREDIT,
                        amount,
                        "P2P payment pending - funds reserved",
                        "PENDING"
                ));
                break;

            case "MERCHANT_PAYMENT":
                // DEBIT: Merchant clearing (pending settlement)
                entries.add(createLedgerEntry(
                        transaction,
                        merchantId != null ? merchantId : "MERCHANT_CLEARING",
                        LedgerAccountType.MERCHANT_CLEARING,
                        EntryType.DEBIT,
                        amount,
                        "Merchant payment initiated - pending settlement",
                        "PENDING"
                ));

                // CREDIT: Customer wallet (funds reserved)
                entries.add(createLedgerEntry(
                        transaction,
                        fromUserId,
                        LedgerAccountType.WALLET_LIABILITY,
                        EntryType.CREDIT,
                        amount,
                        "Merchant payment pending",
                        "PENDING"
                ));
                break;

            case "BILL_PAYMENT":
                // DEBIT: Bill payment clearing
                entries.add(createLedgerEntry(
                        transaction,
                        "BILL_PAYMENT_CLEARING",
                        LedgerAccountType.CLEARING_ASSET,
                        EntryType.DEBIT,
                        amount,
                        "Bill payment initiated",
                        "PENDING"
                ));

                // CREDIT: Customer wallet
                entries.add(createLedgerEntry(
                        transaction,
                        fromUserId,
                        LedgerAccountType.WALLET_LIABILITY,
                        EntryType.CREDIT,
                        amount,
                        "Bill payment pending",
                        "PENDING"
                ));
                break;

            default:
                log.warn("⚠️ Unknown payment type: {} - Using generic clearing entries", paymentType);

                // Generic clearing entries
                entries.add(createLedgerEntry(
                        transaction,
                        "GENERIC_PAYMENT_CLEARING",
                        LedgerAccountType.CLEARING_ASSET,
                        EntryType.DEBIT,
                        amount,
                        "Payment initiated - " + paymentType,
                        "PENDING"
                ));

                entries.add(createLedgerEntry(
                        transaction,
                        fromUserId,
                        LedgerAccountType.WALLET_LIABILITY,
                        EntryType.CREDIT,
                        amount,
                        "Payment pending - " + paymentType,
                        "PENDING"
                ));
        }

        return entries;
    }

    /**
     * Creates a single ledger entry with PENDING status.
     */
    private LedgerEntryEntity createLedgerEntry(
            TransactionEntity transaction,
            String accountId,
            LedgerAccountType accountType,
            EntryType entryType,
            BigDecimal amount,
            String description,
            String status) {

        return LedgerEntryEntity.builder()
                .id(UUID.randomUUID())
                .transaction(transaction)
                .accountId(accountId)
                .accountType(accountType)
                .entryType(entryType)
                .amount(amount)
                .description(description)
                .status(status)
                .entryDate(LocalDateTime.now())
                .build();
    }

    /**
     * Validates double-entry balance.
     */
    private void validateDoubleEntry(List<LedgerEntryEntity> entries, String paymentId) {
        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal difference = totalDebits.subtract(totalCredits).abs();
        BigDecimal tolerance = new BigDecimal("0.0001");

        if (difference.compareTo(tolerance) > 0) {
            throw new UnbalancedTransactionException(
                    String.format("Payment %s UNBALANCED: debits=%s, credits=%s, difference=%s",
                            paymentId, totalDebits, totalCredits, difference)
            );
        }
    }

    /**
     * Builds metadata JSON for transaction entity.
     */
    private String buildMetadata(PaymentInitiatedEvent event) {
        return String.format(
                "{\"paymentType\":\"%s\",\"fromUserId\":\"%s\",\"toUserId\":\"%s\",\"merchantId\":\"%s\",\"deviceId\":\"%s\"}",
                event.getPaymentType(),
                event.getFromUserId(),
                event.getToUserId(),
                event.getMerchantId(),
                event.getDeviceId()
        );
    }

    /**
     * Circuit breaker fallback.
     */
    public void handlePaymentInitiatedFallback(
            PaymentInitiatedEvent event,
            Acknowledgment acknowledgment,
            int partition,
            long offset,
            Throwable throwable) {

        log.error("🔥 CIRCUIT BREAKER OPEN: Failed to post pending payment {} to ledger. Event will be sent to DLQ. Error: {}",
                event.getPaymentId(), throwable.getMessage());

        auditService.logCircuitBreakerActivation(
                "PaymentInitiatedEventConsumer",
                event.getPaymentId(),
                throwable.getMessage(),
                LocalDateTime.now()
        );
    }

    /**
     * Custom exceptions.
     */
    public static class UnbalancedTransactionException extends RuntimeException {
        public UnbalancedTransactionException(String message) {
            super(message);
        }
    }

    public static class LedgerPostingException extends RuntimeException {
        public LedgerPostingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
