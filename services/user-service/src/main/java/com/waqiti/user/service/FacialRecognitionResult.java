package com.waqiti.user.service;

import java.math.BigDecimal;
import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class FacialRecognitionResult {
    private boolean matched;
    private BigDecimal matchScore;
    private BigDecimal livenessScore;
    private BigDecimal qualityScore;
    private boolean spoofDetected;
    private ConfidenceLevel confidenceLevel;
    private String provider;
    private boolean requiresRecapture;
    private List<String> fraudIndicators;
}
