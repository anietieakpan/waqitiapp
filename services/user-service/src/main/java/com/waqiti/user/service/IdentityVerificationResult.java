package com.waqiti.user.service;

import java.math.BigDecimal;
import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class IdentityVerificationResult {
    private boolean verified;
    private BigDecimal verificationScore;
    private boolean identityMatch;
    private boolean dateOfBirthMatch;
    private boolean nameMatch;
    private boolean nationalIdMatch;
    private String provider;
    private ConfidenceLevel confidenceLevel;
    private boolean requiresManualReview;
    private List<String> additionalChecksRequired;
}
