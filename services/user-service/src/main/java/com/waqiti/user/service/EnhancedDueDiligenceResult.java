package com.waqiti.user.service;

import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class EnhancedDueDiligenceResult {
    private boolean compliant;
    private double complianceScore;
    private String riskLevel;
    private List<String> flaggedAreas;
    private List<String> recommendedActions;
    private String provider;
    private boolean requiresOngoingMonitoring;
    private java.time.Duration reviewPeriod;
}
