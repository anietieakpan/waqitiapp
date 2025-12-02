package com.waqiti.risk.dto;

import com.waqiti.risk.domain.RiskFactor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Individual risk factor score
 * Represents the contribution of a single risk factor to overall risk
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorScore {

    private RiskFactor factor;
    private Double score; // 0.0 (no risk) to 1.0 (high risk)
    private Double weight; // Weight of this factor in overall score
    private Double weightedScore; // score * weight

    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String reason; // Explanation for this score

    private Instant evaluatedAt;
    private String evaluationMethod; // RULE_BASED, ML_BASED, STATISTICAL, HEURISTIC

    // Contributing signals
    private Map<String, Object> signals; // Specific data points that influenced this score

    // Confidence
    private Double confidence; // 0.0 to 1.0 - how confident we are in this score

    // Threshold information
    private Double threshold; // Threshold for this factor
    private Boolean thresholdExceeded;

    // Recommendations
    private String action; // ALLOW, REVIEW, BLOCK
    private String recommendation;
}
