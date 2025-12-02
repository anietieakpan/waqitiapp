package com.waqiti.reconciliation.kafka;

import com.waqiti.reconciliation.service.WalletReconciliationService;
import com.waqiti.reconciliation.service.BalanceDiscrepancyService;
import com.waqiti.reconciliation.model.WalletReconciliationRecord;
import com.waqiti.reconciliation.model.ReconciliationStatus;
import com.waqiti.common.events.WalletBalanceUpdatedEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.notification.client.NotificationServiceClient;
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
 * CRITICAL FINANCIAL CONSUMER - Wallet Balance Reconciliation
 *
 * This consumer was MISSING causing wallet balance reconciliation failures.
 *
 * Without this consumer:
 * - Wallet balance changes are not reconciled with transaction records
 * - Balance discrepancies go undetected
 * - Financial audit trail is incomplete
 * - Regulatory compliance reporting fails
 * - Customer balance disputes cannot be investigated
 *
 * IDEMPOTENCY PROTECTION:
 * - Uses Redis-backed IdempotencyService for distributed idempotency
 * - 7-day TTL for balance update reconciliation tracking
 * - CRITICAL: Prevents duplicate reconciliation entries
 *
 * Features:
 * - Real-time wallet balance reconciliation
 * - Discrepancy detection and alerting
 * - Transaction-to-balance verification
 * - Automated discrepancy resolution
 * - Audit trail maintenance
 * - Multi-currency reconciliation support
 *
 * FINANCIAL ACCURACY DEPENDS ON THIS CONSUMER - DO NOT DISABLE
 *
 * @author Waqiti Reconciliation Team
 * @version 1.0.0
 * @since 2024-11-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletBalanceUpdatedConsumer {

    private final WalletReconciliationService walletReconciliationService;
    private final BalanceDiscrepancyService discrepancyService;
    private final IdempotencyService idempotencyService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;
    private final NotificationServiceClient notificationServiceClient;

    @KafkaListener(
        topics = "wallet-balance-updated-events",
        groupId = "reconciliation-wallet-balance-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processWalletBalanceUpdate(
            @Payload WalletBalanceUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String walletId = event.getWalletId();
        String transactionId = event.getTransactionId();
        String correlationId = String.format("wallet-balance-recon-%s-%s-p%d-o%d",
            walletId, transactionId, partition, offset);

        // CRITICAL IDEMPOTENCY CHECK
        String idempotencyKey = "wallet-balance-reconciliation:" + walletId + ":" + transactionId + ":" + event.getEventId();
        UUID operationId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(7); // 7-day retention for reconciliation tracking

        try {
            if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
                log.warn("⚠️ DUPLICATE RECONCILIATION PREVENTED - Already processed: walletId={}, transactionId={}, eventId={}",
                        walletId, transactionId, event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing wallet balance reconciliation: walletId={}, transactionId={}, oldBalance={}, newBalance={}, amount={}",
                walletId, transactionId, event.getOldBalance(), event.getNewBalance(), event.getAmount());

            // Validate balance update consistency
            validateBalanceUpdate(event);

            // Create reconciliation record
            WalletReconciliationRecord reconciliationRecord = createReconciliationRecord(event, correlationId);

            // Perform reconciliation checks
            performReconciliationChecks(event, reconciliationRecord, correlationId);

            // Check for discrepancies
            checkForDiscrepancies(event, reconciliationRecord, correlationId);

            // Save reconciliation record
            walletReconciliationService.saveReconciliationRecord(reconciliationRecord);

            // Update metrics
            updateReconciliationMetrics(event, reconciliationRecord);

            // Create audit log
            auditService.logFinancialEvent(
                "WALLET_BALANCE_RECONCILED",
                event.getUserId(),
                walletId,
                "WALLET_RECONCILIATION",
                event.getAmount().doubleValue(),
                "reconciliation_consumer",
                true,
                Map.of(
                    "walletId", walletId,
                    "transactionId", transactionId,
                    "oldBalance", event.getOldBalance().toString(),
                    "newBalance", event.getNewBalance().toString(),
                    "amount", event.getAmount().toString(),
                    "operation", event.getOperation(),
                    "reconciliationStatus", reconciliationRecord.getStatus().toString(),
                    "correlationId", correlationId,
                    "eventId", event.getEventId()
                )
            );

            // Mark idempotency operation as complete
            idempotencyService.completeOperation(idempotencyKey, operationId);

            log.info("Successfully reconciled wallet balance: walletId={}, transactionId={}, status={}",
                walletId, transactionId, reconciliationRecord.getStatus());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process wallet balance reconciliation: walletId={}, transactionId={}, error={}",
                walletId, transactionId, e.getMessage(), e);

            // Send to DLQ
            kafkaTemplate.send("wallet-balance-reconciliation-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", LocalDateTime.now()
            ));

            // Send critical alert for reconciliation failures
            try {
                notificationServiceClient.sendCriticalAlert(
                    "Wallet Reconciliation Failed",
                    String.format("Failed to reconcile wallet %s for transaction %s: %s",
                        walletId, transactionId, e.getMessage()),
                    Map.of("walletId", walletId, "transactionId", transactionId, "correlationId", correlationId)
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send reconciliation failure alert: {}", notificationEx.getMessage());
            }

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        }
    }

    private void validateBalanceUpdate(WalletBalanceUpdatedEvent event) {
        // Validate balance calculation
        BigDecimal expectedNewBalance = event.getOldBalance().add(event.getAmount());

        if (!expectedNewBalance.equals(event.getNewBalance())) {
            log.error("Balance calculation mismatch: walletId={}, expected={}, actual={}",
                event.getWalletId(), expectedNewBalance, event.getNewBalance());

            throw new IllegalStateException(
                String.format("Balance mismatch: expected %s but got %s", expectedNewBalance, event.getNewBalance())
            );
        }

        // Validate non-negative balance (unless overdraft is allowed)
        if (event.getNewBalance().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Negative balance detected: walletId={}, balance={}",
                event.getWalletId(), event.getNewBalance());
        }
    }

    private WalletReconciliationRecord createReconciliationRecord(WalletBalanceUpdatedEvent event, String correlationId) {
        return WalletReconciliationRecord.builder()
            .id(UUID.randomUUID().toString())
            .walletId(event.getWalletId())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .eventId(event.getEventId())
            .oldBalance(event.getOldBalance())
            .newBalance(event.getNewBalance())
            .amount(event.getAmount())
            .operation(event.getOperation())
            .currency(event.getCurrency())
            .status(ReconciliationStatus.PENDING)
            .correlationId(correlationId)
            .reconciledAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .build();
    }

    private void performReconciliationChecks(WalletBalanceUpdatedEvent event,
                                            WalletReconciliationRecord record,
                                            String correlationId) {
        // Verify transaction exists and matches
        boolean transactionMatches = walletReconciliationService.verifyTransactionMatch(
            event.getTransactionId(),
            event.getAmount(),
            event.getWalletId()
        );

        if (!transactionMatches) {
            record.setStatus(ReconciliationStatus.MISMATCH);
            record.setDiscrepancyReason("Transaction amount or wallet mismatch");
            log.warn("Transaction mismatch detected: transactionId={}, walletId={}",
                event.getTransactionId(), event.getWalletId());
            return;
        }

        // Verify balance ledger consistency
        boolean ledgerConsistent = walletReconciliationService.verifyLedgerConsistency(
            event.getWalletId(),
            event.getNewBalance()
        );

        if (!ledgerConsistent) {
            record.setStatus(ReconciliationStatus.LEDGER_MISMATCH);
            record.setDiscrepancyReason("Ledger balance inconsistency");
            log.warn("Ledger mismatch detected: walletId={}", event.getWalletId());
            return;
        }

        // All checks passed
        record.setStatus(ReconciliationStatus.RECONCILED);
        log.info("Reconciliation checks passed: walletId={}, transactionId={}",
            event.getWalletId(), event.getTransactionId());
    }

    private void checkForDiscrepancies(WalletBalanceUpdatedEvent event,
                                      WalletReconciliationRecord record,
                                      String correlationId) {
        if (record.getStatus() == ReconciliationStatus.RECONCILED) {
            return; // No discrepancies
        }

        // Create discrepancy record
        discrepancyService.createDiscrepancy(
            event.getWalletId(),
            event.getTransactionId(),
            event.getAmount(),
            record.getStatus(),
            record.getDiscrepancyReason(),
            correlationId
        );

        // Send discrepancy alert
        kafkaTemplate.send("reconciliation-discrepancy-events", Map.of(
            "type", "WALLET_BALANCE_DISCREPANCY",
            "walletId", event.getWalletId(),
            "transactionId", event.getTransactionId(),
            "amount", event.getAmount(),
            "status", record.getStatus(),
            "reason", record.getDiscrepancyReason(),
            "correlationId", correlationId,
            "timestamp", LocalDateTime.now()
        ));

        log.error("Reconciliation discrepancy detected: walletId={}, status={}, reason={}",
            event.getWalletId(), record.getStatus(), record.getDiscrepancyReason());
    }

    private void updateReconciliationMetrics(WalletBalanceUpdatedEvent event, WalletReconciliationRecord record) {
        // Update metrics for monitoring
        walletReconciliationService.updateMetrics(
            record.getStatus(),
            event.getOperation(),
            event.getCurrency()
        );
    }
}
