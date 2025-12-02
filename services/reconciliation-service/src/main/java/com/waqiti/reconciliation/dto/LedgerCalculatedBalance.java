package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerCalculatedBalance {

    private UUID accountId;
    
    private String accountCode;
    
    private String accountName;
    
    private BigDecimal balance;
    
    private String currency;
    
    private LocalDateTime calculatedAt;
    
    private LocalDateTime balanceAsOf;
    
    private BalanceType balanceType;
    
    private BigDecimal debitTotal;
    
    private BigDecimal creditTotal;
    
    private int totalTransactions;
    
    private UUID lastTransactionId;
    
    private LocalDateTime lastTransactionDate;
    
    private List<BalanceComponent> balanceComponents;
    
    private CalculationMetadata calculationMetadata;

    public enum BalanceType {
        CURRENT_BALANCE,
        AVAILABLE_BALANCE,
        PENDING_BALANCE,
        CLEARED_BALANCE,
        BOOK_BALANCE,
        LEDGER_BALANCE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceComponent {
        private String componentType;
        private BigDecimal amount;
        private String description;
        private boolean isIncluded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalculationMetadata {
        private String calculationMethod;
        private LocalDateTime dataAsOf;
        private boolean includesPendingTransactions;
        private boolean includesReversals;
        private String balanceCalculationRule;
        private Long calculationTimeMs;
    }

    public BigDecimal getSignedBalance() {
        return balance != null ? balance : BigDecimal.ZERO;
    }

    public BigDecimal getAbsoluteBalance() {
        return balance != null ? balance.abs() : BigDecimal.ZERO;
    }

    public boolean isPositiveBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegativeBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZeroBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean hasRecentActivity() {
        return lastTransactionDate != null && 
               lastTransactionDate.isAfter(LocalDateTime.now().minusDays(1));
    }
}