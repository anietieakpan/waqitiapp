package com.waqiti.ml.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Individual model prediction result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPrediction {
    
    private String modelName;
    private String modelVersion;
    private double score;
    private double confidence;
    private long latencyMs;
    private boolean failed;
    private String failureReason;
    private LocalDateTime timestamp;
    private float[] features;
    private Map<String, Double> featureImportance;
    private Map<String, Object> metadata;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public boolean isSuccessful() {
        return !failed;
    }
    
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    public boolean isLowLatency() {
        return latencyMs <= 100;
    }
}