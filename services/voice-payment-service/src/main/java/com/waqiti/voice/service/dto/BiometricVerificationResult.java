package com.waqiti.voice.service.dto;

import lombok.*;

/**
 * Biometric Verification Result DTO
 *
 * Contains results of voice biometric verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricVerificationResult {

    /**
     * Whether biometric matched
     */
    private Boolean matched;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidence;

    /**
     * Threshold used for matching
     */
    private Double threshold;

    /**
     * Reason for failure (if not matched)
     */
    private String reason;

    /**
     * Whether liveness check passed
     */
    private Boolean livenessCheckPassed;

    /**
     * Whether liveness check failed
     */
    @Builder.Default
    private Boolean livenessCheckFailed = false;

    /**
     * Whether anti-spoofing check passed
     */
    private Boolean antiSpoofingPassed;

    /**
     * Whether spoofing was detected
     */
    @Builder.Default
    private Boolean spoofingDetected = false;

    /**
     * Whether user requires enrollment
     */
    @Builder.Default
    private Boolean requiresEnrollment = false;

    /**
     * Whether account is locked
     */
    @Builder.Default
    private Boolean accountLocked = false;

    /**
     * Additional metadata
     */
    private java.util.Map<String, Object> metadata;
}
