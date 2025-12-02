package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive report of the period close process containing all
 * relevant information about the closing activities, results, and
 * financial position at period end.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodCloseReport {
    
    /**
     * Unique identifier for this report
     */
    private UUID reportId;
    
    /**
     * Period that was closed
     */
    private UUID periodId;
    
    /**
     * Period identification information
     */
    private PeriodInfo periodInfo;
    
    /**
     * Overall status of the period close
     */
    private String closeStatus;
    
    /**
     * Type of close that was performed
     */
    private String closeType;
    
    /**
     * When the period close process started
     */
    private LocalDateTime closeStartedAt;
    
    /**
     * When the period close process completed
     */
    private LocalDateTime closeCompletedAt;
    
    /**
     * Duration of the close process in milliseconds
     */
    private Long closeDurationMs;
    
    /**
     * User who initiated the period close
     */
    private String closeInitiatedBy;
    
    /**
     * User who completed the period close
     */
    private String closeCompletedBy;
    
    /**
     * Financial statements generated during close
     */
    private FinancialStatements financialStatements;
    
    /**
     * Trial balance at period end
     */
    private TrialBalanceResponse trialBalance;
    
    /**
     * Summary of closing entries
     */
    private ClosingEntriesSummary closingEntriesSummary;
    
    /**
     * Key financial metrics and ratios
     */
    private PeriodCloseMetrics periodMetrics;
    
    /**
     * Comparison with previous period
     */
    private PeriodComparison periodComparison;
    
    /**
     * Account balances summary
     */
    private AccountBalancesSummary accountBalancesSummary;
    
    /**
     * Bank reconciliation status
     */
    private BankReconciliationSummary bankReconciliationSummary;
    
    /**
     * Outstanding items analysis
     */
    private OutstandingItemsAnalysis outstandingItemsAnalysis;
    
    /**
     * Inter-company reconciliation summary
     */
    private InterCompanyReconciliationSummary interCompanyReconciliationSummary;
    
    /**
     * Rollforward information
     */
    private PeriodRollforward rollforwardInfo;
    
    /**
     * List of all journal entries created during close
     */
    private List<JournalEntry> closingJournalEntries;
    
    /**
     * List of reversing entries scheduled
     */
    private List<ReversingEntry> scheduledReversingEntries;
    
    /**
     * Validation results
     */
    private PeriodCloseValidation validationResults;
    
    /**
     * Any exceptions or issues encountered
     */
    private List<CloseException> closeExceptions;
    
    /**
     * Checklist of completed activities
     */
    private List<PeriodCloseChecklistItem> closeChecklist;
    
    /**
     * Performance metrics for the close process
     */
    private ClosePerformanceMetrics performanceMetrics;
    
    /**
     * Currency used for the report
     */
    private String reportCurrency;
    
    /**
     * Exchange rates used (if multi-currency)
     */
    private Map<String, BigDecimal> exchangeRates;
    
    /**
     * When this report was generated
     */
    private LocalDateTime reportGeneratedAt;
    
    /**
     * User who generated this report
     */
    private String reportGeneratedBy;
    
    /**
     * Report format version
     */
    private String reportVersion;
    
    /**
     * Additional metadata for the report
     */
    private String metadata;
    
    /**
     * Whether this report has been approved
     */
    private boolean isApproved;
    
    /**
     * When the report was approved
     */
    private LocalDateTime approvedAt;
    
    /**
     * User who approved the report
     */
    private String approvedBy;
    
    /**
     * Approval comments
     */
    private String approvalComments;
}

/**
 * Key financial metrics calculated during period close
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PeriodCloseMetrics {
    
    /**
     * Net income for the period
     */
    private BigDecimal netIncome;
    
    /**
     * Total revenue for the period
     */
    private BigDecimal totalRevenue;
    
    /**
     * Total expenses for the period
     */
    private BigDecimal totalExpenses;
    
    /**
     * Gross profit margin
     */
    private BigDecimal grossProfitMargin;
    
    /**
     * Net profit margin
     */
    private BigDecimal netProfitMargin;
    
    /**
     * Total assets at period end
     */
    private BigDecimal totalAssets;
    
    /**
     * Total liabilities at period end
     */
    private BigDecimal totalLiabilities;
    
    /**
     * Total equity at period end
     */
    private BigDecimal totalEquity;
    
    /**
     * Working capital
     */
    private BigDecimal workingCapital;
    
    /**
     * Current ratio
     */
    private BigDecimal currentRatio;
    
    /**
     * Debt to equity ratio
     */
    private BigDecimal debtToEquityRatio;
    
    /**
     * Return on assets
     */
    private BigDecimal returnOnAssets;
    
    /**
     * Return on equity
     */
    private BigDecimal returnOnEquity;
}

/**
 * Comparison with previous period
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PeriodComparison {
    
    /**
     * Previous period ID
     */
    private UUID previousPeriodId;
    
    /**
     * Previous period end date
     */
    private LocalDate previousPeriodEndDate;
    
    /**
     * Net income variance
     */
    private BigDecimal netIncomeVariance;
    
    /**
     * Net income variance percentage
     */
    private BigDecimal netIncomeVariancePercent;
    
    /**
     * Revenue variance
     */
    private BigDecimal revenueVariance;
    
    /**
     * Revenue variance percentage
     */
    private BigDecimal revenueVariancePercent;
    
    /**
     * Expense variance
     */
    private BigDecimal expenseVariance;
    
    /**
     * Expense variance percentage
     */
    private BigDecimal expenseVariancePercent;
    
    /**
     * Asset variance
     */
    private BigDecimal assetVariance;
    
    /**
     * Asset variance percentage
     */
    private BigDecimal assetVariancePercent;
    
    /**
     * Key variances by account category
     */
    private Map<String, BigDecimal> variancesByCategory;
}

/**
 * Account balances summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AccountBalancesSummary {
    
    /**
     * Number of accounts with balances
     */
    private int accountsWithBalances;
    
    /**
     * Number of zero balance accounts
     */
    private int zeroBalanceAccounts;
    
    /**
     * Largest debit balance
     */
    private BigDecimal largestDebitBalance;
    
    /**
     * Largest credit balance
     */
    private BigDecimal largestCreditBalance;
    
    /**
     * Account with largest debit balance
     */
    private String largestDebitAccount;
    
    /**
     * Account with largest credit balance
     */
    private String largestCreditAccount;
    
    /**
     * Total debit balances
     */
    private BigDecimal totalDebitBalances;
    
    /**
     * Total credit balances
     */
    private BigDecimal totalCreditBalances;
    
    /**
     * Balance variance (should be zero)
     */
    private BigDecimal balanceVariance;
}

/**
 * Bank reconciliation summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BankReconciliationSummary {
    
    /**
     * Total number of bank accounts
     */
    private int totalBankAccounts;
    
    /**
     * Number of reconciled accounts
     */
    private int reconciledAccounts;
    
    /**
     * Number of accounts pending reconciliation
     */
    private int pendingReconciliations;
    
    /**
     * Total unreconciled amount
     */
    private BigDecimal totalUnreconciledAmount;
    
    /**
     * Oldest unreconciled item date
     */
    private LocalDate oldestUnreconciledDate;
    
    /**
     * Average days to reconcile
     */
    private int averageDaysToReconcile;
}

/**
 * Outstanding items analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OutstandingItemsAnalysis {
    
    /**
     * Total outstanding items
     */
    private int totalOutstandingItems;
    
    /**
     * Total outstanding amount
     */
    private BigDecimal totalOutstandingAmount;
    
    /**
     * Items over 30 days
     */
    private int itemsOver30Days;
    
    /**
     * Items over 60 days
     */
    private int itemsOver60Days;
    
    /**
     * Items over 90 days
     */
    private int itemsOver90Days;
    
    /**
     * Amount over 90 days
     */
    private BigDecimal amountOver90Days;
    
    /**
     * Oldest outstanding item date
     */
    private LocalDate oldestOutstandingDate;
}

/**
 * Inter-company reconciliation summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class InterCompanyReconciliationSummary {
    
    /**
     * Total inter-company accounts
     */
    private int totalInterCompanyAccounts;
    
    /**
     * Reconciled inter-company accounts
     */
    private int reconciledAccounts;
    
    /**
     * Accounts with discrepancies
     */
    private int accountsWithDiscrepancies;
    
    /**
     * Total unreconciled amount
     */
    private BigDecimal totalUnreconciledAmount;
    
    /**
     * Largest discrepancy amount
     */
    private BigDecimal largestDiscrepancyAmount;
    
    /**
     * Number of entities involved
     */
    private int entitiesInvolved;
}

/**
 * Exception or issue encountered during close
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CloseException {
    
    /**
     * Exception type
     */
    private String exceptionType;
    
    /**
     * Severity level
     */
    private String severity;
    
    /**
     * Exception description
     */
    private String description;
    
    /**
     * Account or area affected
     */
    private String affectedArea;
    
    /**
     * Resolution taken
     */
    private String resolution;
    
    /**
     * Who resolved the exception
     */
    private String resolvedBy;
    
    /**
     * When the exception was resolved
     */
    private LocalDateTime resolvedAt;
}

/**
 * Performance metrics for the close process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ClosePerformanceMetrics {
    
    /**
     * Number of journal entries processed
     */
    private int journalEntriesProcessed;
    
    /**
     * Number of accounts processed
     */
    private int accountsProcessed;
    
    /**
     * Number of transactions processed
     */
    private long transactionsProcessed;
    
    /**
     * Average processing time per entry (ms)
     */
    private long avgProcessingTimePerEntry;
    
    /**
     * Peak memory usage during close
     */
    private long peakMemoryUsage;
    
    /**
     * Number of validation checks performed
     */
    private int validationChecksPerformed;
    
    /**
     * Number of errors encountered and resolved
     */
    private int errorsEncountered;
    
    /**
     * System performance rating (EXCELLENT, GOOD, FAIR, POOR)
     */
    private String performanceRating;
}