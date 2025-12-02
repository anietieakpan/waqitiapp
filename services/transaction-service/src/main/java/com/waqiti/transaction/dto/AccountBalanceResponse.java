package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for account balance inquiries.
 * Provides comprehensive balance information including available, current, and blocked amounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceResponse {
    
    private String accountId;
    private String accountNumber;
    private String accountType;
    private String currency;
    
    // Balance information
    private BigDecimal availableBalance;
    private BigDecimal currentBalance;
    private BigDecimal blockedAmount;
    private BigDecimal pendingCredits;
    private BigDecimal pendingDebits;
    private BigDecimal overdraftLimit;
    private BigDecimal minimumBalance;
    
    // Account status
    private String status;
    private Boolean isActive;
    private Boolean canDebit;
    private Boolean canCredit;
    
    // Limits
    private BigDecimal dailyTransactionLimit;
    private BigDecimal remainingDailyLimit;
    private Integer dailyTransactionCount;
    private Integer remainingDailyTransactions;
    
    // Timestamps
    private LocalDateTime balanceAsOf;
    private LocalDateTime lastTransactionDate;
    private LocalDateTime lastUpdateDate;
    
    // Additional information
    private String branchCode;
    private String customerId;
    private Map<String, Object> metadata;
    
    /**
     * Calculate net available balance considering all factors
     */
    public BigDecimal getNetAvailableBalance() {
        BigDecimal netBalance = availableBalance != null ? availableBalance : BigDecimal.ZERO;
        
        if (pendingDebits != null) {
            netBalance = netBalance.subtract(pendingDebits);
        }
        
        if (overdraftLimit != null && netBalance.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal availableOverdraft = overdraftLimit.add(netBalance);
            return availableOverdraft.max(BigDecimal.ZERO);
        }
        
        return netBalance.max(BigDecimal.ZERO);
    }
    
    /**
     * Check if account has sufficient balance for a transaction
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return getNetAvailableBalance().compareTo(amount) >= 0;
    }
    
    /**
     * Check if transaction is within daily limits
     */
    public boolean isWithinDailyLimit(BigDecimal amount) {
        if (remainingDailyLimit == null) {
            return true; // No limit set
        }
        return amount.compareTo(remainingDailyLimit) <= 0;
    }
    
    /**
     * Check if account can perform transaction
     */
    public boolean canPerformTransaction(BigDecimal amount, boolean isDebit) {
        if (!Boolean.TRUE.equals(isActive)) {
            return false;
        }
        
        if (isDebit) {
            return Boolean.TRUE.equals(canDebit) && 
                   hasSufficientBalance(amount) && 
                   isWithinDailyLimit(amount);
        } else {
            return Boolean.TRUE.equals(canCredit);
        }
    }
}