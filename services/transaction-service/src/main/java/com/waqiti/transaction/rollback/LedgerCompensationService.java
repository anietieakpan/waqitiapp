package com.waqiti.transaction.rollback;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.model.CompensationAction;
import com.waqiti.common.client.LedgerServiceClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Enterprise-grade Ledger Compensation Service
 * 
 * Handles double-entry bookkeeping compensations during transaction rollbacks.
 * Ensures ledger consistency with reversal journal entries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerCompensationService {

    private final LedgerServiceClient ledgerServiceClient;
    private final CompensationAuditService compensationAuditService;

    /**
     * Execute ledger compensation for transaction rollback
     * Creates reversal journal entries to maintain double-entry consistency
     */
    @CircuitBreaker(name = "ledger-compensation", fallbackMethod = "compensateFallback")
    @Retry(name = "ledger-compensation")
    @Bulkhead(name = "ledger-compensation")
    @Transactional
    public CompensationAction.CompensationResult compensateLedgerEntries(
            Transaction transaction, CompensationAction action) {
        
        log.info("CRITICAL: Executing ledger compensation for transaction: {} - action: {}", 
                transaction.getId(), action.getActionId());

        try {
            // Check idempotency
            if (isCompensationAlreadyApplied(transaction.getId(), action.getActionId())) {
                log.warn("Ledger compensation already applied for action: {}", action.getActionId());
                return CompensationAction.CompensationResult.builder()
                    .actionId(action.getActionId())
                    .status(CompensationAction.CompensationStatus.ALREADY_COMPLETED)
                    .message("Ledger compensation already applied")
                    .completedAt(LocalDateTime.now())
                    .build();
            }

            // Create reversal journal entries
            JournalEntry reversalEntry = createReversalJournalEntry(transaction);
            
            // Post to ledger service
            LedgerPostingResult postingResult = ledgerServiceClient.postJournalEntry(reversalEntry);
            
            if (!postingResult.isSuccessful()) {
                throw new LedgerCompensationException(
                    "Failed to post reversal journal entry: " + postingResult.getErrorMessage());
            }

            // Record compensation audit
            compensationAuditService.recordCompensation(
                transaction.getId(), 
                action.getActionId(), 
                "LEDGER", 
                "COMPLETED"
            );

            log.info("CRITICAL: Ledger compensation completed for transaction: {} - Journal Entry: {}", 
                    transaction.getId(), postingResult.getJournalEntryId());

            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(CompensationAction.CompensationStatus.COMPLETED)
                .message("Ledger reversal posted successfully")
                .metadata(Map.of(
                    "journalEntryId", postingResult.getJournalEntryId(),
                    "postingReference", postingResult.getPostingReference()
                ))
                .completedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("CRITICAL: Ledger compensation failed for transaction: {}", transaction.getId(), e);
            
            // Record failure in audit
            compensationAuditService.recordCompensationFailure(
                transaction.getId(), 
                action.getActionId(), 
                "LEDGER", 
                e.getMessage()
            );

            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(CompensationAction.CompensationStatus.FAILED)
                .errorMessage(e.getMessage())
                .failedAt(LocalDateTime.now())
                .retryable(true)
                .build();
        }
    }

    /**
     * Create reversal journal entry for transaction
     */
    private JournalEntry createReversalJournalEntry(Transaction transaction) {
        JournalEntry.JournalEntryBuilder entryBuilder = JournalEntry.builder()
            .id(UUID.randomUUID())
            .referenceType("TRANSACTION_ROLLBACK")
            .referenceId(transaction.getId().toString())
            .description("Reversal of transaction: " + transaction.getId())
            .entryDate(LocalDateTime.now())
            .reversalOf(transaction.getJournalEntryId());

        List<JournalEntryLine> lines = new ArrayList<>();

        // Create reversal lines based on transaction type
        switch (transaction.getType()) {
            case TRANSFER -> {
                // Reverse transfer: Credit sender, Debit receiver
                lines.add(createJournalLine(
                    transaction.getFromAccountNumber(),
                    transaction.getAmount(),
                    "CREDIT",
                    "Reversal - Transfer refund to sender"
                ));
                lines.add(createJournalLine(
                    transaction.getToAccountNumber(),
                    transaction.getAmount(),
                    "DEBIT",
                    "Reversal - Transfer reversal from receiver"
                ));
            }
            case PAYMENT -> {
                // Reverse payment: Credit payer, Debit merchant
                lines.add(createJournalLine(
                    transaction.getFromAccountNumber(),
                    transaction.getAmount(),
                    "CREDIT",
                    "Reversal - Payment refund"
                ));
                if (transaction.getMerchantAccountNumber() != null) {
                    BigDecimal merchantAmount = transaction.getAmount()
                        .subtract(transaction.getFeeAmount() != null ? transaction.getFeeAmount() : BigDecimal.ZERO);
                    lines.add(createJournalLine(
                        transaction.getMerchantAccountNumber(),
                        merchantAmount,
                        "DEBIT",
                        "Reversal - Merchant payment reversal"
                    ));
                }
                // Handle fee reversal
                if (transaction.getFeeAmount() != null && transaction.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
                    lines.add(createJournalLine(
                        "FEE_INCOME_ACCOUNT",
                        transaction.getFeeAmount(),
                        "DEBIT",
                        "Reversal - Fee income reversal"
                    ));
                }
            }
            case WITHDRAWAL -> {
                // Reverse withdrawal: Credit user account
                lines.add(createJournalLine(
                    transaction.getFromAccountNumber(),
                    transaction.getAmount().add(
                        transaction.getFeeAmount() != null ? transaction.getFeeAmount() : BigDecimal.ZERO
                    ),
                    "CREDIT",
                    "Reversal - Withdrawal reversal"
                ));
                lines.add(createJournalLine(
                    "CASH_ACCOUNT",
                    transaction.getAmount(),
                    "DEBIT",
                    "Reversal - Cash account adjustment"
                ));
            }
            case DEPOSIT -> {
                // Reverse deposit: Debit user account
                lines.add(createJournalLine(
                    transaction.getToAccountNumber(),
                    transaction.getAmount().subtract(
                        transaction.getFeeAmount() != null ? transaction.getFeeAmount() : BigDecimal.ZERO
                    ),
                    "DEBIT",
                    "Reversal - Deposit reversal"
                ));
                lines.add(createJournalLine(
                    "CASH_ACCOUNT",
                    transaction.getAmount(),
                    "CREDIT",
                    "Reversal - Cash account adjustment"
                ));
            }
            case FEE -> {
                // Reverse fee: Credit user, Debit fee income
                lines.add(createJournalLine(
                    transaction.getFromAccountNumber(),
                    transaction.getAmount(),
                    "CREDIT",
                    "Reversal - Fee refund"
                ));
                lines.add(createJournalLine(
                    "FEE_INCOME_ACCOUNT",
                    transaction.getAmount(),
                    "DEBIT",
                    "Reversal - Fee income reversal"
                ));
            }
            case REFUND -> {
                // Reverse refund: Debit refund recipient, Credit original account
                lines.add(createJournalLine(
                    transaction.getToAccountNumber(),
                    transaction.getAmount(),
                    "DEBIT",
                    "Reversal - Refund reversal"
                ));
                if (transaction.getFromAccountNumber() != null) {
                    lines.add(createJournalLine(
                        transaction.getFromAccountNumber(),
                        transaction.getAmount(),
                        "CREDIT",
                        "Reversal - Refund source restoration"
                    ));
                }
            }
        }

        // Validate double-entry balance
        validateDoubleEntryBalance(lines);

        return entryBuilder
            .lines(lines)
            .status(JournalEntry.Status.PENDING)
            .createdBy("ROLLBACK_SERVICE")
            .createdAt(LocalDateTime.now())
            .build();
    }

    /**
     * Create individual journal entry line
     */
    private JournalEntryLine createJournalLine(String accountNumber, BigDecimal amount, 
                                              String type, String description) {
        return JournalEntryLine.builder()
            .id(UUID.randomUUID())
            .accountNumber(accountNumber)
            .amount(amount)
            .type(JournalEntryLine.Type.valueOf(type))
            .description(description)
            .build();
    }

    /**
     * Validate double-entry balance (debits must equal credits)
     */
    private void validateDoubleEntryBalance(List<JournalEntryLine> lines) {
        BigDecimal totalDebits = lines.stream()
            .filter(line -> line.getType() == JournalEntryLine.Type.DEBIT)
            .map(JournalEntryLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = lines.stream()
            .filter(line -> line.getType() == JournalEntryLine.Type.CREDIT)
            .map(JournalEntryLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalStateException(
                String.format("Double-entry imbalance: Debits=%s, Credits=%s", 
                    totalDebits, totalCredits));
        }
    }

    /**
     * Check if compensation has already been applied
     */
    private boolean isCompensationAlreadyApplied(UUID transactionId, String actionId) {
        return compensationAuditService.isCompensationApplied(transactionId, actionId, "LEDGER");
    }

    /**
     * Fallback method for circuit breaker
     */
    public CompensationAction.CompensationResult compensateFallback(
            Transaction transaction, CompensationAction action, Exception ex) {
        
        log.error("CIRCUIT_BREAKER: Ledger compensation circuit breaker activated for transaction: {}", 
                transaction.getId(), ex);

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.CIRCUIT_BREAKER_OPEN)
            .errorMessage("Ledger service temporarily unavailable - compensation queued for retry")
            .failedAt(LocalDateTime.now())
            .retryable(true)
            .build();
    }

    /**
     * Generate ledger compensation actions for a transaction
     */
    public List<CompensationAction> generateActions(Transaction transaction) {
        List<CompensationAction> actions = new ArrayList<>();

        // Main ledger reversal action
        actions.add(CompensationAction.builder()
            .actionId(UUID.randomUUID().toString())
            .actionType(CompensationAction.ActionType.LEDGER_REVERSAL)
            .targetService("ledger-service")
            .targetResourceId(transaction.getJournalEntryId())
            .compensationData(Map.of(
                "transactionId", transaction.getId(),
                "originalAmount", transaction.getAmount(),
                "currency", transaction.getCurrency(),
                "reversalType", "FULL_REVERSAL"
            ))
            .priority(1)
            .retryable(true)
            .maxRetries(5)
            .build());

        // Subsidiary ledger updates if applicable
        if (transaction.getSubsidiaryLedgers() != null && !transaction.getSubsidiaryLedgers().isEmpty()) {
            for (String subsidiaryLedger : transaction.getSubsidiaryLedgers()) {
                actions.add(CompensationAction.builder()
                    .actionId(UUID.randomUUID().toString())
                    .actionType(CompensationAction.ActionType.SUBSIDIARY_LEDGER_UPDATE)
                    .targetService("ledger-service")
                    .targetResourceId(subsidiaryLedger)
                    .compensationData(Map.of(
                        "transactionId", transaction.getId(),
                        "ledgerType", subsidiaryLedger,
                        "operation", "REVERSAL"
                    ))
                    .priority(2)
                    .retryable(true)
                    .maxRetries(3)
                    .build());
            }
        }

        return actions;
    }

    // Internal DTOs
    @lombok.Builder
    @lombok.Data
    private static class JournalEntry {
        private UUID id;
        private String referenceType;
        private String referenceId;
        private String description;
        private LocalDateTime entryDate;
        private String reversalOf;
        private List<JournalEntryLine> lines;
        private Status status;
        private String createdBy;
        private LocalDateTime createdAt;

        enum Status {
            PENDING, POSTED, REJECTED, REVERSED
        }
    }

    @lombok.Builder
    @lombok.Data
    private static class JournalEntryLine {
        private UUID id;
        private String accountNumber;
        private BigDecimal amount;
        private Type type;
        private String description;

        enum Type {
            DEBIT, CREDIT
        }
    }

    @lombok.Builder
    @lombok.Data
    private static class LedgerPostingResult {
        private boolean successful;
        private String journalEntryId;
        private String postingReference;
        private String errorMessage;
    }

    // Custom exception
    public static class LedgerCompensationException extends RuntimeException {
        public LedgerCompensationException(String message) {
            super(message);
        }
    }
}