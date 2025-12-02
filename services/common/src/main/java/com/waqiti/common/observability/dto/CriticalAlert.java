package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a critical alert that requires immediate attention
 */
@Data
@Builder
public class CriticalAlert {
    
    private String id;
    private String title;
    private String description;
    private AlertSeverity severity;
    private AlertCategory category;
    private String source;
    private LocalDateTime timestamp;
    private LocalDateTime expiresAt;
    private AlertStatus status;
    
    // Context and metadata
    private String affectedService;
    private String affectedResource;
    private Map<String, Object> metadata;
    private Map<String, String> tags;
    
    // Alert specific data
    private Double threshold;
    private Double currentValue;
    private String unit;
    private String conditionDescription;
    
    // Action and resolution
    private String recommendedAction;
    private String escalationPolicy;
    private String assignedTo;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private String resolutionNotes;
    private LocalDateTime resolvedAt;
    
    // Correlation
    private String correlationId;
    private String parentAlertId;
    private Integer relatedAlertsCount;
    
    /**
     * Check if alert requires immediate action
     */
    public boolean requiresImmediateAction() {
        // Emergency and Critical severity alerts always require immediate action
        if (severity == AlertSeverity.EMERGENCY || severity == AlertSeverity.CRITICAL) {
            return true;
        }
        
        // High severity alerts that are not acknowledged
        return severity == AlertSeverity.HIGH && acknowledgedAt == null;
    }
    
    /**
     * Check if alert requires immediate escalation
     */
    public boolean requiresEscalation() {
        if (status == AlertStatus.RESOLVED || status == AlertStatus.SUPPRESSED) {
            return false;
        }
        
        // Emergency and Critical alerts that are unacknowledged for more than 5 minutes
        if ((severity == AlertSeverity.EMERGENCY || severity == AlertSeverity.CRITICAL) &&
            acknowledgedAt == null &&
            timestamp.isBefore(LocalDateTime.now().minusMinutes(5))) {
            return true;
        }
        
        // High severity alerts unacknowledged for more than 15 minutes
        return severity == AlertSeverity.HIGH &&
               acknowledgedAt == null &&
               timestamp.isBefore(LocalDateTime.now().minusMinutes(15));
    }
    
    /**
     * Check if alert has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Get time elapsed since alert was created
     */
    public long getAgeInMinutes() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
    }
    
    /**
     * Get alert priority score for sorting
     */
    public int getPriorityScore() {
        int score = severity.getPriority() * 100;
        
        // Add urgency based on age
        long ageMinutes = getAgeInMinutes();
        if (ageMinutes > 60) score += 50;
        else if (ageMinutes > 30) score += 30;
        else if (ageMinutes > 15) score += 15;
        
        // Add priority for unacknowledged alerts
        if (acknowledgedAt == null) score += 25;
        
        // Add priority for escalated alerts
        if (requiresEscalation()) score += 100;
        
        return score;
    }
}

enum AlertSeverity {
    EMERGENCY(5, "Emergency"),
    CRITICAL(4, "Critical"),
    HIGH(3, "High"),
    MEDIUM(2, "Medium"),
    LOW(1, "Low"),
    INFO(0, "Info");
    
    private final int priority;
    private final String displayName;
    
    AlertSeverity(int priority, String displayName) {
        this.priority = priority;
        this.displayName = displayName;
    }
    
    public int getPriority() { return priority; }
    public String getDisplayName() { return displayName; }
}

enum AlertCategory {
    PERFORMANCE,
    SECURITY,
    BUSINESS,
    INFRASTRUCTURE,
    NETWORK,
    DATABASE,
    EXTERNAL_SERVICE,
    COMPLIANCE,
    CAPACITY,
    AVAILABILITY
}

enum AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED,
    SUPPRESSED,
    EXPIRED
}