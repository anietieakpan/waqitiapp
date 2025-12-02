package com.waqiti.ledger.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Comprehensive request DTO for bank reconciliation with full validation and advanced matching options.
 * Supports automated and manual reconciliation processes.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class BankReconciliationRequest {

    /**
     * Bank account code for reconciliation.
     */
    @NotBlank(message = "Bank account code is required")
    @JsonProperty("bank_account_code")
    private String bankAccountCode;

    /**
     * Date of reconciliation.
     */
    @NotNull(message = "Reconciliation date is required")
    @JsonProperty("reconciliation_date")
    private LocalDate reconciliationDate;

    /**
     * Ending balance from bank statement.
     */
    @NotNull(message = "Ending balance is required")
    @JsonProperty("ending_balance")
    private BigDecimal endingBalance;

    /**
     * Bank statement date.
     */
    @NotNull(message = "Statement date is required")
    @JsonProperty("statement_date")
    private LocalDate statementDate;

    /**
     * Opening balance from bank statement.
     */
    @JsonProperty("opening_balance")
    private BigDecimal openingBalance;

    /**
     * List of bank statement entries.
     */
    @NotEmpty(message = "At least one bank statement entry is required")
    @Valid
    @JsonProperty("bank_statement_entries")
    private List<BankStatementEntry> bankStatementEntries;

    /**
     * Bank statement number/reference.
     */
    @JsonProperty("statement_number")
    private String statementNumber;

    /**
     * Bank name.
     */
    @JsonProperty("bank_name")
    private String bankName;

    /**
     * Account number being reconciled.
     */
    @JsonProperty("account_number")
    private String accountNumber;

    /**
     * Person performing the reconciliation.
     */
    @JsonProperty("performed_by")
    private String performedBy;

    /**
     * Additional notes for the reconciliation.
     */
    private String notes;

    /**
     * Reconciliation configuration options.
     */
    @Valid
    @JsonProperty("reconciliation_config")
    private ReconciliationConfig reconciliationConfig;

    /**
     * Previous reconciliation reference for continuity.
     */
    @JsonProperty("previous_reconciliation_id")
    private String previousReconciliationId;

    /**
     * Workflow and approval settings.
     */
    @Valid
    @JsonProperty("workflow_settings")
    private WorkflowSettings workflowSettings;

    /**
     * Configuration options for reconciliation processing.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationConfig {
        
        /**
         * Whether to enable automatic matching.
         */
        @JsonProperty("automatic_matching")
        @Builder.Default
        private Boolean automaticMatching = true;
        
        /**
         * Tolerance amount for matching transactions.
         */
        @DecimalMin(value = "0.00", message = "Matching tolerance must be non-negative")
        @JsonProperty("matching_tolerance")
        @Builder.Default
        private BigDecimal matchingTolerance = BigDecimal.valueOf(0.01);
        
        /**
         * Date tolerance in days for matching.
         */
        @Min(value = 0, message = "Date tolerance must be non-negative")
        @Max(value = 30, message = "Date tolerance cannot exceed 30 days")
        @JsonProperty("date_tolerance_days")
        @Builder.Default
        private Integer dateToleranceDays = 3;
        
        /**
         * Whether to allow partial matches.
         */
        @JsonProperty("allow_partial_matches")
        @Builder.Default
        private Boolean allowPartialMatches = false;
        
        /**
         * Whether to auto-clear matched items.
         */
        @JsonProperty("auto_clear_matches")
        @Builder.Default
        private Boolean autoClearMatches = true;
        
        /**
         * Minimum confidence score for automatic matching (0-100).
         */
        @Min(value = 0)
        @Max(value = 100)
        @JsonProperty("min_confidence_score")
        @Builder.Default
        private Integer minConfidenceScore = 80;
        
        /**
         * Whether to include cleared items in reconciliation.
         */
        @JsonProperty("include_cleared_items")
        @Builder.Default
        private Boolean includeClearedItems = false;
        
        /**
         * Matching algorithms to use.
         */
        @JsonProperty("matching_algorithms")
        @Builder.Default
        private List<String> matchingAlgorithms = List.of("EXACT_AMOUNT", "FUZZY_DESCRIPTION", "DATE_RANGE");
    }

    /**
     * Workflow and approval settings.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowSettings {
        
        /**
         * Whether reconciliation requires approval.
         */
        @JsonProperty("requires_approval")
        @Builder.Default
        private Boolean requiresApproval = false;
        
        /**
         * Approver user ID.
         */
        @JsonProperty("approver_id")
        private String approverId;
        
        /**
         * Auto-approval threshold (reconciliations below this variance are auto-approved).
         */
        @JsonProperty("auto_approval_threshold")
        private BigDecimal autoApprovalThreshold;
        
        /**
         * Whether to send notifications on completion.
         */
        @JsonProperty("send_notifications")
        @Builder.Default
        private Boolean sendNotifications = true;
        
        /**
         * Notification recipients.
         */
        @JsonProperty("notification_recipients")
        @Builder.Default
        private List<String> notificationRecipients = new java.util.ArrayList<>();
        
        /**
         * Priority level for this reconciliation.
         */
        @JsonProperty("priority_level")
        @Builder.Default
        private String priorityLevel = "NORMAL";
    }

    /**
     * Validates the reconciliation request for business logic compliance.
     *
     * @return true if valid
     * @throws IllegalArgumentException if validation fails
     */
    public boolean validateBusinessRules() {
        // Validate date relationships
        if (statementDate.isAfter(reconciliationDate)) {
            throw new IllegalArgumentException("Statement date cannot be after reconciliation date");
        }
        
        // Validate balance consistency
        if (openingBalance != null && endingBalance != null) {
            BigDecimal calculatedEnding = calculateExpectedEndingBalance();
            BigDecimal variance = endingBalance.subtract(calculatedEnding).abs();
            BigDecimal tolerance = reconciliationConfig != null && reconciliationConfig.matchingTolerance != null ? 
                reconciliationConfig.matchingTolerance : BigDecimal.valueOf(0.01);
            
            if (variance.compareTo(tolerance) > 0) {
                // Log warning instead of using System.out.print
                log.warn("Large variance detected in bank reconciliation: {}", variance);
            }
        }
        
        return true;
    }

    private BigDecimal calculateExpectedEndingBalance() {
        if (openingBalance == null || bankStatementEntries == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = openingBalance;
        for (BankStatementEntry entry : bankStatementEntries) {
            if (entry.getAmount() != null) {
                total = total.add(entry.getAmount());
            }
        }
        return total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankReconciliationRequest that = (BankReconciliationRequest) o;
        return Objects.equals(bankAccountCode, that.bankAccountCode) &&
               Objects.equals(reconciliationDate, that.reconciliationDate) &&
               Objects.equals(statementNumber, that.statementNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bankAccountCode, reconciliationDate, statementNumber);
    }

    @Override
    public String toString() {
        return "BankReconciliationRequest{" +
               "bankAccountCode='" + bankAccountCode + '\'' +
               ", reconciliationDate=" + reconciliationDate +
               ", endingBalance=" + endingBalance +
               ", statementDate=" + statementDate +
               ", entriesCount=" + (bankStatementEntries != null ? bankStatementEntries.size() : 0) +
               '}';
    }
}

/**
 * Bank statement entry with comprehensive transaction details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class BankStatementEntry {

    /**
     * Unique identifier for this statement entry.
     */
    @JsonProperty("entry_id")
    private String entryId;

    /**
     * Transaction date and time.
     */
    @NotNull(message = "Transaction date is required")
    @JsonProperty("transaction_date")
    private LocalDateTime transactionDate;

    /**
     * Transaction amount (positive for credits, negative for debits).
     */
    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    /**
     * Transaction description.
     */
    @NotBlank(message = "Description is required")
    private String description;

    /**
     * Bank reference number.
     */
    private String reference;

    /**
     * Bank transaction code.
     */
    @JsonProperty("transaction_code")
    private String transactionCode;

    /**
     * Counterparty name.
     */
    @JsonProperty("counterparty_name")
    private String counterpartyName;

    /**
     * Counterparty account number.
     */
    @JsonProperty("counterparty_account")
    private String counterpartyAccount;

    /**
     * Transaction type (DEBIT, CREDIT, TRANSFER, etc.).
     */
    @JsonProperty("transaction_type")
    private String transactionType;

    /**
     * Value date (when transaction becomes effective).
     */
    @JsonProperty("value_date")
    private LocalDate valueDate;

    /**
     * Running balance after this transaction.
     */
    @JsonProperty("running_balance")
    private BigDecimal runningBalance;

    /**
     * Additional transaction details.
     */
    @JsonProperty("additional_details")
    @Builder.Default
    private java.util.Map<String, String> additionalDetails = new java.util.HashMap<>();

    /**
     * Whether this entry has been matched during reconciliation.
     */
    @JsonProperty("is_matched")
    @Builder.Default
    private Boolean isMatched = false;

    /**
     * ID of the ledger entry this was matched to.
     */
    @JsonProperty("matched_ledger_entry_id")
    private String matchedLedgerEntryId;

    /**
     * Confidence score of the match (0-100).
     */
    @JsonProperty("match_confidence")
    private Integer matchConfidence;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankStatementEntry that = (BankStatementEntry) o;
        return Objects.equals(entryId, that.entryId) &&
               Objects.equals(reference, that.reference) &&
               Objects.equals(transactionDate, that.transactionDate) &&
               Objects.equals(amount, that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryId, reference, transactionDate, amount);
    }
}