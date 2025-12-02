package com.waqiti.account.domain;

/**
 * Dormancy Action Enumeration
 * Defines the actions to be taken based on account dormancy level
 */
public enum DormancyAction {
    NO_ACTION("No Action", "No action required"),
    NOTIFY_USER("Notify User", "Send notification to account holder"),
    LIMIT_TRANSACTIONS("Limit Transactions", "Restrict certain transaction types"),
    FREEZE_ACCOUNT("Freeze Account", "Temporarily freeze the account"),
    CLOSE_ACCOUNT("Close Account", "Close the dormant account"),
    TRANSFER_FUNDS("Transfer Funds", "Transfer remaining funds to designated account"),
    REPORT_TO_AUTHORITIES("Report to Authorities", "Report abandoned funds to state authorities");

    private final String displayName;
    private final String description;

    DormancyAction(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static DormancyAction getActionForLevel(DormancyLevel level) {
        return switch (level) {
            case ACTIVE -> NO_ACTION;
            case INACTIVE -> NOTIFY_USER;
            case DORMANT_LEVEL_1 -> LIMIT_TRANSACTIONS;
            case DORMANT_LEVEL_2 -> FREEZE_ACCOUNT;
            case DORMANT_LEVEL_3 -> CLOSE_ACCOUNT;
            case ABANDONED -> REPORT_TO_AUTHORITIES;
        };
    }
}