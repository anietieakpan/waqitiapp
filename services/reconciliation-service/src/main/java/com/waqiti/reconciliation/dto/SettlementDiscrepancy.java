package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementDiscrepancy {

    private String discrepancyType;
    
    private String fieldName;
    
    private String expectedValue;
    
    private String actualValue;
    
    private BigDecimal amountDifference;
    
    private String description;
    
    private String severity;
    
    private boolean autoResolvable;
    
    @Builder.Default
    private LocalDateTime detectedAt = LocalDateTime.now();
    
    private String impactAssessment;
    
    private String recommendedAction;
    
    private DiscrepancyCategory category;

    public enum DiscrepancyCategory {
        AMOUNT_MISMATCH,
        CURRENCY_MISMATCH,
        DATE_MISMATCH,
        REFERENCE_MISMATCH,
        STATUS_MISMATCH,
        COUNTERPARTY_MISMATCH,
        INSTRUCTION_MISMATCH,
        TIMING_DIFFERENCE
    }

    public boolean isCritical() {
        return "CRITICAL".equalsIgnoreCase(severity);
    }

    public boolean isHigh() {
        return "HIGH".equalsIgnoreCase(severity);
    }

    public boolean isMedium() {
        return "MEDIUM".equalsIgnoreCase(severity);
    }

    public boolean isLow() {
        return "LOW".equalsIgnoreCase(severity);
    }

    public boolean canBeAutoResolved() {
        return autoResolvable;
    }

    public boolean hasAmountImpact() {
        return amountDifference != null && amountDifference.compareTo(BigDecimal.ZERO) != 0;
    }

    public BigDecimal getAbsoluteAmountDifference() {
        return amountDifference != null ? amountDifference.abs() : BigDecimal.ZERO;
    }

    public boolean isSignificantAmount() {
        BigDecimal threshold = new BigDecimal("1000.00"); // Configurable threshold
        return hasAmountImpact() && getAbsoluteAmountDifference().compareTo(threshold) > 0;
    }
}