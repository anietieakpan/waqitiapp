package com.waqiti.common.alerting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Production-grade Alert model
 *
 * Represents a system alert with severity, source, and metadata
 * Used for multi-channel alerting (PagerDuty, Slack, Email, SMS)
 *
 * @author Waqiti Engineering
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    /**
     * Unique alert identifier (used for deduplication)
     */
    private String id;

    /**
     * Alert severity level
     */
    private AlertSeverity severity;

    /**
     * Alert source/origin (service name, component)
     */
    private String source;

    /**
     * Alert title (short summary)
     */
    private String title;

    /**
     * Alert message/description (detailed information)
     */
    private String message;

    /**
     * Alert description (same as message for builder compatibility)
     */
    private String description;

    /**
     * Alert type/category
     */
    private String type;

    /**
     * Alert metadata (additional context)
     */
    private Map<String, Object> metadata;

    /**
     * Alert creation timestamp
     */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Alert timestamp (alias for createdAt for builder compatibility)
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Alert deduplication key (for grouping similar alerts)
     */
    private String dedupKey;

    /**
     * Whether this alert should trigger PagerDuty incident
     */
    @Builder.Default
    private boolean triggerPagerDuty = false;

    /**
     * Whether this alert should send Slack notification
     */
    @Builder.Default
    private boolean sendSlack = true;

    /**
     * Number of times this alert has been sent (for rate limiting)
     */
    @Builder.Default
    private int sendCount = 0;

    /**
     * Convenience method: Get alert ID
     * Maps to 'id' field for backward compatibility
     */
    public String getAlertId() {
        return this.id;
    }

    /**
     * Convenience method: Set alert ID
     * Maps to 'id' field for backward compatibility
     */
    public void setAlertId(String alertId) {
        this.id = alertId;
    }
}
