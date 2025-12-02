package com.waqiti.analytics.dto.response;

/**
 * Cash Flow Trend Enum
 *
 * Represents the directional trend of cash flow over time.
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
public enum CashFlowTrend {
    IMPROVING,     // Cash flow becoming more positive
    DETERIORATING, // Cash flow becoming more negative
    STABLE,        // Cash flow relatively consistent
    VOLATILE       // Cash flow highly variable
}
