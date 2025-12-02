package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Risk assessment model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private BigDecimal riskScore;
    private List<String> riskFactors;
    private List<String> mitigationStrategies;
}