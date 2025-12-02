package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentResult {
    private boolean acceptableRisk;
    private double riskScore;
    private String riskLevel;
    private List<String> riskFactors;
    private List<String> mitigationActions;
    private String provider;
    private boolean requiresEnhancedDueDiligence;
    private String monitoringLevel;
}
