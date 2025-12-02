package com.waqiti.frauddetection.integration.pytorch;

import com.waqiti.frauddetection.dto.ml.PyTorchPredictionResponse;
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
 * Production-ready PyTorch serving client
 * Handles communication with PyTorch TorchServe endpoints
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PyTorchServingClient {

    private final RestTemplate restTemplate;

    @Value("${fraud-detection.pytorch.serving-url:http://localhost:8080}")
    private String servingUrl;

    @Value("${fraud-detection.pytorch.timeout:5000}")
    private int timeoutMs;

    @Value("${fraud-detection.pytorch.model-name:fraud_detection}")
    private String defaultModelName;

    /**
     * Make prediction using PyTorch model
     */
    public PyTorchPredictionResponse predict(Map<String, Object> features) {
        return predict(defaultModelName, features);
    }

    /**
     * Make prediction using specific model version
     */
    public PyTorchPredictionResponse predict(String modelName, Map<String, Object> features) {
        long startTime = System.currentTimeMillis();
        String predictionId = UUID.randomUUID().toString();

        try {
            log.debug("Making PyTorch prediction with model: {}, predictionId: {}", modelName, predictionId);

            // Prepare request
            String url = String.format("%s/predictions/%s", servingUrl, modelName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Prediction-Id", predictionId);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("instances", List.of(features));

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
                log.warn("Unexpected response from PyTorch serving: {}", response.getStatusCode());
                return createErrorResponse(predictionId, modelName, "UNEXPECTED_RESPONSE", latency);
            }

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Error making PyTorch prediction: {}", e.getMessage(), e);
            return createErrorResponse(predictionId, modelName, e.getMessage(), latency);
        }
    }

    /**
     * Parse PyTorch serving response into standard format
     */
    private PyTorchPredictionResponse parsePredictionResponse(
            Map<String, Object> responseBody,
            String modelName,
            String predictionId,
            long latency) {

        try {
            // Extract predictions
            List<Map<String, Object>> predictions = (List<Map<String, Object>>) responseBody.get("predictions");
            if (predictions == null || predictions.isEmpty()) {
                return createErrorResponse(predictionId, modelName, "NO_PREDICTIONS", latency);
            }

            Map<String, Object> prediction = predictions.get(0);

            // Extract scores and probabilities
            Double fraudProb = extractFraudProbability(prediction);
            String predictedClass = fraudProb > 0.5 ? "FRAUD" : "LEGITIMATE";

            Map<String, Double> classProbabilities = new HashMap<>();
            classProbabilities.put("FRAUD", fraudProb);
            classProbabilities.put("LEGITIMATE", 1.0 - fraudProb);

            // Build response
            return PyTorchPredictionResponse.builder()
                    .predictionId(predictionId)
                    .modelName(modelName)
                    .modelVersion(extractModelVersion(responseBody))
                    .predictedClass(predictedClass)
                    .fraudProbability(fraudProb)
                    .confidenceScore(Math.max(fraudProb, 1.0 - fraudProb))
                    .classProbabilities(classProbabilities)
                    .rawScores(extractRawScores(prediction))
                    .featureImportance(extractFeatureImportance(prediction))
                    .inferenceLatencyMs(latency)
                    .predictedAt(LocalDateTime.now())
                    .metadata(extractMetadata(responseBody))
                    .isReliable(true)
                    .modelHealthStatus("HEALTHY")
                    .build();

        } catch (Exception e) {
            log.error("Error parsing PyTorch response: {}", e.getMessage(), e);
            return createErrorResponse(predictionId, modelName, "PARSE_ERROR: " + e.getMessage(), latency);
        }
    }

    /**
     * Extract fraud probability from prediction
     */
    private Double extractFraudProbability(Map<String, Object> prediction) {
        // Try multiple common formats
        if (prediction.containsKey("fraud_probability")) {
            return ((Number) prediction.get("fraud_probability")).doubleValue();
        }
        if (prediction.containsKey("probability")) {
            return ((Number) prediction.get("probability")).doubleValue();
        }
        if (prediction.containsKey("score")) {
            return ((Number) prediction.get("score")).doubleValue();
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
     * Extract raw prediction scores
     */
    private List<Double> extractRawScores(Map<String, Object> prediction) {
        if (prediction.containsKey("scores")) {
            return (List<Double>) prediction.get("scores");
        }
        return List.of();
    }

    /**
     * Extract feature importance
     */
    private Map<String, Double> extractFeatureImportance(Map<String, Object> prediction) {
        if (prediction.containsKey("feature_importance")) {
            return (Map<String, Double>) prediction.get("feature_importance");
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
     * Create error response
     */
    private PyTorchPredictionResponse createErrorResponse(
            String predictionId,
            String modelName,
            String error,
            long latency) {

        return PyTorchPredictionResponse.builder()
                .predictionId(predictionId)
                .modelName(modelName)
                .predictedClass("UNKNOWN")
                .fraudProbability(0.5)
                .confidenceScore(0.0)
                .inferenceLatencyMs(latency)
                .predictedAt(LocalDateTime.now())
                .isReliable(false)
                .warnings(List.of(error))
                .modelHealthStatus("ERROR")
                .build();
    }

    /**
     * Check model health
     */
    public boolean isHealthy() {
        try {
            String url = servingUrl + "/ping";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("PyTorch serving health check failed: {}", e.getMessage());
            return false;
        }
    }
}
