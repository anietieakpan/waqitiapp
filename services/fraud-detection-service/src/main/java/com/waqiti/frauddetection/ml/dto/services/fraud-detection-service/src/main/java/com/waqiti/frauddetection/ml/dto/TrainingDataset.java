package com.waqiti.frauddetection.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingDataset {
    private List<TrainingExample> examples;
    private int size;
    private String[] featureNames;
    private Map<String, Object> metadata;
}
