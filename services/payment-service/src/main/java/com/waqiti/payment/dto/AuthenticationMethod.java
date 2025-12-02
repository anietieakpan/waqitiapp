package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for authentication methods
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthenticationMethod {
    
    private String methodId;
    private String userId;
    private String type; // PASSWORD, BIOMETRIC, SMS, EMAIL, TOTP, HARDWARE_TOKEN
    private String subType; // FINGERPRINT, FACE_ID, VOICE, etc.
    
    private boolean enabled;
    private boolean primary;
    private boolean verified;
    
    // Method-specific data
    private String identifier; // phone number, email, etc.
    private String displayName;
    private String deviceId;
    private String publicKey;
    
    // Security properties
    private String securityLevel; // LOW, MEDIUM, HIGH
    private boolean requiresDevice;
    private boolean allowsBackup;
    
    // Usage tracking
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant lastVerifiedAt;
    private Integer usageCount;
    
    // Status information
    private String status; // ACTIVE, DISABLED, EXPIRED, REVOKED
    private Instant expiresAt;
    private String statusReason;
    
    private Map<String, Object> metadata;
}