package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Fraud Risk Assessment DTO
 *
 * Comprehensive fraud risk scoring and indicators.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRiskAssessment {

    @Min(0) @Max(100)
    private Integer overallRiskScore; // 0-100, higher = higher risk

    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    private List<String> riskFactors;
    private List<String> anomaliesDetected;
    private Integer suspiciousTransactionCount;
    private Boolean requiresReview;
    private String recommendation; // ALLOW, REVIEW, BLOCK
}
