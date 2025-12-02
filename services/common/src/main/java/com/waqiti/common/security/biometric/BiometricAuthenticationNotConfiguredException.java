package com.waqiti.common.security.biometric;

/**
 * Exception thrown when biometric authentication is not properly configured for a device or platform.
 *
 * SECURITY RATIONALE:
 * This exception is critical for preventing authentication bypass vulnerabilities. When biometric
 * liveness detection or other security features are not implemented for a specific biometric type
 * or platform, returning 'true' by default creates a security hole that could allow:
 *
 * 1. Presentation attacks (spoofing with photos, videos, synthetic samples)
 * 2. Replay attacks using captured biometric data
 * 3. Bypassing multi-factor authentication requirements
 * 4. Unauthorized access through unverified biometric submissions
 *
 * By throwing this exception instead, we enforce the principle of "fail-secure" - the system
 * explicitly rejects authentication attempts when proper security controls are not in place,
 * rather than implicitly allowing them.
 *
 * This follows security best practices:
 * - Fail-safe defaults: Default to denying access when security controls are incomplete
 * - Complete mediation: Every authentication attempt must pass through all configured checks
 * - Defense in depth: Multiple layers of validation rather than relying on single points
 *
 * @see BiometricAuthenticationService
 * @since 1.0.0
 */
public class BiometricAuthenticationNotConfiguredException extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message explaining what configuration is missing
     */
    public BiometricAuthenticationNotConfiguredException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message explaining what configuration is missing
     * @param cause the underlying cause of the configuration failure
     */
    public BiometricAuthenticationNotConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }
}
