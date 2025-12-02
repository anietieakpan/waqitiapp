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
public class FeatureVector {
    private Map<String, Object> features;
    private String[] featureNames;
    private double[] featureValues;
}
