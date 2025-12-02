package com.waqiti.ml.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * ML-based Anomaly Detection Service
 *
 * Provides machine learning-based anomaly detection capabilities
 * for transaction patterns, user behavior, and system metrics.
 *
 * @author Waqiti ML Team
 * @version 1.0.0
 * @since 2025-10-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MLAnomalyDetectionService {

    private final ModelInferenceService modelInferenceService;

    /**
     * Detect anomalies in transaction data
     */
    public AnomalyDetectionResult detectTransactionAnomaly(Map<String, Object> transactionData) {
        log.debug("Detecting transaction anomaly for data: {}", transactionData);

        try {
            // Use ML model to detect anomalies
            double anomalyScore = modelInferenceService.predict("transaction-anomaly-model", transactionData);

            boolean isAnomalous = anomalyScore > 0.7; // 70% threshold
            String severity = calculateSeverity(anomalyScore);

            return AnomalyDetectionResult.builder()
                .isAnomalous(isAnomalous)
                .anomalyScore(anomalyScore)
                .severity(severity)
                .detectionMethod("ML_MODEL")
                .modelVersion("1.0.0")
                .build();

        } catch (Exception e) {
            log.error("Error detecting transaction anomaly: {}", e.getMessage(), e);
            return AnomalyDetectionResult.builder()
                .isAnomalous(false)
                .anomalyScore(0.0)
                .severity("UNKNOWN")
                .detectionMethod("ERROR")
                .build();
        }
    }

    /**
     * Detect anomalies in user behavior
     */
    public AnomalyDetectionResult detectBehaviorAnomaly(String userId, Map<String, Object> behaviorData) {
        log.debug("Detecting behavior anomaly for user: {}", userId);

        try {
            double anomalyScore = modelInferenceService.predict("behavior-anomaly-model", behaviorData);

            boolean isAnomalous = anomalyScore > 0.65; // 65% threshold for behavior
            String severity = calculateSeverity(anomalyScore);

            return AnomalyDetectionResult.builder()
                .isAnomalous(isAnomalous)
                .anomalyScore(anomalyScore)
                .severity(severity)
                .detectionMethod("ML_BEHAVIOR_MODEL")
                .modelVersion("1.0.0")
                .build();

        } catch (Exception e) {
            log.error("Error detecting behavior anomaly: {}", e.getMessage(), e);
            return AnomalyDetectionResult.builder()
                .isAnomalous(false)
                .anomalyScore(0.0)
                .severity("UNKNOWN")
                .build();
        }
    }

    /**
     * Detect anomalies in system metrics
     */
    public AnomalyDetectionResult detectMetricAnomaly(String metricName, double metricValue,
                                                      Map<String, Object> context) {
        log.debug("Detecting metric anomaly for: {} = {}", metricName, metricValue);

        try {
            Map<String, Object> modelInput = Map.of(
                "metricName", metricName,
                "metricValue", metricValue,
                "context", context
            );

            double anomalyScore = modelInferenceService.predict("metric-anomaly-model", modelInput);

            boolean isAnomalous = anomalyScore > 0.75; // 75% threshold for metrics
            String severity = calculateSeverity(anomalyScore);

            return AnomalyDetectionResult.builder()
                .isAnomalous(isAnomalous)
                .anomalyScore(anomalyScore)
                .severity(severity)
                .detectionMethod("ML_METRIC_MODEL")
                .modelVersion("1.0.0")
                .build();

        } catch (Exception e) {
            log.error("Error detecting metric anomaly: {}", e.getMessage(), e);
            return AnomalyDetectionResult.builder()
                .isAnomalous(false)
                .anomalyScore(0.0)
                .severity("UNKNOWN")
                .build();
        }
    }

    private String calculateSeverity(double anomalyScore) {
        if (anomalyScore >= 0.9) return "CRITICAL";
        if (anomalyScore >= 0.75) return "HIGH";
        if (anomalyScore >= 0.6) return "MEDIUM";
        if (anomalyScore >= 0.4) return "LOW";
        return "MINIMAL";
    }

    @lombok.Data
    @lombok.Builder
    public static class AnomalyDetectionResult {
        private boolean isAnomalous;
        private double anomalyScore;
        private String severity;
        private String detectionMethod;
        private String modelVersion;
    }
}
