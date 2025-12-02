package com.waqiti.corebanking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Banking account information response")
public class AccountResponseDto {

    @Schema(description = "Unique account identifier", example = "acc-12345678")
    private String accountId;

    @Schema(description = "Account number for display", example = "1234567890")
    private String accountNumber;

    @Schema(description = "ID of the account owner", example = "user-123")
    private String userId;

    @Schema(description = "Type of account", 
            example = "USER_WALLET",
            allowableValues = {"USER_WALLET", "USER_SAVINGS", "USER_CREDIT", "BUSINESS_OPERATING"})
    private String accountType;

    @Schema(description = "Account category for accounting", 
            example = "ASSET",
            allowableValues = {"ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"})
    private String accountCategory;

    @Schema(description = "Account currency", example = "USD")
    private String currency;

    @Schema(description = "Current account status", 
            example = "ACTIVE",
            allowableValues = {"PENDING", "ACTIVE", "SUSPENDED", "FROZEN", "DORMANT", "CLOSED"})
    private String status;

    @Schema(description = "Current account balance", example = "1250.75")
    private BigDecimal currentBalance;

    @Schema(description = "Available balance for transactions", example = "1150.75")
    private BigDecimal availableBalance;

    @Schema(description = "Pending transaction amount", example = "100.00")
    private BigDecimal pendingBalance;

    @Schema(description = "Reserved balance", example = "0.00")
    private BigDecimal reservedBalance;

    @Schema(description = "Credit limit if applicable", example = "1000.00")
    private BigDecimal creditLimit;

    @Schema(description = "Interest rate percentage", example = "2.5")
    private BigDecimal interestRate;

    @Schema(description = "Daily transaction limit", example = "5000.00")
    private BigDecimal dailyLimit;

    @Schema(description = "Monthly transaction limit", example = "50000.00")
    private BigDecimal monthlyLimit;

    @Schema(description = "Account description", example = "Primary checking account")
    private String description;

    @Schema(description = "Parent account ID if hierarchical", example = "parent-acc-123")
    private String parentAccountId;

    @Schema(description = "Chart of accounts code", example = "1100")
    private String accountCode;

    @Schema(description = "Compliance level", 
            example = "STANDARD",
            allowableValues = {"BASIC", "STANDARD", "ENHANCED", "PREMIUM"})
    private String complianceLevel;

    @Schema(description = "Whether account is frozen", example = "false")
    private Boolean isFrozen;

    @Schema(description = "Whether account allows overdrafts", example = "false")
    private Boolean allowOverdraft;

    @Schema(description = "Account creation timestamp", example = "2024-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Last update timestamp", example = "2024-01-15T10:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Last activity timestamp", example = "2024-01-15T15:45:00Z")
    private Instant lastActivityAt;

    @Schema(description = "Account closure timestamp if closed", example = "2024-06-15T10:30:00Z")
    private Instant closedAt;

    @Schema(description = "Additional account metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Account holder information")
    private AccountHolderDto accountHolder;

    @Schema(description = "Version for optimistic locking", example = "1")
    private Long version;

    // Helper methods for business logic
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isClosed() {
        return "CLOSED".equals(status);
    }

    public boolean hasCreditLimit() {
        return creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getEffectiveBalance() {
        BigDecimal effective = currentBalance;
        if (hasCreditLimit()) {
            effective = effective.add(creditLimit);
        }
        return effective;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Account holder information")
    public static class AccountHolderDto {
        
        @Schema(description = "Account holder name", example = "John Doe")
        private String name;
        
        @Schema(description = "Account holder email", example = "john.doe@example.com")
        private String email;
        
        @Schema(description = "Account holder phone", example = "+1234567890")
        private String phone;
        
        @Schema(description = "Account holder type", 
                example = "INDIVIDUAL",
                allowableValues = {"INDIVIDUAL", "BUSINESS", "ORGANIZATION"})
        private String holderType;
    }
}