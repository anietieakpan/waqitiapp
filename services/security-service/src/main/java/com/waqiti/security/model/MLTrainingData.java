package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ML Training Data
 * Data package for training ML models
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLTrainingData {

    private MLFeatureVector features;
    private String label;
    private Double weight;
    private List<String> anomalyTypes;
    private Instant timestamp;
    private Map<String, Object> metadata;

    public static MLTrainingData fromAuthEvent(AuthenticationEvent event) {
        return MLTrainingData.builder()
            .features(MLFeatureVector.fromAuthEvent(event))
            .label(event.getAuthResult() != null ? event.getAuthResult().toString() : "UNKNOWN")
            .weight(1.0)
            .timestamp(event.getTimestamp())
            .metadata(event.getMetadata())
            .build();
    }
}
