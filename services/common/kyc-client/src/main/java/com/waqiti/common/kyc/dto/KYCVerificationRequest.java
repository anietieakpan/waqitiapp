package com.waqiti.common.kyc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCVerificationRequest {

    @NotNull(message = "Verification level is required")
    private VerificationLevel verificationLevel;

    private String preferredProvider;

    private Map<String, String> additionalInfo;

    private String returnUrl;

    private String webhookUrl;

    @Builder.Default
    private boolean consentGiven = false;

    private String consentText;

    private String consentTimestamp;

    private String ipAddress;

    private String userAgent;

    private Map<String, Object> metadata;

    public enum VerificationLevel {
        BASIC,
        INTERMEDIATE,
        ADVANCED
    }
}