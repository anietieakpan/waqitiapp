package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Collection of closing journal entries created during the period close process.
 * This DTO contains all the journal entries required to properly close
 * revenue and expense accounts and transfer balances to retained earnings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosingEntries {
    
    /**
     * Period for which closing entries are generated
     */
    private UUID periodId;
    
    /**
     * Period end date
     */
    private LocalDate periodEndDate;
    
    /**
     * Revenue closing entries
     */
    private List<JournalEntry> revenueClosingEntries;
    
    /**
     * Expense closing entries
     */
    private List<JournalEntry> expenseClosingEntries;
    
    /**
     * Retained earnings transfer entries
     */
    private List<JournalEntry> retainedEarningsEntries;
    
    /**
     * Dividend closing entries
     */
    private List<JournalEntry> dividendClosingEntries;
    
    /**
     * Other closing entries (adjustments, etc.)
     */
    private List<JournalEntry> otherClosingEntries;
    
    /**
     * Total revenue amount closed
     */
    private BigDecimal totalRevenueClosed;
    
    /**
     * Total expense amount closed
     */
    private BigDecimal totalExpensesClosed;
    
    /**
     * Net income transferred to retained earnings
     */
    private BigDecimal netIncomeTransferred;
    
    /**
     * Total dividends closed
     */
    private BigDecimal totalDividendsClosed;
    
    /**
     * Currency for all closing entries
     */
    private String currency;
    
    /**
     * Summary of accounts closed
     */
    private ClosingEntriesSummary summary;
    
    /**
     * When the closing entries were created
     */
    private LocalDateTime createdAt;
    
    /**
     * Who created the closing entries
     */
    private String createdBy;
    
    /**
     * Status of the closing entries (DRAFT, POSTED, APPROVED)
     */
    private String status;
    
    /**
     * Whether all closing entries are balanced
     */
    private boolean isBalanced;
    
    /**
     * Any validation errors with the closing entries
     */
    private List<String> validationErrors;
    
    /**
     * Custom metadata for the closing entries
     */
    private String metadata;
}

/**
 * Summary information about the closing entries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ClosingEntriesSummary {
    
    /**
     * Total number of journal entries created
     */
    private int totalJournalEntries;
    
    /**
     * Total number of ledger entries created
     */
    private int totalLedgerEntries;
    
    /**
     * Number of revenue accounts closed
     */
    private int revenueAccountsClosed;
    
    /**
     * Number of expense accounts closed
     */
    private int expenseAccountsClosed;
    
    /**
     * Number of dividend accounts closed
     */
    private int dividendAccountsClosed;
    
    /**
     * Total debit amount in closing entries
     */
    private BigDecimal totalDebits;
    
    /**
     * Total credit amount in closing entries
     */
    private BigDecimal totalCredits;
    
    /**
     * Whether closing entries are completely balanced
     */
    private boolean isCompletelyBalanced;
    
    /**
     * Any balance variance (should be zero)
     */
    private BigDecimal balanceVariance;
    
    /**
     * Largest single closing entry amount
     */
    private BigDecimal largestEntryAmount;
    
    /**
     * Number of manual adjustments included
     */
    private int manualAdjustmentCount;
}