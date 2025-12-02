package com.waqiti.investment.tax.enums;

/**
 * Tax Transaction Types for IRS Reporting
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
public enum TransactionType {
    // Stock Transactions (1099-B)
    STOCK_SALE,
    STOCK_PURCHASE,

    // Dividend Transactions (1099-DIV)
    DIVIDEND_ORDINARY,
    DIVIDEND_QUALIFIED,
    DIVIDEND_CAPITAL_GAIN,
    RETURN_OF_CAPITAL,

    // Interest Income (1099-INT)
    INTEREST_INCOME,
    BOND_INTEREST,

    // Options
    OPTION_EXERCISE,
    OPTION_ASSIGNMENT,

    // Corporate Actions
    STOCK_SPLIT,
    MERGER_ACQUISITION,
    SPINOFF
}
