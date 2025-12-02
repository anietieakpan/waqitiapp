package com.waqiti.user.service;

import java.math.BigDecimal;
import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DocumentAuthenticityResult {
    private boolean authentic;
    private BigDecimal authenticityScore;
    private int securityFeaturesFound;
    private List<String> fraudIndicators;
    private String provider;
    private boolean requiresManualReview;
    private boolean tamperingDetected;
}
