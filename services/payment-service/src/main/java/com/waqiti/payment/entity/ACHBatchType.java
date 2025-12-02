package com.waqiti.payment.entity;

/**
 * ACH Batch Type Enum
 *
 * Standard Entry Class (SEC) codes defined by NACHA
 * Each type has specific rules and use cases
 *
 * @author Waqiti Engineering
 */
public enum ACHBatchType {
    /**
     * PPD - Prearranged Payment and Deposit Entry
     * Consumer payments (payroll, bill pay, etc.)
     */
    PPD("Prearranged Payment and Deposit"),

    /**
     * CCD - Corporate Credit or Debit Entry
     * Business-to-business payments
     */
    CCD("Corporate Credit or Debit"),

    /**
     * WEB - Internet-Initiated Entry
     * E-commerce and online payments
     */
    WEB("Internet-Initiated Entry"),

    /**
     * TEL - Telephone-Initiated Entry
     * Phone-authorized payments
     */
    TEL("Telephone-Initiated Entry"),

    /**
     * CIE - Customer-Initiated Entry
     * ATM and point-of-sale transactions
     */
    CIE("Customer-Initiated Entry"),

    /**
     * CTX - Corporate Trade Exchange
     * Complex business payments with addenda records
     */
    CTX("Corporate Trade Exchange"),

    /**
     * POS - Point-of-Sale Entry
     * Retail point-of-sale debit transactions
     */
    POS("Point-of-Sale Entry"),

    /**
     * ARC - Accounts Receivable Entry
     * Check-to-ACH conversion at lockbox
     */
    ARC("Accounts Receivable Entry"),

    /**
     * BOC - Back Office Conversion Entry
     * Check-to-ACH conversion in back office
     */
    BOC("Back Office Conversion Entry"),

    /**
     * POP - Point-of-Purchase Entry
     * Check-to-ACH conversion at point of purchase
     */
    POP("Point-of-Purchase Entry"),

    /**
     * RCK - Re-presented Check Entry
     * Re-submission of returned checks
     */
    RCK("Re-presented Check Entry"),

    /**
     * IAT - International ACH Transaction
     * Cross-border ACH payments
     */
    IAT("International ACH Transaction"),

    /**
     * MTE - Machine Transfer Entry
     * ATM cash withdrawals
     */
    MTE("Machine Transfer Entry"),

    /**
     * SHR - Shared Network Transaction
     * ATM network transactions
     */
    SHR("Shared Network Transaction"),

    /**
     * XCK - Destroyed Check Entry
     * Destroyed checks converted to ACH
     */
    XCK("Destroyed Check Entry");

    private final String description;

    ACHBatchType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this batch type requires consumer authorization
     */
    public boolean requiresConsumerAuthorization() {
        return this == PPD || this == WEB || this == TEL;
    }

    /**
     * Check if this batch type is for business transactions
     */
    public boolean isBusinessTransaction() {
        return this == CCD || this == CTX;
    }

    /**
     * Check if this batch type is check conversion
     */
    public boolean isCheckConversion() {
        return this == ARC || this == BOC || this == POP || this == RCK || this == XCK;
    }

    /**
     * Check if this batch type requires addenda records
     */
    public boolean requiresAddenda() {
        return this == CTX || this == IAT;
    }
}
