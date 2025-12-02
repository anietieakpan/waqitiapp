package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonValue;
import javax.annotation.concurrent.Immutable;

/**
 * Canonical Alert Level enumeration for the Waqiti fraud detection system.
 *
 * <p><strong>This is the ONLY AlertLevel enum - all other versions have been consolidated here.</strong></p>
 *
 * <p>Thread Safety: IMMUTABLE - Enums are inherently thread-safe</p>
 *
 * <p>Defines escalation and response urgency levels with SLA requirements,
 * notification rules, and recommended automated actions.</p>
 *
 * @since 1.0.0
 * @version 2.0.0 - Consolidated from alert, dto, and model packages
 */
@Immutable
public enum AlertLevel {
    /**
     * Critical alerts requiring immediate action (< 15 minutes SLA)
     * <ul>
     *   <li>Examples: High-value fraud, account takeover, money laundering</li>
     *   <li>Action: BLOCK_TRANSACTION_AND_NOTIFY</li>
     *   <li>Notification: Required immediately</li>
     * </ul>
     */
    CRITICAL("Critical", 1, 15, 0.90, true, true, "#DC2626"),

    /**
     * High priority alerts requiring urgent action (< 1 hour SLA)
     * <ul>
     *   <li>Examples: Transaction fraud, suspicious patterns, account compromise</li>
     *   <li>Action: REQUIRE_ADDITIONAL_VERIFICATION</li>
     *   <li>Notification: Required within 30 minutes</li>
     * </ul>
     */
    HIGH("High", 2, 60, 0.70, true, true, "#EA580C"),

    /**
     * Medium priority alerts requiring timely review (< 4 hours SLA)
     * <ul>
     *   <li>Examples: Velocity breaches, behavioral anomalies, geographic anomalies</li>
     *   <li>Action: FLAG_FOR_REVIEW</li>
     *   <li>Notification: Management notification required</li>
     * </ul>
     */
    MEDIUM("Medium", 3, 240, 0.50, false, true, "#D97706"),

    /**
     * Low priority alerts for routine review (< 24 hours SLA)
     * <ul>
     *   <li>Examples: Minor rule violations, threshold breaches</li>
     *   <li>Action: LOG_AND_MONITOR</li>
     *   <li>Notification: Optional</li>
     * </ul>
     */
    LOW("Low", 4, 1440, 0.30, false, false, "#059669"),

    /**
     * Informational alerts for awareness (< 3 days SLA)
     * <ul>
     *   <li>Examples: Pattern notifications, system events, statistics</li>
     *   <li>Action: LOG_ONLY</li>
     *   <li>Notification: None</li>
     * </ul>
     */
    INFO("Info", 5, 4320, 0.10, false, false, "#0891B2");

    private final String displayName;
    private final int priority; // Lower number = higher priority (1 = highest)
    private final int slaMinutes; // SLA response time in minutes
    private final double minimumRiskScore; // Minimum risk score threshold (0.0 - 1.0)
    private final boolean requiresNotification;
    private final boolean requiresInvestigation;
    private final String uiColor; // Hex color for UI display

    /**
     * Private constructor for enum constants
     */
    AlertLevel(String displayName, int priority, int slaMinutes, double minimumRiskScore,
               boolean requiresNotification, boolean requiresInvestigation, String uiColor) {
        this.displayName = displayName;
        this.priority = priority;
        this.slaMinutes = slaMinutes;
        this.minimumRiskScore = minimumRiskScore;
        this.requiresNotification = requiresNotification;
        this.requiresInvestigation = requiresInvestigation;
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
     * Get priority (1 = highest priority)
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Get SLA response time in minutes
     */
    public int getSlaMinutes() {
        return slaMinutes;
    }

    /**
     * Get minimum risk score threshold for this level
     */
    public double getMinimumRiskScore() {
        return minimumRiskScore;
    }

    /**
     * Check if immediate notification is required
     */
    public boolean requiresNotification() {
        return requiresNotification;
    }

    /**
     * Check if investigation is required
     */
    public boolean requiresInvestigation() {
        return requiresInvestigation;
    }

    /**
     * Get UI color (hex format)
     */
    public String getColor() {
        return uiColor;
    }

    /**
     * Check if this level is higher priority than another
     *
     * @param other The other alert level to compare
     * @return true if this level has higher priority (lower priority number)
     */
    public boolean isHigherPriorityThan(AlertLevel other) {
        return this.priority < other.priority;
    }

    /**
     * Check if this level is at least as high priority as another
     *
     * @param other The other alert level to compare
     * @return true if this level has equal or higher priority
     */
    public boolean isAtLeastPriority(AlertLevel other) {
        return this.priority <= other.priority;
    }

    /**
     * Check if this level requires immediate action (within 1 hour)
     *
     * @return true for CRITICAL and HIGH levels
     */
    public boolean requiresImmediateAction() {
        return this == CRITICAL || this == HIGH;
    }

    /**
     * Get recommended automated action for this level
     *
     * @return Action string for automated processing
     */
    public String getRecommendedAction() {
        return switch (this) {
            case CRITICAL -> "BLOCK_TRANSACTION_AND_NOTIFY";
            case HIGH -> "REQUIRE_ADDITIONAL_VERIFICATION";
            case MEDIUM -> "FLAG_FOR_REVIEW";
            case LOW -> "LOG_AND_MONITOR";
            case INFO -> "LOG_ONLY";
        };
    }

    /**
     * Convert string to AlertLevel (case-insensitive with fallback)
     *
     * @param level String representation of alert level
     * @return AlertLevel enum, defaults to MEDIUM if invalid
     */
    public static AlertLevel fromString(String level) {
        if (level == null || level.isBlank()) {
            return MEDIUM; // Safe default
        }

        try {
            return AlertLevel.valueOf(level.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            // Log warning in production
            return MEDIUM; // Safe fallback
        }
    }

    /**
     * Get alert level based on fraud risk score (0.0 - 1.0)
     *
     * @param score Risk score from 0.0 to 1.0
     * @return Appropriate AlertLevel for the score
     */
    public static AlertLevel fromRiskScore(double score) {
        if (score >= CRITICAL.minimumRiskScore) return CRITICAL;
        if (score >= HIGH.minimumRiskScore) return HIGH;
        if (score >= MEDIUM.minimumRiskScore) return MEDIUM;
        if (score >= LOW.minimumRiskScore) return LOW;
        return INFO;
    }

    /**
     * Get alert level based on fraud score percentage (0-100)
     *
     * @param scorePercentage Risk score from 0 to 100
     * @return Appropriate AlertLevel for the score
     */
    public static AlertLevel fromFraudScore(double scorePercentage) {
        return fromRiskScore(scorePercentage / 100.0);
    }

    /**
     * Get alert level from priority number
     *
     * @param priority Priority value (1-5)
     * @return AlertLevel corresponding to priority, defaults to MEDIUM
     */
    public static AlertLevel fromPriority(int priority) {
        for (AlertLevel level : values()) {
            if (level.priority == priority) {
                return level;
            }
        }
        return MEDIUM; // Safe default
    }

    /**
     * Get SLA deadline timestamp based on current time
     *
     * @return Timestamp when SLA expires
     */
    public java.time.LocalDateTime getSlaDeadline() {
        return java.time.LocalDateTime.now().plusMinutes(slaMinutes);
    }

    /**
     * Check if SLA is breached given an alert creation time
     *
     * @param createdAt When the alert was created
     * @return true if SLA has been breached
     */
    public boolean isSlaBreached(java.time.LocalDateTime createdAt) {
        if (createdAt == null) {
            return false;
        }
        java.time.LocalDateTime deadline = createdAt.plusMinutes(slaMinutes);
        return java.time.LocalDateTime.now().isAfter(deadline);
    }

    /**
     * Get remaining time until SLA breach in minutes
     *
     * @param createdAt When the alert was created
     * @return Minutes remaining (negative if breached)
     */
    public long getMinutesUntilSlaBreach(java.time.LocalDateTime createdAt) {
        if (createdAt == null) {
            return slaMinutes;
        }
        java.time.LocalDateTime deadline = createdAt.plusMinutes(slaMinutes);
        return java.time.Duration.between(java.time.LocalDateTime.now(), deadline).toMinutes();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
