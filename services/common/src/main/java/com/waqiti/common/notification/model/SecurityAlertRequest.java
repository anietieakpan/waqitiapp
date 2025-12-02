package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Security alert notification request
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SecurityAlertRequest extends NotificationRequest {
    
    /**
     * Type of security alert
     */
    private SecurityAlertType alertType;
    
    /**
     * Alert title
     */
    private String title;
    
    /**
     * Detailed message
     */
    private String message;
    
    /**
     * User affected
     */
    private String affectedUserId;
    
    /**
     * IP address involved
     */
    private String ipAddress;
    
    /**
     * Geographic location
     */
    private Map<String, Object> geoLocation;
    
    /**
     * Device information
     */
    private Map<String, Object> deviceInfo;
    
    /**
     * Risk score (0-100)
     */
    private Integer riskScore;
    
    /**
     * Threat indicators
     */
    private List<Map<String, Object>> threatIndicators;
    
    /**
     * Actions taken automatically
     */
    private List<String> actionsTaken;
    
    /**
     * Recommended actions for user
     */
    private List<String> recommendedActions;
    
    /**
     * Time of the security event
     */
    private Instant eventTime;
    
    /**
     * Session information
     */
    private Map<String, Object> sessionInfo;
    
    /**
     * Additional context
     */
    private Map<String, Object> context;
    
    /**
     * Whether immediate action is required
     */
    private boolean immediateActionRequired;
    
    /**
     * Link to security center
     */
    private String securityCenterUrl;
    
    
    public enum SecurityAlertType {
        SUSPICIOUS_LOGIN,
        FAILED_LOGIN_ATTEMPTS,
        NEW_DEVICE_LOGIN,
        UNUSUAL_LOCATION_LOGIN,
        PASSWORD_CHANGE,
        MFA_DISABLED,
        ACCOUNT_LOCKOUT,
        PRIVILEGE_ESCALATION,
        DATA_EXPORT,
        API_KEY_CREATED,
        SUSPICIOUS_TRANSACTION,
        ACCOUNT_TAKEOVER_ATTEMPT,
        BRUTE_FORCE_ATTACK,
        CREDENTIAL_STUFFING,
        SESSION_HIJACKING,
        MALWARE_DETECTED,
        PHISHING_ATTEMPT,
        UNAUTHORIZED_ACCESS,
        POLICY_VIOLATION,
        COMPLIANCE_BREACH
    }
}