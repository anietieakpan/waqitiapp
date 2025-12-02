package com.waqiti.account.service;

import java.util.List;

@lombok.Data
public class RiskAssessmentResponse {
    private int riskScore;
    private List<String> riskFactors;
    private List<String> recommendations;
    private boolean requiresManualReview;
}
