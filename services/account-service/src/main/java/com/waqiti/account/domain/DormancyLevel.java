package com.waqiti.account.domain;

/**
 * Dormancy Level Enumeration
 * Defines the levels of account dormancy for regulatory compliance
 */
public enum DormancyLevel {
    ACTIVE("Active", 0, "Account is actively being used"),
    INACTIVE("Inactive", 90, "Account has no activity for 90+ days"),
    DORMANT_LEVEL_1("Dormant Level 1", 180, "Account dormant for 6+ months"),
    DORMANT_LEVEL_2("Dormant Level 2", 365, "Account dormant for 1+ year"),
    DORMANT_LEVEL_3("Dormant Level 3", 1095, "Account dormant for 3+ years"),
    ABANDONED("Abandoned", 2555, "Account considered abandoned (7+ years)");

    private final String displayName;
    private final int daysSinceLastActivity;
    private final String description;

    DormancyLevel(String displayName, int daysSinceLastActivity, String description) {
        this.displayName = displayName;
        this.daysSinceLastActivity = daysSinceLastActivity;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDaysSinceLastActivity() {
        return daysSinceLastActivity;
    }

    public String getDescription() {
        return description;
    }

    public boolean requiresAction() {
        return this.ordinal() >= DORMANT_LEVEL_1.ordinal();
    }

    public static DormancyLevel fromDaysInactive(int days) {
        if (days < 90) return ACTIVE;
        if (days < 180) return INACTIVE;
        if (days < 365) return DORMANT_LEVEL_1;
        if (days < 1095) return DORMANT_LEVEL_2;
        if (days < 2555) return DORMANT_LEVEL_3;
        return ABANDONED;
    }
}