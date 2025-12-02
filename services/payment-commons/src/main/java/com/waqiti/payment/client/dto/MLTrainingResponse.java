package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ML Training Response
 * 
 * Response from triggering machine learning model training.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLTrainingResponse {
    
    /**
     * Training job ID
     */
    private String trainingJobId;
    
    /**
     * Model ID being trained
     */
    private String modelId;
    
    /**
     * Training status
     */
    private TrainingStatus status;
    
    /**
     * Estimated completion time
     */
    private LocalDateTime estimatedCompletion;
    
    /**
     * Progress percentage (0-100)
     */
    private Integer progressPercentage;
    
    /**
     * Current training phase
     */
    private String currentPhase;
    
    /**
     * Training metrics
     */
    private Map<String, Object> metrics;
    
    /**
     * Training started timestamp
     */
    private LocalDateTime startedAt;
    
    /**
     * Training completed timestamp
     */
    private LocalDateTime completedAt;
    
    /**
     * Error message if failed
     */
    private String errorMessage;
    
    /**
     * Training duration in minutes
     */
    private Long durationMinutes;
    
    /**
     * Resource utilization
     */
    private ResourceUsage resourceUsage;
    
    /**
     * Model performance after training
     */
    private ModelPerformanceMetrics performance;
    
    /**
     * Whether model was deployed
     */
    private Boolean deployed;
    
    /**
     * Deployment details if deployed
     */
    private DeploymentInfo deploymentInfo;
    
    public enum TrainingStatus {
        QUEUED,
        STARTING,
        DATA_LOADING,
        PREPROCESSING,
        TRAINING,
        VALIDATING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUsage {
        private Double cpuUtilization;
        private Double memoryUtilization;
        private Long diskUsageMB;
        private String gpuUsage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformanceMetrics {
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Double auc;
        private Map<String, Object> additionalMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeploymentInfo {
        private String deploymentId;
        private String environment;
        private LocalDateTime deployedAt;
        private String version;
    }
    
    /**
     * Check if training is complete
     */
    public boolean isComplete() {
        return status == TrainingStatus.COMPLETED;
    }
    
    /**
     * Check if training failed
     */
    public boolean isFailed() {
        return status == TrainingStatus.FAILED;
    }
    
    /**
     * Check if training is in progress
     */
    public boolean isInProgress() {
        return status == TrainingStatus.STARTING ||
               status == TrainingStatus.DATA_LOADING ||
               status == TrainingStatus.PREPROCESSING ||
               status == TrainingStatus.TRAINING ||
               status == TrainingStatus.VALIDATING;
    }
}