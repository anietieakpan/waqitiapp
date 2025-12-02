package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map; /**
 * Velocity analysis results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityAnalysis {
    private double riskScore;
    private double confidence;
    private boolean isHighVelocity;
    private List<String> riskFactors;
    private Map<String, Object> velocityMetrics;
}
