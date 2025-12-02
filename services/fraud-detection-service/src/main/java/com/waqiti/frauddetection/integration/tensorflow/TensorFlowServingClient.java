package com.waqiti.frauddetection.integration.tensorflow;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * TensorFlow Serving REST API Client
 * 
 * Provides integration with TensorFlow Serving via REST API.
 * In production, this would typically use gRPC for better performance.
 * 
 * @author Waqiti ML Team
 * @since 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class TensorFlowServingClient {
    
    private final String host;
    private final int port;
    private RestTemplate restTemplate;
    private String baseUrl;
    
    public void initialize() {
        log.info("Initializing TensorFlow Serving client: {}:{}", host, port);
        
        restTemplate = new RestTemplate();
        baseUrl = String.format("http://%s:%d/v1/models", host, port);
        
        log.info("TensorFlow Serving client initialized");
    }
    
    /**
     * Make prediction request to TensorFlow Serving
     * Protected with circuit breaker for ML model resilience
     */
    @CircuitBreaker(name = "tensorflow-serving", fallbackMethod = "predictFallback")
    @Retry(name = "tensorflow-serving")
    @RateLimiter(name = "tensorflow-serving")
    @Bulkhead(name = "tensorflow-serving")
    public TensorFlowPredictionResponse predict(String modelName, String version, 
                                              float[][] inputs, int timeoutMs) {
        String url = String.format("%s/%s/versions/%s:predict", baseUrl, modelName, version);
        
        try {
            // Prepare request payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("signature_name", "serving_default");
            requestBody.put("inputs", Map.of("features", inputs));
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            log.debug("Sending prediction request to TensorFlow Serving: {}", url);
            
            // Make REST call
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                requestEntity, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseResponse(response.getBody());
            } else {
                throw new TensorFlowServingException("Unexpected response: " + response.getStatusCode());
            }
            
        } catch (ResourceAccessException e) {
            log.error("TensorFlow Serving connection failed: {}", url, e);
            throw new TensorFlowServingException("Connection to TensorFlow Serving failed", e);
        } catch (Exception e) {
            log.error("TensorFlow Serving prediction failed: {}", url, e);
            throw new TensorFlowServingException("Prediction request failed", e);
        }
    }
    
    /**
     * Batch prediction request
     * Protected with circuit breaker for batch processing resilience
     */
    @CircuitBreaker(name = "tensorflow-serving", fallbackMethod = "batchPredictFallback")
    @Retry(name = "tensorflow-serving")
    @RateLimiter(name = "tensorflow-serving")
    @Bulkhead(name = "tensorflow-serving")
    public TensorFlowPredictionResponse batchPredict(String modelName, String version, 
                                                   float[][] batchInputs, int timeoutMs) {
        return predict(modelName, version, batchInputs, timeoutMs);
    }
    
    /**
     * Check TensorFlow Serving health
     * Protected with circuit breaker for health monitoring resilience
     */
    @CircuitBreaker(name = "tensorflow-serving", fallbackMethod = "healthCheckFallback")
    @Retry(name = "tensorflow-serving")
    public boolean healthCheck() {
        try {
            String url = String.format("http://%s:%d/v1/models", host, port);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (Exception e) {
            log.error("TensorFlow Serving health check failed", e);
            return false;
        }
    }
    
    /**
     * Check if specific model is available
     * Protected with circuit breaker for model availability checks
     */
    @CircuitBreaker(name = "tensorflow-serving", fallbackMethod = "isModelAvailableFallback")
    @Retry(name = "tensorflow-serving")
    public boolean isModelAvailable(String modelName, String version) {
        try {
            String url = String.format("%s/%s/versions/%s/metadata", baseUrl, modelName, version);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (Exception e) {
            log.debug("Model not available: {} version {}", modelName, version);
            return false;
        }
    }
    
    /**
     * Register new model with serving (placeholder)
     */
    public void registerModel(String modelName, String version, String modelPath) {
        log.info("Registering model with TensorFlow Serving: {} version {} at {}", 
                modelName, version, modelPath);
        
        // In production, this would use TensorFlow Serving model config API
        // or model management service
    }
    
    /**
     * Update model version (placeholder)
     */
    public void updateModelVersion(String modelName, String version) {
        log.info("Updating model version in TensorFlow Serving: {} to version {}", 
                modelName, version);
        
        // In production, this would update the model serving configuration
    }
    
    private TensorFlowPredictionResponse parseResponse(Map<String, Object> responseBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> outputs = (Map<String, Object>) responseBody.get("outputs");
            
            Map<String, float[][]> parsedOutputs = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                String outputName = entry.getKey();
                Object outputValue = entry.getValue();
                
                // Parse output tensor (simplified - would need proper tensor parsing in production)
                if (outputValue instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.List<Double>> listOutput = 
                        (java.util.List<java.util.List<Double>>) outputValue;
                    
                    float[][] tensorOutput = new float[listOutput.size()][];
                    for (int i = 0; i < listOutput.size(); i++) {
                        java.util.List<Double> row = listOutput.get(i);
                        tensorOutput[i] = new float[row.size()];
                        for (int j = 0; j < row.size(); j++) {
                            tensorOutput[i][j] = row.get(j).floatValue();
                        }
                    }
                    
                    parsedOutputs.put(outputName, tensorOutput);
                }
            }
            
            return TensorFlowPredictionResponse.builder()
                .outputs(parsedOutputs)
                .successful(true)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to parse TensorFlow Serving response", e);
            throw new TensorFlowServingException("Failed to parse response", e);
        }
    }
    
    public static class TensorFlowServingException extends RuntimeException {
        public TensorFlowServingException(String message) {
            super(message);
        }
        
        public TensorFlowServingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Circuit Breaker Fallback Methods
    
    /**
     * Fallback method for predict when circuit breaker is open
     * Returns conservative fraud predictions when ML service is unavailable
     */
    public TensorFlowPredictionResponse predictFallback(String modelName, String version, 
                                                       float[][] inputs, int timeoutMs, Exception ex) {
        log.error("TENSORFLOW_CIRCUIT_BREAKER: Prediction circuit breaker activated for model: {} v{}", 
            modelName, version, ex);
        
        // Return conservative predictions when ML service is unavailable
        // In production, could use simpler rule-based fallback or cached model
        float[][] fallbackOutputs = new float[inputs.length][1];
        for (int i = 0; i < inputs.length; i++) {
            // Conservative: flag as potentially fraudulent if any risk indicators present
            fallbackOutputs[i][0] = 0.7f; // Medium-high risk score as default
        }
        
        Map<String, float[][]> outputs = new HashMap<>();
        outputs.put("fraud_probability", fallbackOutputs);
        outputs.put("confidence", new float[][]{{0.3f}}); // Low confidence in fallback
        
        return TensorFlowPredictionResponse.builder()
            .outputs(outputs)
            .successful(false)
            .fallbackUsed(true)
            .message("ML service unavailable - using conservative fallback")
            .build();
    }
    
    /**
     * Fallback method for batchPredict when circuit breaker is open
     */
    public TensorFlowPredictionResponse batchPredictFallback(String modelName, String version, 
                                                            float[][] batchInputs, int timeoutMs, Exception ex) {
        log.error("TENSORFLOW_CIRCUIT_BREAKER: Batch prediction circuit breaker activated for model: {} v{}", 
            modelName, version, ex);
        
        // Use same conservative approach for batch predictions
        return predictFallback(modelName, version, batchInputs, timeoutMs, ex);
    }
    
    /**
     * Fallback method for healthCheck when circuit breaker is open
     */
    public boolean healthCheckFallback(Exception ex) {
        log.error("TENSORFLOW_CIRCUIT_BREAKER: Health check circuit breaker activated", ex);
        // Return false to indicate service is unhealthy
        return false;
    }
    
    /**
     * Fallback method for isModelAvailable when circuit breaker is open
     */
    public boolean isModelAvailableFallback(String modelName, String version, Exception ex) {
        log.error("TENSORFLOW_CIRCUIT_BREAKER: Model availability check circuit breaker activated for: {} v{}", 
            modelName, version, ex);
        // Return false to indicate model is not available
        return false;
    }
}