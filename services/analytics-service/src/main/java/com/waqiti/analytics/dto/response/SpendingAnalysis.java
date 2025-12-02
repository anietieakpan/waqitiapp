package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spending Analysis DTO
 *
 * Detailed analysis of user spending patterns including category breakdowns,
 * daily/hourly trends, and spending velocity metrics.
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingAnalysis {

    /**
     * Total amount spent in the period
     */
    private BigDecimal totalSpent;

    /**
     * Spending breakdown by category
     */
    private List<CategorySpending> categoryBreakdown;

    /**
     * Daily spending trend
     */
    private List<DailySpending> dailySpending;

    /**
     * Hourly spending pattern
     */
    private List<HourlySpending> hourlySpending;

    /**
     * Spending velocity (average spending per day)
     */
    private BigDecimal spendingVelocity;

    /**
     * Overall spending trend (INCREASING, DECREASING, STABLE)
     */
    private SpendingTrend spendingTrend;

    /**
     * Average daily spend
     */
    private BigDecimal averageDailySpend;

    /**
     * Peak spending hour (0-23)
     */
    private Integer peakSpendingHour;

    /**
     * Spending consistency score (0.0 - 1.0, higher = more consistent)
     */
    private BigDecimal spendingConsistency;

    /**
     * Top spending category
     */
    private String topCategory;

    /**
     * Number of days with spending
     */
    private Integer activeDays;
}
