package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ML prediction request DTO
 * Request for machine learning model predictions in fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"sensitiveFeatures"})
public class MLPredictionRequest {
    
    @NotNull
    private UUID requestId;
    
    @NotNull
    private String modelName;
    
    private String modelVersion;
    
    @NotNull
    private String entityId; // Transaction ID, User ID, etc.
    
    @NotNull
    private PredictionType predictionType;
    
    @NotNull
    private LocalDateTime requestTimestamp;
    
    // Feature data for the model
    @NotNull
    private Map<String, Object> features;
    
    // Sensitive features (encrypted/masked)
    private Map<String, String> sensitiveFeatures;
    
    // Prediction context
    private PredictionContext context;
    
    // Model execution preferences
    private ExecutionPreferences executionPreferences;
    
    // Real-time data
    private RealtimeContext realtimeContext;
    
    // Historical context
    private HistoricalContext historicalContext;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    public enum PredictionType {
        FRAUD_SCORE,           // Overall fraud risk score
        TRANSACTION_RISK,      // Transaction-specific risk
        USER_RISK,            // User behavior risk
        MERCHANT_RISK,        // Merchant risk assessment
        ANOMALY_DETECTION,    // Behavioral anomaly detection
        CLASSIFICATION,       // Binary classification (fraud/not fraud)
        CLUSTERING,           // Pattern clustering
        TIME_SERIES,          // Time series anomaly detection
        ENSEMBLE             // Ensemble model prediction
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionContext {
        private String businessContext;
        private String channel;
        private String applicationName;
        private String geographicRegion;
        private String regulatoryZone;
        private String riskAssessmentReason;
        private Integer urgencyLevel; // 1-5
        private Boolean isRealTime;
        private Map<String, Object> contextualAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionPreferences {
        @Builder.Default
        private Boolean enableExplainability = true;
        
        private Boolean useLatestModel;
        
        private Boolean enableFeatureImportance;
        
        private Boolean enableConfidenceInterval;
        
        private Double confidenceThreshold;
        
        private Integer maxExecutionTimeMs;
        
        private String executionMode; // FAST, BALANCED, THOROUGH
        
        @Builder.Default
        private List<String> excludedModels = List.of();
        
        @Builder.Default
        private List<String> preferredModels = List.of();
        
        private Map<String, Object> modelParameters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealtimeContext {
        private String currentSessionId;
        private LocalDateTime sessionStartTime;
        private String deviceFingerprint;
        private String ipAddress;
        private String userAgent;
        private String currentLocation;
        private Map<String, Object> realtimeMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalContext {
        private Integer lookbackDays;
        private Boolean includeTransactionHistory;
        private Boolean includeBehavioralHistory;
        private Boolean includeRiskHistory;
        private LocalDateTime historicalDataFromDate;
        private LocalDateTime historicalDataToDate;
        private Map<String, Object> historicalFilters;
    }
    
    // Business logic methods
    public boolean isRealTimePrediction() {
        return context != null && 
               context.getIsRealTime() != null && 
               context.getIsRealTime();
    }
    
    public boolean isHighPriority() {
        return context != null && 
               context.getUrgencyLevel() != null && 
               context.getUrgencyLevel() >= 4;
    }
    
    public boolean requiresExplainability() {
        return executionPreferences != null && 
               executionPreferences.getEnableExplainability() != null && 
               executionPreferences.getEnableExplainability();
    }
    
    public boolean hasHistoricalContext() {
        return historicalContext != null && 
               (historicalContext.getIncludeTransactionHistory() != null && 
                historicalContext.getIncludeTransactionHistory() ||
                historicalContext.getIncludeBehavioralHistory() != null && 
                historicalContext.getIncludeBehavioralHistory());
    }
    
    public boolean hasRealtimeContext() {
        return realtimeContext != null && 
               realtimeContext.getCurrentSessionId() != null;
    }
    
    public boolean isFastExecutionMode() {
        return executionPreferences != null && 
               "FAST".equals(executionPreferences.getExecutionMode());
    }
    
    public boolean isThoroughExecutionMode() {
        return executionPreferences != null && 
               "THOROUGH".equals(executionPreferences.getExecutionMode());
    }
    
    public boolean hasTimeConstraints() {
        return executionPreferences != null && 
               executionPreferences.getMaxExecutionTimeMs() != null;
    }
    
    public Integer getEffectiveTimeoutMs() {
        if (executionPreferences != null && 
            executionPreferences.getMaxExecutionTimeMs() != null) {
            return executionPreferences.getMaxExecutionTimeMs();
        }
        
        // Default timeouts based on execution mode and priority
        if (isHighPriority() || isRealTimePrediction()) {
            return isFastExecutionMode() ? 1000 : 5000;
        }
        
        return isThoroughExecutionMode() ? 30000 : 10000;
    }
    
    public boolean hasModelPreferences() {
        return executionPreferences != null && 
               ((executionPreferences.getPreferredModels() != null && 
                 !executionPreferences.getPreferredModels().isEmpty()) ||
                (executionPreferences.getExcludedModels() != null && 
                 !executionPreferences.getExcludedModels().isEmpty()));
    }
    
    public boolean excludesModel(String modelName) {
        return executionPreferences != null && 
               executionPreferences.getExcludedModels() != null && 
               executionPreferences.getExcludedModels().contains(modelName);
    }
    
    public boolean prefersModel(String modelName) {
        return executionPreferences != null && 
               executionPreferences.getPreferredModels() != null && 
               executionPreferences.getPreferredModels().contains(modelName);
    }
    
    public boolean isComplianceRelated() {
        return context != null && 
               context.getRegulatoryZone() != null;
    }
    
    public boolean requiresFeatureImportance() {
        return executionPreferences != null && 
               executionPreferences.getEnableFeatureImportance() != null && 
               executionPreferences.getEnableFeatureImportance();
    }
    
    public boolean requiresConfidenceInterval() {
        return executionPreferences != null && 
               executionPreferences.getEnableConfidenceInterval() != null && 
               executionPreferences.getEnableConfidenceInterval();
    }
}