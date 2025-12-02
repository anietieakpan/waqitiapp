package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; /**
 * Overall risk analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAnalysis {
    private double overallRiskScore;
    private double velocityRisk;
    private double patternRisk;
    private double anomalyRisk;
    private double baseRisk;
    private TransactionAnalysisService.RiskLevel riskLevel;
    private double confidence;
    private List<String> riskFactors;
}
