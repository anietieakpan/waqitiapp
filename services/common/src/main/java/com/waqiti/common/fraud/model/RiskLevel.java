package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonValue;
import javax.annotation.concurrent.Immutable;

/**
 * Risk Level enumeration for fraud detection and user risk profiling.
 *
 * <p>Represents the overall risk assessment level for transactions, users, and accounts.
 * Used across fraud detection, risk scoring, and compliance systems.</p>
 *
 * <p>Thread Safety: IMMUTABLE - Enums are inherently thread-safe</p>
 *
 * @since 1.0.0
 * @version 1.0.0
 */
@Immutable
public enum RiskLevel {
    /**
     * Minimal risk - Trusted user/transaction with clean history
     * <ul>
     *   <li>Score Range: 0.0 - 0.2</li>
     *   <li>Action: Allow without additional checks</li>
     *   <li>Examples: Verified users with 6+ months history, small amounts</li>
     * </ul>
     */
    MINIMAL("Minimal", 1, 0.0, 0.2, "#10B981"),

    /**
     * Low risk - Normal transaction patterns, minor concerns
     * <ul>
     *   <li>Score Range: 0.2 - 0.4</li>
     *   <li>Action: Standard processing</li>
     *   <li>Examples: Regular users, typical transaction amounts</li>
     * </ul>
     */
    LOW("Low", 2, 0.2, 0.4, "#059669"),

    /**
     * Medium risk - Some suspicious indicators present
     * <ul>
     *   <li>Score Range: 0.4 - 0.6</li>
     *   <li>Action: Enhanced monitoring, possible manual review</li>
     *   <li>Examples: New users, unusual patterns, velocity breaches</li>
     * </ul>
     */
    MEDIUM("Medium", 3, 0.4, 0.6, "#D97706"),

    /**
     * High risk - Multiple fraud indicators detected
     * <ul>
     *   <li>Score Range: 0.6 - 0.8</li>
     *   <li>Action: Additional verification required</li>
     *   <li>Examples: Suspicious device, location mismatch, high-value transactions</li>
     * </ul>
     */
    HIGH("High", 4, 0.6, 0.8, "#EA580C"),

    /**
     * Critical risk - Strong fraud indicators, immediate action required
     * <ul>
     *   <li>Score Range: 0.8 - 1.0</li>
     *   <li>Action: Block transaction, notify security team</li>
     *   <li>Examples: Known fraud patterns, blacklisted entities, account takeover</li>
     * </ul>
     */
    CRITICAL("Critical", 5, 0.8, 1.0, "#DC2626");

    private final String displayName;
    private final int priority; // Lower number = lower risk (1 = minimal risk)
    private final double minScore; // Minimum risk score for this level (0.0 - 1.0)
    private final double maxScore; // Maximum risk score for this level (0.0 - 1.0)
    private final String uiColor; // Hex color for UI display

    /**
     * Private constructor for enum constants
     */
    RiskLevel(String displayName, int priority, double minScore, double maxScore, String uiColor) {
        this.displayName = displayName;
        this.priority = priority;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.uiColor = uiColor;
    }

    /**
     * Get display name for UI
     */
    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get priority (1 = minimal risk, 5 = critical risk)
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Get minimum risk score threshold for this level
     */
    public double getMinScore() {
        return minScore;
    }

    /**
     * Get maximum risk score threshold for this level
     */
    public double getMaxScore() {
        return maxScore;
    }

    /**
     * Get UI color (hex format)
     */
    public String getColor() {
        return uiColor;
    }

    /**
     * Check if this level is higher risk than another
     *
     * @param other The other risk level to compare
     * @return true if this level has higher risk (higher priority number)
     */
    public boolean isHigherRiskThan(RiskLevel other) {
        return this.priority > other.priority;
    }

    /**
     * Check if this level is at least as risky as another
     *
     * @param other The other risk level to compare
     * @return true if this level has equal or higher risk
     */
    public boolean isAtLeastRisk(RiskLevel other) {
        return this.priority >= other.priority;
    }

    /**
     * Check if this level requires enhanced verification
     *
     * @return true for HIGH and CRITICAL levels
     */
    public boolean requiresEnhancedVerification() {
        return this == HIGH || this == CRITICAL;
    }

    /**
     * Check if this level requires immediate action
     *
     * @return true for CRITICAL level
     */
    public boolean requiresImmediateAction() {
        return this == CRITICAL;
    }

    /**
     * Get recommended action for this risk level
     *
     * @return Action string for automated processing
     */
    public String getRecommendedAction() {
        return switch (this) {
            case MINIMAL -> "ALLOW_WITHOUT_CHECKS";
            case LOW -> "STANDARD_PROCESSING";
            case MEDIUM -> "ENHANCED_MONITORING";
            case HIGH -> "REQUIRE_VERIFICATION";
            case CRITICAL -> "BLOCK_AND_NOTIFY";
        };
    }

    /**
     * Convert string to RiskLevel (case-insensitive with fallback)
     *
     * @param level String representation of risk level
     * @return RiskLevel enum, defaults to MEDIUM if invalid
     */
    public static RiskLevel fromString(String level) {
        if (level == null || level.isBlank()) {
            return MEDIUM; // Safe default
        }

        try {
            return RiskLevel.valueOf(level.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            // Log warning in production
            return MEDIUM; // Safe fallback
        }
    }

    /**
     * Get risk level based on risk score (0.0 - 1.0)
     *
     * @param score Risk score from 0.0 to 1.0
     * @return Appropriate RiskLevel for the score
     */
    public static RiskLevel fromScore(double score) {
        if (score >= CRITICAL.minScore) return CRITICAL;
        if (score >= HIGH.minScore) return HIGH;
        if (score >= MEDIUM.minScore) return MEDIUM;
        if (score >= LOW.minScore) return LOW;
        return MINIMAL;
    }

    /**
     * Get risk level based on percentage score (0-100)
     *
     * @param scorePercentage Risk score from 0 to 100
     * @return Appropriate RiskLevel for the score
     */
    public static RiskLevel fromPercentage(double scorePercentage) {
        return fromScore(scorePercentage / 100.0);
    }

    /**
     * Get risk level from priority number
     *
     * @param priority Priority value (1-5)
     * @return RiskLevel corresponding to priority, defaults to MEDIUM
     */
    public static RiskLevel fromPriority(int priority) {
        for (RiskLevel level : values()) {
            if (level.priority == priority) {
                return level;
            }
        }
        return MEDIUM; // Safe default
    }

    /**
     * Convert to FraudRiskLevel for compatibility
     *
     * @return Equivalent FraudRiskLevel
     */
    public FraudRiskLevel toFraudRiskLevel() {
        return switch (this) {
            case MINIMAL, LOW -> FraudRiskLevel.LOW;
            case MEDIUM -> FraudRiskLevel.MEDIUM;
            case HIGH -> FraudRiskLevel.HIGH;
            case CRITICAL -> FraudRiskLevel.CRITICAL;
        };
    }

    /**
     * Create from FraudRiskLevel for compatibility
     *
     * @param fraudRiskLevel FraudRiskLevel to convert
     * @return Equivalent RiskLevel
     */
    public static RiskLevel fromFraudRiskLevel(FraudRiskLevel fraudRiskLevel) {
        if (fraudRiskLevel == null) return MEDIUM;
        return switch (fraudRiskLevel) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case CRITICAL -> CRITICAL;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
