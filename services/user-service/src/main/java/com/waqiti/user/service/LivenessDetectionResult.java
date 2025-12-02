package com.waqiti.user.service;

import java.math.BigDecimal;
import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class LivenessDetectionResult {
    private boolean live;
    private BigDecimal livenessScore;
    private boolean spoofDetected;
    private BigDecimal quality;
    private ConfidenceLevel confidenceLevel;
    private String provider;
    private int challengesPassed;
    private List<String> spoofIndicators;
}
