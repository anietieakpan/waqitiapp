package com.waqiti.ml.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Feature Engineering Service for ML Models
 *
 * Transforms raw data into features suitable for machine learning models.
 *
 * @author Waqiti ML Team
 * @version 1.0.0
 * @since 2025-10-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FeatureEngineeringService {

    /**
     * Extract features from transaction data
     */
    public Map<String, Double> extractTransactionFeatures(Map<String, Object> transactionData) {
        log.debug("Extracting transaction features from data");

        Map<String, Double> features = new HashMap<>();

        try {
            // Extract numerical features
            features.put("amount", getDoubleValue(transactionData, "amount"));
            features.put("hour_of_day", getDoubleValue(transactionData, "hourOfDay"));
            features.put("day_of_week", getDoubleValue(transactionData, "dayOfWeek"));
            features.put("merchant_category", getDoubleValue(transactionData, "merchantCategory"));

            // Calculate derived features
            features.put("amount_deviation", calculateAmountDeviation(transactionData));
            features.put("frequency_score", calculateFrequencyScore(transactionData));
            features.put("velocity_score", calculateVelocityScore(transactionData));

            log.debug("Extracted {} features from transaction data", features.size());
            return features;

        } catch (Exception e) {
            log.error("Error extracting transaction features: {}", e.getMessage(), e);
            return features; // Return partial features
        }
    }

    /**
     * Extract features from user behavior data
     */
    public Map<String, Double> extractBehaviorFeatures(Map<String, Object> behaviorData) {
        log.debug("Extracting behavior features from data");

        Map<String, Double> features = new HashMap<>();

        try {
            features.put("session_duration", getDoubleValue(behaviorData, "sessionDuration"));
            features.put("page_views", getDoubleValue(behaviorData, "pageViews"));
            features.put("actions_count", getDoubleValue(behaviorData, "actionsCount"));
            features.put("error_rate", getDoubleValue(behaviorData, "errorRate"));

            log.debug("Extracted {} behavior features", features.size());
            return features;

        } catch (Exception e) {
            log.error("Error extracting behavior features: {}", e.getMessage(), e);
            return features;
        }
    }

    private double getDoubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double calculateAmountDeviation(Map<String, Object> data) {
        // Placeholder - would calculate deviation from user's average
        return 0.5;
    }

    private double calculateFrequencyScore(Map<String, Object> data) {
        // Placeholder - would calculate transaction frequency
        return 0.3;
    }

    private double calculateVelocityScore(Map<String, Object> data) {
        // Placeholder - would calculate transaction velocity
        return 0.4;
    }
}
