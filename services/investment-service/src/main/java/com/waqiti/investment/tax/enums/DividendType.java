package com.waqiti.investment.tax.enums;

/**
 * Dividend Classification for IRS Form 1099-DIV
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
public enum DividendType {
    /**
     * Ordinary dividends (taxed as ordinary income)
     * Box 1a on Form 1099-DIV
     */
    ORDINARY,

    /**
     * Qualified dividends (taxed at preferential capital gains rates)
     * Box 1b on Form 1099-DIV
     * Must meet holding period requirements
     */
    QUALIFIED,

    /**
     * Capital gain distributions from mutual funds/ETFs
     * Box 2a on Form 1099-DIV
     */
    CAPITAL_GAIN,

    /**
     * Return of capital (non-taxable, reduces cost basis)
     * Box 3 on Form 1099-DIV
     */
    RETURN_OF_CAPITAL,

    /**
     * Exempt-interest dividends (from municipal bonds)
     * Box 10 on Form 1099-DIV
     */
    EXEMPT_INTEREST
}
