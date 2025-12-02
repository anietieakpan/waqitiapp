package com.waqiti.compliance.domain;

/**
 * Risk level assessment for AML screening results.
 * Determines the severity of AML findings and appropriate actions.
 */
public enum AMLRiskLevel {

    /**
     * No risk identified - clear to proceed
     */
    NO_RISK(0, "No Risk", "Clear - no matches found"),

    /**
     * Minimal risk - very low confidence matches only
     */
    MINIMAL(1, "Minimal Risk", "Minimal risk - very low confidence matches"),

    /**
     * Low risk - proceed with standard monitoring
     */
    LOW(2, "Low Risk", "Low risk matches - proceed with monitoring"),

    /**
     * Medium risk - enhanced due diligence recommended
     */
    MEDIUM(3, "Medium Risk", "Medium risk matches - enhanced due diligence recommended"),

    /**
     * High risk - manual review required
     */
    HIGH(4, "High Risk", "High risk matches - manual review required"),

    /**
     * Critical risk - immediate action required, block transaction
     */
    CRITICAL(5, "Critical Risk", "Critical risk - immediate action required"),

    /**
     * Prohibited - entity is on sanctions list, must be blocked
     */
    PROHIBITED(6, "Prohibited", "Entity is prohibited - must be blocked");

    private final int level;
    private final String displayName;
    private final String description;

    AMLRiskLevel(int level, String displayName, String description) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this risk level requires manual review
     */
    public boolean requiresManualReview() {
        return this.level >= HIGH.level;
    }

    /**
     * Check if this risk level requires transaction blocking
     */
    public boolean requiresBlocking() {
        return this.level >= CRITICAL.level;
    }

    /**
     * Get risk level from numeric score
     */
    public static AMLRiskLevel fromScore(int score) {
        if (score == 0) return NO_RISK;
        if (score <= 25) return LOW;
        if (score <= 50) return MEDIUM;
        if (score <= 75) return HIGH;
        if (score <= 90) return CRITICAL;
        return PROHIBITED;
    }
}
