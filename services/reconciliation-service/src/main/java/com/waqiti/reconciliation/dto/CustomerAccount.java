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
public class CustomerAccount {

    private UUID accountId;
    
    private String accountNumber;
    
    private String accountName;
    
    private AccountType accountType;
    
    private AccountStatus accountStatus;
    
    private UUID customerId;
    
    private String customerName;
    
    private String currency;
    
    private BigDecimal currentBalance;
    
    private BigDecimal availableBalance;
    
    private LocalDateTime createdDate;
    
    private LocalDateTime lastActivityDate;
    
    private String branchCode;
    
    private String productCode;
    
    private AccountLimits limits;
    
    private List<String> features;
    
    private Map<String, String> additionalAttributes;

    public enum AccountType {
        CHECKING("Checking Account"),
        SAVINGS("Savings Account"),
        MONEY_MARKET("Money Market Account"),
        CERTIFICATE_OF_DEPOSIT("Certificate of Deposit"),
        LOAN("Loan Account"),
        CREDIT_CARD("Credit Card Account"),
        INVESTMENT("Investment Account"),
        BUSINESS_CHECKING("Business Checking"),
        BUSINESS_SAVINGS("Business Savings");

        private final String description;

        AccountType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum AccountStatus {
        ACTIVE("Active"),
        DORMANT("Dormant"),
        FROZEN("Frozen"),
        CLOSED("Closed"),
        SUSPENDED("Suspended"),
        PENDING_ACTIVATION("Pending Activation");

        private final String description;

        AccountStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
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
        private BigDecimal minimumBalance;
        private BigDecimal maximumBalance;
        private BigDecimal overdraftLimit;
        private int dailyTransactionLimit;
        private int monthlyTransactionLimit;
    }

    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(accountStatus);
    }

    public boolean isClosed() {
        return AccountStatus.CLOSED.equals(accountStatus);
    }

    public boolean isFrozen() {
        return AccountStatus.FROZEN.equals(accountStatus);
    }

    public boolean isDormant() {
        return AccountStatus.DORMANT.equals(accountStatus);
    }

    public boolean hasPositiveBalance() {
        return currentBalance != null && currentBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasNegativeBalance() {
        return currentBalance != null && currentBalance.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isOverdrawn() {
        return hasNegativeBalance();
    }

    public boolean hasRecentActivity() {
        return lastActivityDate != null && 
               lastActivityDate.isAfter(LocalDateTime.now().minusDays(30));
    }

    public boolean isBusinessAccount() {
        return AccountType.BUSINESS_CHECKING.equals(accountType) ||
               AccountType.BUSINESS_SAVINGS.equals(accountType);
    }

    public BigDecimal getAccountAge() {
        if (createdDate == null) return BigDecimal.ZERO;
        
        long daysSinceCreation = java.time.temporal.ChronoUnit.DAYS.between(
            createdDate.toLocalDate(), 
            LocalDateTime.now().toLocalDate()
        );
        return new BigDecimal(daysSinceCreation);
    }
}