package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud detection and prevention metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudMetrics {
    private Long totalChecks;
    private Long fraudulentTransactions;
    private Long blockedTransactions;
    private BigDecimal fraudAmount;
    private Double fraudRate;
    private Double falsePositiveRate;
    private Map<String, Long> fraudByType;
    private List<String> topRiskFactors;
    
    // Additional comprehensive metrics
    private Double averageRiskScore;
    private Long reviewedTransactions;
    private Long whitelistedTransactions;
    private Map<String, Long> fraudByRegion;
    private Map<String, Long> fraudByPaymentMethod;
    private Double preventionEfficiency;
    private BigDecimal savedAmount;
    
    /**
     * Calculate fraud prevention effectiveness
     */
    public double getPreventionEffectiveness() {
        if (totalChecks == null || totalChecks == 0) return 0.0;
        long prevented = (blockedTransactions != null ? blockedTransactions : 0);
        return (prevented * 100.0) / totalChecks;
    }
    
    /**
     * Calculate average loss per fraudulent transaction
     */
    public BigDecimal getAverageFraudLoss() {
        if (fraudulentTransactions == null || fraudulentTransactions == 0 || fraudAmount == null) {
            return BigDecimal.ZERO;
        }
        return fraudAmount.divide(BigDecimal.valueOf(fraudulentTransactions), 2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Get fraud severity level based on fraud rate
     */
    public FraudSeverity getFraudSeverityLevel() {
        if (fraudRate == null) return FraudSeverity.UNKNOWN;
        if (fraudRate >= 5.0) return FraudSeverity.CRITICAL;
        if (fraudRate >= 2.0) return FraudSeverity.HIGH;
        if (fraudRate >= 1.0) return FraudSeverity.MODERATE;
        if (fraudRate >= 0.5) return FraudSeverity.LOW;
        return FraudSeverity.MINIMAL;
    }
    
    /**
     * Check if fraud rate is above industry benchmark
     */
    public boolean isAboveIndustryBenchmark(double benchmark) {
        return fraudRate != null && fraudRate > benchmark;
    }
    
    /**
     * Fraud severity levels
     */
    public enum FraudSeverity {
        MINIMAL("< 0.5%", "green"),
        LOW("0.5-1%", "lightgreen"),
        MODERATE("1-2%", "yellow"),
        HIGH("2-5%", "orange"),
        CRITICAL("> 5%", "red"),
        UNKNOWN("N/A", "gray");
        
        private final String range;
        private final String color;
        
        FraudSeverity(String range, String color) {
            this.range = range;
            this.color = color;
        }
        
        public String getRange() { return range; }
        public String getColor() { return color; }
    }
}