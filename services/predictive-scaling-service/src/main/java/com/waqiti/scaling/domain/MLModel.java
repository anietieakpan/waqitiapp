package com.waqiti.scaling.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ml_models")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MLModel {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "model_id", unique = true, nullable = false, length = 50)
    private String modelId;
    
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;
    
    @Column(name = "model_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ModelType modelType;
    
    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;
    
    @Column(name = "framework", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private MLFramework framework;
    
    @Column(name = "algorithm", nullable = false, length = 50)
    private String algorithm;
    
    @Column(name = "model_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ModelStatus modelStatus = ModelStatus.TRAINING;
    
    @Column(name = "model_purpose", columnDefinition = "TEXT")
    private String modelPurpose;
    
    // Model Files and Artifacts
    @Column(name = "model_file_path", length = 500)
    private String modelFilePath;
    
    @Column(name = "model_artifact_url", length = 500)
    private String modelArtifactUrl;
    
    @Column(name = "model_format", length = 30)
    private String modelFormat; // PICKLE, ONNX, TENSORFLOW_SAVED_MODEL, PYTORCH_SCRIPT, etc.
    
    @Column(name = "model_size_bytes")
    private Long modelSizeBytes;
    
    @Column(name = "model_checksum", length = 100)
    private String modelChecksum;
    
    // Training Configuration
    @Type(type = "jsonb")
    @Column(name = "training_config", columnDefinition = "jsonb")
    private Map<String, Object> trainingConfig;
    
    @Type(type = "jsonb")
    @Column(name = "hyperparameters", columnDefinition = "jsonb")
    private Map<String, Object> hyperparameters;
    
    @Column(name = "input_features")
    private Integer inputFeatures;
    
    @Column(name = "output_dimensions")
    private Integer outputDimensions;
    
    @Type(type = "jsonb")
    @Column(name = "feature_names", columnDefinition = "jsonb")
    private List<String> featureNames;
    
    @Type(type = "jsonb")
    @Column(name = "feature_importance", columnDefinition = "jsonb")
    private Map<String, Double> featureImportance;
    
    @Type(type = "jsonb")
    @Column(name = "target_variables", columnDefinition = "jsonb")
    private List<String> targetVariables;
    
    // Training Data
    @Column(name = "training_data_size")
    private Long trainingDataSize;
    
    @Column(name = "training_data_start_date")
    private LocalDateTime trainingDataStartDate;
    
    @Column(name = "training_data_end_date")
    private LocalDateTime trainingDataEndDate;
    
    @Column(name = "training_samples")
    private Long trainingSamples;
    
    @Column(name = "validation_samples")
    private Long validationSamples;
    
    @Column(name = "test_samples")
    private Long testSamples;
    
    @Type(type = "jsonb")
    @Column(name = "data_preprocessing", columnDefinition = "jsonb")
    private Map<String, Object> dataPreprocessing;
    
    // Training Process
    @Column(name = "training_started_at")
    private LocalDateTime trainingStartedAt;
    
    @Column(name = "training_completed_at")
    private LocalDateTime trainingCompletedAt;
    
    @Column(name = "training_duration_seconds")
    private Long trainingDurationSeconds;
    
    @Column(name = "training_epochs")
    private Integer trainingEpochs;
    
    @Column(name = "training_iterations")
    private Long trainingIterations;
    
    @Column(name = "early_stopping_epoch")
    private Integer earlyStoppingEpoch;
    
    @Type(type = "jsonb")
    @Column(name = "training_history", columnDefinition = "jsonb")
    private List<TrainingEpoch> trainingHistory;
    
    @Column(name = "convergence_achieved")
    private Boolean convergenceAchieved = false;
    
    // Model Performance Metrics
    @Column(name = "training_accuracy")
    private Double trainingAccuracy;
    
    @Column(name = "validation_accuracy")
    private Double validationAccuracy;
    
    @Column(name = "test_accuracy")
    private Double testAccuracy;
    
    @Column(name = "training_loss")
    private Double trainingLoss;
    
    @Column(name = "validation_loss")
    private Double validationLoss;
    
    @Column(name = "test_loss")
    private Double testLoss;
    
    @Column(name = "mae") // Mean Absolute Error
    private Double mae;
    
    @Column(name = "mse") // Mean Squared Error
    private Double mse;
    
    @Column(name = "rmse") // Root Mean Squared Error
    private Double rmse;
    
    @Column(name = "mape") // Mean Absolute Percentage Error
    private Double mape;
    
    @Column(name = "r2_score") // R-squared Score
    private Double r2Score;
    
    @Column(name = "precision_score")
    private Double precisionScore;
    
    @Column(name = "recall_score")
    private Double recallScore;
    
    @Column(name = "f1_score")
    private Double f1Score;
    
    @Column(name = "auc_score") // Area Under Curve
    private Double aucScore;
    
    @Type(type = "jsonb")
    @Column(name = "confusion_matrix", columnDefinition = "jsonb")
    private Map<String, Object> confusionMatrix;
    
    @Type(type = "jsonb")
    @Column(name = "performance_metrics", columnDefinition = "jsonb")
    private Map<String, Object> performanceMetrics;
    
    // Model Validation
    @Column(name = "cross_validation_score")
    private Double crossValidationScore;
    
    @Column(name = "cross_validation_std")
    private Double crossValidationStd;
    
    @Column(name = "holdout_validation_score")
    private Double holdoutValidationScore;
    
    @Type(type = "jsonb")
    @Column(name = "validation_results", columnDefinition = "jsonb")
    private Map<String, Object> validationResults;
    
    @Column(name = "overfitting_score")
    private Double overfittingScore;
    
    @Column(name = "generalization_score")
    private Double generalizationScore;
    
    // Deployment and Production
    @Column(name = "deployed_at")
    private LocalDateTime deployedAt;
    
    @Column(name = "deployment_environment", length = 50)
    private String deploymentEnvironment;
    
    @Column(name = "serving_endpoint", length = 500)
    private String servingEndpoint;
    
    @Column(name = "prediction_count")
    private Long predictionCount = 0L;
    
    @Column(name = "successful_predictions")
    private Long successfulPredictions = 0L;
    
    @Column(name = "failed_predictions")
    private Long failedPredictions = 0L;
    
    @Column(name = "average_prediction_time_ms")
    private Double averagePredictionTimeMs;
    
    @Column(name = "last_prediction_at")
    private LocalDateTime lastPredictionAt;
    
    // Model Monitoring
    @Column(name = "model_drift_score")
    private Double modelDriftScore;
    
    @Column(name = "data_drift_score")
    private Double dataDriftScore;
    
    @Column(name = "performance_degradation")
    private Double performanceDegradation;
    
    @Column(name = "last_monitored_at")
    private LocalDateTime lastMonitoredAt;
    
    @Type(type = "jsonb")
    @Column(name = "drift_detection_results", columnDefinition = "jsonb")
    private Map<String, Object> driftDetectionResults;
    
    @Column(name = "requires_retraining")
    private Boolean requiresRetraining = false;
    
    @Column(name = "retraining_threshold")
    private Double retrainingThreshold = 0.8;
    
    // Model Lifecycle
    @Column(name = "model_lifecycle_stage", length = 30)
    @Enumerated(EnumType.STRING)
    private ModelLifecycleStage lifecycleStage = ModelLifecycleStage.DEVELOPMENT;
    
    @Column(name = "predecessor_model_id", length = 50)
    private String predecessorModelId;
    
    @Column(name = "successor_model_id", length = 50)
    private String successorModelId;
    
    @Column(name = "retirement_scheduled_at")
    private LocalDateTime retirementScheduledAt;
    
    @Column(name = "retired_at")
    private LocalDateTime retiredAt;
    
    @Column(name = "retirement_reason", columnDefinition = "TEXT")
    private String retirementReason;
    
    // Model Metadata
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "model_description", columnDefinition = "TEXT")
    private String modelDescription;
    
    @Type(type = "jsonb")
    @Column(name = "model_tags", columnDefinition = "jsonb")
    private List<String> modelTags;
    
    @Type(type = "jsonb")
    @Column(name = "model_metadata", columnDefinition = "jsonb")
    private Map<String, Object> modelMetadata;
    
    @Type(type = "jsonb")
    @Column(name = "experiment_tracking", columnDefinition = "jsonb")
    private Map<String, Object> experimentTracking;
    
    // Compliance and Governance
    @Column(name = "model_approval_status", length = 30)
    @Enumerated(EnumType.STRING)
    private ModelApprovalStatus approvalStatus = ModelApprovalStatus.PENDING;
    
    @Column(name = "approved_by", length = 100)
    private String approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "compliance_checked")
    private Boolean complianceChecked = false;
    
    @Type(type = "jsonb")
    @Column(name = "compliance_results", columnDefinition = "jsonb")
    private Map<String, Object> complianceResults;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (modelId == null) {
            modelId = "ML_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum ModelType {
        REGRESSION,           // Predict continuous values
        CLASSIFICATION,       // Predict discrete classes
        TIME_SERIES,          // Time series forecasting
        ANOMALY_DETECTION,    // Detect anomalous patterns
        CLUSTERING,           // Group similar data points
        REINFORCEMENT,        // Reinforcement learning
        ENSEMBLE,             // Ensemble of multiple models
        DEEP_LEARNING,        // Deep neural networks
        BAYESIAN,            // Bayesian models
        DECISION_TREE        // Tree-based models
    }
    
    public enum MLFramework {
        TENSORFLOW,
        PYTORCH,
        SCIKIT_LEARN,
        XGBOOST,
        LIGHTGBM,
        CATBOOST,
        KERAS,
        SPARK_ML,
        H2O,
        ONNX,
        CUSTOM
    }
    
    public enum ModelStatus {
        TRAINING,
        TRAINED,
        VALIDATING,
        VALIDATED,
        DEPLOYED,
        SERVING,
        MONITORING,
        DEGRADED,
        FAILED,
        RETIRED
    }
    
    public enum ModelLifecycleStage {
        DEVELOPMENT,
        STAGING,
        PRODUCTION,
        ARCHIVED,
        DEPRECATED
    }
    
    public enum ModelApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        CONDITIONAL,
        EXPIRED
    }
    
    // Nested classes for JSON storage
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingEpoch {
        private Integer epoch;
        private Double trainingLoss;
        private Double validationLoss;
        private Double trainingAccuracy;
        private Double validationAccuracy;
        private Long durationMs;
        private LocalDateTime timestamp;
        private Map<String, Object> additionalMetrics;
    }
    
    // Business logic methods
    
    public boolean isProductionReady() {
        return modelStatus == ModelStatus.VALIDATED &&
               approvalStatus == ModelApprovalStatus.APPROVED &&
               testAccuracy != null && testAccuracy >= 0.85;
    }
    
    public boolean requiresRetraining() {
        if (requiresRetraining != null && requiresRetraining) {
            return true;
        }
        
        // Check performance degradation
        if (performanceDegradation != null && retrainingThreshold != null) {
            return performanceDegradation > (1.0 - retrainingThreshold);
        }
        
        // Check model age
        if (deployedAt != null && deployedAt.isBefore(LocalDateTime.now().minusMonths(6))) {
            return true;
        }
        
        return false;
    }
    
    public boolean hasSignificantDrift() {
        return (modelDriftScore != null && modelDriftScore > 0.3) ||
               (dataDriftScore != null && dataDriftScore > 0.3);
    }
    
    public boolean isPerformingWell() {
        if (testAccuracy != null) {
            return testAccuracy >= retrainingThreshold;
        }
        
        // Fallback to validation accuracy
        if (validationAccuracy != null) {
            return validationAccuracy >= retrainingThreshold;
        }
        
        return false;
    }
    
    public boolean isOverfitting() {
        if (trainingAccuracy != null && validationAccuracy != null) {
            return (trainingAccuracy - validationAccuracy) > 0.1;
        }
        
        return overfittingScore != null && overfittingScore > 0.7;
    }
    
    public Double getPredictionSuccessRate() {
        if (predictionCount == 0) {
            return null;
        }
        
        return (double) successfulPredictions / predictionCount;
    }
    
    public void startTraining() {
        this.modelStatus = ModelStatus.TRAINING;
        this.trainingStartedAt = LocalDateTime.now();
    }
    
    public void completeTraining(Double accuracy, Double loss) {
        this.modelStatus = ModelStatus.TRAINED;
        this.trainingCompletedAt = LocalDateTime.now();
        this.trainingAccuracy = accuracy;
        this.trainingLoss = loss;
        
        if (trainingStartedAt != null) {
            this.trainingDurationSeconds = java.time.Duration
                    .between(trainingStartedAt, trainingCompletedAt).getSeconds();
        }
    }
    
    public void validate(Map<String, Object> validationResults) {
        this.modelStatus = ModelStatus.VALIDATED;
        this.validationResults = validationResults;
        
        // Extract common validation metrics
        if (validationResults.containsKey("accuracy")) {
            this.validationAccuracy = (Double) validationResults.get("accuracy");
        }
        if (validationResults.containsKey("loss")) {
            this.validationLoss = (Double) validationResults.get("loss");
        }
    }
    
    public void deploy(String environment, String endpoint) {
        this.modelStatus = ModelStatus.DEPLOYED;
        this.lifecycleStage = ModelLifecycleStage.PRODUCTION;
        this.deployedAt = LocalDateTime.now();
        this.deploymentEnvironment = environment;
        this.servingEndpoint = endpoint;
    }
    
    public void recordPrediction(boolean successful, long latencyMs) {
        this.predictionCount++;
        this.lastPredictionAt = LocalDateTime.now();
        
        if (successful) {
            this.successfulPredictions++;
        } else {
            this.failedPredictions++;
        }
        
        // Update average prediction time (running average)
        if (averagePredictionTimeMs == null) {
            averagePredictionTimeMs = (double) latencyMs;
        } else {
            averagePredictionTimeMs = (averagePredictionTimeMs * (predictionCount - 1) + latencyMs) / predictionCount;
        }
    }
    
    public void updateDriftScores(Double modelDrift, Double dataDrift) {
        this.modelDriftScore = modelDrift;
        this.dataDriftScore = dataDrift;
        this.lastMonitoredAt = LocalDateTime.now();
        
        // Determine if retraining is needed
        if (hasSignificantDrift()) {
            this.requiresRetraining = true;
            this.modelStatus = ModelStatus.DEGRADED;
        }
    }
    
    public void approve(String approver, String reason) {
        this.approvalStatus = ModelApprovalStatus.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
    }
    
    public void reject(String reason) {
        this.approvalStatus = ModelApprovalStatus.REJECTED;
        this.retirementReason = reason;
    }
    
    public void retire(String reason) {
        this.modelStatus = ModelStatus.RETIRED;
        this.lifecycleStage = ModelLifecycleStage.ARCHIVED;
        this.retiredAt = LocalDateTime.now();
        this.retirementReason = reason;
    }
    
    public void scheduleRetirement(LocalDateTime scheduledDate, String reason) {
        this.retirementScheduledAt = scheduledDate;
        this.retirementReason = reason;
    }
    
    public boolean isScheduledForRetirement() {
        return retirementScheduledAt != null && 
               LocalDateTime.now().isAfter(retirementScheduledAt);
    }
    
    public Map<String, Object> getModelSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("modelId", modelId);
        summary.put("modelName", modelName);
        summary.put("modelType", modelType);
        summary.put("framework", framework);
        summary.put("algorithm", algorithm);
        summary.put("version", modelVersion);
        summary.put("status", modelStatus);
        summary.put("accuracy", getBestAccuracy());
        summary.put("predictionCount", predictionCount);
        summary.put("successRate", getPredictionSuccessRate());
        summary.put("deployedAt", deployedAt);
        summary.put("requiresRetraining", requiresRetraining());
        summary.put("driftScore", Math.max(modelDriftScore != null ? modelDriftScore : 0,
                                          dataDriftScore != null ? dataDriftScore : 0));
        return summary;
    }
    
    private Double getBestAccuracy() {
        if (testAccuracy != null) return testAccuracy;
        if (validationAccuracy != null) return validationAccuracy;
        return trainingAccuracy;
    }
    
    public boolean canPredict() {
        return modelStatus == ModelStatus.DEPLOYED ||
               modelStatus == ModelStatus.SERVING ||
               modelStatus == ModelStatus.MONITORING;
    }
    
    public Map<String, Object> toMLExperiment() {
        Map<String, Object> experiment = new java.util.HashMap<>();
        experiment.put("model_id", modelId);
        experiment.put("algorithm", algorithm);
        experiment.put("hyperparameters", hyperparameters);
        experiment.put("training_config", trainingConfig);
        experiment.put("performance_metrics", performanceMetrics);
        experiment.put("feature_importance", featureImportance);
        experiment.put("training_duration", trainingDurationSeconds);
        experiment.put("model_size", modelSizeBytes);
        return experiment;
    }
}