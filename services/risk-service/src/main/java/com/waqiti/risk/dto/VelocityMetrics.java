package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Velocity check metrics
 * Tracks transaction frequency and amount over time windows
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityMetrics {

    private String userId;
    private String merchantId;
    private Instant calculatedAt;

    // Amount-based velocity
    private BigDecimal amountLast1Hour;
    private BigDecimal amountLast24Hours;
    private BigDecimal amountLast7Days;
    private BigDecimal amountLast30Days;

    // Count-based velocity
    private Integer transactionsLast1Hour;
    private Integer transactionsLast24Hours;
    private Integer transactionsLast7Days;
    private Integer transactionsLast30Days;

    // Limits
    private BigDecimal hourlyLimit;
    private BigDecimal dailyLimit;
    private BigDecimal weeklyLimit;
    private BigDecimal monthlyLimit;

    // Breach indicators
    private Boolean hourlyLimitBreached;
    private Boolean dailyLimitBreached;
    private Boolean weeklyLimitBreached;
    private Boolean monthlyLimitBreached;

    // Percentage of limit used
    private Double hourlyLimitUsagePercent;
    private Double dailyLimitUsagePercent;
    private Double weeklyLimitUsagePercent;
    private Double monthlyLimitUsagePercent;

    // Historical comparison
    private BigDecimal averageDailyAmount;
    private BigDecimal averageWeeklyAmount;
    private Double deviationFromAverage; // How many standard deviations

    // Spike detection
    private Boolean suddenSpike;
    private Double spikeMultiplier; // current / average

    // Pattern indicators
    private Boolean unusualVelocity;
    private String velocityPattern; // NORMAL, RAMPING_UP, SPIKE, SUSTAINED_HIGH
}
