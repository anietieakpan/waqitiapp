package com.waqiti.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Security alert event for user-related security incidents.
 * Consumed by user-service to take immediate protective actions on user accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityAlertEvent {
    
    // ======================== Event Metadata ========================
    @NotBlank(message = "Event ID is required")
    private String eventId;
    
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @NotBlank(message = "Source service is required")
    private String source;
    
    // ======================== User & Account ========================
    @NotBlank(message = "User ID is required")
    private String userId;
    
    private String accountId;
    
    private String email;
    
    private String phoneNumber;
    
    // ======================== Alert Details ========================
    @NotNull(message = "Alert type is required")
    private AlertType alertType;
    
    @NotNull(message = "Severity is required")
    private Severity severity;
    
    @NotNull(message = "Threat level is required")
    private ThreatLevel threatLevel;
    
    @NotBlank(message = "Alert title is required")
    @Size(max = 200)
    private String alertTitle;
    
    @NotBlank(message = "Alert description is required")
    @Size(max = 2000)
    private String alertDescription;
    
    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double confidenceScore;
    
    // ======================== Threat Context ========================
    private String threatActor;
    
    private String attackVector;
    
    private String attackPattern;
    
    private List<String> indicators;
    
    private Map<String, Object> threatIntelligence;
    
    // ======================== Session & Device ========================
    private String sessionId;
    
    private String deviceId;
    
    private String deviceFingerprint;
    
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
    private String sourceIpAddress;
    
    private String userAgent;
    
    private String geolocation;
    
    private Boolean knownDevice;
    
    private Boolean trustedLocation;
    
    // ======================== Authentication Context ========================
    private String authenticationMethod;
    
    private Integer failedAttempts;
    
    private LocalDateTime lastSuccessfulAuth;
    
    private LocalDateTime lastFailedAuth;
    
    private Boolean mfaEnabled;
    
    private Boolean mfaBypassed;
    
    // ======================== Affected Resources ========================
    private List<String> affectedResources;
    
    private List<String> compromisedCredentials;
    
    private List<String> suspiciousActivities;
    
    private Map<String, Object> forensicData;
    
    // ======================== Required Actions ========================
    @NotNull
    @Builder.Default
    private Boolean requiresImmediateAction = true;
    
    @NotNull
    private RequiredAction requiredAction;
    
    private List<String> recommendedActions;
    
    @Builder.Default
    private Boolean autoExecute = false;
    
    private Integer actionTimeoutMinutes;
    
    // ======================== Impact Assessment ========================
    private String potentialImpact;
    
    private String actualImpact;
    
    private Integer affectedUsers;
    
    private Double estimatedLoss;
    
    private Boolean dataExposed;
    
    private List<String> exposedDataTypes;
    
    // ======================== Response Tracking ========================
    private String incidentId;
    
    private Boolean incidentCreated;
    
    private String assignedTo;
    
    private LocalDateTime detectedAt;
    
    private LocalDateTime respondedAt;
    
    private Long responseTimeMs;
    
    private String responseStatus;
    
    // ======================== Notification Settings ========================
    @Builder.Default
    private Boolean notifyUser = true;
    
    @Builder.Default
    private Boolean notifySecurityTeam = true;
    
    private List<String> notificationChannels;
    
    private String notificationPriority;
    
    // ======================== Evidence & Forensics ========================
    private Map<String, String> evidenceLinks;
    
    private List<String> logReferences;
    
    private String screenshotUrl;
    
    private Map<String, Object> rawEventData;
    
    // ======================== Compliance & Regulatory ========================
    private Boolean gdprRelevant;
    
    private Boolean breachNotificationRequired;
    
    private String regulatoryJurisdiction;
    
    private LocalDateTime breachNotificationDeadline;
    
    // ======================== Metadata ========================
    private Map<String, Object> additionalData;
    
    private String environment;
    
    private String version;
    
    // ======================== Enums ========================
    
    public enum AlertType {
        // Authentication threats
        BRUTE_FORCE_ATTACK,
        CREDENTIAL_STUFFING,
        PASSWORD_SPRAY,
        ACCOUNT_TAKEOVER,
        SESSION_HIJACKING,
        
        // Access violations
        UNAUTHORIZED_ACCESS,
        PRIVILEGE_ESCALATION,
        SUSPICIOUS_LOGIN,
        IMPOSSIBLE_TRAVEL,
        UNUSUAL_ACTIVITY,
        
        // Data security
        DATA_EXFILTRATION,
        SENSITIVE_DATA_ACCESS,
        BULK_DATA_DOWNLOAD,
        API_ABUSE,
        
        // Account security
        COMPROMISED_CREDENTIALS,
        WEAK_PASSWORD_DETECTED,
        SHARED_CREDENTIALS,
        CREDENTIAL_LEAK,
        
        // Device & network
        MALWARE_DETECTED,
        SUSPICIOUS_DEVICE,
        TOR_ACCESS,
        VPN_DETECTED,
        PROXY_DETECTED,
        
        // Social engineering
        PHISHING_ATTEMPT,
        SOCIAL_ENGINEERING,
        MFA_BYPASS_ATTEMPT,
        
        // System security
        INJECTION_ATTEMPT,
        XSS_ATTEMPT,
        CSRF_ATTEMPT,
        XXE_ATTEMPT,
        
        // Compliance
        REGULATORY_VIOLATION,
        POLICY_VIOLATION,
        AUDIT_FAILURE,
        
        // Other
        CUSTOM_THREAT,
        UNKNOWN_THREAT
    }
    
    public enum Severity {
        CRITICAL(1),
        HIGH(2),
        MEDIUM(3),
        LOW(4),
        INFO(5);
        
        private final int priority;
        
        Severity(int priority) {
            this.priority = priority;
        }
        
        public int getPriority() {
            return priority;
        }
    }
    
    public enum ThreatLevel {
        IMMINENT("Immediate threat - action required now"),
        ACTIVE("Active threat detected"),
        POTENTIAL("Potential threat identified"),
        SUSPICIOUS("Suspicious activity detected"),
        MONITORING("Under monitoring");
        
        private final String description;
        
        ThreatLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum RequiredAction {
        // Account actions
        LOCK_ACCOUNT("Lock user account immediately"),
        SUSPEND_ACCOUNT("Temporarily suspend account"),
        FORCE_LOGOUT("Force logout all sessions"),
        INVALIDATE_SESSIONS("Invalidate all user sessions"),
        
        // Authentication actions
        FORCE_PASSWORD_RESET("Force password reset"),
        REQUIRE_MFA("Require multi-factor authentication"),
        STEP_UP_AUTH("Require step-up authentication"),
        REVOKE_TOKENS("Revoke all authentication tokens"),
        
        // Access control
        RESTRICT_ACCESS("Restrict account access"),
        DISABLE_API_ACCESS("Disable API access"),
        LIMIT_PERMISSIONS("Limit user permissions"),
        QUARANTINE_USER("Quarantine user activities"),
        
        // Monitoring
        ENABLE_MONITORING("Enable enhanced monitoring"),
        LOG_ALL_ACTIVITIES("Log all user activities"),
        ALERT_ON_ACCESS("Alert on any access attempt"),
        
        // Investigation
        COLLECT_EVIDENCE("Collect forensic evidence"),
        CREATE_INCIDENT("Create security incident"),
        MANUAL_REVIEW("Queue for manual review"),
        
        // Communication
        NOTIFY_USER("Notify user of threat"),
        WARN_USER("Send security warning"),
        REQUEST_VERIFICATION("Request identity verification"),
        
        // No action
        MONITOR_ONLY("Monitor without action"),
        LOG_ONLY("Log event only")
    }
    
    // ======================== Helper Methods ========================
    
    public boolean isCritical() {
        return severity == Severity.CRITICAL || 
               threatLevel == ThreatLevel.IMMINENT;
    }
    
    public boolean requiresAccountLock() {
        return requiredAction == RequiredAction.LOCK_ACCOUNT ||
               alertType == AlertType.ACCOUNT_TAKEOVER ||
               alertType == AlertType.COMPROMISED_CREDENTIALS;
    }
    
    public boolean requiresPasswordReset() {
        return requiredAction == RequiredAction.FORCE_PASSWORD_RESET ||
               alertType == AlertType.CREDENTIAL_LEAK ||
               alertType == AlertType.WEAK_PASSWORD_DETECTED;
    }
    
    public boolean isAuthenticationThreat() {
        return alertType == AlertType.BRUTE_FORCE_ATTACK ||
               alertType == AlertType.CREDENTIAL_STUFFING ||
               alertType == AlertType.PASSWORD_SPRAY ||
               alertType == AlertType.SESSION_HIJACKING;
    }
    
    public boolean requiresCompliance() {
        return Boolean.TRUE.equals(breachNotificationRequired) ||
               Boolean.TRUE.equals(gdprRelevant) ||
               alertType == AlertType.REGULATORY_VIOLATION;
    }
}