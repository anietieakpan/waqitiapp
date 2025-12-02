package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Machine Learning fraud analysis
 */
@Data
@Builder
@Jacksonized
public class MLFraudAnalysis {
    private Map<String, Object> features;
    private double prediction;
    private double confidence;
    private String riskLevel;
    private Map<String, Double> featureImportance;
    private String modelVersion;
    private Instant timestamp;
    
    // Legacy fields for backward compatibility
    private List<String> importantFeatures;
    private String modelType;
    private double threshold;
    private boolean fraudPredicted;
    private Instant predictionTimestamp;

    /**
     * Get risk score based on ML prediction
     */
    public double getRiskScore() {
        return prediction;
    }
}