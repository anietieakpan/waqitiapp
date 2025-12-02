package com.waqiti.reconciliation.dto;

import com.waqiti.reconciliation.domain.ReconciliationVariance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceComparisonResult {

    private boolean matched;
    
    private BigDecimal accountServiceBalance;
    
    private BigDecimal ledgerBalance;
    
    private ReconciliationVariance variance;
    
    private ComparisonMethod method;
    
    @Builder.Default
    private LocalDateTime comparedAt = LocalDateTime.now();
    
    private BigDecimal toleranceAmount;
    
    private Double tolerancePercentage;
    
    private List<String> comparisonNotes;
    
    private BalanceComparisonMetadata metadata;

    public enum ComparisonMethod {
        EXACT_MATCH,
        TOLERANCE_BASED,
        PERCENTAGE_BASED,
        TIME_WEIGHTED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceComparisonMetadata {
        private String dataSource1;
        private String dataSource2;
        private LocalDateTime source1Timestamp;
        private LocalDateTime source2Timestamp;
        private boolean realTimeComparison;
        private String comparisonEngine;
        private Long processingTimeMs;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean hasVariance() {
        return variance != null && variance.getAmount().compareTo(BigDecimal.ZERO) != 0;
    }

    public BigDecimal getVarianceAmount() {
        return variance != null ? variance.getAmount() : BigDecimal.ZERO;
    }

    public BigDecimal getAbsoluteVariance() {
        return getVarianceAmount().abs();
    }

    public boolean isWithinTolerance() {
        if (!hasVariance()) return true;
        
        BigDecimal absVariance = getAbsoluteVariance();
        
        // Check amount tolerance
        if (toleranceAmount != null && absVariance.compareTo(toleranceAmount) <= 0) {
            return true;
        }
        
        // Check percentage tolerance
        if (tolerancePercentage != null && accountServiceBalance != null && 
            accountServiceBalance.compareTo(BigDecimal.ZERO) != 0) {
            
            BigDecimal percentageVariance = absVariance
                .divide(accountServiceBalance.abs(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
            
            return percentageVariance.doubleValue() <= tolerancePercentage;
        }
        
        return false;
    }

    public boolean isSignificantVariance() {
        BigDecimal significantThreshold = new BigDecimal("100.00"); // Configurable
        return hasVariance() && getAbsoluteVariance().compareTo(significantThreshold) > 0;
    }

    public double getVariancePercentage() {
        if (!hasVariance() || accountServiceBalance == null || 
            accountServiceBalance.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        
        return getAbsoluteVariance()
            .divide(accountServiceBalance.abs(), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .doubleValue();
    }
}