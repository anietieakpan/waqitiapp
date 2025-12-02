package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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
public class AccountDetails {

    private UUID accountId;
    
    private String accountNumber;
    
    private String accountName;
    
    private String accountType;
    
    private String accountSubType;
    
    private String status;
    
    private UUID customerId;
    
    private String customerName;
    
    private String currency;
    
    private BigDecimal currentBalance;
    
    private BigDecimal availableBalance;
    
    private BigDecimal clearedBalance;
    
    private BigDecimal pendingBalance;
    
    private LocalDateTime openDate;
    
    private LocalDateTime lastActivity;
    
    private String branchCode;
    
    private String productCode;
    
    private String officerCode;
    
    private List<AccountRestriction> restrictions;
    
    private Map<String, String> attributes;
    
    private AccountLimits limits;
    
    private InterestDetails interestDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountRestriction {
        private String restrictionType;
        private String restrictionCode;
        private String reason;
        private LocalDateTime appliedDate;
        private LocalDateTime expiryDate;
        private boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountLimits {
        private BigDecimal dailyDebitLimit;
        private BigDecimal dailyCreditLimit;
        private BigDecimal monthlyDebitLimit;
        private BigDecimal monthlyCreditLimit;
        private BigDecimal overdraftLimit;
        private BigDecimal minimumBalance;
        private BigDecimal maximumBalance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestDetails {
        private BigDecimal interestRate;
        private String interestType;
        private String compoundingFrequency;
        private LocalDateTime lastInterestDate;
        private BigDecimal accruedInterest;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isClosed() {
        return "CLOSED".equalsIgnoreCase(status);
    }

    public boolean hasRestrictions() {
        return restrictions != null && 
               restrictions.stream().anyMatch(AccountRestriction::isActive);
    }

    public boolean hasOverdraft() {
        return limits != null && limits.getOverdraftLimit() != null && 
               limits.getOverdraftLimit().compareTo(BigDecimal.ZERO) > 0;
    }
}