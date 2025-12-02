package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Category Spending DTO
 *
 * Represents spending metrics for a specific category during an analysis period.
 * Used for category-level financial analysis and budgeting insights.
 *
 * Categories include:
 * - GROCERIES, DINING, TRANSPORTATION, UTILITIES, ENTERTAINMENT, HEALTHCARE,
 *   SHOPPING, TRAVEL, EDUCATION, SUBSCRIPTIONS, etc.
 *
 * Metrics calculated:
 * - Total amount spent in category
 * - Number of transactions in category
 * - Percentage of total spending
 * - Average transaction amount
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySpending {

    /**
     * Category name (e.g., "GROCERIES", "DINING", "TRANSPORTATION")
     */
    private String category;

    /**
     * Total amount spent in this category
     */
    private BigDecimal amount;

    /**
     * Number of transactions in this category
     */
    private Integer transactionCount;

    /**
     * Percentage of total spending (0-100)
     */
    private BigDecimal percentage;

    /**
     * Average amount per transaction in this category
     */
    private BigDecimal averageAmount;

    /**
     * Category display name (user-friendly)
     */
    private String displayName;

    /**
     * Comparison to previous period (percentage change)
     */
    private BigDecimal changeFromPrevious;

    /**
     * Budget allocated for this category (if applicable)
     */
    private BigDecimal budgetAmount;

    /**
     * Budget utilization percentage (0-100+)
     */
    private BigDecimal budgetUtilization;

    /**
     * Indicates if spending is over budget
     */
    private Boolean overBudget;
}
