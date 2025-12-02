package com.waqiti.analytics.dto.response;

/**
 * Spending Trend Enum
 *
 * Represents the overall direction of user spending over an analysis period.
 * Calculated using linear regression or moving average analysis.
 *
 * Trend Categories:
 * - INCREASING: Spending is consistently rising (potential budget concern)
 * - DECREASING: Spending is consistently falling (positive financial behavior)
 * - STABLE: Spending remains relatively constant (predictable cash flow)
 * - VOLATILE: Spending fluctuates significantly (unpredictable patterns)
 *
 * Used for:
 * - Budget forecasting
 * - Financial health scoring
 * - Proactive spending alerts
 * - Long-term financial planning
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
public enum SpendingTrend {

    /**
     * Spending is consistently increasing over time
     * Threshold: > 5% increase per period
     */
    INCREASING,

    /**
     * Spending is consistently decreasing over time
     * Threshold: > 5% decrease per period
     */
    DECREASING,

    /**
     * Spending remains relatively stable
     * Threshold: -5% to +5% change per period
     */
    STABLE,

    /**
     * Spending shows high volatility with no clear direction
     * Threshold: Standard deviation > 30% of mean
     */
    VOLATILE
}
