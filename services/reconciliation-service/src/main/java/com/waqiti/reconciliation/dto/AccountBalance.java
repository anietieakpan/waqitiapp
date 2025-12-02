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
public class AccountBalance {

    private UUID accountId;
    
    private String accountNumber;
    
    private String accountType;
    
    private BigDecimal balance;
    
    private BigDecimal availableBalance;
    
    private BigDecimal pendingBalance;
    
    private BigDecimal clearedBalance;
    
    private String currency;
    
    private LocalDateTime balanceDate;
    
    private LocalDateTime lastUpdated;
    
    private BalanceStatus status;
    
    private List<BalanceRestriction> restrictions;
    
    private Map<String, BigDecimal> balanceBreakdown;
    
    private BalanceMetadata metadata;

    public enum BalanceStatus {
        ACTIVE,
        FROZEN,
        DORMANT,
        CLOSED,
        SUSPENDED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceRestriction {
        private String restrictionType;
        private BigDecimal restrictedAmount;
        private String reason;
        private LocalDateTime appliedAt;
        private LocalDateTime expiresAt;
        private boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceMetadata {
        private String balanceCalculationMethod;
        private LocalDateTime lastReconciliationDate;
        private boolean hasUnauthorizedTransactions;
        private boolean hasPendingDeposits;
        private boolean hasPendingWithdrawals;
        private BigDecimal minimumBalance;
        private BigDecimal maximumBalance;
    }

    public BigDecimal getEffectiveBalance() {
        return availableBalance != null ? availableBalance : balance;
    }

    public boolean isActive() {
        return BalanceStatus.ACTIVE.equals(status);
    }

    public boolean isFrozen() {
        return BalanceStatus.FROZEN.equals(status);
    }

    public boolean isDormant() {
        return BalanceStatus.DORMANT.equals(status);
    }

    public boolean hasRestrictions() {
        return restrictions != null && !restrictions.isEmpty();
    }

    public boolean hasPositiveBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasNegativeBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZeroBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) == 0;
    }

    public BigDecimal getRestrictedAmount() {
        if (restrictions == null) return BigDecimal.ZERO;
        
        return restrictions.stream()
                .filter(BalanceRestriction::isActive)
                .map(BalanceRestriction::getRestrictedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getUnrestrictedBalance() {
        BigDecimal effectiveBalance = getEffectiveBalance();
        BigDecimal restrictedAmount = getRestrictedAmount();
        return effectiveBalance.subtract(restrictedAmount);
    }
}