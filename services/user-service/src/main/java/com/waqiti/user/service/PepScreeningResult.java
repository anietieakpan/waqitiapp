package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PepScreeningResult {
    private boolean pepMatch;
    private double riskScore;
    private int matchesFound;
    private int highRiskMatches;
    private String provider;
    private boolean requiresManualReview;
    private String complianceAction;
}
