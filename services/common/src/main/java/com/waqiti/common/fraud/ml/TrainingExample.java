package com.waqiti.common.fraud.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a training example for machine learning models in fraud detection.
 * Contains features, labels, and metadata for supervised learning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingExample {
    
    /**
     * Unique identifier for the training example
     */
    private String id;
    
    /**
     * Feature values for this training example
     * Key: feature name, Value: feature value
     */
    private Map<String, Object> features;
    
    /**
     * Target label for supervised learning
     * 1.0 = fraud, 0.0 = not fraud
     */
    private Double label;
    
    /**
     * Weight of this example for training
     * Higher weights give more importance during training
     */
    @Builder.Default
    private double weight = 1.0;
    
    /**
     * Timestamp when this example was created
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Source of this training data
     */
    private String source;
    
    /**
     * Data quality score (0.0 to 1.0)
     */
    @Builder.Default
    private double qualityScore = 1.0;
    
    /**
     * Whether this example has been validated by experts
     */
    @Builder.Default
    private boolean validated = false;
    
    /**
     * Confidence in the label accuracy (0.0 to 1.0)
     */
    @Builder.Default
    private double labelConfidence = 1.0;
    
    /**
     * Additional metadata about this example
     */
    private Map<String, Object> metadata;
    
    /**
     * Dataset split assignment
     */
    private DatasetSplit split;
    
    /**
     * Transaction type for context
     */
    private String transactionType;
    
    /**
     * User segment for targeted training
     */
    private String userSegment;
    
    /**
     * Geographic region for regional models
     */
    private String region;
    
    /**
     * Time-based features for temporal patterns
     */
    private Map<String, Double> temporalFeatures;
    
    /**
     * Check if this is a positive fraud example
     */
    public boolean isFraudExample() {
        return label != null && label >= 0.5;
    }
    
    /**
     * Check if this example is suitable for training
     */
    public boolean isValidForTraining() {
        return features != null && 
               !features.isEmpty() &&
               label != null &&
               qualityScore >= 0.5 &&
               labelConfidence >= 0.7;
    }
    
    /**
     * Get feature value as double (with default)
     */
    public double getFeatureAsDouble(String featureName, double defaultValue) {
        Object value = features.get(featureName);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * Get feature value as string (with default)
     */
    public String getFeatureAsString(String featureName, String defaultValue) {
        Object value = features.get(featureName);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Check if feature exists
     */
    public boolean hasFeature(String featureName) {
        return features != null && features.containsKey(featureName);
    }
    
    /**
     * Get the age of this training example in days
     */
    public long getAgeInDays() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toDays();
    }
    
    /**
     * Check if this example is recent (within specified days)
     */
    public boolean isRecent(int maxDays) {
        return getAgeInDays() <= maxDays;
    }
    
    /**
     * Create a feature summary string for logging
     */
    public String getFeatureSummary() {
        if (features == null || features.isEmpty()) {
            return "No features";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Features: ");
        
        features.entrySet().stream()
                .limit(5) // Show first 5 features
                .forEach(entry -> {
                    summary.append(entry.getKey())
                           .append("=")
                           .append(entry.getValue())
                           .append(", ");
                });
        
        if (features.size() > 5) {
            summary.append("... (").append(features.size()).append(" total)");
        }
        
        return summary.toString();
    }
    
    /**
     * Create training summary for monitoring
     */
    public String getTrainingSummary() {
        return String.format(
            "ID: %s, Label: %.1f, Weight: %.2f, Quality: %.2f, " +
            "Confidence: %.2f, Features: %d, Age: %d days, Validated: %s",
            id, label, weight, qualityScore, labelConfidence,
            features != null ? features.size() : 0,
            getAgeInDays(), validated
        );
    }
    
    /**
     * Create a deep copy of this training example
     */
    public TrainingExample deepCopy() {
        return TrainingExample.builder()
                .id(this.id)
                .features(this.features != null ? Map.copyOf(this.features) : null)
                .label(this.label)
                .weight(this.weight)
                .timestamp(this.timestamp)
                .source(this.source)
                .qualityScore(this.qualityScore)
                .validated(this.validated)
                .labelConfidence(this.labelConfidence)
                .metadata(this.metadata != null ? Map.copyOf(this.metadata) : null)
                .split(this.split)
                .transactionType(this.transactionType)
                .userSegment(this.userSegment)
                .region(this.region)
                .temporalFeatures(this.temporalFeatures != null ? Map.copyOf(this.temporalFeatures) : null)
                .build();
    }
    
    /**
     * Validate data integrity
     */
    public boolean validate() {
        if (features == null || features.isEmpty()) {
            return false;
        }
        
        if (label == null || label < 0.0 || label > 1.0) {
            return false;
        }
        
        if (weight < 0.0) {
            return false;
        }
        
        if (qualityScore < 0.0 || qualityScore > 1.0) {
            return false;
        }
        
        if (labelConfidence < 0.0 || labelConfidence > 1.0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Dataset split enumeration
     */
    public enum DatasetSplit {
        TRAIN, VALIDATION, TEST, HOLDOUT
    }
}