package com.waqiti.frauddetection.dto.ml;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * TensorFlow Model Prediction Response
 * Production-grade TensorFlow Serving response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TensorFlowPredictionResponse {
    
    private String modelName;
    private String modelVersion;
    private Long modelVersionNumber;
    
    // Predictions
    @Builder.Default
    private List<Double> predictions = new java.util.ArrayList<>();
    
    private Double fraudProbability;
    private Double legitimateProbability;
    
    // Confidence and Uncertainty
    private Double confidence;
    private Double uncertainty;
    private Double variance;
    
    // Output Tensors
    @Builder.Default
    private Map<String, List<Double>> outputs = new java.util.HashMap<>();
    
    // Attention/Importance
    @Builder.Default
    private Map<String, Double> featureImportances = new java.util.HashMap<>();
    
    // Performance
    private Long inferenceTimeMs;
    private Long preprocessingTimeMs;
    private Long postprocessingTimeMs;
    
    // Metadata
    private String requestId;
    private Long timestamp;
    private String servingSignature;
    
    @Builder.Default
    private Map<String, Object> metadata = new java.util.HashMap<>();
    
    /**
     * Get primary prediction
     */
    public Double getPrimaryPrediction() {
        return predictions != null && !predictions.isEmpty() 
            ? predictions.get(0) 
            : null;
    }
    
    /**
     * Check if high confidence
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }
}
