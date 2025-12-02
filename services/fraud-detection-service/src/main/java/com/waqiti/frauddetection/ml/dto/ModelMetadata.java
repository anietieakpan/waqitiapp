package com.waqiti.frauddetection.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMetadata {
    private String modelId;
    private String modelName;
    private String modelVersion;
    private String framework;
    private LocalDateTime trainedAt;
    private Map<String, Object> hyperparameters;
    private Map<String, Double> performanceMetrics;
}
