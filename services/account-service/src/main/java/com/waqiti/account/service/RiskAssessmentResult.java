package com.waqiti.account.service;

import java.util.Date;
import java.util.List;

@lombok.Data
@lombok.Builder
public class RiskAssessmentResult {
    private final int riskScore;
    private final String riskLevel;
    private final List<String> riskFactors;
    private final List<String> recommendations;
    private final boolean requiresManualReview;
    private final Date assessedAt;
    
    public int getRiskScore() {
        return riskScore;
    }
}
