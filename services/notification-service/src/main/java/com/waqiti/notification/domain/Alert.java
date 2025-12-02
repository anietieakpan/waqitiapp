package com.waqiti.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Alert domain model for notification service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    private String id;
    private String type;
    private AlertSeverity severity;
    private String title;
    private String description;
    private String source;
    private String affectedService;
    private String category;
    private Boolean requiresAcknowledgment;
    private LocalDateTime timestamp;
    private String correlationId;
    private Map<String, Object> metadata;
    private String status;
    private String assignee;
    private LocalDateTime resolvedAt;
    private String notificationChannels;

    /**
     * Check if alert is resolved
     */
    public boolean isResolved() {
        return resolvedAt != null && ("RESOLVED".equals(status) || "CLOSED".equals(status));
    }

    /**
     * Get alert age in minutes
     */
    public long getAgeInMinutes() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
    }

    /**
     * Check if alert is critical and unresolved
     */
    public boolean isCriticalAndUnresolved() {
        return AlertSeverity.CRITICAL.equals(severity) && !isResolved();
    }

    /**
     * Check if escalation is required
     */
    public boolean requiresEscalation() {
        return severity == AlertSeverity.HIGH ||
               severity == AlertSeverity.CRITICAL ||
               Boolean.TRUE.equals(requiresAcknowledgment);
    }
}