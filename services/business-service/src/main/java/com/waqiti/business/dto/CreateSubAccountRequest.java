package com.waqiti.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a business sub-account")
public class CreateSubAccountRequest {
    
    @NotBlank(message = "Account name is required")
    @Size(min = 2, max = 100, message = "Account name must be between 2 and 100 characters")
    @Schema(description = "Name of the sub-account", example = "Marketing Budget", required = true)
    private String accountName;
    
    @NotBlank(message = "Account type is required")
    @Pattern(regexp = "^(OPERATIONAL|SAVINGS|RESERVE|PAYROLL|TAX|ESCROW|PETTY_CASH|PROJECT|CUSTOM)$",
             message = "Invalid account type")
    @Schema(description = "Type of sub-account", example = "OPERATIONAL", required = true)
    private String accountType;
    
    @Size(max = 500, message = "Purpose must not exceed 500 characters")
    @Schema(description = "Purpose or description of the sub-account")
    private String purpose;
    
    @DecimalMin(value = "0.00", message = "Spending limit must be non-negative")
    @DecimalMax(value = "999999999.99", message = "Spending limit exceeds maximum allowed")
    @Digits(integer = 9, fraction = 2, message = "Invalid spending limit format")
    @Schema(description = "Monthly spending limit for the sub-account", example = "10000.00")
    private BigDecimal spendingLimit;
    
    @DecimalMin(value = "0.00", message = "Daily limit must be non-negative")
    @Digits(integer = 9, fraction = 2, message = "Invalid daily limit format")
    @Schema(description = "Daily transaction limit", example = "1000.00")
    private BigDecimal dailyLimit;
    
    @Min(value = 1, message = "Transaction count limit must be at least 1")
    @Max(value = 10000, message = "Transaction count limit exceeds maximum")
    @Schema(description = "Maximum number of transactions per month", example = "100")
    private Integer transactionCountLimit;
    
    @Schema(description = "Initial deposit amount for the sub-account")
    @DecimalMin(value = "0.00", message = "Initial deposit must be non-negative")
    @Digits(integer = 9, fraction = 2, message = "Invalid deposit amount format")
    private BigDecimal initialDeposit;
    
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    @Schema(description = "Currency code for the sub-account", example = "USD")
    private String currency;
    
    @Schema(description = "List of authorized user IDs who can access this sub-account")
    private List<String> authorizedUsers;
    
    @Schema(description = "Approval settings for the sub-account")
    private ApprovalSettings approvalSettings;
    
    @Schema(description = "Notification settings for the sub-account")
    private NotificationSettings notificationSettings;
    
    @Schema(description = "Auto-transfer rules for the sub-account")
    private AutoTransferRules autoTransferRules;
    
    @Schema(description = "Additional metadata for the sub-account")
    private Map<String, Object> metadata;
    
    @Schema(description = "Tags for categorization and reporting")
    private List<String> tags;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalSettings {
        @Schema(description = "Require approval for transactions above this amount")
        private BigDecimal approvalThreshold;
        
        @Schema(description = "Number of approvals required")
        @Min(1) @Max(5)
        private Integer approvalsRequired;
        
        @Schema(description = "List of approver user IDs")
        private List<String> approverIds;
        
        @Schema(description = "Allow self-approval for small amounts")
        private Boolean allowSelfApproval;
        
        @Schema(description = "Approval timeout in hours")
        @Min(1) @Max(168)
        private Integer approvalTimeoutHours;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {
        @Schema(description = "Send notifications for all transactions")
        private Boolean notifyOnTransaction;
        
        @Schema(description = "Send low balance alerts")
        private Boolean lowBalanceAlert;
        
        @Schema(description = "Low balance threshold")
        private BigDecimal lowBalanceThreshold;
        
        @Schema(description = "Send spending limit alerts")
        private Boolean spendingLimitAlert;
        
        @Schema(description = "Alert when this percentage of limit is reached")
        @Min(1) @Max(100)
        private Integer spendingLimitAlertPercentage;
        
        @Schema(description = "Email addresses for notifications")
        private List<String> notificationEmails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoTransferRules {
        @Schema(description = "Enable automatic top-up when balance is low")
        private Boolean enableAutoTopUp;
        
        @Schema(description = "Minimum balance to trigger top-up")
        private BigDecimal minBalance;
        
        @Schema(description = "Amount to top-up")
        private BigDecimal topUpAmount;
        
        @Schema(description = "Source account ID for top-up")
        private String sourceAccountId;
        
        @Schema(description = "Enable sweep to main account when balance exceeds maximum")
        private Boolean enableSweep;
        
        @Schema(description = "Maximum balance before sweep")
        private BigDecimal maxBalance;
        
        @Schema(description = "Destination account ID for sweep")
        private String sweepDestinationId;
    }
}