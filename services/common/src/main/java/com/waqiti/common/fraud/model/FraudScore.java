package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comprehensive fraud scoring model that aggregates multiple risk factors
 * into a single actionable score with detailed component breakdowns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScore {
    
    /**
     * Overall aggregated fraud score (0.0 to 1.0)
     * 0.0 = No fraud risk, 1.0 = Maximum fraud risk
     */
    private double overallScore;

    /**
     * Score value (alias for overallScore for backward compatibility)
     */
    private double score;

    /**
     * Risk level classification based on the score
     */
    private FraudRiskLevel riskLevel;

    /**
     * Detailed breakdown of fraud score components
     */
    private Object breakdown;

    /**
     * Confidence in the fraud assessment (0.0 to 1.0) - alias for confidenceLevel
     */
    private double confidence;

    /**
     * IP address fraud risk component (0.0 to 1.0)
     */
    private double ipScore;
    
    /**
     * Email fraud risk component (0.0 to 1.0)
     */
    private double emailScore;
    
    /**
     * Account/payment method fraud risk component (0.0 to 1.0)
     */
    private double accountScore;
    
    /**
     * Device fingerprint fraud risk component (0.0 to 1.0)
     */
    private double deviceScore;
    
    /**
     * Behavioral pattern fraud risk component (0.0 to 1.0)
     */
    private double behavioralScore;
    
    /**
     * Transaction velocity fraud risk component (0.0 to 1.0)
     */
    private double velocityScore;
    
    /**
     * Geolocation fraud risk component (0.0 to 1.0)
     */
    private double geoScore;
    
    /**
     * Machine learning model fraud risk component (0.0 to 1.0)
     */
    private double mlScore;
    
    /**
     * Confidence level of the fraud assessment (0.0 to 1.0)
     * Higher values indicate more reliable scoring
     */
    private double confidenceLevel;
    
    /**
     * Weighted factors used in score calculation
     */
    private Map<String, Double> scoreWeights;
    
    /**
     * Additional metadata about the scoring process
     */
    private Map<String, Object> scoringMetadata;
    
    /**
     * Timestamp when the fraud score was calculated
     */
    private LocalDateTime calculatedAt;
    
    /**
     * Version of the fraud scoring algorithm used
     */
    private String scoringVersion;
    
    /**
     * Reason codes explaining the score components
     */
    private java.util.List<String> reasonCodes;
    
    /**
     * Get the fraud score (returns score field or overallScore if not set)
     */
    public double getScore() {
        return score != 0.0 ? score : overallScore;
    }

    /**
     * Get confidence level (returns confidence or confidenceLevel)
     */
    public double getConfidence() {
        return confidence != 0.0 ? confidence : confidenceLevel;
    }

    /**
     * Get confidence level value
     */
    public double getConfidenceLevel() {
        return confidenceLevel != 0.0 ? confidenceLevel : confidence;
    }

    /**
     * Check if this is considered a high-risk score
     */
    public boolean isHighRisk() {
        return getScore() >= 0.7 || overallScore >= 0.7;
    }
    
    /**
     * Check if this is considered a medium-risk score
     */
    public boolean isMediumRisk() {
        return overallScore >= 0.4 && overallScore < 0.7;
    }
    
    /**
     * Check if this is considered a low-risk score
     */
    public boolean isLowRisk() {
        return overallScore < 0.4;
    }
    
    /**
     * Get the dominant risk factor (highest scoring component)
     */
    public String getDominantRiskFactor() {
        double maxScore = Math.max(ipScore, Math.max(emailScore, 
            Math.max(accountScore, Math.max(deviceScore, 
            Math.max(behavioralScore, Math.max(velocityScore, 
            Math.max(geoScore, mlScore)))))));
        
        if (maxScore == ipScore) return "IP_ADDRESS";
        if (maxScore == emailScore) return "EMAIL";
        if (maxScore == accountScore) return "ACCOUNT";
        if (maxScore == deviceScore) return "DEVICE";
        if (maxScore == behavioralScore) return "BEHAVIORAL";
        if (maxScore == velocityScore) return "VELOCITY";
        if (maxScore == geoScore) return "GEOLOCATION";
        if (maxScore == mlScore) return "MACHINE_LEARNING";
        
        return "UNKNOWN";
    }
    
    /**
     * Get risk level based on overall score
     */
    public FraudRiskLevel getRiskLevel() {
        if (overallScore >= 0.8) return FraudRiskLevel.CRITICAL;
        if (overallScore >= 0.6) return FraudRiskLevel.HIGH;
        if (overallScore >= 0.4) return FraudRiskLevel.MEDIUM;
        if (overallScore >= 0.2) return FraudRiskLevel.LOW;
        return FraudRiskLevel.MINIMAL;
    }
}