package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents wallet data for AR display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletData {
    private UUID walletId;
    private UUID userId;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
    private String walletType; // PERSONAL, BUSINESS, SAVINGS
    private List<Transaction> recentTransactions;
    private Map<String, BigDecimal> currencyBalances; // For multi-currency wallets
    private WalletLimits limits;
    private Instant lastUpdated;
    private boolean isActive;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletLimits {
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private BigDecimal transactionLimit;
        private BigDecimal dailySpent;
        private BigDecimal monthlySpent;
    }
    
    public BigDecimal getBalance() {
        return balance != null ? balance : BigDecimal.ZERO;
    }
    
    public String getCurrency() {
        return currency != null ? currency : "USD";
    }
    
    public List<Transaction> getRecentTransactions() {
        return recentTransactions != null ? recentTransactions : List.of();
    }
}