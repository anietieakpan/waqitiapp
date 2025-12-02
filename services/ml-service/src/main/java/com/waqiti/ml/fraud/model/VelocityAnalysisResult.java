package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of velocity-based fraud analysis.
 * Analyzes transaction frequency and volume patterns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityAnalysisResult {

    private LocalDateTime timestamp;

    // Velocity metrics
    private Integer transactionsLast1Min;
    private Integer transactionsLast5Min;
    private Integer transactionsLast1Hour;
    private Integer transactionsLast24Hours;
    private Integer transactionsLast7Days;

    // Amount velocity
    private BigDecimal amountLast1Hour;
    private BigDecimal amountLast24Hours;
    private BigDecimal amountLast7Days;

    // Velocity scores (0.0 - 1.0)
    private Double transactionFrequencyScore;
    private Double amountVelocityScore;
    private Double overallVelocityScore;

    // Thresholds exceeded
    private Boolean exceededHourlyLimit;
    private Boolean exceededDailyLimit;
    private Boolean exceededWeeklyLimit;
    private Boolean exceededAmountLimit;

    // Velocity patterns
    private Boolean rapidSuccession; // Multiple transactions in quick succession
    private Boolean unusualFrequency; // Frequency unusual for this user
    private Boolean amountEscalation; // Transaction amounts increasing rapidly

    // Historical comparison
    private Double averageTransactionsPerDay;
    private Double averageAmountPerTransaction;
    private Double deviationFromNormal; // How much this deviates from user's normal

    /**
     * Check if velocity is suspicious
     */
    public boolean isSuspiciousVelocity() {
        return overallVelocityScore != null && overallVelocityScore > 0.6;
    }

    /**
     * Get highest velocity score component
     */
    public Double getHighestScore() {
        double max = 0.0;
        if (transactionFrequencyScore != null) max = Math.max(max, transactionFrequencyScore);
        if (amountVelocityScore != null) max = Math.max(max, amountVelocityScore);
        return max;
    }
}
