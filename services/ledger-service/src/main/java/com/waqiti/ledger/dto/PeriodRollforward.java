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
 * Contains information about period rollforward process - the transfer of 
 * account balances from a closed period to the new period. This includes
 * opening balances for the new period and any adjustments made during rollforward.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodRollforward {
    
    /**
     * Unique identifier for this rollforward process
     */
    private UUID rollforwardId;
    
    /**
     * Period that was closed (source period)
     */
    private UUID closedPeriodId;
    
    /**
     * New period being opened (target period)
     */
    private UUID newPeriodId;
    
    /**
     * End date of the closed period
     */
    private LocalDate closedPeriodEndDate;
    
    /**
     * Start date of the new period
     */
    private LocalDate newPeriodStartDate;
    
    /**
     * End date of the new period
     */
    private LocalDate newPeriodEndDate;
    
    /**
     * Status of the rollforward process
     */
    private String rollforwardStatus;
    
    /**
     * Type of rollforward (AUTOMATIC, MANUAL, PARTIAL)
     */
    private String rollforwardType;
    
    /**
     * Opening balances for the new period
     */
    private List<OpeningBalance> openingBalances;
    
    /**
     * Carried forward balances by account type
     */
    private Map<String, BigDecimal> balancesByAccountType;
    
    /**
     * Accounts that were rolled forward
     */
    private List<RollforwardAccount> rolledForwardAccounts;
    
    /**
     * Summary statistics for the rollforward
     */
    private RollforwardSummary rollforwardSummary;
    
    /**
     * Journal entries created during rollforward
     */
    private List<JournalEntry> rollforwardEntries;
    
    /**
     * Reversing entries that were processed
     */
    private List<ReversingEntry> processedReversingEntries;
    
    /**
     * Any adjustments made during rollforward
     */
    private List<RollforwardAdjustment> rollforwardAdjustments;
    
    /**
     * When the rollforward process started
     */
    private LocalDateTime rollforwardStartedAt;
    
    /**
     * When the rollforward process completed
     */
    private LocalDateTime rollforwardCompletedAt;
    
    /**
     * Duration of the rollforward process in milliseconds
     */
    private Long rollforwardDurationMs;
    
    /**
     * User who initiated the rollforward
     */
    private String rollforwardInitiatedBy;
    
    /**
     * User who completed the rollforward
     */
    private String rollforwardCompletedBy;
    
    /**
     * Whether the rollforward was successful
     */
    private boolean rollforwardSuccessful;
    
    /**
     * Any errors encountered during rollforward
     */
    private List<String> rollforwardErrors;
    
    /**
     * Any warnings generated during rollforward
     */
    private List<String> rollforwardWarnings;
    
    /**
     * Validation results after rollforward
     */
    private RollforwardValidation rollforwardValidation;
    
    /**
     * Whether rollforward can be reversed
     */
    private boolean canBeReversed;
    
    /**
     * Additional metadata for the rollforward
     */
    private String metadata;
    
    /**
     * Currency used in the rollforward
     */
    private String currency;
    
    /**
     * Exchange rates used (if multi-currency)
     */
    private Map<String, BigDecimal> exchangeRates;
}

/**
 * Opening balance for a specific account in the new period
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OpeningBalance {
    
    /**
     * Account for which opening balance is set
     */
    private UUID accountId;
    
    /**
     * Account code for reference
     */
    private String accountCode;
    
    /**
     * Account name for reference
     */
    private String accountName;
    
    /**
     * Account type (ASSET, LIABILITY, EQUITY, etc.)
     */
    private String accountType;
    
    /**
     * Closing balance from previous period
     */
    private BigDecimal closingBalance;
    
    /**
     * Opening balance for new period (usually same as closing)
     */
    private BigDecimal openingBalance;
    
    /**
     * Any adjustment made to the opening balance
     */
    private BigDecimal adjustment;
    
    /**
     * Final opening balance after adjustments
     */
    private BigDecimal finalOpeningBalance;
    
    /**
     * Currency for this balance
     */
    private String currency;
    
    /**
     * Date when opening balance was set
     */
    private LocalDateTime openingBalanceDate;
    
    /**
     * Whether this opening balance was manually adjusted
     */
    private boolean isManuallyAdjusted;
    
    /**
     * Reason for any manual adjustment
     */
    private String adjustmentReason;
    
    /**
     * User who made the adjustment
     */
    private String adjustedBy;
}

/**
 * Account information for rollforward process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RollforwardAccount {
    
    /**
     * Account ID
     */
    private UUID accountId;
    
    /**
     * Account code
     */
    private String accountCode;
    
    /**
     * Account name
     */
    private String accountName;
    
    /**
     * Account type
     */
    private String accountType;
    
    /**
     * Whether account balance was carried forward
     */
    private boolean wasCarriedForward;
    
    /**
     * Whether account was closed (revenue/expense accounts)
     */
    private boolean wasClosed;
    
    /**
     * Closing balance from previous period
     */
    private BigDecimal closingBalance;
    
    /**
     * Opening balance in new period
     */
    private BigDecimal openingBalance;
    
    /**
     * Whether this account required manual intervention
     */
    private boolean requiredManualIntervention;
    
    /**
     * Notes about this account's rollforward
     */
    private String rollforwardNotes;
}

/**
 * Summary statistics for the rollforward process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RollforwardSummary {
    
    /**
     * Total number of accounts processed
     */
    private int totalAccountsProcessed;
    
    /**
     * Number of accounts with carried forward balances
     */
    private int accountsWithCarriedForwardBalances;
    
    /**
     * Number of accounts that were closed
     */
    private int accountsClosed;
    
    /**
     * Total assets carried forward
     */
    private BigDecimal totalAssetsCarriedForward;
    
    /**
     * Total liabilities carried forward
     */
    private BigDecimal totalLiabilitiesCarriedForward;
    
    /**
     * Total equity carried forward
     */
    private BigDecimal totalEquityCarriedForward;
    
    /**
     * Number of opening balance entries created
     */
    private int openingBalanceEntriesCreated;
    
    /**
     * Number of reversing entries processed
     */
    private int reversingEntriesProcessed;
    
    /**
     * Number of manual adjustments made
     */
    private int manualAdjustmentsMade;
    
    /**
     * Total value of manual adjustments
     */
    private BigDecimal totalManualAdjustments;
    
    /**
     * Whether rollforward is completely balanced
     */
    private boolean isCompletelyBalanced;
    
    /**
     * Any balance variance after rollforward
     */
    private BigDecimal balanceVariance;
}

/**
 * Rollforward adjustment details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RollforwardAdjustment {
    
    /**
     * Unique identifier for the adjustment
     */
    private UUID adjustmentId;
    
    /**
     * Account being adjusted
     */
    private UUID accountId;
    
    /**
     * Account code
     */
    private String accountCode;
    
    /**
     * Type of adjustment
     */
    private String adjustmentType;
    
    /**
     * Adjustment amount
     */
    private BigDecimal adjustmentAmount;
    
    /**
     * Reason for the adjustment
     */
    private String adjustmentReason;
    
    /**
     * User who made the adjustment
     */
    private String adjustedBy;
    
    /**
     * When the adjustment was made
     */
    private LocalDateTime adjustmentDate;
    
    /**
     * Journal entry created for the adjustment
     */
    private UUID adjustmentJournalEntryId;
}

/**
 * Validation results after rollforward
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RollforwardValidation {
    
    /**
     * Whether rollforward validation passed
     */
    private boolean validationPassed;
    
    /**
     * Whether trial balance is balanced after rollforward
     */
    private boolean trialBalanceBalanced;
    
    /**
     * Trial balance variance (should be zero)
     */
    private BigDecimal trialBalanceVariance;
    
    /**
     * Whether all opening balances sum correctly
     */
    private boolean openingBalancesCorrect;
    
    /**
     * Whether all reversing entries were processed
     */
    private boolean reversingEntriesComplete;
    
    /**
     * List of validation issues found
     */
    private List<String> validationIssues;
    
    /**
     * When validation was performed
     */
    private LocalDateTime validationDate;
    
    /**
     * User who performed validation
     */
    private String validatedBy;
}