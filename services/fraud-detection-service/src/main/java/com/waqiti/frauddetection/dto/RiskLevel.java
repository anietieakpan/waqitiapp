package com.waqiti.frauddetection.dto;

/**
 * Risk Level Enumeration
 *
 * Standardized risk levels for fraud detection across the platform.
 * Used for risk classification, decision making, and alert routing.
 *
 * PRODUCTION-GRADE ENUM
 * - Clear severity ordering (LOW < MEDIUM < HIGH < CRITICAL)
 * - Score range mapping for ML model integration
 * - Action recommendations for each level
 * - Alert severity mapping
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0 - Enhanced Production Implementation
 */
public enum RiskLevel {
    LOW(0, 25, "Low Risk", "Transaction approved with standard monitoring", 0),
    MEDIUM(26, 50, "Medium Risk", "Transaction approved with enhanced monitoring", 1),
    HIGH(51, 75, "High Risk", "Transaction flagged for review", 2),
    CRITICAL(76, 100, "Critical Risk", "Transaction blocked pending investigation", 3);

    private final int minScore;
    private final int maxScore;
    private final String displayName;
    private final String description;
    private final int severity;

    RiskLevel(int minScore, int maxScore, String displayName, String description, int severity) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.displayName = displayName;
        this.description = description;
        this.severity = severity;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getSeverity() {
        return severity;
    }

    /**
     * Get risk level from score (0-100 scale)
     */
    public static RiskLevel fromScore(double score) {
        int intScore = (int) Math.round(score);

        for (RiskLevel level : values()) {
            if (intScore >= level.minScore && intScore <= level.maxScore) {
                return level;
            }
        }

        // Default to CRITICAL for scores above 100
        return intScore > 100 ? CRITICAL : LOW;
    }

    /**
     * Get risk level from normalized score (0.0-1.0 scale)
     * Common for ML models
     */
    public static RiskLevel fromNormalizedScore(double normalizedScore) {
        if (normalizedScore < 0.0 || normalizedScore > 1.0) {
            throw new IllegalArgumentException(
                "Normalized score must be between 0.0 and 1.0, got: " + normalizedScore
            );
        }
        return fromScore(normalizedScore * 100);
    }

    /**
     * Check if risk level requires action
     */
    public boolean requiresAction() {
        return this == HIGH || this == CRITICAL;
    }

    /**
     * Check if risk level allows automatic approval
     */
    public boolean allowsAutoApproval() {
        return this == LOW || this == MEDIUM;
    }

    /**
     * Check if this risk level requires manual review
     */
    public boolean requiresManualReview() {
        return this == HIGH || this == CRITICAL;
    }

    /**
     * Check if this risk level should block transaction
     */
    public boolean shouldBlock() {
        return this == CRITICAL;
    }

    /**
     * Check if this risk level is higher than another
     */
    public boolean isHigherThan(RiskLevel other) {
        return this.severity > other.severity;
    }

    /**
     * Check if this risk level is lower than another
     */
    public boolean isLowerThan(RiskLevel other) {
        return this.severity < other.severity;
    }

    /**
     * Escalate to next higher risk level
     * Returns CRITICAL if already at CRITICAL
     */
    public RiskLevel escalate() {
        switch (this) {
            case LOW: return MEDIUM;
            case MEDIUM: return HIGH;
            case HIGH:
            case CRITICAL: return CRITICAL;
            default: return CRITICAL;
        }
    }

    /**
     * De-escalate to next lower risk level
     * Returns LOW if already at LOW
     */
    public RiskLevel deescalate() {
        switch (this) {
            case CRITICAL: return HIGH;
            case HIGH: return MEDIUM;
            case MEDIUM:
            case LOW: return LOW;
            default: return LOW;
        }
    }

    /**
     * Check if this risk level requires PagerDuty alert
     */
    public boolean requiresPagerDutyAlert() {
        return this == CRITICAL;
    }

    /**
     * Check if this risk level requires Slack notification
     */
    public boolean requiresSlackNotification() {
        return this == HIGH || this == CRITICAL;
    }

    /**
     * Get recommended monitoring interval in minutes
     */
    public int getMonitoringIntervalMinutes() {
        switch (this) {
            case LOW: return 1440; // 24 hours
            case MEDIUM: return 60; // 1 hour
            case HIGH: return 15; // 15 minutes
            case CRITICAL: return 5; // 5 minutes
            default: return 60;
        }
    }
}