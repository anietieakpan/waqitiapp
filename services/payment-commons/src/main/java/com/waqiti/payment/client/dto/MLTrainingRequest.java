package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ML Training Request
 * 
 * Request to trigger machine learning model training for fraud detection.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLTrainingRequest {
    
    /**
     * Model ID to train
     */
    private String modelId;
    
    /**
     * Model name
     */
    private String modelName;
    
    /**
     * Training type
     */
    private TrainingType trainingType;
    
    /**
     * Training data source
     */
    private String dataSource;
    
    /**
     * Date range for training data
     */
    private DateRange dataDateRange;
    
    /**
     * Training parameters
     */
    private Map<String, Object> trainingParameters;
    
    /**
     * Features to include in training
     */
    private List<String> features;
    
    /**
     * Target variable
     */
    private String targetVariable;
    
    /**
     * Training algorithm
     */
    private String algorithm;
    
    /**
     * Cross-validation parameters
     */
    private CrossValidationConfig crossValidation;
    
    /**
     * Priority of the training job
     */
    private Priority priority;
    
    /**
     * Whether to deploy model after training
     */
    @Builder.Default
    private Boolean autoDeploy = false;
    
    /**
     * Minimum performance threshold for deployment
     */
    private Double minPerformanceThreshold;
    
    /**
     * Notification settings
     */
    private NotificationConfig notifications;
    
    /**
     * Training timeout in minutes
     */
    @Builder.Default
    private Integer timeoutMinutes = 120;
    
    /**
     * Request timestamp
     */
    private LocalDateTime requestedAt;
    
    /**
     * User who requested the training
     */
    private String requestedBy;
    
    public enum TrainingType {
        FULL_RETRAIN,
        INCREMENTAL,
        TRANSFER_LEARNING,
        ENSEMBLE_UPDATE
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRange {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrossValidationConfig {
        private Integer folds;
        private String strategy;
        private Double testSize;
        private Integer randomSeed;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationConfig {
        private Boolean emailOnComplete;
        private Boolean emailOnFailure;
        private List<String> emailRecipients;
        private String webhookUrl;
    }
}