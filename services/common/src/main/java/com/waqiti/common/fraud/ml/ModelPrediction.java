package com.waqiti.common.fraud.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a machine learning model prediction result for fraud detection.
 * Contains probability scores, confidence levels, and risk factor analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPrediction {
    
    /**
     * Unique identifier of the model that made this prediction
     */
    private String modelId;
    
    /**
     * Fraud probability score (0.0 to 1.0)
     * 0.0 = definitely not fraud, 1.0 = definitely fraud
     */
    private double probability;
    
    /**
     * Confidence level of the prediction (0.0 to 1.0)
     * Higher values indicate more reliable predictions
     */
    private double confidence;
    
    /**
     * List of risk factors that contributed to this prediction
     */
    private List<String> riskFactors;
    
    /**
     * Feature importance scores used in the prediction
     */
    private Map<String, Double> featureImportance;
    
    /**
     * Timestamp when the prediction was made
     */
    @Builder.Default
    private LocalDateTime predictionTime = LocalDateTime.now();
    
    /**
     * Model version used for this prediction
     */
    private String modelVersion;
    
    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;
    
    /**
     * Additional metadata about the prediction
     */
    private Map<String, Object> metadata;
    
    /**
     * Classification threshold used (typically 0.5)
     */
    @Builder.Default
    private double threshold = 0.5;
    
    /**
     * Raw prediction scores from ensemble models
     */
    private Map<String, Double> ensembleScores;
    
    /**
     * Determines if this prediction indicates fraud based on threshold
     */
    public boolean isFraud() {
        return probability >= threshold;
    }
    
    /**
     * Determines if this prediction is reliable based on confidence level
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    /**
     * Gets the risk level based on probability score
     */
    public RiskLevel getRiskLevel() {
        if (probability >= 0.8) {
            return RiskLevel.HIGH;
        } else if (probability >= 0.6) {
            return RiskLevel.MEDIUM;
        } else if (probability >= 0.4) {
            return RiskLevel.LOW;
        } else {
            return RiskLevel.MINIMAL;
        }
    }
    
    /**
     * Creates a summary of the prediction for logging
     */
    public String getSummary() {
        return String.format("Model: %s, Probability: %.3f, Confidence: %.3f, Risk: %s, Fraud: %s", 
            modelId, probability, confidence, getRiskLevel(), isFraud());
    }
    
    /**
     * Validates the prediction data integrity
     */
    public boolean isValid() {
        return modelId != null && 
               probability >= 0.0 && probability <= 1.0 &&
               confidence >= 0.0 && confidence <= 1.0 &&
               predictionTime != null;
    }
    
    /**
     * Risk level enumeration
     */
    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
}