package com.waqiti.account.domain;

/**
 * Account Type Enumeration
 * Defines the various types of financial accounts supported by the system
 */
public enum AccountType {
    CHECKING("Checking Account", "CHK"),
    SAVINGS("Savings Account", "SAV"), 
    BUSINESS_CHECKING("Business Checking", "BCK"),
    BUSINESS_SAVINGS("Business Savings", "BSV"),
    CREDIT_CARD("Credit Card Account", "CC"),
    LOAN("Loan Account", "LOAN"),
    INVESTMENT("Investment Account", "INV"),
    CRYPTO("Cryptocurrency Account", "CRYPTO"),
    FAMILY("Family Account", "FAM"),
    TEEN("Teen Account", "TEEN"),
    CHILD("Child Account", "CHILD"),
    JOINT("Joint Account", "JNT"),
    TRUST("Trust Account", "TRUST"),
    ESCROW("Escrow Account", "ESC"),
    MERCHANT("Merchant Account", "MERCH");

    private final String displayName;
    private final String code;

    AccountType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }
}