/**
 * Recommended Action Enum
 * Actions recommended by fraud detection system
 */
package com.waqiti.crypto.entity;

public enum RecommendedAction {
    ALLOW("Allow transaction to proceed"),
    MONITOR("Monitor transaction but allow"),
    ADDITIONAL_VERIFICATION("Require additional verification"),
    MANUAL_REVIEW("Hold for manual review"),
    BLOCK("Block transaction");

    private final String description;

    RecommendedAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean allowsTransaction() {
        return this == ALLOW || this == MONITOR;
    }

    public boolean requiresIntervention() {
        return this == ADDITIONAL_VERIFICATION || this == MANUAL_REVIEW || this == BLOCK;
    }

    public boolean blocksTransaction() {
        return this == BLOCK;
    }

    public boolean requiresManualReview() {
        return this == MANUAL_REVIEW;
    }
}