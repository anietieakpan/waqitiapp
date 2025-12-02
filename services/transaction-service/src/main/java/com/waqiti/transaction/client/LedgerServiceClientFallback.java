package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.LedgerEntryRequest;
import com.waqiti.transaction.dto.LedgerEntryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for Ledger Service Client
 * 
 * CRITICAL: Ledger is the system of record for all financial movements.
 * NO ledger entries should be created or reversed during service outage
 * to maintain double-entry bookkeeping integrity.
 * 
 * Failure Strategy:
 * - BLOCK all ledger entry creation
 * - BLOCK all ledger reversals
 * - FAIL FAST to prevent partial transactions
 * - PRESERVE ledger consistency at all costs
 * 
 * @author Waqiti Platform Team
 * @since Phase 1 Remediation - Session 6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerServiceClientFallback implements LedgerServiceClient {
    
    /**
     * CRITICAL: Create ledger entry
     * Strategy: BLOCK all entries to maintain integrity
     */
    @Override
    public LedgerEntryResponse createLedgerEntry(LedgerEntryRequest request) {
        log.error("FALLBACK: CRITICAL - Cannot create ledger entry. " +
                 "TransactionId: {}, DebitAccount: {}, CreditAccount: {}, Amount: {}",
                 request.getTransactionId(), request.getDebitAccount(), 
                 request.getCreditAccount(), request.getAmount());
        
        return LedgerEntryResponse.builder()
                .success(false)
                .entryId(null)
                .status("LEDGER_UNAVAILABLE")
                .message("Ledger service unavailable. Transaction blocked for integrity.")
                .fallbackActivated(true)
                .build();
    }
    
    /**
     * CRITICAL: Reverse ledger entry
     * Strategy: BLOCK all reversals during outage
     */
    @Override
    public LedgerEntryResponse reverseLedgerEntry(LedgerEntryRequest request) {
        log.error("FALLBACK: CRITICAL - Cannot reverse ledger entry. " +
                 "OriginalTransactionId: {}, ReversalReason: {}",
                 request.getTransactionId(), request.getDescription());
        
        return LedgerEntryResponse.builder()
                .success(false)
                .entryId(null)
                .status("REVERSAL_BLOCKED")
                .message("Ledger service unavailable. Reversal blocked for integrity.")
                .fallbackActivated(true)
                .build();
    }
}