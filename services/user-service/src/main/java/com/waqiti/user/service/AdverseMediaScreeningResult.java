package com.waqiti.user.service;

import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class AdverseMediaScreeningResult {
    private boolean adverseMediaFound;
    private double riskScore;
    private int articlesFound;
    private int highRiskArticles;
    private List<String> categories;
    private String provider;
    private boolean requiresManualReview;
    private String complianceAction;
}
