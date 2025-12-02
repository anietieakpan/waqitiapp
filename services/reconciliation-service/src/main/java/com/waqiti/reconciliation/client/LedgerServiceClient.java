package com.waqiti.reconciliation.client;

import com.waqiti.reconciliation.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "ledger-service", 
    url = "${services.ledger-service.url:http://ledger-service:8080}",
    fallback = LedgerServiceClientFallback.class
)
public interface LedgerServiceClient {

    /**
     * Get ledger entries for a specific transaction
     */
    @GetMapping("/api/v1/ledger/entries/transaction/{transactionId}")
    List<LedgerEntry> getLedgerEntriesByTransaction(@PathVariable UUID transactionId);

    /**
     * Calculate account balance as of specific date/time
     */
    @GetMapping("/api/v1/ledger/balance/calculated/{accountId}")
    LedgerCalculatedBalance calculateAccountBalance(
        @PathVariable UUID accountId,
        @RequestParam("asOfDate") LocalDateTime asOfDate
    );

    /**
     * Generate trial balance for specific date/time
     */
    @GetMapping("/api/v1/ledger/trial-balance")
    TrialBalanceResponse generateTrialBalance(@RequestParam("asOfDate") LocalDateTime asOfDate);

    /**
     * Get ledger entries for account within date range
     */
    @GetMapping("/api/v1/ledger/entries/account/{accountId}")
    List<LedgerEntry> getLedgerEntriesForAccount(
        @PathVariable UUID accountId,
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate
    );

    /**
     * Validate ledger entry consistency
     */
    @PostMapping("/api/v1/ledger/validate/consistency")
    LedgerConsistencyResult validateLedgerConsistency(@RequestBody LedgerConsistencyRequest request);

    /**
     * Get account balance summary
     */
    @GetMapping("/api/v1/ledger/balance/summary/{accountId}")
    AccountBalanceSummary getAccountBalanceSummary(
        @PathVariable UUID accountId,
        @RequestParam("asOfDate") LocalDateTime asOfDate
    );

    /**
     * Check for duplicate entries
     */
    @PostMapping("/api/v1/ledger/validate/duplicates")
    DuplicateEntriesResult checkDuplicateEntries(@RequestBody DuplicateEntriesRequest request);

    /**
     * Get ledger statistics for period
     */
    @GetMapping("/api/v1/ledger/statistics")
    LedgerStatistics getLedgerStatistics(
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate
    );

    /**
     * Verify posting integrity
     */
    @PostMapping("/api/v1/ledger/verify/posting-integrity")
    PostingIntegrityResult verifyPostingIntegrity(@RequestBody PostingIntegrityRequest request);

    /**
     * Get unbalanced entries
     */
    @GetMapping("/api/v1/ledger/entries/unbalanced")
    List<UnbalancedEntry> getUnbalancedEntries(
        @RequestParam("asOfDate") LocalDateTime asOfDate
    );
}