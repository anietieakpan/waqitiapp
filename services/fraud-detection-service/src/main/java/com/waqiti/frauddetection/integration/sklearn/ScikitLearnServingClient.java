package com.waqiti.frauddetection.integration.sklearn;

import com.waqiti.frauddetection.dto.ml.ScikitLearnPredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Production-ready Scikit-Learn serving client
 * Handles communication with Scikit-Learn Flask/FastAPI serving endpoints
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScikitLearnServingClient {

    private final RestTemplate restTemplate;

    @Value("${fraud-detection.sklearn.serving-url:http://localhost:5000}")
    private String servingUrl;

    @Value("${fraud-detection.sklearn.timeout:5000}")
    private int timeoutMs;

    @Value("${fraud-detection.sklearn.model-name:fraud_detector}")
    private String defaultModelName;

    /**
     * Make prediction using Scikit-Learn model
     */
    public ScikitLearnPredictionResponse predict(Map<String, Object> features) {
        return predict(defaultModelName, features);
    }

    /**
     * Make prediction using specific model version
     */
    public ScikitLearnPredictionResponse predict(String modelName, Map<String, Object> features) {
        long startTime = System.currentTimeMillis();
        String predictionId = UUID.randomUUID().toString();

        try {
            log.debug("Making Scikit-Learn prediction with model: {}, predictionId: {}", modelName, predictionId);

            // Prepare request
            String url = String.format("%s/predict", servingUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Prediction-Id", predictionId);
            headers.set("X-Model-Name", modelName);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("features", features);
            requestBody.put("model_name", modelName);
            requestBody.put("explain", true); // Request feature importance

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Make prediction
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Map.class
            );

            long latency = System.currentTimeMillis() - startTime;

            // Parse response
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parsePredictionResponse(response.getBody(), modelName, predictionId, latency);
            } else {
                log.warn("Unexpected response from Scikit-Learn serving: {}", response.getStatusCode());
                return createErrorResponse(predictionId, modelName, "UNEXPECTED_RESPONSE", latency);
            }

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Error making Scikit-Learn prediction: {}", e.getMessage(), e);
            return createErrorResponse(predictionId, modelName, e.getMessage(), latency);
        }
    }

    /**
     * Parse Scikit-Learn serving response into standard format
     */
    private ScikitLearnPredictionResponse parsePredictionResponse(
            Map<String, Object> responseBody,
            String modelName,
            String predictionId,
            long latency) {

        try {
            // Extract prediction
            Double fraudProb = extractFraudProbability(responseBody);
            String predictedClass = fraudProb > 0.5 ? "FRAUD" : "LEGITIMATE";

            Map<String, Double> classProbabilities = new HashMap<>();
            classProbabilities.put("FRAUD", fraudProb);
            classProbabilities.put("LEGITIMATE", 1.0 - fraudProb);

            // Build response
            return ScikitLearnPredictionResponse.builder()
                    .predictionId(predictionId)
                    .modelName(modelName)
                    .modelVersion(extractModelVersion(responseBody))
                    .algorithm(extractAlgorithm(responseBody))
                    .predictedClass(predictedClass)
                    .fraudProbability(fraudProb)
                    .confidenceScore(Math.max(fraudProb, 1.0 - fraudProb))
                    .classProbabilities(classProbabilities)
                    .decisionScores(extractDecisionScores(responseBody))
                    .featureImportance(extractFeatureImportance(responseBody))
                    .shapValues(extractShapValues(responseBody))
                    .inferenceLatencyMs(latency)
                    .predictedAt(LocalDateTime.now())
                    .metadata(extractMetadata(responseBody))
                    .performanceSnapshot(extractPerformanceSnapshot(responseBody))
                    .isReliable(true)
                    .calibrationScore(extractCalibrationScore(responseBody))
                    .build();

        } catch (Exception e) {
            log.error("Error parsing Scikit-Learn response: {}", e.getMessage(), e);
            return createErrorResponse(predictionId, modelName, "PARSE_ERROR: " + e.getMessage(), latency);
        }
    }

    /**
     * Extract fraud probability from prediction
     */
    private Double extractFraudProbability(Map<String, Object> response) {
        // Try multiple common formats
        if (response.containsKey("fraud_probability")) {
            return ((Number) response.get("fraud_probability")).doubleValue();
        }
        if (response.containsKey("prediction_proba")) {
            List<Double> probas = (List<Double>) response.get("prediction_proba");
            if (probas != null && probas.size() > 1) {
                return probas.get(1); // Assuming fraud is class 1
            }
        }
        if (response.containsKey("probability")) {
            return ((Number) response.get("probability")).doubleValue();
        }
        if (response.containsKey("score")) {
            return ((Number) response.get("score")).doubleValue();
        }
        // Default to neutral
        return 0.5;
    }

    /**
     * Extract model version
     */
    private String extractModelVersion(Map<String, Object> response) {
        if (response.containsKey("model_version")) {
            return response.get("model_version").toString();
        }
        return "unknown";
    }

    /**
     * Extract algorithm name
     */
    private String extractAlgorithm(Map<String, Object> response) {
        if (response.containsKey("algorithm")) {
            return response.get("algorithm").toString();
        }
        if (response.containsKey("model_type")) {
            return response.get("model_type").toString();
        }
        return "unknown";
    }

    /**
     * Extract decision function scores
     */
    private List<Double> extractDecisionScores(Map<String, Object> response) {
        if (response.containsKey("decision_function")) {
            return (List<Double>) response.get("decision_function");
        }
        return List.of();
    }

    /**
     * Extract feature importance
     */
    private Map<String, Double> extractFeatureImportance(Map<String, Object> response) {
        if (response.containsKey("feature_importance")) {
            return (Map<String, Double>) response.get("feature_importance");
        }
        return new HashMap<>();
    }

    /**
     * Extract SHAP values for explainability
     */
    private Map<String, Double> extractShapValues(Map<String, Object> response) {
        if (response.containsKey("shap_values")) {
            return (Map<String, Double>) response.get("shap_values");
        }
        return new HashMap<>();
    }

    /**
     * Extract additional metadata
     */
    private Map<String, Object> extractMetadata(Map<String, Object> response) {
        Map<String, Object> metadata = new HashMap<>();
        if (response.containsKey("metadata")) {
            metadata.putAll((Map<String, Object>) response.get("metadata"));
        }
        return metadata;
    }

    /**
     * Extract model performance snapshot
     */
    private ScikitLearnPredictionResponse.ModelPerformanceSnapshot extractPerformanceSnapshot(
            Map<String, Object> response) {
        if (response.containsKey("performance")) {
            Map<String, Object> perf = (Map<String, Object>) response.get("performance");
            return ScikitLearnPredictionResponse.ModelPerformanceSnapshot.builder()
                    .accuracy(getDoubleOrNull(perf, "accuracy"))
                    .precision(getDoubleOrNull(perf, "precision"))
                    .recall(getDoubleOrNull(perf, "recall"))
                    .f1Score(getDoubleOrNull(perf, "f1_score"))
                    .auc(getDoubleOrNull(perf, "auc"))
                    .build();
        }
        return null;
    }

    /**
     * Extract calibration score
     */
    private Double extractCalibrationScore(Map<String, Object> response) {
        if (response.containsKey("calibration_score")) {
            return ((Number) response.get("calibration_score")).doubleValue();
        }
        return null;
    }

    /**
     * Helper to safely extract double from map
     */
    private Double getDoubleOrNull(Map<String, Object> map, String key) {
        if (map.containsKey(key) && map.get(key) != null) {
            return ((Number) map.get(key)).doubleValue();
        }
        return null;
    }

    /**
     * Create error response
     */
    private ScikitLearnPredictionResponse createErrorResponse(
            String predictionId,
            String modelName,
            String error,
            long latency) {

        return ScikitLearnPredictionResponse.builder()
                .predictionId(predictionId)
                .modelName(modelName)
                .algorithm("unknown")
                .predictedClass("UNKNOWN")
                .fraudProbability(0.5)
                .confidenceScore(0.0)
                .inferenceLatencyMs(latency)
                .predictedAt(LocalDateTime.now())
                .isReliable(false)
                .warnings(List.of(error))
                .build();
    }

    /**
     * Check model health
     */
    public boolean isHealthy() {
        try {
            String url = servingUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("Scikit-Learn serving health check failed: {}", e.getMessage());
            return false;
        }
    }
}
