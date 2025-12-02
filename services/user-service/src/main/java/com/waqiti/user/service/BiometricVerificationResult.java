package com.waqiti.user.service;

import java.math.BigDecimal;
import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class BiometricVerificationResult {
    private boolean matched;
    private BigDecimal matchScore;
    private BigDecimal livenessScore;
    private BigDecimal qualityScore;
    private boolean spoofDetected;
    private String provider;
    private boolean requiresRecapture;
    private ConfidenceLevel confidenceLevel;
    private List<String> fraudIndicators;
}
