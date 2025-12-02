package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily Spending DTO
 *
 * Represents spending on a specific day.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailySpending {

    // Date
    private LocalDate date;
    private Integer dayOfWeek; // 1=Monday, 7=Sunday
    private String dayName; // "Monday", "Tuesday", etc.

    // Spending Details
    private BigDecimal totalSpending;
    private Integer transactionCount;
    private BigDecimal averageTransactionAmount;
    private BigDecimal largestTransaction;

    // Spending Types
    private BigDecimal essentialSpending;
    private BigDecimal discretionarySpending;
    private BigDecimal savingsContributions;

    // Category Breakdown
    private BigDecimal foodAndDining;
    private BigDecimal shopping;
    private BigDecimal transportation;
    private BigDecimal utilities;
    private BigDecimal entertainment;
    private BigDecimal other;

    // Flags
    private Boolean isHighSpendingDay;
    private Boolean hasUnusualActivity;
    private Boolean isPayday;

    // Comparison
    private BigDecimal comparedToAverage; // % difference from average daily spending
    private String comparisonLabel; // "ABOVE_AVERAGE", "BELOW_AVERAGE", "NORMAL"

    // Metadata
    private String currency;
    private String notes;

    // Helper Methods
    public boolean hasSpending() {
        return totalSpending != null && totalSpending.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isWeekend() {
        return dayOfWeek != null && (dayOfWeek == 6 || dayOfWeek == 7);
    }

    public boolean isAboveAverage() {
        return "ABOVE_AVERAGE".equals(comparisonLabel);
    }

    public BigDecimal getDiscretionaryPercentage() {
        if (totalSpending == null || totalSpending.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return discretionarySpending.divide(totalSpending, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
