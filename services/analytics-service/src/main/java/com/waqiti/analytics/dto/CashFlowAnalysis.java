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
 * Cash Flow Analysis DTO
 *
 * Comprehensive cash flow analysis including inflows,
 * outflows, and net cash position.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowAnalysis {

    // Identifiers
    private Long userId;
    private Long accountId;

    // Period
    private LocalDate startDate;
    private LocalDate endDate;
    private String period; // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"

    // Cash Flow Summary
    private BigDecimal totalInflows;
    private BigDecimal totalOutflows;
    private BigDecimal netCashFlow;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;

    // Averages
    private BigDecimal averageDailyInflow;
    private BigDecimal averageDailyOutflow;
    private BigDecimal averageDailyNetCashFlow;
    private BigDecimal averageWeeklyInflow;
    private BigDecimal averageWeeklyOutflow;
    private BigDecimal averageMonthlyInflow;
    private BigDecimal averageMonthlyOutflow;

    // Time-based Cash Flow
    private List<CashFlowData> dailyCashFlow;
    private List<CashFlowData> weeklyCashFlow;
    private List<CashFlowData> monthlyCashFlow;

    // Trends
    private String inflowTrend; // "INCREASING", "DECREASING", "STABLE"
    private String outflowTrend;
    private String netCashFlowTrend;
    private BigDecimal inflowTrendPercentage;
    private BigDecimal outflowTrendPercentage;

    // Period Comparisons
    private BigDecimal previousPeriodNetCashFlow;
    private BigDecimal netCashFlowChange;
    private BigDecimal netCashFlowChangePercentage;

    // Inflow Breakdown
    private Map<String, BigDecimal> inflowByCategory;
    private Map<String, BigDecimal> inflowBySource;
    private String primaryIncomeSource;
    private BigDecimal primaryIncomeAmount;
    private BigDecimal primaryIncomePercentage;

    // Outflow Breakdown
    private Map<String, BigDecimal> outflowByCategory;
    private Map<String, BigDecimal> outflowByMerchant;
    private String largestExpenseCategory;
    private BigDecimal largestExpenseAmount;
    private BigDecimal largestExpensePercentage;

    // Ratios & Metrics
    private BigDecimal savingsRate; // (Income - Expenses) / Income * 100
    private BigDecimal burnRate; // Average daily spending
    private Integer runwayDays; // Days until balance reaches zero at current burn rate
    private BigDecimal cashFlowVolatility; // Standard deviation of daily cash flow

    // Forecasting
    private List<CashFlowForecast> forecasts;
    private BigDecimal forecastedEndOfMonthBalance;
    private BigDecimal forecastedNextMonthNetCashFlow;
    private String forecastConfidence; // "HIGH", "MEDIUM", "LOW"

    // Alerts & Warnings
    private Boolean hasNegativeCashFlow;
    private Boolean isRunwayLow; // < 30 days
    private Boolean hasVolatileCashFlow;
    private List<String> cashFlowAlerts;

    // Recurring Items
    private BigDecimal recurringIncome;
    private BigDecimal recurringExpenses;
    private BigDecimal predictableNetCashFlow;
    private Integer recurringIncomeCount;
    private Integer recurringExpenseCount;

    // Seasonal Patterns
    private Boolean hasSeasonalPattern;
    private String peakIncomeMonth;
    private String peakExpenseMonth;
    private BigDecimal seasonalityIndex;

    // Metadata
    private LocalDateTime generatedAt;
    private String currency;
    private Integer dataQualityScore; // 0-100

    // Helper Methods
    public boolean isPositiveCashFlow() {
        return netCashFlow != null && netCashFlow.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isRunwayHealthy() {
        return runwayDays != null && runwayDays > 90;
    }

    public boolean isHighSavingsRate() {
        return savingsRate != null && savingsRate.compareTo(BigDecimal.valueOf(20)) > 0;
    }

    public BigDecimal getInflowOutflowRatio() {
        if (totalOutflows == null || totalOutflows.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalInflows.divide(totalOutflows, 2, RoundingMode.HALF_UP);
    }
}
