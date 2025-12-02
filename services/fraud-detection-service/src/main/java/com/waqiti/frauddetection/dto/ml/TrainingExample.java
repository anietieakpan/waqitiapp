package com.waqiti.frauddetection.dto.ml;

import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Training Example DTO
 *
 * Represents a single labeled training example for ML model training.
 * Contains features, ground truth label, and metadata.
 *
 * PRODUCTION-GRADE DTO
 * - Complete feature vector
 * - Ground truth labels
 * - Sample weights for class imbalance
 * - Metadata for filtering/stratification
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingExample {

    @NotNull
    private String exampleId;

    @NotNull
    private FeatureVector features;

    /**
     * Ground truth label
     */
    @NotNull
    private Boolean isFraud;

    /**
     * Label confidence (for semi-supervised learning)
     */
    private Double labelConfidence;

    /**
     * Sample weight (for handling class imbalance)
     */
    @Builder.Default
    private Double sampleWeight = 1.0;

    /**
     * Data split assignment
     */
    private String dataSplit; // "train", "validation", "test"

    /**
     * Metadata
     */
    private LocalDateTime createdAt;
    private String source; // "production", "synthetic", "historical"

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public boolean isPositiveExample() {
        return Boolean.TRUE.equals(isFraud);
    }

    public boolean isNegativeExample() {
        return Boolean.FALSE.equals(isFraud);
    }
}
