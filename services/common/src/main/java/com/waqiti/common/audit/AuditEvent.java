package com.waqiti.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Use {@link com.waqiti.common.events.model.AuditEvent} instead.
 * This class is kept for backward compatibility only and will be removed in future versions.
 * 
 * Legacy audit event model - DO NOT USE FOR NEW CODE
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    
    // Unique identifier for this event
    private String eventId;
    
    // Event timestamp
    private Instant timestamp;
    
    // Event classification
    private SecureAuditLogger.EventType eventType;
    
    // Specific action performed
    private String action;
    
    // Entity type being acted upon
    private String entityType;
    
    // Entity identifier (sanitized)
    private String entityId;
    
    // User who performed the action
    private String userId;
    
    // User's roles at time of action
    private List<String> userRoles;
    
    // Whether the action was authorized
    private Boolean authorized;
    
    // Client IP address
    private String ipAddress;
    
    // User agent string (sanitized)
    private String userAgent;
    
    // HTTP method if applicable
    private String requestMethod;
    
    // Request URI (sanitized)
    private String requestUri;
    
    // Session identifier (hashed)
    private String sessionId;
    
    // Service that generated the event
    private String serviceName;
    
    // Service version
    private String serviceVersion;
    
    // Risk score (0-10)
    private Integer riskScore;
    
    // Additional details (sanitized)
    private String details;
    
    // Compliance rule if applicable
    private String complianceRule;
    
    // Additional metadata (sanitized)
    private Map<String, Object> metadata;
    
    // Geographic location if available
    private String location;
    
    // Device fingerprint if available
    private String deviceFingerprint;
    
    /**
     * Risk levels for audit events
     */
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Get risk level based on risk score
     */
    public RiskLevel getRiskLevel() {
        if (riskScore == null) return RiskLevel.LOW;
        if (riskScore >= 8) return RiskLevel.CRITICAL;
        if (riskScore >= 6) return RiskLevel.HIGH;
        if (riskScore >= 4) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
    
    /**
     * Get resource type from entity type
     */
    public String getResourceType() {
        return entityType;
    }
    
    /**
     * Check if operation was successful
     */
    public boolean isSuccess() {
        return authorized != null ? authorized : true;
    }
    
    /**
     * Get event category
     */
    public String getEventCategory() {
        return eventType != null ? eventType.name() : "UNKNOWN";
    }
}