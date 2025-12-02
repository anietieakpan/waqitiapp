package com.waqiti.investment.tax.enums;

/**
 * IRS Form Types for Tax Reporting
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
public enum DocumentType {
    /**
     * Form 1099-B: Proceeds from Broker and Barter Exchange Transactions
     * Reports sales of stocks, bonds, mutual funds, and other securities
     */
    FORM_1099_B,

    /**
     * Form 1099-DIV: Dividends and Distributions
     * Reports dividend payments and capital gain distributions
     */
    FORM_1099_DIV,

    /**
     * Form 1099-INT: Interest Income
     * Reports interest income from bank accounts, bonds, etc.
     */
    FORM_1099_INT,

    /**
     * Form 1099-MISC: Miscellaneous Income
     * Reports other types of income payments
     */
    FORM_1099_MISC
}
