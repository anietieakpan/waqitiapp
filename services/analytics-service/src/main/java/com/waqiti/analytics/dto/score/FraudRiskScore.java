package com.waqiti.analytics.dto.score;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRiskScore {
    private BigDecimal overallScore; // 0-100
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private List<String> riskFactors;
    private BigDecimal velocityScore;
    private BigDecimal patternScore;
    private BigDecimal locationScore;
    private BigDecimal deviceScore;
}