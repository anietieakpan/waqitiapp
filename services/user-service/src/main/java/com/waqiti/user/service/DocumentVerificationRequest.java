package com.waqiti.user.service;

import com.waqiti.user.validation.SafeString;
import jakarta.validation.constraints.NotBlank;

/**
 * Document Verification Request
 *
 * SECURITY:
 * - All document metadata validated
 * - URLs validated for injection attacks
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DocumentVerificationRequest {
    @NotBlank
    @SafeString(maxLength = 100)
    private String userId;

    @NotBlank
    @SafeString(maxLength = 50)
    private String documentType;

    @NotBlank
    @SafeString(maxLength = 100)
    private String documentNumber;

    @NotBlank
    @SafeString(maxLength = 2048)
    private String documentImageUrl;

    @SafeString(maxLength = 2048)
    private String backImageUrl;

    @SafeString(maxLength = 50)
    private String verificationLevel;

    private boolean extractText;
    private boolean checkAuthenticity;
    private java.time.Instant requestTimestamp;
}
