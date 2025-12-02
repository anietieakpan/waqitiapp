package com.waqiti.frauddetection.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingExample {
    private FeatureVector features;
    private String label;
    private double weight;
}
