package com.waqiti.common.fraud.ml;

import com.waqiti.common.fraud.alert.FraudAlert;
import com.waqiti.common.fraud.model.FraudScore;
import com.waqiti.common.fraud.model.FraudRiskLevel;
import com.waqiti.common.fraud.model.AlertLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive ML prediction result containing ensemble predictions and analysis.
 * Used as the primary response from the MachineLearningModelService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionResult {
    
    /**
     * Final ensemble fraud probability (0.0 to 1.0)
     */
    private double fraudProbability;
    
    /**
     * Overall confidence in the ensemble prediction (0.0 to 1.0)
     */
    private double confidence;
    
    /**
     * Primary model used for the prediction (e.g., "ensemble", "random_forest_v1")
     */
    private String modelUsed;
    
    /**
     * Engineered features used in the prediction
     */
    private Map<String, Double> features;
    
    /**
     * Timestamp of the prediction
     */
    @Builder.Default
    private LocalDateTime predictionTime = LocalDateTime.now();
    
    /**
     * Aggregated risk factors from all models
     */
    private List<String> riskFactors;
    
    /**
     * Individual predictions from ensemble models
     */
    private List<ModelPrediction> ensemblePredictions;
    
    /**
     * Feature importance scores averaged across models
     */
    private Map<String, Double> featureImportance;
    
    /**
     * Processing performance metrics
     */
    private PredictionMetrics metrics;
    
    /**
     * Additional context and metadata
     */
    private Map<String, Object> context;
    
    /**
     * Model weights used in ensemble calculation
     */
    private Map<String, Double> modelWeights;
    
    /**
     * Alert level based on fraud probability
     */
    private AlertLevel alertLevel;
    
    /**
     * Recommended actions based on prediction
     */
    private List<String> recommendedActions;
    
    /**
     * Explanation of the prediction for interpretability
     */
    private String explanation;
    
    /**
     * Determines if this prediction indicates fraud
     */
    public boolean isFraud() {
        return fraudProbability >= 0.5;
    }
    
    /**
     * Determines if this is a high-confidence prediction
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    /**
     * Gets the risk level classification
     */
    public RiskLevel getRiskLevel() {
        if (fraudProbability >= 0.9) {
            return RiskLevel.CRITICAL;
        } else if (fraudProbability >= 0.7) {
            return RiskLevel.HIGH;
        } else if (fraudProbability >= 0.5) {
            return RiskLevel.MEDIUM;
        } else if (fraudProbability >= 0.3) {
            return RiskLevel.LOW;
        } else {
            return RiskLevel.MINIMAL;
        }
    }
    
    /**
     * Gets alert level for monitoring systems
     */
    public AlertLevel getAlertLevel() {
        if (alertLevel != null) {
            return alertLevel;
        }
        
        // Auto-calculate based on fraud probability and confidence
        if (fraudProbability >= 0.8 && confidence >= 0.8) {
            return AlertLevel.CRITICAL;
        } else if (fraudProbability >= 0.6 && confidence >= 0.7) {
            return AlertLevel.HIGH;
        } else if (fraudProbability >= 0.4 && confidence >= 0.6) {
            return AlertLevel.MEDIUM;
        } else if (fraudProbability >= 0.2) {
            return AlertLevel.LOW;
        } else {
            return AlertLevel.INFO;
        }
    }
    
    /**
     * Creates a detailed summary for logging and monitoring
     */
    public String getDetailedSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Fraud Probability: %.3f, ", fraudProbability));
        summary.append(String.format("Confidence: %.3f, ", confidence));
        summary.append(String.format("Model: %s, ", modelUsed));
        summary.append(String.format("Risk Level: %s, ", getRiskLevel()));
        summary.append(String.format("Alert Level: %s", getAlertLevel()));
        
        if (riskFactors != null && !riskFactors.isEmpty()) {
            summary.append(String.format(", Risk Factors: [%s]", String.join(", ", riskFactors)));
        }
        
        if (metrics != null) {
            summary.append(String.format(", Processing Time: %dms", metrics.getProcessingTimeMs()));
        }
        
        return summary.toString();
    }
    
    /**
     * Gets the top risk factors (up to limit)
     */
    public List<String> getTopRiskFactors(int limit) {
        if (riskFactors == null || riskFactors.isEmpty()) {
            return List.of();
        }
        
        return riskFactors.stream()
                .limit(limit)
                .toList();
    }
    
    /**
     * Validates the prediction result integrity
     */
    public boolean isValid() {
        return fraudProbability >= 0.0 && fraudProbability <= 1.0 &&
               confidence >= 0.0 && confidence <= 1.0 &&
               modelUsed != null &&
               predictionTime != null;
    }
    
    /**
     * Creates a fraud alert if threshold is exceeded
     */
    public FraudAlert toFraudAlert() {
        if (!isFraud()) {
            return null;
        }
        
        FraudScore fraudScore = FraudScore.builder()
                .score(fraudProbability)
                .confidence(confidence)
                .calculatedAt(predictionTime)
                .scoringVersion(modelUsed)
                .build();
        
        return FraudAlert.builder()
                .alertId("ML_" + System.currentTimeMillis())
                .level(convertToFraudDtoAlertLevel(getAlertLevel()))
                .fraudScore(fraudScore)
                .riskLevel(convertToFraudRiskLevel(getRiskLevel()))
                .timestamp(predictionTime)
                .build();
    }
    
    /**
     * Convert method simplified - now uses canonical AlertLevel
     * No conversion needed since AlertLevel is now unified across all packages
     */
    private AlertLevel convertToFraudDtoAlertLevel(AlertLevel mlAlertLevel) {
        // Direct return since AlertLevel is now canonical
        return mlAlertLevel;
    }
    
    /**
     * Convert MLPredictionResult.RiskLevel to FraudDTOs.FraudRiskLevel
     */
    private FraudRiskLevel convertToFraudRiskLevel(RiskLevel mlRiskLevel) {
        return switch (mlRiskLevel) {
            case MINIMAL -> FraudRiskLevel.MINIMAL;
            case LOW -> FraudRiskLevel.LOW;
            case MEDIUM -> FraudRiskLevel.MEDIUM;
            case HIGH -> FraudRiskLevel.HIGH;
            case CRITICAL -> FraudRiskLevel.HIGH; // Map CRITICAL to HIGH since DTO doesn't have CRITICAL
        };
    }
    
    /**
     * Risk level enumeration
     */
    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * AlertLevel enum removed - now uses canonical com.waqiti.common.fraud.alert.AlertLevel
     * All references throughout the platform now use the unified AlertLevel enum
     */

    /**
     * Prediction performance metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionMetrics {
        private long processingTimeMs;
        private int modelsUsed;
        private long featureEngineeringTimeMs;
        private long ensembleTimeMs;
        private String performanceCategory;

        public boolean isSlowPrediction() {
            return processingTimeMs > 1000; // 1 second threshold
        }
    }

    /**
     * Get processing time in milliseconds (convenience method)
     */
    public long getProcessingTimeMs() {
        return metrics != null ? metrics.getProcessingTimeMs() : 0L;
    }
}