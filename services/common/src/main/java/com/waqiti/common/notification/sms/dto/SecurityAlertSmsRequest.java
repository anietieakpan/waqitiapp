package com.waqiti.common.notification.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for sending security alert SMS messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAlertSmsRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Alert type is required")
    private String alertType;
    
    @NotBlank(message = "Alert description is required")
    private String alertDescription;
    
    /**
     * Severity level of the security alert
     */
    @Builder.Default
    private SecuritySeverity severity = SecuritySeverity.MEDIUM;
    
    @Builder.Default
    private SmsPriority priority = SmsPriority.HIGH;
    
    @Builder.Default
    private SmsType type = SmsType.SECURITY_ALERT;
    
    /**
     * Source system that generated the alert
     */
    private String sourceSystem;
    private String securityEvent;
    
    /**
     * Detected location or IP address
     */
    private String detectedLocation;
    
    /**
     * Device information
     */
    private String deviceInfo;
    
    /**
     * Timestamp when the security event occurred
     */
    private LocalDateTime eventTime;
    
    /**
     * Additional context for the alert
     */
    private Map<String, Object> context;
    
    /**
     * Reference ID for tracking
     */
    private String referenceId;
    
    /**
     * Whether immediate action is required
     */
    @Builder.Default
    private boolean requiresImmediateAction = true;
    
    /**
     * Recommended action for the user
     */
    private String recommendedAction;
    
    /**
     * Language code for message localization
     */
    @Builder.Default
    private String languageCode = "en";
    
    /**
     * Custom message override
     */
    private String customMessage;
    
    @Builder.Default
    private LocalDateTime requestTime = LocalDateTime.now();
    
    /**
     * Get security event
     */
    public String getSecurityEvent() {
        return securityEvent != null ? securityEvent : alertType;
    }
    
    /**
     * Get IP address from detected location
     */
    public String getIpAddress() {
        return detectedLocation;
    }
    
    /**
     * Security alert severity levels
     */
    public enum SecuritySeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}