package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of MFA verification attempt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaVerificationResult {

    /**
     * Whether the MFA verification was successful
     */
    private boolean valid;

    /**
     * Reason for verification failure (if applicable)
     */
    private String failureReason;

    /**
     * Whether the device is trusted
     */
    private boolean deviceTrusted;

    /**
     * Device identifier
     */
    private String deviceId;

    /**
     * Timestamp of verification
     */
    private java.time.Instant verifiedAt;

    /**
     * Additional metadata about the verification
     */
    private java.util.Map<String, Object> metadata;

    /**
     * Create a successful verification result
     */
    public static MfaVerificationResult success(String deviceId, boolean deviceTrusted) {
        return MfaVerificationResult.builder()
            .valid(true)
            .deviceId(deviceId)
            .deviceTrusted(deviceTrusted)
            .verifiedAt(java.time.Instant.now())
            .build();
    }

    /**
     * Create a failed verification result
     */
    public static MfaVerificationResult failure(String reason) {
        return MfaVerificationResult.builder()
            .valid(false)
            .failureReason(reason)
            .deviceTrusted(false)
            .verifiedAt(java.time.Instant.now())
            .build();
    }
}
