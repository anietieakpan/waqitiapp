package com.waqiti.common.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpStatusCodeException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionException;

/**
 * Executes individual saga steps by making HTTP calls to microservices.
 * Handles retries, timeouts, and result processing.
 */
@Service
@Slf4j
public class SagaStepExecutor {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SagaMetricsService metricsService;
    
    public SagaStepExecutor(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            SagaMetricsService metricsService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    /**
     * Execute a saga step asynchronously
     */
    public CompletableFuture<StepResult> executeStep(String sagaId, SagaStep step, Map<String, Object> sagaData) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Executing step: sagaId={}, stepId={}, endpoint={}", 
                sagaId, step.getStepId(), step.getServiceEndpoint());
            
            long startTime = System.currentTimeMillis();
            StepResult result = null;
            
            try {
                // Validate step
                step.validate();
                
                // Execute with retries
                result = executeWithRetries(sagaId, step, sagaData);
                
                // Record success metrics
                long duration = System.currentTimeMillis() - startTime;
                metricsService.recordStepExecution(sagaId, step.getStepId(), true, duration);
                
                log.info("Step completed successfully: sagaId={}, stepId={}, duration={}ms", 
                    sagaId, step.getStepId(), duration);
                
                return result;
                
            } catch (Exception e) {
                // Record failure metrics
                long duration = System.currentTimeMillis() - startTime;
                metricsService.recordStepExecution(sagaId, step.getStepId(), false, duration);
                
                log.error("Step execution failed: sagaId={}, stepId={}", sagaId, step.getStepId(), e);
                
                return StepResult.builder()
                    .stepId(step.getStepId())
                    .status(StepStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .executionTime(duration)
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Execute step with retry logic
     */
    private StepResult executeWithRetries(String sagaId, SagaStep step, Map<String, Object> sagaData) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts <= step.getMaxRetries()) {
            try {
                attempts++;
                
                if (attempts > 1) {
                    long delay = step.calculateRetryDelay(attempts - 1);
                    log.info("Retrying step after {}ms: sagaId={}, stepId={}, attempt={}", 
                        delay, sagaId, step.getStepId(), attempts);
                    
                    TimeUnit.MILLISECONDS.sleep(delay);
                }
                
                // Execute the actual HTTP call
                StepResult result = executeSingleAttempt(sagaId, step, sagaData, attempts);
                
                if (result.getStatus() == StepStatus.COMPLETED) {
                    return result;
                } else if (result.getStatus() == StepStatus.FAILED && !step.isIdempotent()) {
                    // Don't retry non-idempotent operations
                    return result;
                }
                
                lastException = new RuntimeException("Step failed: " + result.getErrorMessage());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Step execution interrupted", e);
            } catch (Exception e) {
                lastException = e;
                
                // Check if we should retry this exception
                if (!shouldRetry(e, step, attempts)) {
                    break;
                }
            }
        }
        
        // All retries exhausted
        String errorMessage = "Step failed after " + attempts + " attempts";
        if (lastException != null) {
            errorMessage += ": " + lastException.getMessage();
        }
        
        return StepResult.builder()
            .stepId(step.getStepId())
            .status(StepStatus.FAILED)
            .errorMessage(errorMessage)
            .retryCount(attempts - 1)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Execute a single attempt of the step
     */
    private StepResult executeSingleAttempt(String sagaId, SagaStep step, 
                                          Map<String, Object> sagaData, int attemptNumber) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Prepare request
            HttpEntity<Object> request = createHttpRequest(step, sagaData, sagaId);
            
            // Make HTTP call with timeout
            ResponseEntity<String> response = restTemplate.exchange(
                step.getServiceEndpoint(),
                HttpMethod.valueOf(step.getHttpMethod().toUpperCase()),
                request,
                String.class
            );
            
            // Process response
            return processResponse(step, response, startTime);
            
        } catch (HttpStatusCodeException e) {
            return handleHttpError(step, e, startTime);
        } catch (ResourceAccessException e) {
            return handleTimeoutError(step, e, startTime);
        } catch (Exception e) {
            return handleGeneralError(step, e, startTime);
        }
    }

    /**
     * Create HTTP request with headers and body
     */
    private HttpEntity<Object> createHttpRequest(SagaStep step, Map<String, Object> sagaData, String sagaId) {
        // Create headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Saga-ID", sagaId);
        headers.add("X-Step-ID", step.getStepId());
        headers.add("X-Request-ID", UUID.randomUUID().toString());
        
        // Add custom headers
        step.getHeaders().forEach(headers::add);
        
        // Prepare request body
        Map<String, Object> requestBody = new HashMap<>();
        
        // Add step parameters
        requestBody.putAll(step.getParameters());
        
        // Add relevant saga data (filtered for security)
        Map<String, Object> filteredSagaData = filterSagaData(sagaData, step);
        requestBody.putAll(filteredSagaData);
        
        // Add step metadata
        requestBody.put("_saga", Map.of(
            "sagaId", sagaId,
            "stepId", step.getStepId(),
            "stepType", step.getStepType()
        ));
        
        return new HttpEntity<>(requestBody, headers);
    }

    /**
     * Process successful HTTP response
     */
    private StepResult processResponse(SagaStep step, ResponseEntity<String> response, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        
        try {
            // Check if status code indicates success
            if (!step.isSuccessStatusCode(response.getStatusCodeValue())) {
                return StepResult.builder()
                    .stepId(step.getStepId())
                    .status(StepStatus.FAILED)
                    .errorMessage("Unexpected status code: " + response.getStatusCodeValue())
                    .executionTime(executionTime)
                    .timestamp(LocalDateTime.now())
                    .build();
            }
            
            // Parse response body
            Map<String, Object> responseData = new HashMap<>();
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                try {
                    responseData = objectMapper.readValue(response.getBody(), Map.class);
                } catch (Exception e) {
                    log.warn("Failed to parse response body as JSON: {}", e.getMessage());
                    responseData.put("rawResponse", response.getBody());
                }
            }
            
            // Add response metadata
            responseData.put("_response", Map.of(
                "statusCode", response.getStatusCodeValue(),
                "headers", response.getHeaders().toSingleValueMap()
            ));
            
            return StepResult.builder()
                .stepId(step.getStepId())
                .status(StepStatus.COMPLETED)
                .data(responseData)
                .executionTime(executionTime)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error processing response for step: {}", step.getStepId(), e);
            
            return StepResult.builder()
                .stepId(step.getStepId())
                .status(StepStatus.FAILED)
                .errorMessage("Failed to process response: " + e.getMessage())
                .executionTime(executionTime)
                .timestamp(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Handle HTTP error responses
     */
    private StepResult handleHttpError(SagaStep step, HttpStatusCodeException e, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        
        String errorMessage = "HTTP " + e.getStatusCode() + ": " + e.getStatusText();
        
        // Try to extract error details from response body
        try {
            if (e.getResponseBodyAsString() != null && !e.getResponseBodyAsString().isEmpty()) {
                Map<String, Object> errorBody = objectMapper.readValue(e.getResponseBodyAsString(), Map.class);
                if (errorBody.containsKey("message")) {
                    errorMessage += " - " + errorBody.get("message");
                } else if (errorBody.containsKey("error")) {
                    errorMessage += " - " + errorBody.get("error");
                }
            }
        } catch (Exception parseException) {
            log.debug("Failed to parse error response body", parseException);
        }
        
        // Determine if this is a critical failure
        boolean isCritical = step.isCritical() && 
                            (e.getStatusCode().is4xxClientError() || e.getStatusCode().is5xxServerError());
        
        return StepResult.builder()
            .stepId(step.getStepId())
            .status(isCritical ? StepStatus.FAILED : StepStatus.FAILED)
            .errorMessage(errorMessage)
            .errorCode(String.valueOf(e.getStatusCode().value()))
            .executionTime(executionTime)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Handle timeout errors
     */
    private StepResult handleTimeoutError(SagaStep step, ResourceAccessException e, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        
        String errorMessage = "Step execution timeout: " + e.getMessage();
        
        return StepResult.builder()
            .stepId(step.getStepId())
            .status(StepStatus.FAILED)
            .errorMessage(errorMessage)
            .errorCode("TIMEOUT")
            .executionTime(executionTime)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Handle general errors
     */
    private StepResult handleGeneralError(SagaStep step, Exception e, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        
        String errorMessage = "Step execution error: " + e.getMessage();
        
        return StepResult.builder()
            .stepId(step.getStepId())
            .status(StepStatus.FAILED)
            .errorMessage(errorMessage)
            .errorCode("GENERAL_ERROR")
            .executionTime(executionTime)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Check if an exception should trigger a retry
     */
    private boolean shouldRetry(Exception e, SagaStep step, int attemptNumber) {
        // Don't retry if max attempts reached
        if (attemptNumber > step.getMaxRetries()) {
            return false;
        }
        
        // Don't retry non-idempotent operations
        if (!step.isIdempotent()) {
            return false;
        }
        
        // Retry on timeout and connection errors
        if (e instanceof ResourceAccessException) {
            return true;
        }
        
        // Retry on specific HTTP status codes
        if (e instanceof HttpStatusCodeException) {
            HttpStatusCodeException httpException = (HttpStatusCodeException) e;
            HttpStatus status = HttpStatus.resolve(httpException.getStatusCode().value());
            
            // Retry on server errors (5xx) and some client errors
            return status != null && (status.is5xxServerError() || 
                   status == HttpStatus.REQUEST_TIMEOUT ||
                   status == HttpStatus.TOO_MANY_REQUESTS);
        }
        
        // Don't retry by default
        return false;
    }

    /**
     * Filter saga data to only include relevant fields for the step
     */
    private Map<String, Object> filterSagaData(Map<String, Object> sagaData, SagaStep step) {
        // In a real implementation, this would filter based on step configuration
        // For now, return all non-sensitive data
        Map<String, Object> filtered = new HashMap<>();
        
        sagaData.forEach((key, value) -> {
            // Skip sensitive fields
            if (!key.toLowerCase().contains("password") && 
                !key.toLowerCase().contains("secret") && 
                !key.toLowerCase().contains("token")) {
                filtered.put(key, value);
            }
        });
        
        return filtered;
    }
}

/**
 * Result of a step execution
 */
// StepResult class has been moved to a separate file: StepResult.java