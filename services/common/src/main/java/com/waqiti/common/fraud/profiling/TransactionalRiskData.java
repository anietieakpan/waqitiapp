package com.waqiti.common.fraud.profiling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Transactional risk analysis data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionalRiskData {
    private String userId;
    private double velocityRisk;
    private double amountRisk;
    private double patternRisk;
    private double merchantRisk;
    private LocalDateTime analysisDate;
    private Map<String, Object> transactionMetrics;
    /** Average transaction amount - CRITICAL: Using BigDecimal for fraud detection precision */
    private BigDecimal averageTransactionAmount;

    public double calculateRiskScore() {
        return (velocityRisk + amountRisk + patternRisk + merchantRisk) / 4.0;
    }

    /**
     * Get average transaction amount (convenience getter)
     */
    public BigDecimal getAverageTransactionAmount() {
        if (averageTransactionAmount != null) {
            return averageTransactionAmount;
        }
        if (transactionMetrics != null && transactionMetrics.containsKey("averageAmount")) {
            Object value = transactionMetrics.get("averageAmount");
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            } else if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Get average daily transactions count
     */
    public Double getAverageDailyTransactions() {
        if (transactionMetrics != null && transactionMetrics.containsKey("avgDailyTransactions")) {
            Object value = transactionMetrics.get("avgDailyTransactions");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return 0.0;
    }
}
