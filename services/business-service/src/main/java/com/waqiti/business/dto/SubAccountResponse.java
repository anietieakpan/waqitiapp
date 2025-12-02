package com.waqiti.business.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Business sub-account response with comprehensive details")
public class SubAccountResponse {
    
    @Schema(description = "Unique sub-account identifier")
    private UUID subAccountId;
    
    @Schema(description = "Main account identifier")
    private UUID mainAccountId;
    
    @Schema(description = "Sub-account name", example = "Marketing Budget")
    private String accountName;
    
    @Schema(description = "Sub-account number", example = "SUB-ABC123-4567")
    private String accountNumber;
    
    @Schema(description = "Type of sub-account", example = "OPERATIONAL")
    private String accountType;
    
    @Schema(description = "Purpose of the sub-account")
    private String purpose;
    
    @Schema(description = "Current balance")
    private BigDecimal currentBalance;
    
    @Schema(description = "Available balance")
    private BigDecimal availableBalance;
    
    @Schema(description = "Pending transactions amount")
    private BigDecimal pendingAmount;
    
    @Schema(description = "Monthly spending limit")
    private BigDecimal spendingLimit;
    
    @Schema(description = "Amount spent this month")
    private BigDecimal monthlySpent;
    
    @Schema(description = "Remaining monthly budget")
    private BigDecimal remainingBudget;
    
    @Schema(description = "Daily transaction limit")
    private BigDecimal dailyLimit;
    
    @Schema(description = "Amount spent today")
    private BigDecimal dailySpent;
    
    @Schema(description = "Currency code", example = "USD")
    private String currency;
    
    @Schema(description = "Account active status")
    private boolean isActive;
    
    @Schema(description = "Account frozen status")
    private boolean isFrozen;
    
    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;
    
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
    
    @Schema(description = "Last transaction timestamp")
    private LocalDateTime lastTransactionAt;
    
    @Schema(description = "Transaction statistics")
    private TransactionStats transactionStats;
    
    @Schema(description = "List of authorized users")
    private List<AuthorizedUser> authorizedUsers;
    
    @Schema(description = "Approval settings")
    private ApprovalSettings approvalSettings;
    
    @Schema(description = "Auto-transfer rules")
    private AutoTransferRules autoTransferRules;
    
    @Schema(description = "Account metadata")
    private Map<String, Object> metadata;
    
    @Schema(description = "Account tags")
    private List<String> tags;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionStats {
        private Integer totalTransactions;
        private Integer monthlyTransactions;
        private Integer dailyTransactions;
        private BigDecimal averageTransactionAmount;
        private BigDecimal largestTransaction;
        private LocalDateTime lastTransactionDate;
        private Map<String, Integer> transactionsByCategory;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorizedUser {
        private UUID userId;
        private String userName;
        private String email;
        private String role;
        private List<String> permissions;
        private LocalDateTime addedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalSettings {
        private boolean requiresApproval;
        private BigDecimal approvalThreshold;
        private Integer approvalsRequired;
        private Integer pendingApprovals;
        private List<UUID> approverIds;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoTransferRules {
        private boolean autoTopUpEnabled;
        private BigDecimal minBalance;
        private BigDecimal topUpAmount;
        private boolean sweepEnabled;
        private BigDecimal maxBalance;
        private LocalDateTime lastAutoTransfer;
    }
}