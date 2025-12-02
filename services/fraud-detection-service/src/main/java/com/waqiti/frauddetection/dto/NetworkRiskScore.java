package com.waqiti.frauddetection.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Network-based risk scoring result from graph analysis
 */
@Data
@Builder
public class NetworkRiskScore {
    private String userId;
    private double networkRiskScore;
    private double fraudRingProbability;
    private double centralityScore;
    private double suspiciousPatternScore;
    private double launderingRiskScore;
    private LocalDateTime analysisTimestamp;
    
    public static NetworkRiskScore defaultScore() {
        return NetworkRiskScore.builder()
            .networkRiskScore(0.0)
            .fraudRingProbability(0.0)
            .centralityScore(0.0)
            .suspiciousPatternScore(0.0)
            .launderingRiskScore(0.0)
            .analysisTimestamp(LocalDateTime.now())
            .build();
    }
    
    public boolean isHighRisk() {
        return networkRiskScore > 0.7 || fraudRingProbability > 0.8 || launderingRiskScore > 0.7;
    }
    
    public boolean requiresManualReview() {
        return networkRiskScore > 0.5 || fraudRingProbability > 0.6;
    }
}