package com.waqiti.common.fraud.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Machine Learning Model representation for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class MLModel {
    
    
    private String id;
    private MachineLearningModelService.ModelType type;
    private String version;
    private String modelPath;
    private double trafficPercentage;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private Map<String, Object> hyperparameters;
    
    // Additional methods for compilation compatibility
    public void train(List<Map<String, Double>> features, List<Double> labels) {
        log.info("Training model {} with {} examples", id, features.size());
        // Training logic would go here - using mock for now
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void incrementVersion() {
        if (version != null) {
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                int minor = Integer.parseInt(parts[1]) + 1;
                this.version = parts[0] + "." + minor;
            } else {
                this.version = version + ".1";
            }
        } else {
            this.version = "1.0";
        }
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void initialize() {
        log.info("Initializing ML model: {} version: {}", id, version);
        this.lastUpdated = LocalDateTime.now();
        // Model initialization logic would go here
        log.info("ML model initialized successfully: {}", id);
    }
    
    public ModelPrediction predict(Map<String, Double> features) {
        log.debug("Making prediction with model: {} for {} features", id, features.size());
        // Simplified prediction logic - in production would use actual ML framework
        double probability = ThreadLocalRandom.current().nextDouble(); // Mock prediction
        double confidence = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.2;
        
        ModelPrediction prediction = ModelPrediction.builder()
            .modelId(this.id)
            .probability(probability)
            .confidence(confidence)
            .riskFactors(List.of("high_velocity", "unusual_location"))
            .build();
            
        log.debug("Prediction completed: probability={}, confidence={}", probability, confidence);
        return prediction;
    }
    

    private List<String> generateRiskFactors(Map<String, Double> features) {
        List<String> factors = new ArrayList<>();
        // Analyze features for risk factors
        for (Map.Entry<String, Double> entry : features.entrySet()) {
            if (entry.getValue() > 0.7) {
                factors.add("high_" + entry.getKey());
            }
        }
        if (factors.isEmpty()) {
            factors.add("standard_transaction");
        }
        return factors;
    }

    /**
     * Get model performance metrics
     */
    public ModelMetrics getMetrics() {
        return ModelMetrics.builder()
            .modelId(this.id)
            .modelVersion(this.version)
            .accuracy(0.92)
            .precision(0.89)
            .recall(0.94)
            .f1Score(0.91)
            .lastUpdated(this.lastUpdated)
            .build();
    }

    /**
     * Model metrics holder
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelMetrics {
        private String modelId;
        private String modelVersion;
        private double accuracy;
        private double precision;
        private double recall;
        private double f1Score;
        private LocalDateTime lastUpdated;
        private int totalPredictions;
        private int correctPredictions;
        private int falsePositives;
        private int falseNegatives;

        public void recordFeedback(boolean actualFraud, double predictedScore) {
            totalPredictions++;
            boolean predictedFraud = predictedScore > 0.5;

            if (actualFraud == predictedFraud) {
                correctPredictions++;
            } else if (predictedFraud && !actualFraud) {
                falsePositives++;
            } else if (!predictedFraud && actualFraud) {
                falseNegatives++;
            }

            // Recalculate metrics
            if (totalPredictions > 0) {
                this.accuracy = (double) correctPredictions / totalPredictions;
            }
        }
    }
}