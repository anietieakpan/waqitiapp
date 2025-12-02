package com.waqiti.investment.domain.enums;

public enum AccountStatus {
    PENDING_ACTIVATION("Pending Activation"),
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    CLOSED("Closed"),
    RESTRICTED("Restricted"),
    UNDER_REVIEW("Under Review");

    private final String displayName;

    AccountStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOperational() {
        return this == ACTIVE;
    }

    public boolean canReceiveFunds() {
        return this == ACTIVE || this == RESTRICTED;
    }

    public boolean canWithdrawFunds() {
        return this == ACTIVE;
    }

    public boolean canTrade() {
        return this == ACTIVE;
    }
}