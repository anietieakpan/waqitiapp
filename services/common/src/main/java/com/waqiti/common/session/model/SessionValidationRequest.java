package com.waqiti.common.session.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request for validating an existing session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionValidationRequest {
    
    @NotBlank(message = "Session ID is required")
    private String sessionId;
    
    private String accessToken;
    private String refreshToken;
    private String ipAddress;
    private String userAgent;
    private String deviceId;
    private Map<String, Object> context;
    
    @Builder.Default
    private boolean checkLocation = true;
    
    @Builder.Default
    private boolean checkDevice = true;
    
    @Builder.Default
    private boolean extendSession = false;
    
    private String validationReason;
    
    /**
     * Required permissions for this validation
     */
    private String[] requiredPermissions;
    
    /**
     * Required roles for this validation
     */
    private String[] requiredRoles;
    
    /**
     * Maximum allowed idle time in seconds
     */
    @Builder.Default
    private long maxIdleSeconds = 1800; // 30 minutes
    
    /**
     * Create basic validation request
     */
    public static SessionValidationRequest basic(String sessionId) {
        return SessionValidationRequest.builder()
            .sessionId(sessionId)
            .build();
    }
    
    /**
     * Create strict validation request
     */
    public static SessionValidationRequest strict(String sessionId, String ipAddress, String deviceId) {
        return SessionValidationRequest.builder()
            .sessionId(sessionId)
            .ipAddress(ipAddress)
            .deviceId(deviceId)
            .checkLocation(true)
            .checkDevice(true)
            .build();
    }
}