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
public class ModelPrediction {
    private double score;
    private String prediction;
    private double confidence;
    private Map<String, Double> classProbabilities;
    private Map<String, Double> featureImportance;
}
