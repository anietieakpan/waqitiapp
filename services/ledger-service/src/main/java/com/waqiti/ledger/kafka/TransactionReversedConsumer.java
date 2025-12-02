package com.waqiti.ledger.kafka;

import com.waqiti.ledger.service.LedgerService;
import com.waqiti.ledger.service.DoubleEntryBookkeepingService;
import com.waqiti.ledger.model.LedgerEntry;
import com.waqiti.ledger.model.LedgerEntryType;
import com.waqiti.common.events.TransactionReversedEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FINANCIAL CONSUMER - Transaction Reversal Handler
 *
 * This consumer was MISSING causing ledger inconsistencies.
 *
 * Without this consumer:
 * - Transaction reversals are not recorded in the ledger
 * - Double-entry bookkeeping is broken
 * - Financial statements become inaccurate
 * - Audit trail has gaps
 * - Regulatory compliance fails
 * - Account balances do not reconcile
 *
 * IDEMPOTENCY PROTECTION:
 * - Uses Redis-backed IdempotencyService for distributed idempotency
 * - 90-day TTL for reversal tracking (regulatory requirement)
 * - CRITICAL: Prevents duplicate reversal entries in ledger
 *
 * Features:
 * - Double-entry bookkeeping for reversals
 * - Automatic ledger entry creation
 * - Account balance adjustment
 * - Audit trail maintenance
 * - Reversal reason tracking
 * - Multi-currency support
 * - Regulatory compliance reporting
 *
 * FINANCIAL ACCURACY DEPENDS ON THIS CONSUMER - DO NOT DISABLE
 *
 * @author Waqiti Ledger Team
 * @version 1.0.0
 * @since 2024-11-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionReversedConsumer {

    private final LedgerService ledgerService;
    private final DoubleEntryBookkeepingService bookkeepingService;
    private final IdempotencyService idempotencyService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;

    @KafkaListener(
        topics = "transaction-reversed",
        groupId = "ledger-transaction-reversal-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processTransactionReversal(
            @Payload TransactionReversedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String transactionId = event.getTransactionId();
        String reversalId = event.getReversalId();
        String correlationId = String.format("transaction-reversal-%s-%s-p%d-o%d",
            transactionId, reversalId, partition, offset);

        // CRITICAL IDEMPOTENCY CHECK
        String idempotencyKey = "transaction-reversal:" + transactionId + ":" + reversalId + ":" + event.getEventId();
        UUID operationId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(90); // 90-day retention for regulatory compliance

        try {
            if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
                log.warn("  DUPLICATE REVERSAL PREVENTED - Already processed: transactionId={}, reversalId={}, eventId={}",
                        transactionId, reversalId, event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing transaction reversal: transactionId={}, reversalId={}, amount={}, reason={}",
                transactionId, reversalId, event.getAmount(), event.getReversalReason());

            // Retrieve original transaction ledger entries
            var originalEntries = ledgerService.findEntriesByTransactionId(transactionId);

            if (originalEntries.isEmpty()) {
                log.error("Original transaction not found in ledger: transactionId={}", transactionId);
                throw new IllegalStateException("Original transaction ledger entries not found");
            }

            // Create reversal ledger entries (opposite of original)
            createReversalLedgerEntries(event, originalEntries, correlationId);

            // Update account balances
            updateAccountBalances(event, originalEntries, correlationId);

            // Record reversal metadata
            recordReversalMetadata(event, correlationId);

            // Send reconciliation event
            sendReconciliationEvent(event, correlationId);

            // Update ledger metrics
            updateLedgerMetrics(event);

            // Create comprehensive audit log
            auditService.logFinancialEvent(
                "TRANSACTION_REVERSED",
                event.getUserId(),
                transactionId,
                "REVERSAL",
                event.getAmount().doubleValue(),
                "ledger_consumer",
                true,
                Map.of(
                    "transactionId", transactionId,
                    "reversalId", reversalId,
                    "amount", event.getAmount().toString(),
                    "currency", event.getCurrency(),
                    "reversalReason", event.getReversalReason(),
                    "originalTransactionDate", event.getOriginalTransactionDate().toString(),
                    "reversalDate", event.getReversalDate().toString(),
                    "correlationId", correlationId,
                    "eventId", event.getEventId()
                )
            );

            // Mark idempotency operation as complete
            idempotencyService.completeOperation(idempotencyKey, operationId);

            log.info("Successfully processed transaction reversal: transactionId={}, reversalId={}, amount={}",
                transactionId, reversalId, event.getAmount());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process transaction reversal: transactionId={}, reversalId={}, error={}",
                transactionId, reversalId, e.getMessage(), e);

            // Send to DLQ
            kafkaTemplate.send("transaction-reversal-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", LocalDateTime.now()
            ));

            // Send critical alert
            kafkaTemplate.send("critical-alerts", Map.of(
                "alertType", "LEDGER_REVERSAL_FAILED",
                "severity", "CRITICAL",
                "message", String.format("Failed to process transaction reversal: %s", transactionId),
                "transactionId", transactionId,
                "reversalId", reversalId,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", LocalDateTime.now()
            ));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        }
    }

    private void createReversalLedgerEntries(TransactionReversedEvent event,
                                            java.util.List<LedgerEntry> originalEntries,
                                            String correlationId) {
        log.info("Creating reversal ledger entries: transactionId={}, entriesCount={}",
            event.getTransactionId(), originalEntries.size());

        for (LedgerEntry original : originalEntries) {
            // Create opposite entry for reversal
            LedgerEntry reversalEntry = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(event.getTransactionId())
                .reversalId(event.getReversalId())
                .accountId(original.getAccountId())
                .amount(original.getAmount())
                .entryType(reverseEntryType(original.getEntryType())) // DEBIT <-> CREDIT
                .currency(event.getCurrency())
                .description(String.format("REVERSAL: %s - Reason: %s",
                    original.getDescription(), event.getReversalReason()))
                .reversalReason(event.getReversalReason())
                .originalEntryId(original.getId())
                .entryDate(event.getReversalDate())
                .correlationId(correlationId)
                .createdAt(LocalDateTime.now())
                .build();

            ledgerService.createEntry(reversalEntry);
        }

        log.info("Created {} reversal ledger entries for transactionId={}",
            originalEntries.size(), event.getTransactionId());
    }

    private LedgerEntryType reverseEntryType(LedgerEntryType originalType) {
        return originalType == LedgerEntryType.DEBIT ? LedgerEntryType.CREDIT : LedgerEntryType.DEBIT;
    }

    private void updateAccountBalances(TransactionReversedEvent event,
                                      java.util.List<LedgerEntry> originalEntries,
                                      String correlationId) {
        log.info("Updating account balances for reversal: transactionId={}", event.getTransactionId());

        for (LedgerEntry original : originalEntries) {
            // Reverse the balance change
            BigDecimal balanceAdjustment = original.getEntryType() == LedgerEntryType.DEBIT
                ? original.getAmount()  // DEBIT reversal -> add back
                : original.getAmount().negate(); // CREDIT reversal -> subtract back

            ledgerService.adjustAccountBalance(
                original.getAccountId(),
                balanceAdjustment,
                String.format("Reversal of transaction %s", event.getTransactionId()),
                correlationId
            );
        }

        log.info("Updated account balances for {} accounts", originalEntries.size());
    }

    private void recordReversalMetadata(TransactionReversedEvent event, String correlationId) {
        ledgerService.recordReversalMetadata(
            event.getReversalId(),
            event.getTransactionId(),
            event.getReversalReason(),
            event.getReversalDate(),
            event.getInitiatedBy(),
            correlationId
        );
    }

    private void sendReconciliationEvent(TransactionReversedEvent event, String correlationId) {
        // Notify reconciliation service
        kafkaTemplate.send("ledger-reconciliation-events", Map.of(
            "eventType", "TRANSACTION_REVERSAL",
            "transactionId", event.getTransactionId(),
            "reversalId", event.getReversalId(),
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "reversalDate", event.getReversalDate(),
            "correlationId", correlationId,
            "timestamp", LocalDateTime.now()
        ));
    }

    private void updateLedgerMetrics(TransactionReversedEvent event) {
        ledgerService.updateMetrics(
            "REVERSAL",
            event.getReversalReason(),
            event.getCurrency(),
            event.getAmount()
        );
    }
}
