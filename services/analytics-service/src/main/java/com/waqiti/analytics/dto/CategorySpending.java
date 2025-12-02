package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Category Spending DTO
 *
 * Spending breakdown by transaction category.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySpending {

    // Category Details
    private String categoryCode;
    private String categoryName;
    private String categoryType; // "ESSENTIAL", "DISCRETIONARY", "SAVINGS", "INCOME"

    // Spending Metrics
    private BigDecimal totalAmount;
    private BigDecimal averageTransactionAmount;
    private Integer transactionCount;
    private BigDecimal percentageOfTotal;

    // Time Period
    private LocalDate startDate;
    private LocalDate endDate;

    // Trends
    private String trend; // "INCREASING", "DECREASING", "STABLE"
    private BigDecimal trendPercentage;
    private BigDecimal periodOverPeriodChange;

    // Comparison
    private BigDecimal previousPeriodAmount;
    private BigDecimal changeAmount;
    private BigDecimal changePercentage;

    // Transaction Details
    private BigDecimal largestTransaction;
    private BigDecimal smallestTransaction;
    private String topMerchant;
    private BigDecimal topMerchantAmount;

    // Frequency
    private Integer averageTransactionsPerWeek;
    private Integer averageTransactionsPerMonth;
    private Boolean isRecurring;

    // Budget
    private BigDecimal budgetAmount;
    private BigDecimal budgetRemaining;
    private BigDecimal budgetUtilizationPct;
    private Boolean isOverBudget;

    // Metadata
    private String currency;
    private Integer rank; // 1 = highest spending category

    // Helper Methods
    public boolean isOverBudget() {
        return isOverBudget != null && isOverBudget;
    }

    public boolean isEssential() {
        return "ESSENTIAL".equals(categoryType);
    }

    public boolean isDiscretionary() {
        return "DISCRETIONARY".equals(categoryType);
    }

    public boolean isIncreasing() {
        return "INCREASING".equals(trend);
    }

    public BigDecimal getBudgetVariance() {
        if (budgetAmount == null || totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.subtract(budgetAmount);
    }
}
