package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map; /**
 * Pattern analysis results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternAnalysis {
    private double riskScore;
    private double confidence;
    private boolean hasUnusualPatterns;
    private List<String> riskFactors;
    private Map<String, Object> patternMetrics;

    public boolean hasUnusualPatterns() {
        return hasUnusualPatterns;
    }
}
