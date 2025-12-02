package com.waqiti.ledger.service;

import com.waqiti.ledger.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ledger Service Interface
 * 
 * Defines the contract for ledger service operations including
 * journal entry management, audit trails, and integration.
 */
public interface LedgerService {

    /**
     * Creates a new journal entry
     */
    JournalEntryResponse createJournalEntry(CreateJournalEntryRequest request);

    /**
     * Creates multiple journal entries in batch
     */
    BatchJournalEntryResponse createBatchJournalEntries(BatchJournalEntryRequest request);

    /**
     * Gets journal entries with filtering and pagination
     */
    Page<JournalEntryResponse> getJournalEntries(JournalEntryFilter filter, Pageable pageable);

    /**
     * Gets detailed journal entry information
     */
    JournalEntryDetailResponse getJournalEntryDetails(UUID entryId);

    /**
     * Reverses a journal entry
     */
    JournalEntryResponse reverseJournalEntry(UUID entryId, ReverseJournalEntryRequest request);

    /**
     * Gets audit trail for ledger operations
     */
    Page<LedgerAuditTrailResponse> getAuditTrail(String entityType, String entityId,
                                               LocalDate startDate, LocalDate endDate,
                                               Pageable pageable);

    /**
     * Calculates wallet balance for reconciliation
     */
    java.math.BigDecimal calculateWalletBalance(UUID walletId, String currency, java.time.LocalDateTime asOfTime);

    /**
     * Gets recent ledger entries for a wallet
     */
    java.util.List<com.waqiti.ledger.domain.LedgerEntry> getRecentLedgerEntries(UUID walletId, String currency, java.time.LocalDateTime sinceDate);

    /**
     * P1-2 CRITICAL FIX: Get wallet balance from ledger for reconciliation
     *
     * Returns the authoritative wallet balance calculated from ledger entries.
     * This is the "source of truth" used by wallet-service reconciliation.
     *
     * @param walletId Wallet UUID
     * @return Wallet balance response with ledger-calculated balance
     */
    WalletBalanceResponse getWalletBalance(UUID walletId);
}