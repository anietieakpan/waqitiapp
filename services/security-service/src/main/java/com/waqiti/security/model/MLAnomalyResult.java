package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * ML Anomaly Detection Result
 * Result from machine learning based anomaly detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLAnomalyResult {

    private boolean anomalous;
    private Double anomalyScore;
    private Double confidence;
    private AnomalySeverity severity;
    private List<String> contributingFeatures;
    private Map<String, Double> featureScores;
    private String modelName;
    private String modelVersion;
}
