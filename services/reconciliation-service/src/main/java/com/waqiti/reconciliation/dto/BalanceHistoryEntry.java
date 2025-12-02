package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceHistoryEntry {

    private UUID accountId;
    
    private LocalDateTime snapshotDate;
    
    private BigDecimal openingBalance;
    
    private BigDecimal closingBalance;
    
    private BigDecimal availableBalance;
    
    private BigDecimal clearedBalance;
    
    private BigDecimal pendingBalance;
    
    private BigDecimal totalDebits;
    
    private BigDecimal totalCredits;
    
    private int transactionCount;
    
    private BigDecimal minimumBalance;
    
    private BigDecimal maximumBalance;
    
    private String currency;
    
    private String balanceType;

    public BigDecimal getNetChange() {
        if (openingBalance == null || closingBalance == null) {
            return BigDecimal.ZERO;
        }
        return closingBalance.subtract(openingBalance);
    }

    public boolean hasPositiveChange() {
        return getNetChange().compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasNegativeChange() {
        return getNetChange().compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean hasActivity() {
        return transactionCount > 0;
    }

    public BigDecimal getAbsoluteChange() {
        return getNetChange().abs();
    }
}