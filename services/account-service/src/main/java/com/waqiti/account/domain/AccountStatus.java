package com.waqiti.account.domain;

/**
 * Account Status Enumeration
 * Defines the lifecycle states of financial accounts
 */
public enum AccountStatus {
    PENDING("Pending Activation", "Account is pending activation"),
    ACTIVE("Active", "Account is active and operational"),
    SUSPENDED("Suspended", "Account is temporarily suspended"),
    FROZEN("Frozen", "Account is frozen due to security concerns"),
    CLOSED("Closed", "Account has been permanently closed"),
    DORMANT("Dormant", "Account is inactive due to lack of activity"),
    BLOCKED("Blocked", "Account is blocked pending investigation"),
    LIMITED("Limited", "Account has limited functionality"),
    UNDER_REVIEW("Under Review", "Account is under compliance review"),
    RESTRICTED("Restricted", "Account has usage restrictions");

    private final String displayName;
    private final String description;

    AccountStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOperational() {
        return this == ACTIVE || this == LIMITED;
    }

    public boolean isBlocked() {
        return this == SUSPENDED || this == FROZEN || this == BLOCKED;
    }
}