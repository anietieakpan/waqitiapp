package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Income Source DTO
 *
 * Represents a single income source with details
 * about amount, frequency, and reliability.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeSource {

    // Source Identification
    private String sourceName;
    private String sourceType; // "SALARY", "FREELANCE", "INVESTMENT", "RENTAL", "BUSINESS", "OTHER"
    private String merchantName;
    private String merchantCategory;

    // Amount Details
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal percentageOfTotalIncome;

    // Frequency
    private Integer depositCount;
    private String frequency; // "WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY", "IRREGULAR"
    private Integer averageDaysBetweenDeposits;
    private LocalDate firstDepositDate;
    private LocalDate lastDepositDate;

    // Reliability
    private String reliability; // "HIGH", "MEDIUM", "LOW"
    private BigDecimal consistencyScore; // 0-100
    private Boolean isRecurring;
    private Boolean isPredictable;

    // Trends
    private String trend; // "INCREASING", "DECREASING", "STABLE"
    private BigDecimal trendPercentage;
    private BigDecimal previousPeriodAmount;
    private BigDecimal changeAmount;
    private BigDecimal changePercentage;

    // Projections
    private BigDecimal projectedNextMonthAmount;
    private BigDecimal projectedAnnualAmount;

    // Risk Indicators
    private Boolean hasRecentlyDecreased;
    private Boolean hasStoppedRecently;
    private Integer daysSinceLastDeposit;

    // Metadata
    private String currency;
    private Integer rank; // 1 = primary income source
    private String notes;

    // Helper Methods
    public boolean isPrimarySalary() {
        return "SALARY".equals(sourceType) && rank != null && rank == 1;
    }

    public boolean isHighlyReliable() {
        return "HIGH".equals(reliability) || (consistencyScore != null && consistencyScore.compareTo(BigDecimal.valueOf(80)) > 0);
    }

    public boolean isRegularRecurring() {
        return isRecurring != null && isRecurring &&
                ("WEEKLY".equals(frequency) || "BIWEEKLY".equals(frequency) || "MONTHLY".equals(frequency));
    }

    public boolean requiresAttention() {
        return hasRecentlyDecreased != null && hasRecentlyDecreased ||
                hasStoppedRecently != null && hasStoppedRecently;
    }

    public BigDecimal getAnnualizedAmount() {
        if (averageAmount == null || frequency == null) {
            return BigDecimal.ZERO;
        }
        int periodsPerYear = switch (frequency) {
            case "WEEKLY" -> 52;
            case "BIWEEKLY" -> 26;
            case "MONTHLY" -> 12;
            case "QUARTERLY" -> 4;
            default -> 0;
        };
        return averageAmount.multiply(BigDecimal.valueOf(periodsPerYear));
    }
}
