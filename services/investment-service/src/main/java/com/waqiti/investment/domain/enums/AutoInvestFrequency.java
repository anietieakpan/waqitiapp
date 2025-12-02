package com.waqiti.investment.domain.enums;

public enum AutoInvestFrequency {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    BIWEEKLY("Bi-Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly");

    private final String displayName;

    AutoInvestFrequency(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDaysInterval() {
        switch (this) {
            case DAILY:
                return 1;
            case WEEKLY:
                return 7;
            case BIWEEKLY:
                return 14;
            case MONTHLY:
                return 30; // Approximate
            case QUARTERLY:
                return 90; // Approximate
            default:
                return 30;
        }
    }

    public boolean isHighFrequency() {
        return this == DAILY || this == WEEKLY;
    }
}