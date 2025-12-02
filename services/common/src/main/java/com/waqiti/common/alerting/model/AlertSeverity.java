package com.waqiti.common.alerting.model;

/**
 * Production-grade Alert Severity Levels
 *
 * Defines standardized severity levels for system alerts
 * Aligned with PagerDuty severity conventions
 *
 * @author Waqiti Engineering
 * @version 1.0.0
 */
public enum AlertSeverity {
    /**
     * CRITICAL - System down, immediate action required
     * Examples: Database offline, payment processing halted, security breach
     */
    CRITICAL(":rotating_light:", "#d9534f", 1),

    /**
     * ERROR - Major functionality impaired, urgent action needed
     * Examples: High error rate, performance degradation, service unavailable
     * Alias for HIGH severity
     */
    ERROR(":x:", "#f0ad4e", 2),

    /**
     * HIGH - Major functionality impaired, urgent action needed
     * Examples: High error rate, performance degradation, service unavailable
     */
    HIGH(":warning:", "#f0ad4e", 2),

    /**
     * WARNING - Significant issue, action needed within hours
     * Examples: Non-critical service degradation, elevated error rates
     * Alias for MEDIUM severity
     */
    WARNING(":large_orange_diamond:", "#5bc0de", 3),

    /**
     * MEDIUM - Significant issue, action needed within hours
     * Examples: Non-critical service degradation, elevated error rates
     */
    MEDIUM(":large_blue_diamond:", "#5bc0de", 3),

    /**
     * LOW - Minor issue, action needed within days
     * Examples: Warning thresholds exceeded, informational alerts
     */
    LOW(":small_blue_diamond:", "#5cb85c", 4),

    /**
     * INFO - Informational only, no action required
     * Examples: Deployment notifications, routine status updates
     */
    INFO(":information_source:", "#777777", 5);

    private final String emoji;
    private final String colorCode;
    private final int priority;

    AlertSeverity(String emoji, String colorCode, int priority) {
        this.emoji = emoji;
        this.colorCode = colorCode;
        this.priority = priority;
    }

    /**
     * Get Slack emoji for this severity level
     */
    public String getEmoji() {
        return emoji;
    }

    /**
     * Get color code for visual representation (Slack, HTML)
     */
    public String getColorCode() {
        return colorCode;
    }

    /**
     * Get numeric priority (1=highest, 5=lowest)
     */
    public int getPriority() {
        return priority;
    }
}
