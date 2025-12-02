package com.waqiti.account.domain;

/**
 * Compliance Level Enumeration
 * Defines the compliance verification levels for regulatory requirements
 */
public enum ComplianceLevel {
    BASIC("Basic", "Basic identity verification", 1000),
    STANDARD("Standard", "Standard KYC compliance", 10000),
    ENHANCED("Enhanced", "Enhanced due diligence", 50000),
    INSTITUTIONAL("Institutional", "Institutional compliance", Integer.MAX_VALUE);

    private final String displayName;
    private final String description;
    private final int transactionLimit;

    ComplianceLevel(String displayName, String description, int transactionLimit) {
        this.displayName = displayName;
        this.description = description;
        this.transactionLimit = transactionLimit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getTransactionLimit() {
        return transactionLimit;
    }

    public boolean allowsAmount(double amount) {
        return amount <= transactionLimit;
    }
}