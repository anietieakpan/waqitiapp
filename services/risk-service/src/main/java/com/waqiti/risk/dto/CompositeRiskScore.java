package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Composite risk score aggregating multiple risk factors
 * Final risk assessment for a transaction or entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompositeRiskScore {

    private String assessmentId;
    private String transactionId;
    private String userId;

    // Overall risk
    private Double overallScore; // 0.0 (no risk) to 1.0 (high risk)
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String riskCategory; // FRAUD, AML, COMPLIANCE, OPERATIONAL

    // Component scores
    private List<RiskFactorScore> factorScores;
    private Double ruleBasedScore;
    private Double mlBasedScore;
    private Double statisticalScore;

    // Decision
    private String decision; // APPROVE, REVIEW, DECLINE, BLOCK
    private String action; // ALLOW, MANUAL_REVIEW, AUTO_BLOCK, ESCALATE

    // Confidence
    private Double confidence; // 0.0 to 1.0
    private String confidenceLevel; // LOW, MEDIUM, HIGH

    // Reasoning
    private String primaryReason; // Main reason for risk score
    private List<String> contributingFactors; // All factors contributing to decision
    private Map<String, String> evidenceMap; // factor -> evidence

    // Thresholds
    private Double approvalThreshold;
    private Double reviewThreshold;
    private Double blockThreshold;
    private Boolean thresholdBreached;

    // Timing
    private Instant assessedAt;
    private Long processingTimeMs;

    // Model information
    private String modelVersion;
    private List<String> modelsUsed; // Which ML models contributed
    private List<String> rulesTriggered; // Which rules fired

    // Recommendations
    private List<String> recommendations; // Actions to mitigate risk
    private Boolean requiresManualReview;
    private String reviewQueue; // HIGH_PRIORITY, STANDARD, LOW_PRIORITY

    // Metadata
    private Map<String, Object> metadata;
    private String notes;
}
