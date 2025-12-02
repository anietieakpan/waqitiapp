package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of behavioral analysis for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralAnalysisResult {
    
    private boolean anomalyDetected;
    private boolean anomalous;
    private double anomalyScore;
    private double riskScore;
    private List<String> anomalies;
    private String recommendation;
    private String description;
    
    /**
     * Create an anomaly result
     */
    public static BehavioralAnalysisResult anomalyDetected(double score, List<String> anomalies) {
        return BehavioralAnalysisResult.builder()
            .anomalyDetected(true)
            .anomalyScore(score)
            .anomalies(anomalies)
            .recommendation("Review transaction for potential fraud")
            .build();
    }
    
    /**
     * Create a normal result
     */
    public static BehavioralAnalysisResult normal() {
        return BehavioralAnalysisResult.builder()
            .anomalyDetected(false)
            .anomalyScore(0.0)
            .anomalies(List.of())
            .recommendation("Normal behavior pattern")
            .build();
    }
    
    /**
     * Check if anomaly is detected
     */
    public boolean isAnomalous() {
        return anomalyDetected;
    }
    
    /**
     * Get risk score (alias for anomaly score)
     */
    public double getRiskScore() {
        return riskScore > 0 ? riskScore : anomalyScore;
    }
    
    /**
     * Get description of the behavioral analysis
     */
    public String getDescription() {
        if (anomalies != null && !anomalies.isEmpty()) {
            return String.join(", ", anomalies);
        }
        return recommendation != null ? recommendation : 
               (anomalyDetected ? "Behavioral anomaly detected" : "Normal behavioral pattern");
    }
}