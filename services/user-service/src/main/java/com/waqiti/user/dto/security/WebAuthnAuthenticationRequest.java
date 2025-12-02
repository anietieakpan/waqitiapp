package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * WebAuthn/FIDO2 authentication request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnAuthenticationRequest {
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Credential ID is required")
    private String credentialId;
    
    @NotBlank(message = "Authenticator data is required")
    private String authenticatorData;
    
    @NotBlank(message = "Client data JSON is required")
    private String clientDataJSON;
    
    @NotBlank(message = "Signature is required")
    private String signature;
}