package com.waqiti.frauddetection.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingJobConfig {
    private String jobId;
    private String modelType;
    private Map<String, Object> hyperparameters;
    private int epochs;
    private double learningRate;
    private int batchSize;
}
