package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request to initiate an accounting period close process.
 * This represents a comprehensive period closing request with all necessary parameters
 * and options for customizing the closing process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodCloseRequest {
    
    /**
     * Unique identifier for the accounting period to be closed
     */
    @NotNull(message = "Period ID is required")
    private UUID periodId;
    
    /**
     * Start date of the period being closed
     */
    @NotNull(message = "Period start date is required")
    private LocalDate periodStartDate;
    
    /**
     * End date of the period being closed
     */
    @NotNull(message = "Period end date is required")
    private LocalDate periodEndDate;
    
    /**
     * Type of period close (SOFT, HARD, PRELIMINARY)
     */
    @Builder.Default
    private String closeType = "SOFT";
    
    /**
     * Whether to run validation checks before closing
     */
    @Builder.Default
    private boolean runValidation = true;
    
    /**
     * Whether to generate financial statements as part of the close
     */
    @Builder.Default
    private boolean generateFinancialStatements = true;
    
    /**
     * Whether to create closing journal entries
     */
    @Builder.Default
    private boolean createClosingEntries = true;
    
    /**
     * Whether to create reversing entries for the next period
     */
    @Builder.Default
    private boolean createReversingEntries = false;
    
    /**
     * Whether to close revenue accounts to retained earnings
     */
    @Builder.Default
    private boolean closeRevenueAccounts = true;
    
    /**
     * Whether to close expense accounts to retained earnings
     */
    @Builder.Default
    private boolean closeExpenseAccounts = true;
    
    /**
     * Whether to create opening balances for the next period
     */
    @Builder.Default
    private boolean createOpeningBalances = true;
    
    /**
     * Whether to generate period close reports
     */
    @Builder.Default
    private boolean generateReports = true;
    
    /**
     * Whether to perform rollforward to next period
     */
    @Builder.Default
    private boolean performRollforward = true;
    
    /**
     * Specific accounts to include in closing (if empty, all applicable accounts)
     */
    private List<UUID> accountsToClose;
    
    /**
     * Specific accounts to exclude from closing
     */
    private List<UUID> accountsToExclude;
    
    /**
     * Target retained earnings account ID
     */
    private UUID retainedEarningsAccountId;
    
    /**
     * Currency for the closing process
     */
    private String currency;
    
    /**
     * User ID who initiated the close request
     */
    @NotNull(message = "Closed by user is required")
    private String closedBy;
    
    /**
     * Optional notes or comments about the period close
     */
    private String notes;
    
    /**
     * Scheduled time for the close (if null, execute immediately)
     */
    private LocalDateTime scheduledAt;
    
    /**
     * Whether to notify stakeholders upon completion
     */
    @Builder.Default
    private boolean notifyStakeholders = false;
    
    /**
     * List of email addresses to notify upon completion
     */
    private List<String> notificationEmails;
    
    /**
     * Custom metadata for the close request
     */
    private String metadata;
}