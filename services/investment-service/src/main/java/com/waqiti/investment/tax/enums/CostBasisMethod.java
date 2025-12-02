package com.waqiti.investment.tax.enums;

/**
 * Cost Basis Calculation Methods (IRS Publication 550)
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
public enum CostBasisMethod {
    /**
     * First In, First Out
     * Default method for stocks
     */
    FIFO,

    /**
     * Last In, First Out
     */
    LIFO,

    /**
     * Specific Identification
     * Taxpayer selects specific shares to sell
     */
    SPECIFIC_ID,

    /**
     * Average Cost
     * Used for mutual funds
     */
    AVERAGE_COST,

    /**
     * Highest In, First Out
     * Tax optimization strategy
     */
    HIFO
}
