package com.waqiti.ml.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Metadata for machine learning models
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMetadata {
    private String modelName;
    private String modelVersion;
    private String modelType;
    private String trainingDate;
    private LocalDateTime lastUpdated;
    private double accuracy;
    private double precision;
    private double recall;
    private double f1Score;
    private double auc;
    private int featureCount;
    private long trainingDataSize;
    private long modelSizeBytes;
    private boolean isLoaded;
    private boolean isActive;
    private String description;
    private Map<String, Object> hyperparameters;
    private Map<String, Double> featureImportance;
    private String modelPath;
    private String framework;
    private String environment;
    private Map<String, Object> performanceMetrics;
    private Map<String, Object> customMetadata;
}