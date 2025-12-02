package com.waqiti.investment.domain.enums;

public enum AutoInvestStatus {
    ACTIVE("Active"),
    PAUSED("Paused"),
    CANCELLED("Cancelled"),
    COMPLETED("Completed"),
    PENDING("Pending");

    private final String displayName;

    AutoInvestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isExecutable() {
        return this == ACTIVE;
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == COMPLETED;
    }

    public boolean canBeModified() {
        return this == ACTIVE || this == PAUSED || this == PENDING;
    }
}