package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Hourly Spending DTO
 *
 * Represents spending metrics aggregated by hour of day (0-23).
 * Used for identifying spending patterns based on time of day.
 *
 * Business Insights:
 * - Peak spending hours (e.g., lunch hours 12-14, evening 18-20)
 * - Low activity hours (e.g., late night 2-6)
 * - Impulse buying windows
 * - Optimal notification timing for spending alerts
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlySpending {

    /**
     * Hour of day (0-23, where 0 = midnight, 12 = noon, 23 = 11 PM)
     */
    private Integer hour;

    /**
     * Total amount spent during this hour across all days
     */
    private BigDecimal amount;

    /**
     * Number of transactions during this hour
     */
    private Integer transactionCount;

    /**
     * Average amount per transaction in this hour
     */
    private BigDecimal averageAmount;

    /**
     * Percentage of total spending (0-100)
     */
    private BigDecimal percentage;

    /**
     * Time period description (e.g., "Morning", "Afternoon", "Evening", "Night")
     */
    private String timePeriod;

    /**
     * Indicates if this is a peak spending hour
     */
    private Boolean isPeakHour;
}
