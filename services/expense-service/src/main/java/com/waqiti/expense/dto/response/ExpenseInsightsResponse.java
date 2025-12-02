package com.waqiti.expense.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Expense Insights Response DTO
 * Provides intelligent insights and recommendations about spending patterns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseInsightsResponse {

    // Summary
    private BigDecimal totalSpending;
    private BigDecimal averageDailySpending;
    private BigDecimal projectedMonthlySpending;
    private String currency;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    // Trends
    private List<SpendingTrend> trends;
    private List<CategoryInsight> categoryInsights;
    private List<MerchantInsight> topMerchants;

    // Anomalies and Alerts
    private List<AnomalyAlert> anomalies;
    private List<String> recommendations;
    private List<BudgetAlert> budgetAlerts;

    // Predictions
    private BigDecimal predictedNextMonthSpending;
    private Double spendingTrendPercentage; // Positive = increasing, Negative = decreasing
    private String riskLevel; // LOW, MEDIUM, HIGH

    // Comparisons
    private ComparisonData comparisonToPreviousMonth;
    private ComparisonData comparisonToAverage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpendingTrend {
        private LocalDate date;
        private BigDecimal amount;
        private Integer transactionCount;
        private String trend; // INCREASING, DECREASING, STABLE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInsight {
        private String categoryId;
        private String categoryName;
        private BigDecimal totalSpent;
        private BigDecimal averageTransaction;
        private Integer transactionCount;
        private Double percentageOfTotal;
        private String trend; // INCREASING, DECREASING, STABLE
        private List<String> insights;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantInsight {
        private String merchantName;
        private String merchantCategory;
        private BigDecimal totalSpent;
        private Integer transactionCount;
        private BigDecimal averageTransaction;
        private LocalDate lastTransaction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyAlert {
        private String type; // UNUSUAL_AMOUNT, UNUSUAL_MERCHANT, UNUSUAL_CATEGORY, DUPLICATE
        private String severity; // LOW, MEDIUM, HIGH
        private String message;
        private String expenseId;
        private BigDecimal amount;
        private LocalDate date;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetAlert {
        private String budgetId;
        private String budgetName;
        private BigDecimal plannedAmount;
        private BigDecimal spentAmount;
        private BigDecimal remainingAmount;
        private Double percentageUsed;
        private String alertLevel; // WARNING, CRITICAL, EXCEEDED
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonData {
        private BigDecimal amount;
        private Double percentageChange;
        private String direction; // UP, DOWN, SAME
        private String interpretation;
    }
}
