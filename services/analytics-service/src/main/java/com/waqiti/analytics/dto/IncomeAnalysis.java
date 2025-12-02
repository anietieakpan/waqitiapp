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
 * Income Analysis DTO
 *
 * Comprehensive income analysis including sources,
 * stability, trends, and forecasts.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeAnalysis {

    // Identifiers
    private Long userId;
    private Long accountId;

    // Period
    private LocalDate startDate;
    private LocalDate endDate;
    private String period; // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"

    // Total Income
    private BigDecimal totalIncome;
    private BigDecimal averageDailyIncome;
    private BigDecimal averageWeeklyIncome;
    private BigDecimal averageMonthlyIncome;
    private BigDecimal annualizedIncome;

    // Income Sources
    private List<IncomeSource> incomeSources;
    private Integer incomeSourceCount;
    private String primaryIncomeSource;
    private BigDecimal primaryIncomeAmount;
    private BigDecimal primaryIncomePercentage;

    // Income Types
    private BigDecimal salaryIncome;
    private BigDecimal freelanceIncome;
    private BigDecimal investmentIncome;
    private BigDecimal rentalIncome;
    private BigDecimal businessIncome;
    private BigDecimal otherIncome;

    // Recurring vs One-time
    private BigDecimal recurringIncome;
    private BigDecimal oneTimeIncome;
    private BigDecimal recurringIncomePercentage;
    private Integer recurringIncomeCount;

    // Time-based Income
    private List<DailyIncome> dailyIncome;
    private Map<String, BigDecimal> weeklyIncome; // Week number -> amount
    private Map<String, BigDecimal> monthlyIncome; // Month -> amount

    // Trends
    private String incomeTrend; // "INCREASING", "DECREASING", "STABLE"
    private BigDecimal trendPercentage;
    private BigDecimal weekOverWeekChange;
    private BigDecimal monthOverMonthChange;
    private BigDecimal yearOverYearChange;

    // Period Comparison
    private BigDecimal previousPeriodIncome;
    private BigDecimal incomeChange;
    private BigDecimal incomeChangePercentage;

    // Stability Metrics
    private String incomeStability; // "STABLE", "VOLATILE", "UNPREDICTABLE"
    private BigDecimal incomeVolatility; // Standard deviation
    private BigDecimal coefficientOfVariation; // CV = std dev / mean
    private Boolean hasStableIncome;

    // Frequency Analysis
    private Integer averageDepositsPerWeek;
    private Integer averageDepositsPerMonth;
    private Integer largestGapBetweenDeposits; // days
    private BigDecimal depositFrequencyScore; // 0-100

    // Forecasting
    private BigDecimal forecastedNextMonthIncome;
    private BigDecimal forecastedNextQuarterIncome;
    private String forecastConfidence; // "HIGH", "MEDIUM", "LOW"

    // Anomalies
    private Boolean hasAnomalies;
    private Integer anomalyCount;
    private List<String> unusualIncomeEvents;

    // Seasonal Patterns
    private Boolean hasSeasonalPattern;
    private String peakIncomeMonth;
    private String lowestIncomeMonth;
    private BigDecimal seasonalityIndex;

    // Concentration Risk
    private BigDecimal incomeConcentrationRatio; // % from primary source
    private Boolean isHighlyConcentrated; // > 80% from one source
    private String diversificationScore; // "HIGH", "MEDIUM", "LOW"

    // Growth Metrics
    private BigDecimal threeMonthGrowthRate;
    private BigDecimal sixMonthGrowthRate;
    private BigDecimal yearOverYearGrowthRate;
    private String growthTrend; // "ACCELERATING", "DECELERATING", "STEADY"

    // Metadata
    private LocalDateTime generatedAt;
    private String currency;
    private Integer dataQualityScore; // 0-100

    // Helper Methods
    public boolean isIncomeIncreasing() {
        return "INCREASING".equals(incomeTrend);
    }

    public boolean isIncomeStable() {
        return "STABLE".equals(incomeStability);
    }

    public boolean hasHighDiversification() {
        return "HIGH".equals(diversificationScore) ||
                (incomeSourceCount != null && incomeSourceCount >= 3);
    }

    public boolean isPrimaryIncomeDominant() {
        return primaryIncomePercentage != null &&
                primaryIncomePercentage.compareTo(BigDecimal.valueOf(80)) > 0;
    }

    public BigDecimal getRecurringIncomeRatio() {
        if (totalIncome == null || totalIncome.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return recurringIncome.divide(totalIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
