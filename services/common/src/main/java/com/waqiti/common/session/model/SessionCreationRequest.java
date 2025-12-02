package com.waqiti.common.session.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;

/**
 * Request for creating a new user session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCreationRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Username is required")
    private String username;
    
    private String deviceId;
    private String deviceType;
    
    @NotBlank(message = "IP address is required")
    private String ipAddress;
    
    private String userAgent;
    private Set<String> roles;
    private Set<String> permissions;
    private Map<String, Object> attributes;
    
    @NotNull(message = "MFA verified status is required")
    @Builder.Default
    private boolean mfaVerified = false;
    
    private String mfaMethod;
    private GeoLocation location;
    
    @Builder.Default
    private int sessionTimeoutMinutes = 30;
    
    @Builder.Default
    private boolean rememberMe = false;
    
    private String refreshToken;
    private String accessToken;
    
    /**
     * Client application ID
     */
    private String clientId;
    
    /**
     * Session creation reason
     */
    private String creationReason;
    
    /**
     * Parent session ID (for linked sessions)
     */
    private String parentSessionId;
}