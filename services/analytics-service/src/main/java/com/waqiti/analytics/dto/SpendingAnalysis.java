package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Spending Analysis DTO
 *
 * Detailed spending behavior analysis including trends,
 * patterns, and predictions.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingAnalysis {

    // Identifiers
    private Long userId;
    private Long accountId;

    // Period
    private LocalDate startDate;
    private LocalDate endDate;
    private String period; // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"

    // Total Spending
    private BigDecimal totalSpending;
    private BigDecimal averageDailySpending;
    private BigDecimal averageWeeklySpending;
    private BigDecimal averageMonthlySpending;

    // Category Breakdown
    private List<CategorySpending> categoryBreakdown;
    private String topSpendingCategory;
    private BigDecimal topCategoryAmount;
    private BigDecimal topCategoryPercentage;

    // Trends
    private String spendingTrend; // "INCREASING", "DECREASING", "STABLE"
    private BigDecimal trendPercentage;
    private BigDecimal weekOverWeekChange;
    private BigDecimal monthOverMonthChange;
    private BigDecimal yearOverYearChange;

    // Time-based Patterns
    private Map<String, BigDecimal> dailySpending; // ISO date -> amount
    private Map<String, BigDecimal> weeklySpending; // Week number -> amount
    private Map<String, BigDecimal> monthlySpending; // Month -> amount
    private Map<Integer, BigDecimal> hourlySpending; // Hour (0-23) -> amount
    private Map<Integer, BigDecimal> dayOfWeekSpending; // Day (1-7) -> amount

    // Peak Times
    private Integer peakSpendingDay; // Day of month (1-31)
    private Integer peakSpendingDayOfWeek; // 1=Monday, 7=Sunday
    private Integer peakSpendingHour; // 0-23
    private BigDecimal peakDayAmount;

    // Discretionary vs Essential
    private BigDecimal discretionarySpending;
    private BigDecimal essentialSpending;
    private BigDecimal discretionaryPercentage;
    private BigDecimal essentialPercentage;

    // Merchant Analysis
    private Integer uniqueMerchantCount;
    private Integer repeatMerchantCount;
    private String topMerchant;
    private BigDecimal topMerchantAmount;
    private BigDecimal merchantConcentrationRatio; // % spent at top 3 merchants

    // Transaction Size Distribution
    private BigDecimal largestTransaction;
    private BigDecimal smallestTransaction;
    private BigDecimal medianTransaction;
    private Integer smallTransactionCount; // < $10
    private Integer mediumTransactionCount; // $10-$100
    private Integer largeTransactionCount; // > $100

    // Recurring Payments
    private Integer recurringPaymentCount;
    private BigDecimal recurringPaymentTotal;
    private List<String> recurringMerchants;

    // Anomalies
    private Boolean hasAnomalies;
    private Integer anomalyCount;
    private List<String> anomalyDescriptions;
    private BigDecimal unusualSpendingAmount;

    // Budgets
    private BigDecimal budgetAmount;
    private BigDecimal budgetRemaining;
    private BigDecimal budgetUtilizationPct;
    private Boolean isOverBudget;

    // Forecasts
    private BigDecimal forecastedNextMonthSpending;
    private BigDecimal forecastedNextWeekSpending;
    private String forecastConfidence; // "HIGH", "MEDIUM", "LOW"

    // Metadata
    private LocalDateTime generatedAt;
    private String currency;
    private Integer dataQualityScore; // 0-100

    // Helper Methods
    public boolean isSpendingIncreasing() {
        return "INCREASING".equals(spendingTrend);
    }

    public boolean isOverBudget() {
        return isOverBudget != null && isOverBudget;
    }

    public boolean hasHighDiscretionarySpending() {
        return discretionaryPercentage != null &&
                discretionaryPercentage.compareTo(BigDecimal.valueOf(50)) > 0;
    }

    public BigDecimal getAverageTransactionAmount() {
        if (categoryBreakdown == null || categoryBreakdown.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int totalCount = categoryBreakdown.stream()
                .mapToInt(CategorySpending::getTransactionCount)
                .sum();
        if (totalCount == 0) return BigDecimal.ZERO;
        return totalSpending.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
    }
}
