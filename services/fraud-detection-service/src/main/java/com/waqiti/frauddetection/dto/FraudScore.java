package com.waqiti.frauddetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScore {
    
    private double score;
    private double confidence;
    private int modelCount;
    private List<ModelPrediction> individualPredictions;
}