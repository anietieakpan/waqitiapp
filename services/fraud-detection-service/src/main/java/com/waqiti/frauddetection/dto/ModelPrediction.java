package com.waqiti.frauddetection.dto;

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
    
    private String modelName;
    private float fraudScore;
    private double confidence;
    private long executionTimeMs;
    private Map<String, Object> metadata;
}