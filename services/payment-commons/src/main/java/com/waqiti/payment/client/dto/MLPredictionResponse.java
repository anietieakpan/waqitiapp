package com.waqiti.payment.client.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ML prediction response DTO
 * Response from machine learning model predictions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MLPredictionResponse {
    
    private UUID responseId;
    
    private UUID requestId;
    
    private String modelName;
    
    private String modelVersion;
    
    private String entityId;
    
    private PredictionStatus status;
    
    private LocalDateTime predictionTimestamp;
    
    private Long processingTimeMs;
    
    // Core prediction results
    private PredictionResult predictionResult;
    
    // Model performance info
    private ModelInfo modelInfo;
    
    // Feature analysis
    private FeatureAnalysis featureAnalysis;
    
    // Explainability results
    private ExplainabilityResults explainability;
    
    // Confidence and uncertainty
    private ConfidenceMetrics confidenceMetrics;
    
    // Quality indicators
    private QualityIndicators qualityIndicators;
    
    // Additional insights
    @Builder.Default
    private List<PredictionInsight> insights = List.of();
    
    // Warnings and alerts
    @Builder.Default
    private List<PredictionWarning> warnings = List.of();
    
    private Map<String, Object> additionalData;
    
    public enum PredictionStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED,
        TIMEOUT,
        MODEL_UNAVAILABLE,
        INSUFFICIENT_DATA
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionResult {
        private BigDecimal score;                // Primary prediction score (0-100)
        private String classification;           // FRAUD, NOT_FRAUD, SUSPICIOUS
        private Double probability;             // Probability of fraud (0.0-1.0)
        private String riskLevel;              // LOW, MEDIUM, HIGH, CRITICAL
        private String recommendation;          // APPROVE, REVIEW, DECLINE
        private Map<String, Object> predictions; // Multi-output predictions
        private String decisionBoundary;       // Information about decision boundary
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo {
        private String modelId;
        private String modelType;              // RANDOM_FOREST, NEURAL_NETWORK, etc.
        private String modelFamily;           // ENSEMBLE, DEEP_LEARNING, etc.
        private LocalDateTime modelTrainedAt;
        private LocalDateTime modelDeployedAt;
        private String modelVersion;
        private Double modelAccuracy;
        private Double modelPrecision;
        private Double modelRecall;
        private String modelStatus;           // ACTIVE, DEPRECATED, TESTING
        private Map<String, Object> modelMetadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureAnalysis {
        private Integer totalFeatures;
        private Integer featuresUsed;
        private Integer missingFeatures;
        @Builder.Default
        private List<String> missingFeatureNames = List.of();
        @Builder.Default
        private List<FeatureImportance> featureImportances = List.of();
        @Builder.Default
        private List<FeatureContribution> featureContributions = List.of();
        private Map<String, Object> featureStatistics;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FeatureImportance {
            private String featureName;
            private Double importance;          // 0.0-1.0
            private String importanceType;     // GLOBAL, LOCAL, PERMUTATION
            private Integer rank;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FeatureContribution {
            private String featureName;
            private Object featureValue;
            private Double contribution;        // Positive/negative contribution to score
            private String contributionType;   // SHAP, LIME, etc.
            private Double baseline;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExplainabilityResults {
        private String explanationMethod;      // SHAP, LIME, GRADCAM, etc.
        private String primaryExplanation;
        @Builder.Default
        private List<String> keyFactors = List.of();
        @Builder.Default
        private List<String> riskFactors = List.of();
        @Builder.Default
        private List<String> protectiveFactors = List.of();
        private LocalExplanation localExplanation;
        private GlobalExplanation globalExplanation;
        private CounterfactualExplanation counterfactual;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class LocalExplanation {
            private String explanation;
            @Builder.Default
            private List<String> reasons = List.of();
            private Map<String, Double> featureShapValues;
            private String visualizationUrl;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class GlobalExplanation {
            private String modelBehavior;
            @Builder.Default
            private List<String> globalPatterns = List.of();
            private Map<String, Double> globalFeatureImportance;
            private String interpretabilityScore;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CounterfactualExplanation {
            private String whatIfScenario;
            private Map<String, Object> alternativeValues;
            private BigDecimal alternativeScore;
            private String alternativeClassification;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfidenceMetrics {
        private Double predictionConfidence;   // 0.0-1.0
        private Double modelConfidence;        // Model's confidence in its own prediction
        private String uncertaintyType;        // ALEATORIC, EPISTEMIC, TOTAL
        private Double uncertaintyMeasure;
        private ConfidenceInterval confidenceInterval;
        private String reliabilityScore;       // HIGH, MEDIUM, LOW
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ConfidenceInterval {
            private Double lowerBound;
            private Double upperBound;
            private Double confidenceLevel;    // e.g., 0.95 for 95%
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityIndicators {
        private Double dataQuality;           // 0.0-1.0
        private Double featureQuality;        // 0.0-1.0
        private Double predictionQuality;     // 0.0-1.0
        private String qualityAssessment;     // EXCELLENT, GOOD, FAIR, POOR
        @Builder.Default
        private List<String> qualityIssues = List.of();
        private Map<String, Double> qualityMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionInsight {
        private String insightType;           // PATTERN, ANOMALY, TREND
        private String insight;
        private Double relevance;             // 0.0-1.0
        private String evidenceStrength;      // STRONG, MODERATE, WEAK
        private Map<String, Object> supportingData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionWarning {
        private WarningType warningType;
        private String warning;
        private WarningLevel level;
        private String recommendation;
        private Map<String, Object> warningContext;
        
        public enum WarningType {
            DATA_QUALITY,
            MODEL_DRIFT,
            FEATURE_IMPORTANCE_CHANGE,
            UNCERTAINTY_HIGH,
            PREDICTION_BOUNDARY,
            PERFORMANCE_DEGRADATION
        }
        
        public enum WarningLevel {
            INFO,
            WARNING,
            ERROR,
            CRITICAL
        }
    }
    
    // Business logic methods
    public boolean isSuccessful() {
        return status == PredictionStatus.SUCCESS;
    }
    
    public boolean isHighRiskPrediction() {
        return predictionResult != null && 
               ("HIGH".equals(predictionResult.getRiskLevel()) ||
                "CRITICAL".equals(predictionResult.getRiskLevel()) ||
                (predictionResult.getScore() != null && 
                 predictionResult.getScore().compareTo(new BigDecimal("70")) > 0));
    }
    
    public boolean isHighConfidence() {
        return confidenceMetrics != null && 
               confidenceMetrics.getPredictionConfidence() != null && 
               confidenceMetrics.getPredictionConfidence() >= 0.8;
    }
    
    public boolean hasGoodQuality() {
        return qualityIndicators != null && 
               ("EXCELLENT".equals(qualityIndicators.getQualityAssessment()) ||
                "GOOD".equals(qualityIndicators.getQualityAssessment()));
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    public boolean hasCriticalWarnings() {
        return warnings != null && 
               warnings.stream()
                   .anyMatch(w -> w.getLevel() == PredictionWarning.WarningLevel.CRITICAL);
    }
    
    public boolean recommendsDecline() {
        return predictionResult != null && 
               "DECLINE".equals(predictionResult.getRecommendation());
    }
    
    public boolean recommendsReview() {
        return predictionResult != null && 
               "REVIEW".equals(predictionResult.getRecommendation());
    }
    
    public boolean isFraudPrediction() {
        return predictionResult != null && 
               ("FRAUD".equals(predictionResult.getClassification()) ||
                "SUSPICIOUS".equals(predictionResult.getClassification()));
    }
    
    public boolean hasMissingFeatures() {
        return featureAnalysis != null && 
               featureAnalysis.getMissingFeatures() != null && 
               featureAnalysis.getMissingFeatures() > 0;
    }
    
    public boolean isReliable() {
        return isSuccessful() && 
               isHighConfidence() && 
               hasGoodQuality() && 
               !hasCriticalWarnings();
    }
    
    public String getTopRiskFactor() {
        if (explainability != null && 
            explainability.getRiskFactors() != null && 
            !explainability.getRiskFactors().isEmpty()) {
            return explainability.getRiskFactors().get(0);
        }
        
        if (featureAnalysis != null && 
            featureAnalysis.getFeatureContributions() != null) {
            return featureAnalysis.getFeatureContributions().stream()
                .filter(fc -> fc.getContribution() != null && fc.getContribution() > 0)
                .max((fc1, fc2) -> fc1.getContribution().compareTo(fc2.getContribution()))
                .map(FeatureAnalysis.FeatureContribution::getFeatureName)
                .orElse(null);
        }
        
        return null;
    }
    
    public Double getFraudProbabilityPercentage() {
        if (predictionResult != null && predictionResult.getProbability() != null) {
            return predictionResult.getProbability() * 100.0;
        }
        return null;
    }
}