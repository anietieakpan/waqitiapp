package com.waqiti.payment.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ML Fraud Prediction Result
 *
 * Contains the fraud risk prediction from the ML model along with
 * confidence metrics and performance tracking.
 *
 * @author Waqiti ML Engineering Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLFraudPrediction {

    /**
     * Transaction ID that was scored
     */
    private String transactionId;

    /**
     * Risk score from ML model (0.0 to 1.0)
     * - 0.0-0.3: LOW risk
     * - 0.3-0.6: MEDIUM risk
     * - 0.6-0.8: HIGH risk
     * - 0.8-1.0: CRITICAL risk
     */
    private Double riskScore;

    /**
     * Model confidence in the prediction (0.0 to 1.0)
     * Higher confidence means more reliable prediction
     */
    private Double confidence;

    /**
     * Risk level categorization
     */
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    /**
     * ML model version used for prediction
     */
    private String modelVersion;

    /**
     * Time taken for ML inference (milliseconds)
     */
    private Long inferenceDurationMs;

    /**
     * Time taken for feature engineering (milliseconds)
     */
    private Long featureEngineeringDurationMs;

    /**
     * Total prediction time including feature extraction (milliseconds)
     */
    private Long totalDurationMs;

    /**
     * Whether fallback rule-based scoring was used
     */
    private Boolean fallbackUsed;

    /**
     * Reason for fallback (if applicable)
     */
    private String fallbackReason;

    /**
     * Number of features used in prediction
     */
    private Integer featureCount;

    /**
     * Additional metadata
     */
    private java.util.Map<String, Object> metadata;
}
