package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ML-based fraud score result for comprehensive fraud assessment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLFraudScore {
    
    private String scoreId;
    private String modelId;
    private String modelVersion;
    private LocalDateTime calculatedAt;
    
    // Primary fraud score
    private double score;
    private double confidence;
    private String riskLevel;
    private boolean fraudDetected;
    
    // Model ensemble results
    private Map<String, Double> modelScores;
    private Map<String, Double> modelConfidences;
    private double ensembleScore;
    private String ensembleMethod;
    
    // Feature contributions
    private Map<String, Double> featureImportance;
    private Map<String, Double> featureValues;
    private List<String> topRiskFeatures;
    private Map<String, String> featureExplanations;
    
    // Risk factors
    private List<String> detectedRiskFactors;
    private Map<String, Double> riskFactorScores;
    private String primaryRiskFactor;
    
    // Behavioral analysis
    private double behavioralAnomalyScore;
    private List<String> behavioralAnomalies;
    private double deviationFromProfile;
    
    // Transaction pattern analysis
    private double velocityRiskScore;
    private double amountRiskScore;
    private double timingRiskScore;
    private double locationRiskScore;
    private double merchantRiskScore;
    
    // Historical context
    private double historicalComparisonScore;
    private int similarTransactionsFound;
    private double averageHistoricalScore;
    
    // Model performance metrics
    private double modelAccuracy;
    private double modelPrecision;
    private double modelRecall;
    private double modelF1Score;
    private LocalDateTime modelTrainingDate;
    
    // Explanation and interpretation
    private String scoreExplanation;
    private List<String> keyIndicators;
    private Map<String, String> interpretationNotes;
    
    // Performance data
    private long calculationTimeMs;
    private String calculationMethod;
    private Map<String, Object> modelMetadata;
    
    /**
     * Risk levels for ML fraud scoring
     */
    public static class RiskLevel {
        public static final String MINIMAL = "MINIMAL";
        public static final String LOW = "LOW";
        public static final String MEDIUM = "MEDIUM";
        public static final String HIGH = "HIGH";
        public static final String CRITICAL = "CRITICAL";
        public static final String EXTREME = "EXTREME";
    }
    
    /**
     * Common risk factors
     */
    public static class RiskFactor {
        public static final String HIGH_VELOCITY = "HIGH_VELOCITY";
        public static final String UNUSUAL_AMOUNT = "UNUSUAL_AMOUNT";
        public static final String NEW_DEVICE = "NEW_DEVICE";
        public static final String UNUSUAL_LOCATION = "UNUSUAL_LOCATION";
        public static final String OFF_HOURS_TRANSACTION = "OFF_HOURS_TRANSACTION";
        public static final String HIGH_RISK_MERCHANT = "HIGH_RISK_MERCHANT";
        public static final String BEHAVIORAL_CHANGE = "BEHAVIORAL_CHANGE";
        public static final String SUSPICIOUS_PATTERN = "SUSPICIOUS_PATTERN";
    }
    
    /**
     * Determine risk level based on score
     */
    public String determineRiskLevel() {
        if (score >= 0.95) return RiskLevel.EXTREME;
        if (score >= 0.85) return RiskLevel.CRITICAL;
        if (score >= 0.70) return RiskLevel.HIGH;
        if (score >= 0.50) return RiskLevel.MEDIUM;
        if (score >= 0.25) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }
    
    /**
     * Check if score indicates high fraud risk
     */
    public boolean isHighRisk() {
        return score >= 0.7 && confidence >= 0.8;
    }
    
    /**
     * Check if fraud is detected with high confidence
     */
    public boolean isHighConfidenceFraud() {
        return fraudDetected && confidence >= 0.85 && score >= 0.8;
    }
    
    /**
     * Get the top N risk features
     */
    public List<String> getTopRiskFeatures(int n) {
        if (featureImportance == null) {
            return List.of();
        }
        
        return featureImportance.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }
    
    /**
     * Get weighted risk score considering confidence
     */
    public double getWeightedRiskScore() {
        return score * confidence;
    }
    
    /**
     * Calculate overall behavioral risk
     */
    public double getOverallBehavioralRisk() {
        return (behavioralAnomalyScore + deviationFromProfile) / 2.0;
    }
    
    /**
     * Calculate transaction context risk
     */
    public double getTransactionContextRisk() {
        return (velocityRiskScore + amountRiskScore + timingRiskScore + 
                locationRiskScore + merchantRiskScore) / 5.0;
    }
    
    /**
     * Get risk summary
     */
    public String getRiskSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("ML Fraud Score: ").append(String.format("%.3f", score));
        summary.append(" (").append(determineRiskLevel()).append(")");
        
        if (primaryRiskFactor != null) {
            summary.append(" - Primary Risk: ").append(primaryRiskFactor);
        }
        
        if (confidence < 0.7) {
            summary.append(" - Low Confidence");
        }
        
        return summary.toString();
    }
    
    /**
     * Check if model results are reliable
     */
    public boolean isReliable() {
        return confidence >= 0.7 && 
               modelAccuracy >= 0.8 && 
               calculationTimeMs < 5000; // Under 5 seconds
    }
    
    /**
     * Get feature contribution summary
     */
    public Map<String, String> getFeatureContributionSummary() {
        Map<String, String> summary = new java.util.HashMap<>();
        
        if (featureImportance != null) {
            featureImportance.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> {
                        String feature = entry.getKey();
                        double importance = entry.getValue();
                        double value = featureValues != null ? 
                                      featureValues.getOrDefault(feature, 0.0) : 0.0;
                        
                        summary.put(feature, String.format("Importance: %.2f, Value: %.2f", 
                                                           importance, value));
                    });
        }
        
        return summary;
    }
    
    /**
     * Add risk factor with score
     */
    public void addRiskFactor(String factor, double score) {
        if (detectedRiskFactors == null) {
            detectedRiskFactors = new java.util.ArrayList<>();
        }
        if (riskFactorScores == null) {
            riskFactorScores = new java.util.HashMap<>();
        }
        
        detectedRiskFactors.add(factor);
        riskFactorScores.put(factor, score);
    }
    
    /**
     * Add feature contribution
     */
    public void addFeatureContribution(String feature, double importance, double value) {
        if (featureImportance == null) {
            featureImportance = new java.util.HashMap<>();
        }
        if (featureValues == null) {
            featureValues = new java.util.HashMap<>();
        }
        
        featureImportance.put(feature, importance);
        featureValues.put(feature, value);
    }
}