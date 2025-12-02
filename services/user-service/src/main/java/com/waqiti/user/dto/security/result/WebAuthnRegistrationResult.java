package com.waqiti.user.dto.security.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of WebAuthn/FIDO2 registration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnRegistrationResult {
    private boolean success;
    private String credentialId;
    private String errorCode;
    private String errorMessage;
}