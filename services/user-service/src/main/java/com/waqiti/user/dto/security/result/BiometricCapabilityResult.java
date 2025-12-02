package com.waqiti.user.dto.security.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of biometric capability check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricCapabilityResult {
    private boolean fingerprintSupported;
    private boolean faceIdSupported;
    private boolean webAuthnSupported;
    private boolean behavioralSupported;
    private boolean platformAuthenticatorAvailable;
    private boolean userVerifyingPlatformAuthenticatorAvailable;
}