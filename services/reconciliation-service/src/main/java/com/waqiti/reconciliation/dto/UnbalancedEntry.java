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
public class UnbalancedEntry {

    private UUID entryId;
    
    private UUID transactionId;
    
    private String reference;
    
    private String accountCode;
    
    private String description;
    
    private BigDecimal debitAmount;
    
    private BigDecimal creditAmount;
    
    private BigDecimal imbalanceAmount;
    
    private String currency;
    
    private LocalDateTime transactionDate;
    
    private LocalDateTime detectedAt;
    
    private UnbalanceType unbalanceType;
    
    private String severity;
    
    private List<String> possibleCauses;
    
    private String recommendedAction;

    public enum UnbalanceType {
        MISSING_CREDIT,
        MISSING_DEBIT,
        AMOUNT_MISMATCH,
        CURRENCY_MISMATCH,
        ORPHANED_ENTRY
    }

    public BigDecimal getImbalanceAmount() {
        if (debitAmount == null && creditAmount == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal debits = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        BigDecimal credits = creditAmount != null ? creditAmount : BigDecimal.ZERO;
        
        return debits.subtract(credits);
    }

    public boolean isSignificantImbalance() {
        BigDecimal threshold = new BigDecimal("10.00");
        return getImbalanceAmount().abs().compareTo(threshold) > 0;
    }

    public boolean isCritical() {
        return "CRITICAL".equalsIgnoreCase(severity);
    }

    public boolean hasRecommendedAction() {
        return recommendedAction != null && !recommendedAction.isEmpty();
    }
}