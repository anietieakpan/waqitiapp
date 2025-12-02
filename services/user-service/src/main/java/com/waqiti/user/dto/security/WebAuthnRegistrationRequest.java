package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * WebAuthn/FIDO2 registration request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnRegistrationRequest {
    private String userId;
    
    @NotBlank(message = "Attestation object is required")
    private String attestationObject;
    
    @NotBlank(message = "Client data JSON is required")
    private String clientDataJSON;
    
    @NotBlank(message = "Device fingerprint is required") 
    private String deviceFingerprint;
    
    private String authenticatorType;
    private String userAgent;
    private String ipAddress;
}