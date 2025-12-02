package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map; /**
 * Anomaly analysis results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyAnalysis {
    private double riskScore;
    private double confidence;
    private boolean hasSignificantAnomalies;
    private List<String> anomalies;
    private Map<String, Object> anomalyMetrics;

    public boolean hasSignificantAnomalies() {
        return hasSignificantAnomalies;
    }
}
