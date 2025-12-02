package com.waqiti.payment.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ML Prediction Request
 *
 * Request payload sent to ML service for fraud prediction.
 *
 * @author Waqiti ML Engineering Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionRequest {

    /**
     * Unique request ID for tracking
     */
    private String requestId;

    /**
     * Transaction ID being scored
     */
    private String transactionId;

    /**
     * User ID associated with transaction
     */
    private String userId;

    /**
     * ML model name to use
     */
    private String modelName; // e.g., "fraud_detection"

    /**
     * ML model version to use
     */
    private String modelVersion; // e.g., "v2.0"

    /**
     * Feature vector for prediction
     */
    private MLFeatureVector features;

    /**
     * Request timestamp
     */
    private LocalDateTime timestamp;

    /**
     * Timeout for prediction (milliseconds)
     */
    private Integer timeout;

    /**
     * Additional request metadata
     */
    private Map<String, Object> metadata;
}
