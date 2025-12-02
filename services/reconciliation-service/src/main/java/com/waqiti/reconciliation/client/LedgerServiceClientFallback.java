package com.waqiti.reconciliation.client;

import com.waqiti.reconciliation.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * CRITICAL Fallback implementation for LedgerServiceClient
 * 
 * Reconciliation Service Fallback Philosophy:
 * - READ operations: Return STALE_DATA indicator (prevents decisions on incorrect data)
 * - WRITE operations: BLOCK completely (cannot reconcile without ledger confirmation)
 * - VALIDATION operations: Return UNAVAILABLE status (triggers manual review)
 * - STATISTICAL operations: Return empty/null (non-critical data)
 * 
 * Financial Reconciliation Considerations:
 * - Reconciliation requires accurate ledger state
 * - Cannot mark transactions as reconciled without ledger confirmation
 * - Better to delay reconciliation than to reconcile incorrectly
 * - Audit trail must reflect service unavailability
 * - Regulatory compliance requires complete reconciliation accuracy
 * 
 * @author Waqiti Platform Team - Phase 1 Remediation
 * @since Session 5
 */
@Slf4j
@Component
public class LedgerServiceClientFallback implements LedgerServiceClient {

    /**
     * BLOCK ledger entry retrieval - cannot reconcile without accurate ledger data
     */
    @Override
    public List<LedgerEntry> getLedgerEntriesByTransaction(UUID transactionId) {
        log.error("FALLBACK ACTIVATED: BLOCKING ledger entry retrieval - Ledger Service unavailable for transaction: {}", 
                transactionId);
        
        // CRITICAL: Cannot return stale/cached ledger entries for reconciliation
        // Must return empty list with clear indication of unavailability
        // Calling code should detect empty list and skip reconciliation
        return Collections.emptyList();
    }

    /**
     * BLOCK balance calculation - cannot reconcile with stale balance data
     */
    @Override
    public LedgerCalculatedBalance calculateAccountBalance(UUID accountId, LocalDateTime asOfDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING balance calculation - Ledger Service unavailable for account: {} asOf: {}", 
                accountId, asOfDate);
        
        // Return null balance with UNAVAILABLE status
        // Reconciliation process must detect and skip
        return LedgerCalculatedBalance.builder()
                .accountId(accountId)
                .asOfDate(asOfDate)
                .calculatedBalance(null)
                .status("LEDGER_UNAVAILABLE")
                .message("Ledger service temporarily unavailable - balance cannot be calculated")
                .isStale(true)
                .build();
    }

    /**
     * BLOCK trial balance generation - critical for financial integrity
     */
    @Override
    public TrialBalanceResponse generateTrialBalance(LocalDateTime asOfDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING trial balance generation - Ledger Service unavailable asOf: {}", asOfDate);
        
        // Trial balance is critical for financial reporting
        // Cannot generate from stale data
        return TrialBalanceResponse.builder()
                .asOfDate(asOfDate)
                .totalDebits(null)
                .totalCredits(null)
                .isBalanced(null)
                .status("UNAVAILABLE")
                .message("Trial balance unavailable - ledger service down. Manual reconciliation required.")
                .entriesCount(0)
                .accounts(Collections.emptyList())
                .build();
    }

    /**
     * BLOCK account entries retrieval - reconciliation requires accurate data
     */
    @Override
    public List<LedgerEntry> getLedgerEntriesForAccount(UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING ledger entries retrieval - Ledger Service unavailable for account: {}", accountId);
        
        // Cannot reconcile account without ledger entries
        return Collections.emptyList();
    }

    /**
     * BLOCK consistency validation - cannot validate without ledger access
     */
    @Override
    public LedgerConsistencyResult validateLedgerConsistency(LedgerConsistencyRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING consistency validation - Ledger Service unavailable");
        
        // Consistency validation is critical
        // Return UNKNOWN status to trigger manual review
        return LedgerConsistencyResult.builder()
                .isConsistent(null)
                .status("LEDGER_UNAVAILABLE")
                .message("Cannot validate consistency - ledger service down")
                .inconsistencies(Collections.emptyList())
                .requiresManualReview(true)
                .build();
    }

    /**
     * BLOCK balance summary - reconciliation depends on accurate summaries
     */
    @Override
    public AccountBalanceSummary getAccountBalanceSummary(UUID accountId, LocalDateTime asOfDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING balance summary - Ledger Service unavailable for account: {}", accountId);
        
        return AccountBalanceSummary.builder()
                .accountId(accountId)
                .asOfDate(asOfDate)
                .currentBalance(null)
                .availableBalance(null)
                .status("UNAVAILABLE")
                .message("Balance summary unavailable - ledger service down")
                .isStale(true)
                .build();
    }

    /**
     * BLOCK duplicate entry check - critical for data integrity
     */
    @Override
    public DuplicateEntriesResult checkDuplicateEntries(DuplicateEntriesRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING duplicate entry check - Ledger Service unavailable");
        
        // Cannot check for duplicates without ledger access
        // Return UNKNOWN to prevent reconciliation from proceeding
        return DuplicateEntriesResult.builder()
                .hasDuplicates(null)
                .status("UNAVAILABLE")
                .message("Cannot check duplicates - ledger service down")
                .duplicateCount(null)
                .duplicates(Collections.emptyList())
                .requiresManualReview(true)
                .build();
    }

    /**
     * ALLOW statistics retrieval with STALE indicator (non-critical for reconciliation)
     */
    @Override
    public LedgerStatistics getLedgerStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        log.warn("FALLBACK ACTIVATED: Returning empty statistics - Ledger Service unavailable");
        
        // Statistics are non-critical for reconciliation operations
        // Can be empty without blocking reconciliation
        return LedgerStatistics.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalEntries(0)
                .totalDebits(null)
                .totalCredits(null)
                .status("UNAVAILABLE")
                .message("Statistics unavailable - ledger service down")
                .isStale(true)
                .build();
    }

    /**
     * BLOCK posting integrity verification - critical for financial accuracy
     */
    @Override
    public PostingIntegrityResult verifyPostingIntegrity(PostingIntegrityRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING posting integrity verification - Ledger Service unavailable");
        
        // Posting integrity is critical - cannot verify without ledger
        return PostingIntegrityResult.builder()
                .isIntegrityValid(null)
                .status("UNAVAILABLE")
                .message("Cannot verify posting integrity - ledger service down")
                .violations(Collections.emptyList())
                .requiresManualReview(true)
                .build();
    }

    /**
     * BLOCK unbalanced entries retrieval - critical for reconciliation
     */
    @Override
    public List<UnbalancedEntry> getUnbalancedEntries(LocalDateTime asOfDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING unbalanced entries retrieval - Ledger Service unavailable asOf: {}", asOfDate);
        
        // Cannot identify unbalanced entries without ledger access
        // Return empty list - reconciliation process should detect and skip
        return Collections.emptyList();
    }
}