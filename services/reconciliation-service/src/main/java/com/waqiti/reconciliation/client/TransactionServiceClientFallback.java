package com.waqiti.reconciliation.client;

import com.waqiti.reconciliation.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.UUID;

/**
 * CRITICAL Fallback implementation for TransactionServiceClient
 * 
 * Transaction Service Fallback Philosophy for Reconciliation:
 * - READ operations: Return UNAVAILABLE indicator (prevents incorrect reconciliation)
 * - WRITE operations: BLOCK completely (cannot update transaction status without confirmation)
 * - SEARCH operations: Return empty results (prevents reconciliation on incomplete data)
 * - REVERSAL operations: BLOCK (critical financial operation)
 * 
 * Reconciliation-Specific Considerations:
 * - Cannot mark transactions as reconciled without transaction service confirmation
 * - Better to delay reconciliation than reconcile with incomplete/stale data
 * - Must maintain audit trail showing service unavailability
 * - Regulatory compliance requires accurate transaction state
 * - Failed reconciliation can be retried when service recovers
 * 
 * @author Waqiti Platform Team - Phase 1 Remediation
 * @since Session 5
 */
@Slf4j
@Component
public class TransactionServiceClientFallback implements TransactionServiceClient {

    /**
     * BLOCK transaction details retrieval - reconciliation requires accurate data
     */
    @Override
    public TransactionDetails getTransactionDetails(UUID transactionId) {
        log.error("FALLBACK ACTIVATED: BLOCKING transaction details retrieval - Transaction Service unavailable for: {}", 
                transactionId);
        
        // Return null object with UNAVAILABLE status
        // Reconciliation process must detect and skip this transaction
        return TransactionDetails.builder()
                .transactionId(transactionId)
                .status("UNAVAILABLE")
                .message("Transaction service temporarily unavailable - cannot retrieve details")
                .isStale(true)
                .build();
    }

    /**
     * BLOCK transactions for reconciliation - cannot reconcile without transaction list
     */
    @Override
    public List<TransactionSummary> getTransactionsForReconciliation(
            LocalDateTime startDate, LocalDateTime endDate, String status) {
        log.error("FALLBACK ACTIVATED: BLOCKING reconciliation transaction retrieval - Transaction Service unavailable. " +
                "Date range: {} to {}", startDate, endDate);
        
        // Cannot perform reconciliation without transaction list
        // Return empty list - reconciliation process should detect and delay
        return Collections.emptyList();
    }

    /**
     * BLOCK unmatched transactions retrieval - critical for reconciliation
     */
    @Override
    public List<UnmatchedTransaction> getUnmatchedTransactions(LocalDateTime asOfDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING unmatched transactions retrieval - Transaction Service unavailable asOf: {}", 
                asOfDate);
        
        // Unmatched transactions are critical for reconciliation
        // Cannot proceed without this data
        return Collections.emptyList();
    }

    /**
     * BLOCK transaction lookup by external reference
     */
    @Override
    public TransactionDetails getTransactionByExternalReference(String externalReference) {
        log.error("FALLBACK ACTIVATED: BLOCKING transaction lookup - Transaction Service unavailable for reference: {}", 
                externalReference);
        
        return TransactionDetails.builder()
                .externalReference(externalReference)
                .status("UNAVAILABLE")
                .message("Transaction service unavailable - cannot lookup by external reference")
                .isStale(true)
                .build();
    }

    /**
     * BLOCK transaction search - reconciliation requires accurate search results
     */
    @Override
    public List<TransactionDetails> searchTransactions(TransactionSearchRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING transaction search - Transaction Service unavailable");
        
        // Cannot reconcile based on incomplete search results
        return Collections.emptyList();
    }

    /**
     * BLOCK pending transactions retrieval
     */
    @Override
    public List<PendingTransaction> getPendingTransactions() {
        log.error("FALLBACK ACTIVATED: BLOCKING pending transactions retrieval - Transaction Service unavailable");
        
        // Pending transactions affect reconciliation status
        return Collections.emptyList();
    }

    /**
     * BLOCK failed transactions retrieval - important for exception reconciliation
     */
    @Override
    public List<FailedTransaction> getFailedTransactions(LocalDateTime startDate, LocalDateTime endDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING failed transactions retrieval - Transaction Service unavailable. " +
                "Date range: {} to {}", startDate, endDate);
        
        // Failed transactions must be reconciled
        // Cannot proceed without this data
        return Collections.emptyList();
    }

    /**
     * BLOCK transaction validation - critical for reconciliation accuracy
     */
    @Override
    public TransactionValidationResult validateTransaction(UUID transactionId, TransactionValidationRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING transaction validation - Transaction Service unavailable for: {}", transactionId);
        
        // Cannot validate transaction without service access
        return TransactionValidationResult.builder()
                .transactionId(transactionId)
                .isValid(null)
                .status("UNAVAILABLE")
                .message("Cannot validate transaction - service unavailable")
                .validationErrors(Collections.emptyList())
                .requiresManualReview(true)
                .build();
    }

    /**
     * ALLOW statistics with UNAVAILABLE indicator (non-critical for reconciliation)
     */
    @Override
    public TransactionStatistics getTransactionStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        log.warn("FALLBACK ACTIVATED: Returning empty statistics - Transaction Service unavailable. " +
                "Date range: {} to {}", startDate, endDate);
        
        // Statistics are non-critical for reconciliation operations
        return TransactionStatistics.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalCount(0)
                .totalAmount(null)
                .status("UNAVAILABLE")
                .message("Statistics unavailable - transaction service down")
                .isStale(true)
                .build();
    }

    /**
     * BLOCK transaction status update - cannot update without confirmation
     */
    @Override
    public TransactionStatusUpdateResult updateTransactionStatus(
            UUID transactionId, TransactionStatusUpdateRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING transaction status update - Transaction Service unavailable for: {}", 
                transactionId);
        
        // CRITICAL: Cannot update transaction status during reconciliation without service confirmation
        // This would create inconsistent state between reconciliation and transaction service
        return TransactionStatusUpdateResult.builder()
                .transactionId(transactionId)
                .success(false)
                .status("BLOCKED")
                .message("Cannot update transaction status - service unavailable. Transaction remains in original state.")
                .errorCode("TRANSACTION_SERVICE_UNAVAILABLE")
                .requiresRetry(true)
                .build();
    }

    /**
     * BLOCK duplicate transactions retrieval - important for reconciliation accuracy
     */
    @Override
    public List<DuplicateTransactionGroup> getDuplicateTransactions(LocalDateTime startDate, LocalDateTime endDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING duplicate transactions retrieval - Transaction Service unavailable. " +
                "Date range: {} to {}", startDate, endDate);
        
        // Duplicate detection is critical for reconciliation
        return Collections.emptyList();
    }

    /**
     * BLOCK account transactions retrieval
     */
    @Override
    public List<AccountTransaction> getTransactionsByAccount(
            UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING account transactions retrieval - Transaction Service unavailable for account: {}", 
                accountId);
        
        // Cannot reconcile account without transaction history
        return Collections.emptyList();
    }

    /**
     * BLOCK transaction reversal - CRITICAL financial operation
     */
    @Override
    public TransactionReversalResult reverseTransaction(UUID transactionId, TransactionReversalRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING transaction reversal - Transaction Service unavailable for: {}", transactionId);
        
        // CRITICAL: Cannot reverse transaction without service confirmation
        // Reversals affect financial integrity and must be synchronous
        return TransactionReversalResult.builder()
                .transactionId(transactionId)
                .success(false)
                .status("BLOCKED")
                .message("Cannot reverse transaction - service unavailable. Manual intervention required for critical reversal.")
                .errorCode("TRANSACTION_SERVICE_UNAVAILABLE")
                .requiresManualReview(true)
                .requiresRetry(true)
                .build();
    }
}