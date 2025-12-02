package com.waqiti.transaction.rollback;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Production-grade compensation action service for transaction rollbacks
 * Generates and executes compensation actions to restore system state
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationActionService {

    private final WalletCompensationService walletCompensationService;
    private final LedgerCompensationService ledgerCompensationService;
    private final ExternalSystemCompensationService externalSystemCompensationService;
    private final NotificationCompensationService notificationCompensationService;

    /**
     * Generate compensation actions for a transaction rollback
     */
    public List<CompensationAction> generateCompensationActions(Transaction transaction) {
        log.info("SECURITY: Generating compensation actions for transaction: {}", transaction.getId());

        List<CompensationAction> actions = new ArrayList<>();

        // 1. Wallet compensation actions
        if (walletCompensationService != null) {
            actions.addAll(walletCompensationService.generateActions(transaction));
        }

        // 2. Ledger compensation actions
        if (ledgerCompensationService != null) {
            actions.addAll(ledgerCompensationService.generateActions(transaction));
        }

        // 3. External system compensation actions
        if (externalSystemCompensationService != null) {
            actions.addAll(externalSystemCompensationService.generateActions(transaction));
        }

        // 4. Notification compensation actions
        if (notificationCompensationService != null) {
            actions.addAll(notificationCompensationService.generateActions(transaction));
        }

        log.info("SECURITY: Generated {} compensation actions for transaction: {}", 
                actions.size(), transaction.getId());

        return actions;
    }

    /**
     * Execute compensation actions in correct order
     */
    public CompensationResult executeCompensationActions(List<CompensationAction> actions) {
        log.info("SECURITY: Executing {} compensation actions", actions.size());

        CompensationResult.CompensationResultBuilder resultBuilder = CompensationResult.builder()
                .startedAt(LocalDateTime.now());

        List<CompensationAction> successfulActions = new ArrayList<>();
        List<CompensationAction> failedActions = new ArrayList<>();

        try {
            // Sort actions by priority (higher priority first)
            actions.sort((a1, a2) -> Integer.compare(a2.getPriority(), a1.getPriority()));

            // Execute each action
            for (CompensationAction action : actions) {
                try {
                    log.debug("SECURITY: Executing compensation action: {} for transaction: {}", 
                            action.getActionType(), action.getTransactionId());

                    executeCompensationAction(action);
                    
                    action.setStatus(CompensationActionStatus.COMPLETED);
                    action.setCompletedAt(LocalDateTime.now());
                    successfulActions.add(action);

                    log.debug("SECURITY: Compensation action completed: {}", action.getActionType());

                } catch (Exception e) {
                    log.error("SECURITY: Compensation action failed: {} for transaction: {}", 
                            action.getActionType(), action.getTransactionId(), e);

                    action.setStatus(CompensationActionStatus.FAILED);
                    action.setFailedAt(LocalDateTime.now());
                    action.setFailureReason(e.getMessage());
                    failedActions.add(action);

                    // Decide whether to continue or halt based on action criticality
                    if (action.isCritical()) {
                        log.error("CRITICAL: Critical compensation action failed, halting rollback: {}", 
                                action.getActionType());
                        break;
                    }
                }
            }

            // Determine overall result
            CompensationResultStatus overallStatus;
            if (failedActions.isEmpty()) {
                overallStatus = CompensationResultStatus.ALL_COMPLETED;
            } else if (successfulActions.isEmpty()) {
                overallStatus = CompensationResultStatus.ALL_FAILED;
            } else {
                overallStatus = CompensationResultStatus.PARTIAL_SUCCESS;
            }

            return resultBuilder
                    .overallResult(overallStatus.toString())
                    .actionsExecuted(actions.size())
                    .successfulActions(successfulActions.size())
                    .failedActions(failedActions.size())
                    .completedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("CRITICAL: Compensation execution failed", e);

            return resultBuilder
                    .overallResult(CompensationResultStatus.EXECUTION_FAILED.toString())
                    .actionsExecuted(0)
                    .failedAt(LocalDateTime.now())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Generate wallet compensation actions
     */
    private List<CompensationAction> generateWalletCompensationActions(Transaction transaction) {
        List<CompensationAction> actions = new ArrayList<>();

        switch (transaction.getType()) {
            case TRANSFER:
                // Reverse the transfer by crediting source and debiting target
                actions.add(CompensationAction.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transaction.getId())
                        .actionType(CompensationActionType.CREDIT_WALLET)
                        .targetWalletId(transaction.getSourceWalletId())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .priority(10)
                        .critical(true)
                        .description("Restore funds to source wallet")
                        .build());

                actions.add(CompensationAction.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transaction.getId())
                        .actionType(CompensationActionType.DEBIT_WALLET)
                        .targetWalletId(transaction.getTargetWalletId())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .priority(9)
                        .critical(true)
                        .description("Remove funds from target wallet")
                        .build());
                break;

            case DEPOSIT:
                // Reverse deposit by debiting the wallet
                actions.add(CompensationAction.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transaction.getId())
                        .actionType(CompensationActionType.DEBIT_WALLET)
                        .targetWalletId(transaction.getTargetWalletId())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .priority(10)
                        .critical(true)
                        .description("Reverse deposit")
                        .build());
                break;

            case WITHDRAWAL:
                // Reverse withdrawal by crediting the wallet
                actions.add(CompensationAction.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transaction.getId())
                        .actionType(CompensationActionType.CREDIT_WALLET)
                        .targetWalletId(transaction.getSourceWalletId())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .priority(10)
                        .critical(true)
                        .description("Reverse withdrawal")
                        .build());
                break;
        }

        return actions;
    }

    /**
     * Generate ledger compensation actions
     */
    private List<CompensationAction> generateLedgerCompensationActions(Transaction transaction) {
        List<CompensationAction> actions = new ArrayList<>();

        // Create reversal ledger entries
        actions.add(CompensationAction.builder()
                .id(UUID.randomUUID())
                .transactionId(transaction.getId())
                .actionType(CompensationActionType.CREATE_REVERSAL_LEDGER_ENTRY)
                .ledgerAccountId(transaction.getSourceAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .priority(8)
                .critical(true)
                .description("Create reversal ledger entries")
                .build());

        return actions;
    }

    /**
     * Generate external system compensation actions
     */
    private List<CompensationAction> generateExternalSystemCompensationActions(Transaction transaction) {
        List<CompensationAction> actions = new ArrayList<>();

        // Notify external payment processors
        if (transaction.getExternalTransactionId() != null) {
            actions.add(CompensationAction.builder()
                    .id(UUID.randomUUID())
                    .transactionId(transaction.getId())
                    .actionType(CompensationActionType.NOTIFY_EXTERNAL_SYSTEM)
                    .externalSystemId(transaction.getPaymentProcessor())
                    .externalTransactionId(transaction.getExternalTransactionId())
                    .priority(5)
                    .critical(false)
                    .description("Notify external payment processor of rollback")
                    .build());
        }

        return actions;
    }

    /**
     * Generate notification compensation actions
     */
    private List<CompensationAction> generateNotificationCompensationActions(Transaction transaction) {
        List<CompensationAction> actions = new ArrayList<>();

        // Notify users of rollback
        actions.add(CompensationAction.builder()
                .id(UUID.randomUUID())
                .transactionId(transaction.getId())
                .actionType(CompensationActionType.SEND_ROLLBACK_NOTIFICATION)
                .targetUserId(transaction.getFromUserId())
                .priority(3)
                .critical(false)
                .description("Notify source user of rollback")
                .build());

        if (transaction.getToUserId() != null) {
            actions.add(CompensationAction.builder()
                    .id(UUID.randomUUID())
                    .transactionId(transaction.getId())
                    .actionType(CompensationActionType.SEND_ROLLBACK_NOTIFICATION)
                    .targetUserId(transaction.getToUserId())
                    .priority(3)
                    .critical(false)
                    .description("Notify target user of rollback")
                    .build());
        }

        return actions;
    }

    /**
     * Execute individual compensation action
     */
    private void executeCompensationAction(CompensationAction action) {
        switch (action.getActionType()) {
            case CREDIT_WALLET:
                walletCompensationService.creditWallet(
                        action.getTargetWalletId(), 
                        action.getAmount(), 
                        action.getCurrency(),
                        action.getTransactionId());
                break;

            case DEBIT_WALLET:
                walletCompensationService.debitWallet(
                        action.getTargetWalletId(), 
                        action.getAmount(), 
                        action.getCurrency(),
                        action.getTransactionId());
                break;

            case CREATE_REVERSAL_LEDGER_ENTRY:
                ledgerCompensationService.createReversalEntry(
                        action.getLedgerAccountId(),
                        action.getAmount(),
                        action.getCurrency(),
                        action.getTransactionId());
                break;

            case NOTIFY_EXTERNAL_SYSTEM:
                externalSystemCompensationService.notifyRollback(
                        action.getExternalSystemId(),
                        action.getExternalTransactionId(),
                        action.getTransactionId());
                break;

            case SEND_ROLLBACK_NOTIFICATION:
                notificationCompensationService.sendRollbackNotification(
                        action.getTargetUserId(),
                        action.getTransactionId());
                break;

            default:
                throw new IllegalArgumentException("Unknown compensation action type: " + 
                                                 action.getActionType());
        }
    }

    // Enums and DTOs
    public enum CompensationActionType {
        CREDIT_WALLET,
        DEBIT_WALLET,
        CREATE_REVERSAL_LEDGER_ENTRY,
        NOTIFY_EXTERNAL_SYSTEM,
        SEND_ROLLBACK_NOTIFICATION
    }

    public enum CompensationActionStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public enum CompensationResultStatus {
        ALL_COMPLETED,
        PARTIAL_SUCCESS,
        ALL_FAILED,
        EXECUTION_FAILED
    }

    @lombok.Builder
    @lombok.Data
    public static class CompensationAction {
        private UUID id;
        private UUID transactionId;
        private CompensationActionType actionType;
        private CompensationActionStatus status = CompensationActionStatus.PENDING;
        
        // Target identifiers
        private UUID targetWalletId;
        private UUID targetUserId;
        private String ledgerAccountId;
        private String externalSystemId;
        private String externalTransactionId;
        
        // Action details
        private BigDecimal amount;
        private String currency;
        private String description;
        private boolean critical;
        private int priority;
        
        // Execution tracking
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
        private String failureReason;
    }

    @lombok.Builder
    @lombok.Data
    public static class CompensationResult {
        private String overallResult;
        private int actionsExecuted;
        private int successfulActions;
        private int failedActions;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
    }
}