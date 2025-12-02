package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily Spending DTO
 *
 * Represents spending metrics for a single day.
 * Used for daily spending trend analysis and cash flow visualization.
 *
 * Enables:
 * - Daily spending pattern identification
 * - Spending velocity calculations
 * - Day-over-day comparisons
 * - Anomaly detection (unusual spending days)
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailySpending {

    /**
     * Date of spending
     */
    private LocalDate date;

    /**
     * Total amount spent on this date
     */
    private BigDecimal amount;

    /**
     * Number of transactions on this date
     */
    private Integer transactionCount;

    /**
     * Day of week (MONDAY, TUESDAY, etc.)
     */
    private String dayOfWeek;

    /**
     * Whether this is a weekend day
     */
    private Boolean isWeekend;

    /**
     * Whether this is a holiday
     */
    private Boolean isHoliday;

    /**
     * Comparison to average daily spending (percentage)
     */
    private BigDecimal percentageOfAverage;

    /**
     * Indicates if this is an anomaly day (unusually high/low spending)
     */
    private Boolean isAnomaly;
}
