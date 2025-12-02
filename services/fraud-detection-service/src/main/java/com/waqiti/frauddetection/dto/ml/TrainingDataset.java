package com.waqiti.frauddetection.dto.ml;

import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Training Dataset DTO
 *
 * Represents a complete dataset for ML model training.
 * Includes training, validation, and test splits with metadata.
 *
 * PRODUCTION-GRADE DTO
 * - Train/val/test splits
 * - Class distribution tracking
 * - Data quality metrics
 * - Versioning support
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingDataset {

    @NotNull
    private String datasetId;

    @NotNull
    private String datasetName;

    private String version;

    /**
     * Data splits
     */
    @Builder.Default
    private List<TrainingExample> trainingExamples = new ArrayList<>();

    @Builder.Default
    private List<TrainingExample> validationExamples = new ArrayList<>();

    @Builder.Default
    private List<TrainingExample> testExamples = new ArrayList<>();

    /**
     * Dataset statistics
     */
    private Integer totalExamples;
    private Integer trainingSize;
    private Integer validationSize;
    private Integer testSize;

    /**
     * Class distribution
     */
    private Integer fraudCount;
    private Integer legitimateCount;
    private Double fraudRate;

    /**
     * Feature information
     */
    @Builder.Default
    private List<String> featureNames = new ArrayList<>();

    private Integer featureCount;

    /**
     * Data quality metrics
     */
    private Double missingValueRate;
    private Double duplicateRate;
    private Map<String, Double> featureCorrelations;

    /**
     * Dataset metadata
     */
    private LocalDateTime createdAt;
    private LocalDateTime dataStartDate;
    private LocalDateTime dataEndDate;
    private String createdBy;
    private String source; // "production", "synthetic", "combined"

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Get class imbalance ratio
     */
    public Double getClassImbalanceRatio() {
        if (fraudCount == null || legitimateCount == null || legitimateCount == 0) {
            return null;
        }
        return (double) fraudCount / legitimateCount;
    }

    /**
     * Check if dataset is balanced
     */
    public boolean isBalanced() {
        Double ratio = getClassImbalanceRatio();
        if (ratio == null) {
            return false;
        }
        // Consider balanced if ratio between 0.8 and 1.2
        return ratio >= 0.8 && ratio <= 1.2;
    }

    /**
     * Get all examples
     */
    public List<TrainingExample> getAllExamples() {
        List<TrainingExample> all = new ArrayList<>();
        if (trainingExamples != null) all.addAll(trainingExamples);
        if (validationExamples != null) all.addAll(validationExamples);
        if (testExamples != null) all.addAll(testExamples);
        return all;
    }
}
