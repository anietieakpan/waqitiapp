package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Hourly Spending DTO
 *
 * Aggregated spending by hour of day for pattern analysis.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlySpending {

    // Time
    private Integer hour; // 0-23
    private String hourLabel; // "00:00", "01:00", etc.
    private String timeOfDay; // "MORNING", "AFTERNOON", "EVENING", "NIGHT"

    // Spending Metrics
    private BigDecimal totalSpending;
    private BigDecimal averageSpending;
    private Integer transactionCount;
    private BigDecimal percentageOfDailySpending;

    // Category Breakdown
    private BigDecimal foodAndDining;
    private BigDecimal shopping;
    private BigDecimal entertainment;
    private BigDecimal transportation;
    private BigDecimal other;

    // Patterns
    private Boolean isPeakHour;
    private Boolean isLowActivityHour;
    private String spendingPattern; // "HIGH", "MEDIUM", "LOW"

    // Metadata
    private String currency;

    // Helper Methods
    public boolean isBusinessHours() {
        return hour != null && hour >= 9 && hour <= 17;
    }

    public boolean isMorning() {
        return "MORNING".equals(timeOfDay);
    }

    public boolean isEvening() {
        return "EVENING".equals(timeOfDay);
    }

    public boolean hasHighActivity() {
        return "HIGH".equals(spendingPattern);
    }
}
