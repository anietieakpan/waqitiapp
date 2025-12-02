package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Spending analysis model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingAnalysis {
    private BigDecimal totalSpending;
    private BigDecimal averageDaily;
    private BigDecimal averageWeekly;
    private BigDecimal averageMonthly;
    private Map<String, BigDecimal> categoryBreakdown;
    private LocalDateTime peakSpendingDate;
    private BigDecimal peakSpendingAmount;
    private String topSpendingCategory;
    
    // Additional fields needed
    private List<CategorySpending> categorySpending;
    private List<DailySpending> dailySpending;
    private List<HourlySpending> hourlySpending;
    private String spendingTrend;
    private BigDecimal averageDailySpending;
    private String topCategory;
    private BigDecimal spendingVelocity;
    private BigDecimal spendingConsistency;
}