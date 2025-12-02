package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.BalanceUpdateEvent;
import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.wallet.domain.WalletBalance;
import com.waqiti.wallet.repository.WalletBalanceRepository;
import com.waqiti.wallet.service.BalanceService;
import com.waqiti.wallet.service.BalanceValidationService;
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
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * DLQ Handler for BalanceUpdatesConsumer
 *
 * Production-grade recovery logic for failed wallet balance updates
 *
 * Recovery Strategies:
 * 1. Insufficient Funds -> Log, notify user, mark as failed
 * 2. Negative Balance -> Immediate freeze, escalate to operations
 * 3. Integrity Violations -> Reconciliation, audit log
 * 4. Duplicate Transaction -> Idempotency check, skip if already processed
 * 5. Transient DB Errors -> RETRY with backoff
 * 6. Balance Mismatch -> Recalculate and correct
 *
 * Critical: Balance operations are financial-critical. All failures
 * trigger immediate alerts and audit logging.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Service
@Slf4j
public class BalanceUpdatesConsumerDlqHandler extends BaseDlqConsumer<Object> {

    @Autowired
    private WalletBalanceRepository balanceRepository;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private BalanceValidationService validationService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public BalanceUpdatesConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BalanceUpdatesConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BalanceUpdatesConsumer.dlq:BalanceUpdatesConsumer.dlq}",
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
            String failureReason = (String) headers.getOrDefault("kafka_exception-message", "Unknown");
            int failureCount = (int) headers.getOrDefault("kafka_dlt-original-offset", 0);

            log.warn("Processing wallet DLQ event: failureReason={} failureCount={}", failureReason, failureCount);

            // Convert to BalanceUpdateEvent
            BalanceUpdateEvent balanceEvent = convertToBalanceUpdateEvent(event);

            if (balanceEvent == null) {
                log.error("Unable to parse balance update event from DLQ");
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Categorize and handle failure
            if (isInsufficientFundsError(failureReason)) {
                return handleInsufficientFunds(balanceEvent);
            }

            if (isNegativeBalanceError(failureReason)) {
                return handleNegativeBalance(balanceEvent);
            }

            if (isIntegrityViolation(failureReason)) {
                return handleIntegrityViolation(balanceEvent);
            }

            if (isDuplicateTransactionError(failureReason)) {
                return handleDuplicateTransaction(balanceEvent);
            }

            if (isTransientDatabaseError(failureReason)) {
                return handleTransientDatabaseError(balanceEvent, failureCount);
            }

            if (isBalanceMismatchError(failureReason)) {
                return handleBalanceMismatch(balanceEvent);
            }

            if (isConcurrencyError(failureReason)) {
                return handleConcurrencyError(balanceEvent, failureCount);
            }

            // Unknown error - critical financial operation failure
            log.error("CRITICAL: Unknown wallet DLQ error: {} for account: {}",
                failureReason, balanceEvent.getAccountId());
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL: Error handling wallet DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Handle insufficient funds errors
     */
    private DlqProcessingResult handleInsufficientFunds(BalanceUpdateEvent event) {
        try {
            log.warn("Insufficient funds for balance update: accountId={} amount={} operation={}",
                event.getAccountId(), event.getAmount(), event.getOperation());

            // Mark transaction as failed
            balanceService.markTransactionFailed(
                event.getTransactionId(),
                "INSUFFICIENT_FUNDS"
            );

            // Send low balance alert
            kafkaTemplate.send("balance-alerts", Map.of(
                "accountId", event.getAccountId(),
                "alertType", "INSUFFICIENT_FUNDS",
                "operation", event.getOperation(),
                "amount", event.getAmount(),
                "currency", event.getCurrency()
            ));

            // Successfully handled - no retry needed
            return DlqProcessingResult.SUCCESS;

        } catch (Exception e) {
            log.error("Failed to handle insufficient funds: accountId={}", event.getAccountId(), e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Handle negative balance errors - CRITICAL
     */
    private DlqProcessingResult handleNegativeBalance(BalanceUpdateEvent event) {
        try {
            log.error("CRITICAL: Negative balance detected: accountId={} transactionId={}",
                event.getAccountId(), event.getTransactionId());

            // Freeze account immediately
            balanceService.freezeAccount(
                event.getAccountId(),
                "NEGATIVE_BALANCE_DETECTED",
                event.getTransactionId()
            );

            // Trigger immediate reconciliation
            balanceService.initiateReconciliation(event.getAccountId());

            // Send critical alert
            kafkaTemplate.send("critical-balance-alerts", Map.of(
                "accountId", event.getAccountId(),
                "alertType", "NEGATIVE_BALANCE",
                "transactionId", event.getTransactionId(),
                "severity", "CRITICAL",
                "timestamp", System.currentTimeMillis()
            ));

            // Mark transaction as failed and reverse
            balanceService.reverseTransaction(
                event.getTransactionId(),
                "NEGATIVE_BALANCE_PREVENTED"
            );

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to handle negative balance: accountId={}", event.getAccountId(), e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Handle balance integrity violations
     */
    private DlqProcessingResult handleIntegrityViolation(BalanceUpdateEvent event) {
        try {
            log.error("Balance integrity violation: accountId={} transactionId={}",
                event.getAccountId(), event.getTransactionId());

            // Recalculate balance from transaction history
            BigDecimal calculatedBalance = balanceService.recalculateBalance(event.getAccountId());

            // Get current balance from DB
            Optional<WalletBalance> balanceOpt = balanceRepository.findByAccountId(event.getAccountId());

            if (balanceOpt.isPresent()) {
                WalletBalance balance = balanceOpt.get();
                BigDecimal dbBalance = balance.getAvailableBalance().add(balance.getHeldBalance());

                if (calculatedBalance.compareTo(dbBalance) != 0) {
                    log.error("Balance mismatch detected: accountId={} calculated={} db={}",
                        event.getAccountId(), calculatedBalance, dbBalance);

                    // Correct the balance
                    balanceService.correctBalance(
                        event.getAccountId(),
                        calculatedBalance,
                        "INTEGRITY_VIOLATION_CORRECTION"
                    );

                    // Create audit trail
                    balanceService.auditBalanceCorrection(
                        event.getAccountId(),
                        dbBalance,
                        calculatedBalance,
                        event.getTransactionId()
                    );
                }
            }

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("Failed to handle integrity violation: accountId={}", event.getAccountId(), e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Handle duplicate transaction errors (idempotency)
     */
    private DlqProcessingResult handleDuplicateTransaction(BalanceUpdateEvent event) {
        try {
            log.info("Duplicate transaction detected: transactionId={}", event.getTransactionId());

            // Check if transaction already processed
            boolean alreadyProcessed = balanceService.isTransactionProcessed(event.getTransactionId());

            if (alreadyProcessed) {
                log.info("Transaction already processed, skipping: transactionId={}", event.getTransactionId());
                return DlqProcessingResult.SUCCESS;
            }

            // Not a duplicate - retry
            log.warn("False positive duplicate: transactionId={} - retrying", event.getTransactionId());
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("Failed to handle duplicate transaction: transactionId={}", event.getTransactionId(), e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Handle transient database errors
     */
    private DlqProcessingResult handleTransientDatabaseError(BalanceUpdateEvent event, int failureCount) {
        if (failureCount >= 5) {
            log.error("Max retry attempts exceeded for DB error: accountId={}", event.getAccountId());

            // Escalate to manual intervention
            balanceService.createManualReviewTicket(
                event.getAccountId(),
                event.getTransactionId(),
                "MAX_DB_RETRIES_EXCEEDED"
            );

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        log.info("Scheduling retry for transient DB error: accountId={} attempt={}",
            event.getAccountId(), failureCount + 1);
        return DlqProcessingResult.RETRY;
    }

    /**
     * Handle balance mismatch errors
     */
    private DlqProcessingResult handleBalanceMismatch(BalanceUpdateEvent event) {
        try {
            log.warn("Balance mismatch: accountId={}", event.getAccountId());

            // Validate balance integrity
            boolean isValid = validationService.validateBalanceIntegrity(event.getAccountId());

            if (!isValid) {
                // Trigger full reconciliation
                balanceService.initiateFullReconciliation(
                    event.getAccountId(),
                    "BALANCE_MISMATCH_DETECTED"
                );

                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Integrity OK - retry the operation
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("Failed to handle balance mismatch: accountId={}", event.getAccountId(), e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Handle optimistic locking / concurrency errors
     */
    private DlqProcessingResult handleConcurrencyError(BalanceUpdateEvent event, int failureCount) {
        if (failureCount >= 3) {
            log.error("Max retry attempts for concurrency error: accountId={}", event.getAccountId());
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        log.info("Retrying due to concurrency conflict: accountId={} attempt={}",
            event.getAccountId(), failureCount + 1);
        return DlqProcessingResult.RETRY;
    }

    // ========== Helper Methods ==========

    private BalanceUpdateEvent convertToBalanceUpdateEvent(Object event) {
        try {
            if (event instanceof BalanceUpdateEvent) {
                return (BalanceUpdateEvent) event;
            }
            return objectMapper.convertValue(event, BalanceUpdateEvent.class);
        } catch (Exception e) {
            log.error("Failed to convert event to BalanceUpdateEvent", e);
            return null;
        }
    }

    private boolean isInsufficientFundsError(String reason) {
        return reason != null && (
            reason.contains("Insufficient") ||
            reason.contains("insufficient") ||
            reason.contains("NSF") ||
            reason.contains("balance")
        );
    }

    private boolean isNegativeBalanceError(String reason) {
        return reason != null && (
            reason.contains("negative") ||
            reason.contains("Negative") ||
            reason.contains("below zero")
        );
    }

    private boolean isIntegrityViolation(String reason) {
        return reason != null && (
            reason.contains("integrity") ||
            reason.contains("mismatch") ||
            reason.contains("reconciliation")
        );
    }

    private boolean isDuplicateTransactionError(String reason) {
        return reason != null && (
            reason.contains("duplicate") ||
            reason.contains("idempotency") ||
            reason.contains("already processed")
        );
    }

    private boolean isTransientDatabaseError(String reason) {
        return reason != null && (
            reason.contains("timeout") ||
            reason.contains("connection") ||
            reason.contains("deadlock") ||
            reason.contains("temporary") ||
            reason.contains("unavailable")
        );
    }

    private boolean isBalanceMismatchError(String reason) {
        return reason != null && (
            reason.contains("mismatch") ||
            reason.contains("inconsistent") ||
            reason.contains("expected")
        );
    }

    private boolean isConcurrencyError(String reason) {
        return reason != null && (
            reason.contains("OptimisticLock") ||
            reason.contains("version") ||
            reason.contains("concurrent") ||
            reason.contains("modified")
        );
    }

    @Override
    protected String getServiceName() {
        return "BalanceUpdatesConsumer";
    }

    @Override
    protected boolean isCriticalEvent(Object event) {
        // All wallet balance updates are critical
        return true;
    }
}
