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
public class PyTorchPredictionResponse {
    private double[] predictions;
    private double[][] probabilities;
    private Map<String, Object> metadata;
}
