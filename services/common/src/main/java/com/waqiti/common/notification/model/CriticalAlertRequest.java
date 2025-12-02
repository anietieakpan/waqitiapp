package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Request for critical alerts
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CriticalAlertRequest extends NotificationRequest {
    
    /**
     * Alert severity
     */
    private AlertSeverity severity;
    
    /**
     * Alert title
     */
    private String title;
    
    /**
     * Alert description
     */
    private String description;
    
    /**
     * Alert message
     */
    private String message;
    
    /**
     * Affected system/component
     */
    private String affectedSystem;
    
    /**
     * Alert category
     */
    private String category;
    
    /**
     * Escalation policy
     */
    private Map<String, Object> escalationPolicy;
    
    /**
     * Recipients for the alert
     */
    private List<String> recipients;
    
    /**
     * Alert metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Incident ID if linked to incident
     */
    private String incidentId;
    
    /**
     * Actions available
     */
    private List<Map<String, Object>> actions;
    
    /**
     * Auto-resolve settings
     */
    private Map<String, Object> autoResolveSettings;

    /**
     * Alert source/origin
     */
    private String source;


    public enum AlertSeverity {
        LOW,
        WARNING,
        MEDIUM,
        HIGH,
        CRITICAL,
        EMERGENCY
    }
    
    public enum ActionType {
        ACKNOWLEDGE,
        RESOLVE,
        ESCALATE,
        SNOOZE,
        CUSTOM
    }
}