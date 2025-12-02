package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily Income DTO
 *
 * Represents income received on a specific day.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyIncome {

    // Date
    private LocalDate date;
    private Integer dayOfWeek; // 1=Monday, 7=Sunday
    private String dayName; // "Monday", "Tuesday", etc.

    // Income Details
    private BigDecimal totalIncome;
    private Integer depositCount;
    private BigDecimal averageDepositAmount;
    private BigDecimal largestDeposit;

    // Income Types
    private BigDecimal salaryIncome;
    private BigDecimal freelanceIncome;
    private BigDecimal investmentIncome;
    private BigDecimal otherIncome;

    // Flags
    private Boolean isRecurringIncomeDay;
    private Boolean hasUnusualIncome;
    private Boolean isPayday;

    // Comparison
    private BigDecimal comparedToAverage; // % difference from average daily income
    private String comparisonLabel; // "ABOVE_AVERAGE", "BELOW_AVERAGE", "NORMAL"

    // Metadata
    private String currency;
    private String notes;

    // Helper Methods
    public boolean hasIncome() {
        return totalIncome != null && totalIncome.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isWeekend() {
        return dayOfWeek != null && (dayOfWeek == 6 || dayOfWeek == 7);
    }

    public boolean isAboveAverage() {
        return "ABOVE_AVERAGE".equals(comparisonLabel);
    }
}
