package com.waqiti.frauddetection.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Feature vector for machine learning models.
 * Contains extracted features for fraud detection prediction.
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureVector {

    /**
     * Transaction identifier
     */
    private String transactionId;

    /**
     * User identifier
     */
    private String userId;

    /**
     * Feature map containing feature name to value mappings
     * Features are normalized to 0.0-1.0 range for ML model input
     */
    private Map<String, Double> featureMap;

    /**
     * Timestamp when features were extracted
     */
    private LocalDateTime extractedAt;

    /**
     * Version of feature extraction algorithm
     */
    @Builder.Default
    private String featureVersion = "1.0";

    /**
     * Number of features in the vector
     */
    public int getFeatureCount() {
        return featureMap != null ? featureMap.size() : 0;
    }

    /**
     * Get feature value by name
     */
    public Double getFeature(String featureName) {
        return featureMap != null ? featureMap.get(featureName) : null;
    }
}
