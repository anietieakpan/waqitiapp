package com.waqiti.user.service;

import com.waqiti.user.validation.SafeString;
import jakarta.validation.constraints.NotBlank;

/**
 * Identity Verification Request
 *
 * SECURITY:
 * - All PII fields validated against injection attacks
 * - KYC/AML compliance data validation
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class IdentityVerificationRequest {
    @NotBlank
    @SafeString(maxLength = 100)
    private String userId;

    @NotBlank
    @SafeString(maxLength = 100)
    private String firstName;

    @NotBlank
    @SafeString(maxLength = 100)
    private String lastName;

    @NotBlank
    @SafeString(maxLength = 20)
    private String dateOfBirth;

    @NotBlank
    @SafeString(maxLength = 100)
    private String nationalId;

    @NotBlank
    @SafeString(maxLength = 10)
    private String countryCode;

    @SafeString(maxLength = 50)
    private String verificationLevel;

    @SafeString(maxLength = 45)
    private String ipAddress;

    @SafeString(maxLength = 500)
    private String userAgent;

    @SafeString(maxLength = 100)
    private String sessionId;

    private java.time.Instant requestTimestamp;
}
