package com.waqiti.kyc.dto.request;

import com.waqiti.kyc.domain.KYCVerification.VerificationLevel;
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

    private String preferredProvider; // ONFIDO, JUMIO, etc.

    private Map<String, String> additionalInfo;

    private String returnUrl; // For redirect after verification

    private String webhookUrl; // For status updates

    @Builder.Default
    private boolean consentGiven = false;

    private String consentText;

    private String consentTimestamp;

    private String ipAddress;

    private String userAgent;

    private Map<String, Object> metadata;
}