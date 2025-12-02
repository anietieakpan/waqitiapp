package com.waqiti.user.dto.security.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of WebAuthn/FIDO2 authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnAuthenticationResult {
    private boolean success;
    private String authToken;
    private String credentialId;
    private String errorCode;
    private String errorMessage;
}