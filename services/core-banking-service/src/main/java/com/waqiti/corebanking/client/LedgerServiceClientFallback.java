package com.waqiti.corebanking.client;

import com.waqiti.corebanking.dto.LedgerEntryDto;
import com.waqiti.corebanking.dto.AccountBalanceDto;
import com.waqiti.corebanking.dto.JournalEntryDto;
import com.waqiti.corebanking.dto.TransactionLedgerDto;
import com.waqiti.corebanking.service.LocalLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fallback implementation for LedgerServiceClient
 * CRITICAL: Provides local ledger backup when remote service is unavailable
 * Ensures financial operations never fail due to network issues
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LedgerServiceClientFallback implements LedgerServiceClient {

    private final LocalLedgerService localLedgerService;

    @Override
    public ResponseEntity<LedgerEntryDto> createLedgerEntry(LedgerEntryDto entry) {
        log.error("FALLBACK: Ledger service unavailable, using local ledger backup for entry: {}", 
            entry.getTransactionReference());
        
        // Store locally for later synchronization
        LedgerEntryDto localEntry = localLedgerService.createLocalEntry(entry);
        
        // Mark as requiring sync
        localLedgerService.markForSync(localEntry.getId());
        
        // Send alert to operations team
        alertService.sendCriticalAlert(
            "Ledger Service Unavailable",
            "Using local ledger fallback for transaction: " + entry.getTransactionReference()
        );
        
        return ResponseEntity.ok(localEntry);
    }

    @Override
    public ResponseEntity<JournalEntryDto> createJournalEntry(JournalEntryDto journalEntry) {
        log.error("FALLBACK: Ledger service unavailable, using local journal backup for: {}", 
            journalEntry.getTransactionId());
        
        // Create local journal entry
        JournalEntryDto localJournal = localLedgerService.createLocalJournal(journalEntry);
        
        // Queue for synchronization when service recovers
        localLedgerService.queueForSync(localJournal);
        
        return ResponseEntity.ok(localJournal);
    }

    @Override
    public ResponseEntity<AccountBalanceDto> getAccountBalance(String accountNumber) {
        log.warn("FALLBACK: Using cached balance for account: {}", accountNumber);
        
        // Return cached balance with warning
        AccountBalanceDto cachedBalance = localLedgerService.getCachedBalance(accountNumber);
        cachedBalance.setStale(true);
        cachedBalance.setWarning("Balance may not reflect latest transactions due to service unavailability");
        
        return ResponseEntity.ok(cachedBalance);
    }

    @Override
    public ResponseEntity<List<LedgerEntryDto>> getLedgerEntries(String accountNumber, int limit) {
        log.warn("FALLBACK: Using local ledger entries for account: {}", accountNumber);
        
        List<LedgerEntryDto> localEntries = localLedgerService.getLocalEntries(accountNumber, limit);
        return ResponseEntity.ok(localEntries);
    }

    @Override
    public ResponseEntity<TransactionLedgerDto> getTransactionLedger(UUID transactionId) {
        log.warn("FALLBACK: Using local transaction ledger for: {}", transactionId);
        
        TransactionLedgerDto localLedger = localLedgerService.getLocalTransactionLedger(transactionId);
        return ResponseEntity.ok(localLedger);
    }

    @Override
    public ResponseEntity<BigDecimal> calculateAvailableBalance(String accountNumber) {
        log.warn("FALLBACK: Using cached available balance for account: {}", accountNumber);
        
        BigDecimal cachedBalance = localLedgerService.getCachedAvailableBalance(accountNumber);
        return ResponseEntity.ok(cachedBalance);
    }
}