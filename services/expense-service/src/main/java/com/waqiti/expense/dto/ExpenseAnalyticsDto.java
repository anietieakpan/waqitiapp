package com.waqiti.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for detailed expense analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseAnalyticsDto {

    // Time Range
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer monthsCovered;

    // Overall Metrics
    private BigDecimal totalSpending;
    private BigDecimal averageMonthlySpending;
    private BigDecimal averageDailySpending;

    // Trends
    private List<MonthlyTrend> monthlyTrends;
    private List<CategoryTrend> categoryTrends;
    private List<MerchantInsight> topMerchants;

    // Patterns
    private Map<String, BigDecimal> spendingByDayOfWeek;
    private Map<String, BigDecimal> spendingByTimeOfDay;
    private String mostExpensiveDay; // Day of week
    private String mostExpensiveMonth;

    // Predictions
    private BigDecimal projectedNextMonthSpending;
    private BigDecimal projectedYearEndSpending;
    private List<String> recommendations;

    // Category Insights
    private String topCategory;
    private BigDecimal topCategoryAmount;
    private Double topCategoryPercentage;

    // Anomalies
    private List<AnomalyAlert> anomalies;

    // Budget Performance
    private BigDecimal totalBudgetedAmount;
    private BigDecimal totalSpentAgainstBudget;
    private Double budgetAdherencePercentage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrend {
        private String month;
        private Integer year;
        private BigDecimal totalAmount;
        private Long expenseCount;
        private BigDecimal averageExpense;
        private Double changeFromPreviousMonth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryTrend {
        private String category;
        private BigDecimal totalAmount;
        private Long count;
        private Double percentageOfTotal;
        private String trend; // INCREASING, DECREASING, STABLE
        private Double changeRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantInsight {
        private String merchantName;
        private BigDecimal totalSpent;
        private Long transactionCount;
        private BigDecimal averageTransaction;
        private String mostCommonCategory;
        private LocalDate lastTransactionDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyAlert {
        private String type; // UNUSUAL_AMOUNT, UNUSUAL_FREQUENCY, NEW_MERCHANT, UNUSUAL_CATEGORY
        private String description;
        private LocalDate date;
        private String expenseId;
        private BigDecimal amount;
        private String severity; // LOW, MEDIUM, HIGH
    }
}
