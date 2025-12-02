package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Risk Assessment DTO
 *
 * Overall financial and fraud risk assessment.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {

    @Min(0) @Max(100)
    private Integer overallRiskScore; // 0-100, higher = higher risk

    private String riskCategory; // LOW, MEDIUM, HIGH, CRITICAL

    @Min(0) @Max(100)
    private Integer fraudRisk;

    @Min(0) @Max(100)
    private Integer creditRisk;

    @Min(0) @Max(100)
    private Integer liquidityRisk;

    private List<String> riskFactors;
    private List<String> mitigationStrategies;
    private Boolean requiresManualReview;
}
