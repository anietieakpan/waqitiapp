package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Ensemble Anomaly Detection Result
 * Result from ensemble of multiple ML models
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnsembleAnomalyResult {

    private boolean anomalous;
    private Double consensusScore;
    private Double confidence;
    private AnomalySeverity severity;
    private Map<String, Double> modelScores;
    private String votingStrategy;
    private Integer modelsInAgreement;
    private Integer totalModels;
}
