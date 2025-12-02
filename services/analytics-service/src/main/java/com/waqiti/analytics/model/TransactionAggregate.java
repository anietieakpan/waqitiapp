package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregated transaction data for analytics processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAggregate {
    
    private String aggregateId;
    private String userId;
    private String merchantId;
    
    // Aggregate metrics
    private Long count;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    
    // Time window
    private Instant windowStart;
    private Instant windowEnd;
    private String timeFrame; // HOURLY, DAILY, WEEKLY, MONTHLY
    
    // Breakdown data
    private Map<String, Long> statusCounts;
    private Map<String, Long> typeCounts;
    private Map<String, BigDecimal> currencyTotals;
    private Map<String, Long> paymentMethodCounts;
    
    // Raw amounts for statistical calculations
    private List<BigDecimal> amounts;
    
    // Geographic data
    private Map<String, Long> locationCounts;
    private Map<String, Long> deviceCounts;
    
    // Risk indicators
    private Long flaggedTransactions;
    private BigDecimal averageRiskScore;
    
    /**
     * Calculate success rate
     */
    public BigDecimal getSuccessRate() {
        Long successful = statusCounts.getOrDefault("COMPLETED", 0L) + 
                         statusCounts.getOrDefault("SUCCESS", 0L);
        
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.valueOf(successful)
                .divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * Calculate failure rate
     */
    public BigDecimal getFailureRate() {
        Long failed = statusCounts.getOrDefault("FAILED", 0L) + 
                     statusCounts.getOrDefault("REJECTED", 0L);
        
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.valueOf(failed)
                .divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * Get dominant payment method
     */
    public String getDominantPaymentMethod() {
        return paymentMethodCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }
    
    /**
     * Get dominant currency
     */
    public String getDominantCurrency() {
        return currencyTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("USD");
    }
    
    /**
     * Calculate transaction velocity (transactions per hour)
     */
    public BigDecimal getTransactionVelocity() {
        if (windowStart == null || windowEnd == null) {
            return BigDecimal.ZERO;
        }
        
        long hours = java.time.Duration.between(windowStart, windowEnd).toHours();
        if (hours == 0) {
            hours = 1; // Minimum 1 hour for calculation
        }
        
        return BigDecimal.valueOf(count).divide(BigDecimal.valueOf(hours), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Check if aggregate shows unusual patterns
     */
    public boolean hasUnusualPatterns() {
        // Check for unusually high failure rate
        if (getFailureRate().compareTo(BigDecimal.valueOf(20)) > 0) {
            return true;
        }
        
        // Check for unusually high risk score
        if (averageRiskScore != null && averageRiskScore.compareTo(BigDecimal.valueOf(70)) > 0) {
            return true;
        }
        
        // Check for unusual velocity
        if (getTransactionVelocity().compareTo(BigDecimal.valueOf(100)) > 0) {
            return true;
        }
        
        return false;
    }
}