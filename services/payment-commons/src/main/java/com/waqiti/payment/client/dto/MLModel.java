package com.waqiti.payment.client.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ML Model DTO
 * Represents a machine learning model used in fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MLModel {
    
    private UUID modelId;
    
    private String modelName;
    
    private String modelVersion;
    
    private ModelType modelType;
    
    private ModelStatus status;
    
    private ModelCategory category;
    
    // Model metadata
    private String description;
    
    private String algorithm;
    
    private String framework; // TENSORFLOW, PYTORCH, SCIKIT_LEARN, etc.
    
    // Training information
    private ModelTraining trainingInfo;
    
    // Performance metrics
    private ModelPerformance performanceMetrics;
    
    // Deployment information
    private ModelDeployment deploymentInfo;
    
    // Model configuration
    private ModelConfiguration configuration;
    
    // Features and schema
    @Builder.Default
    private List<ModelFeature> features = List.of();
    
    // Model artifacts
    private ModelArtifacts artifacts;
    
    // Lifecycle information
    private String createdBy;
    private LocalDateTime createdAt;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedAt;
    
    private Map<String, Object> metadata;
    
    public enum ModelType {
        CLASSIFICATION,      // Binary/multi-class classification
        REGRESSION,         // Regression models
        CLUSTERING,         // Unsupervised clustering
        ANOMALY_DETECTION,  // Outlier detection
        TIME_SERIES,        // Time series forecasting
        ENSEMBLE,          // Ensemble methods
        DEEP_LEARNING,     // Neural networks
        GRADIENT_BOOSTING  // XGBoost, LightGBM, etc.
    }
    
    public enum ModelStatus {
        DEVELOPMENT,
        TRAINING,
        VALIDATION,
        TESTING,
        STAGING,
        PRODUCTION,
        DEPRECATED,
        RETIRED
    }
    
    public enum ModelCategory {
        FRAUD_DETECTION,
        RISK_ASSESSMENT,
        BEHAVIORAL_ANALYSIS,
        TRANSACTION_SCORING,
        USER_PROFILING,
        MERCHANT_SCORING,
        VELOCITY_DETECTION,
        ANOMALY_DETECTION,
        COMPLIANCE_SCREENING
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelTraining {
        private LocalDateTime trainingStartTime;
        private LocalDateTime trainingEndTime;
        private Long trainingDurationMs;
        private String trainingDataset;
        private Integer trainingDataSize;
        private String trainingEnvironment;
        private Map<String, Object> hyperparameters;
        private String trainingJobId;
        private String trainingStatus;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformance {
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Double auc;
        private Double specificity;
        private Double falsePositiveRate;
        private Double falseNegativeRate;
        private Map<String, Double> customMetrics;
        private String performanceGrade; // A, B, C, D, F
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelDeployment {
        private String deploymentEnvironment;
        private LocalDateTime deployedAt;
        private String deployedBy;
        private String deploymentVersion;
        private String endpointUrl;
        private Integer maxConcurrency;
        private Long maxLatencyMs;
        private String scalingPolicy;
        private Map<String, String> deploymentConfig;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelConfiguration {
        private Double predictionThreshold;
        private Integer maxFeatures;
        private Boolean enableExplainability;
        private Boolean enableMonitoring;
        private Integer cacheTtlSeconds;
        private Map<String, Object> modelParameters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelFeature {
        private String featureName;
        private String featureType; // NUMERIC, CATEGORICAL, BOOLEAN, TEXT
        private String dataType;
        private Boolean isRequired;
        private String description;
        private Object defaultValue;
        private String transformation; // STANDARDIZATION, NORMALIZATION, etc.
        private Double importance;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelArtifacts {
        private String modelFilePath;
        private String configFilePath;
        private String schemaFilePath;
        private String documentationUrl;
        private String notebookUrl;
        private Long modelSizeBytes;
        private String checksumMd5;
        private Map<String, String> additionalFiles;
    }
    
    // Business logic methods
    public boolean isProduction() {
        return status == ModelStatus.PRODUCTION;
    }
    
    public boolean isDeprecated() {
        return status == ModelStatus.DEPRECATED || status == ModelStatus.RETIRED;
    }
    
    public boolean hasGoodPerformance() {
        return performanceMetrics != null && 
               performanceMetrics.getAccuracy() != null && 
               performanceMetrics.getAccuracy() >= 0.85;
    }
    
    public boolean isDeployed() {
        return deploymentInfo != null && 
               deploymentInfo.getDeployedAt() != null;
    }
    
    public boolean supportsExplainability() {
        return configuration != null && 
               configuration.getEnableExplainability() != null && 
               configuration.getEnableExplainability();
    }
    
    public boolean requiresFeature(String featureName) {
        if (features == null) {
            return false;
        }
        
        return features.stream()
            .anyMatch(feature -> featureName.equals(feature.getFeatureName()) &&
                               feature.getIsRequired() != null && 
                               feature.getIsRequired());
    }
    
    public String getPerformanceSummary() {
        if (performanceMetrics == null) {
            return "No performance data available";
        }
        
        StringBuilder summary = new StringBuilder();
        if (performanceMetrics.getAccuracy() != null) {
            summary.append(String.format("Accuracy: %.2f%%, ", performanceMetrics.getAccuracy() * 100));
        }
        if (performanceMetrics.getPrecision() != null) {
            summary.append(String.format("Precision: %.2f%%, ", performanceMetrics.getPrecision() * 100));
        }
        if (performanceMetrics.getRecall() != null) {
            summary.append(String.format("Recall: %.2f%%", performanceMetrics.getRecall() * 100));
        }
        
        return summary.toString();
    }
    
    public boolean needsRetraining() {
        // Check if model needs retraining based on performance degradation
        return performanceMetrics != null && 
               performanceMetrics.getAccuracy() != null && 
               performanceMetrics.getAccuracy() < 0.80; // Below 80% accuracy
    }
}