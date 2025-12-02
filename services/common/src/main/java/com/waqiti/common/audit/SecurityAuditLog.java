package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Security audit log for tracking security-related events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditLog {
    
    private UUID id;
    private String userId;
    private String eventType;
    private SecurityEventCategory category;
    private SecuritySeverity severity;
    private String description;
    private LocalDateTime timestamp;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String resource;
    private String resourceId;
    private String action;
    private SecurityOutcome outcome;
    private String threatLevel;
    private Map<String, Object> metadata;
    private String correlationId;
    private String sourceService;
    private String detectionMethod;
    private String mitigationAction;
    private Boolean requiresAlert;
    private boolean success;
    private String failureReason;
    private Map<String, Object> details; // Additional event details (alias for metadata)
    
    /**
     * Security event categories
     */
    public enum SecurityEventCategory {
        AUTHENTICATION,
        AUTHORIZATION,
        DATA_ACCESS,
        DATA_MODIFICATION,
        SUSPICIOUS_ACTIVITY,
        POLICY_VIOLATION,
        SYSTEM_COMPROMISE,
        FRAUD_ATTEMPT,
        FRAUD_DETECTION,
        COMPLIANCE_VIOLATION
    }
    
    /**
     * Security severity levels
     */
    public enum SecuritySeverity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Security event outcomes
     */
    public enum SecurityOutcome {
        SUCCESS,
        FAILURE,
        BLOCKED,
        ALLOWED,
        FLAGGED
    }
    
    /**
     * Create a security audit log
     */
    public static SecurityAuditLog create(String userId, SecurityEventCategory category,
                                        String eventType, String description) {
        return SecurityAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .category(category)
                .eventType(eventType)
                .description(description)
                .timestamp(LocalDateTime.now())
                .severity(SecuritySeverity.MEDIUM)
                .outcome(SecurityOutcome.SUCCESS)
                .requiresAlert(false)
                .build();
    }
    
    /**
     * Create a security audit log for authentication events
     */
    public static SecurityAuditLog createAuthEvent(String userId, String action, 
                                                  SecurityOutcome outcome, String ipAddress) {
        return SecurityAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .category(SecurityEventCategory.AUTHENTICATION)
                .eventType("AUTH_" + action.toUpperCase())
                .action(action)
                .outcome(outcome)
                .description("Authentication " + action + ": " + outcome)
                .ipAddress(ipAddress)
                .timestamp(LocalDateTime.now())
                .severity(outcome == SecurityOutcome.FAILURE ? SecuritySeverity.HIGH : SecuritySeverity.LOW)
                .requiresAlert(outcome == SecurityOutcome.FAILURE)
                .build();
    }
    
    /**
     * Create a security audit log for suspicious activity
     */
    public static SecurityAuditLog createSuspiciousActivity(String userId, String description, 
                                                           String threatLevel, String ipAddress) {
        return SecurityAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .category(SecurityEventCategory.SUSPICIOUS_ACTIVITY)
                .eventType("SUSPICIOUS_ACTIVITY")
                .description(description)
                .threatLevel(threatLevel)
                .ipAddress(ipAddress)
                .timestamp(LocalDateTime.now())
                .severity(SecuritySeverity.HIGH)
                .outcome(SecurityOutcome.FLAGGED)
                .requiresAlert(true)
                .build();
    }
    
    /**
     * Add session information
     */
    public SecurityAuditLog withSession(String sessionId, String ipAddress, String userAgent) {
        this.sessionId = sessionId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        return this;
    }
    
    /**
     * Add resource and action
     */
    public SecurityAuditLog withResourceAction(String resource, String action) {
        this.resource = resource;
        this.action = action;
        return this;
    }
    
    /**
     * Add detection and mitigation details
     */
    public SecurityAuditLog withDetectionMitigation(String detectionMethod, String mitigationAction) {
        this.detectionMethod = detectionMethod;
        this.mitigationAction = mitigationAction;
        return this;
    }
    
    /**
     * Add metadata
     */
    public SecurityAuditLog withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    /**
     * Set as requiring immediate alert
     */
    public SecurityAuditLog requiresAlert() {
        this.requiresAlert = true;
        return this;
    }
    
    /**
     * Get severity enum for compatibility
     */
    public SecuritySeverity getSeverity() {
        return severity != null ? severity : SecuritySeverity.MEDIUM;
    }
    
    /**
     * Get severity as string
     */
    public String getSeverityString() {
        return severity != null ? severity.name() : "MEDIUM";
    }
    
    /**
     * Get additional details about the security event
     */
    public Map<String, Object> getDetails() {
        return metadata;
    }
    
    /**
     * Get event category (alias for category field)
     */
    public SecurityEventCategory getEventCategory() {
        return category;
    }
}