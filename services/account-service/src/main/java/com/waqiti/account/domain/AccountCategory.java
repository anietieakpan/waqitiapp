package com.waqiti.account.domain;

/**
 * Account Category Enumeration
 * Defines the broad categories of accounts for regulatory and operational purposes
 */
public enum AccountCategory {
    CONSUMER("Consumer Account", "Personal consumer banking"),
    BUSINESS("Business Account", "Business and commercial banking"),
    INVESTMENT("Investment Account", "Investment and brokerage services"),
    CRYPTO("Cryptocurrency", "Digital asset management"),
    FAMILY("Family Account", "Family and teen banking"),
    INSTITUTIONAL("Institutional", "Institutional and enterprise clients");

    private final String displayName;
    private final String description;

    AccountCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}