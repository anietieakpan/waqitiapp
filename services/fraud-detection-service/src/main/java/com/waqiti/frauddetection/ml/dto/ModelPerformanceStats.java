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
public class ModelPerformanceStats {
    private double accuracy;
    private double precision;
    private double recall;
    private double f1Score;
    private double auc;
    private Map<String, Double> confusionMatrix;
}
