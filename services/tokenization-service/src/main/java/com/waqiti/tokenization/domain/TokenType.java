package com.waqiti.tokenization.domain;

/**
 * Token Type Enumeration
 *
 * Defines the type of sensitive data being tokenized
 *
 * @author Waqiti Platform Engineering
 */
public enum TokenType {
    /**
     * Payment card (PAN - Primary Account Number)
     * PCI-DSS Requirement 3.2: Must be tokenized
     */
    CARD("CARD", 90),

    /**
     * Bank account number
     */
    BANK_ACCOUNT("BANK", 365),

    /**
     * Social Security Number
     */
    SSN("SSN", 3650),

    /**
     * Tax Identification Number
     */
    TAX_ID("TAX", 3650),

    /**
     * CVV (Card Verification Value)
     * Note: PCI-DSS prohibits storage even when tokenized
     * Included for completeness but should not be used
     */
    CVV("CVV", 1),

    /**
     * Driver's License Number
     */
    DRIVERS_LICENSE("DL", 1825),

    /**
     * Passport Number
     */
    PASSPORT("PASS", 1825),

    /**
     * Generic sensitive data
     */
    SENSITIVE_DATA("DATA", 90);

    private final String prefix;
    private final int defaultExpirationDays;

    TokenType(String prefix, int defaultExpirationDays) {
        this.prefix = prefix;
        this.defaultExpirationDays = defaultExpirationDays;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getDefaultExpirationDays() {
        return defaultExpirationDays;
    }
}
