package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contains validation results for a period close operation.
 * This DTO provides comprehensive validation information to determine
 * if a period is ready for closing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodCloseValidation {
    
    /**
     * Overall validation status
     */
    private boolean isValid;
    
    /**
     * Whether the period can be closed despite warnings
     */
    private boolean canProceed;
    
    /**
     * Period being validated
     */
    private UUID periodId;
    
    /**
     * Trial balance validation results
     */
    private TrialBalanceValidation trialBalanceValidation;
    
    /**
     * Bank reconciliation validation results
     */
    private BankReconciliationValidation bankReconciliationValidation;
    
    /**
     * Outstanding items validation
     */
    private OutstandingItemsValidation outstandingItemsValidation;
    
    /**
     * Inter-company reconciliation validation
     */
    private InterCompanyValidation interCompanyValidation;
    
    /**
     * Critical validation errors that prevent closing
     */
    private List<ValidationIssue> criticalErrors;
    
    /**
     * Warnings that should be reviewed but don't prevent closing
     */
    private List<ValidationIssue> warnings;
    
    /**
     * Information messages about the validation
     */
    private List<ValidationIssue> informationalMessages;
    
    /**
     * Validation summary statistics
     */
    private ValidationSummary summary;
    
    /**
     * When the validation was performed
     */
    private LocalDateTime validatedAt;
    
    /**
     * Who performed the validation
     */
    private String validatedBy;
    
    /**
     * Validation context or metadata
     */
    private String validationContext;
}

/**
 * Trial balance validation details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TrialBalanceValidation {
    private boolean isBalanced;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal variance;
    private List<String> unbalancedAccounts;
    private String currency;
}

/**
 * Bank reconciliation validation details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BankReconciliationValidation {
    private boolean allAccountsReconciled;
    private int totalBankAccounts;
    private int reconciledAccounts;
    private int pendingReconciliations;
    private List<UnreconciledAccount> unreconciledAccounts;
    private BigDecimal totalUnreconciledAmount;
}

/**
 * Outstanding items validation details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OutstandingItemsValidation {
    private boolean hasOutstandingItems;
    private int totalOutstandingItems;
    private BigDecimal totalOutstandingAmount;
    private List<OutstandingItemSummary> itemsByCategory;
    private int itemsOverAgeLimit;
}

/**
 * Inter-company validation details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class InterCompanyValidation {
    private boolean isReconciled;
    private int totalInterCompanyAccounts;
    private int reconciledAccounts;
    private BigDecimal totalUnreconciledAmount;
    private List<InterCompanyDiscrepancy> discrepancies;
}

/**
 * Validation issue details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ValidationIssue {
    private String issueId;
    private String severity; // CRITICAL, WARNING, INFO
    private String category;
    private String title;
    private String description;
    private String recommendation;
    private UUID affectedAccountId;
    private String affectedAccountCode;
    private BigDecimal impactAmount;
    private Map<String, Object> additionalData;
    private LocalDateTime detectedAt;
}

/**
 * Unreconciled account summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UnreconciledAccount {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal unreconciledAmount;
    private LocalDateTime lastReconciledDate;
    private int daysSinceReconciliation;
}

/**
 * Outstanding item summary by category
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OutstandingItemSummary {
    private String category;
    private int itemCount;
    private BigDecimal totalAmount;
    private int itemsOverAgeLimit;
}

/**
 * Validation summary statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ValidationSummary {
    private int totalChecksPerformed;
    private int checksPassedCount;
    private int checksFailedCount;
    private int warningsCount;
    private int criticalErrorsCount;
    private long validationDurationMs;
    private String overallRiskAssessment; // LOW, MEDIUM, HIGH
}