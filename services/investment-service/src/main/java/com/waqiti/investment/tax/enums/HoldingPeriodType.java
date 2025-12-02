package com.waqiti.investment.tax.enums;

/**
 * Holding Period Classification for Capital Gains Tax
 *
 * SHORT_TERM: Held for 365 days or less (taxed as ordinary income)
 * LONG_TERM: Held for more than 365 days (preferential capital gains rate)
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
public enum HoldingPeriodType {
    /**
     * Security held for 365 days or less
     * Taxed at ordinary income rates
     */
    SHORT_TERM,

    /**
     * Security held for more than 365 days
     * Taxed at preferential long-term capital gains rates (0%, 15%, or 20%)
     */
    LONG_TERM
}
