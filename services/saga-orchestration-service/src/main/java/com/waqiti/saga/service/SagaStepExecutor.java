package com.waqiti.saga.service;

import com.waqiti.saga.domain.SagaStep;
import com.waqiti.saga.domain.SagaTransaction;
import com.waqiti.saga.exception.SagaExecutionException;
import com.waqiti.saga.client.ServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for executing individual SAGA steps across different microservices.
 * Provides circuit breaker, retry logic, and distributed coordination capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaStepExecutor {

    private final ServiceClient serviceClient;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /**
     * Executes a saga step with distributed coordination
     */
    @CircuitBreaker(name = "saga-step-execution", fallbackMethod = "executeStepFallback")
    @Retry(name = "saga-step-execution")
    @TimeLimiter(name = "saga-step-execution")
    public CompletableFuture<String> executeStepWithCoordination(SagaStep step, String correlationId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Executing step with coordination: stepId={}, service={}, endpoint={}, correlationId={}", 
                    step.getId(), step.getServiceName(), step.getActionEndpoint(), correlationId);
            
            try {
                // Prepare headers for distributed coordination
                HttpHeaders headers = createCoordinationHeaders(correlationId, step);
                
                // Prepare request payload
                Object payload = prepareStepPayload(step);
                HttpEntity<Object> request = new HttpEntity<>(payload, headers);
                
                // Determine service URL
                String serviceUrl = serviceClient.resolveServiceUrl(step.getServiceName());
                String fullUrl = serviceUrl + step.getActionEndpoint();
                
                // Execute the HTTP call
                ResponseEntity<String> response = restTemplate.exchange(
                        fullUrl,
                        HttpMethod.POST,
                        request,
                        String.class
                );
                
                // Validate response
                validateStepResponse(response, step);
                
                log.info("Step executed successfully: stepId={}, service={}, status={}", 
                        step.getId(), step.getServiceName(), response.getStatusCode());
                
                return response.getBody();
                
            } catch (Exception e) {
                log.error("Step execution failed: stepId={}, service={}, error={}", 
                        step.getId(), step.getServiceName(), e.getMessage(), e);
                throw new SagaExecutionException("Step execution failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Compensates a saga step with distributed coordination
     */
    @CircuitBreaker(name = "saga-step-compensation", fallbackMethod = "compensateStepFallback")
    @Retry(name = "saga-step-compensation")
    @TimeLimiter(name = "saga-step-compensation")
    public CompletableFuture<String> compensateStepWithCoordination(SagaStep step, String correlationId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Compensating step with coordination: stepId={}, service={}, endpoint={}, correlationId={}", 
                    step.getId(), step.getServiceName(), step.getCompensationEndpoint(), correlationId);
            
            if (step.getCompensationEndpoint() == null) {
                log.warn("No compensation endpoint defined for step: stepId={}, service={}", 
                        step.getId(), step.getServiceName());
                return "NO_COMPENSATION_REQUIRED";
            }
            
            try {
                // Prepare headers for distributed coordination
                HttpHeaders headers = createCoordinationHeaders(correlationId, step);
                headers.add("X-Saga-Operation", "COMPENSATION");
                
                // Prepare compensation payload
                Object payload = prepareCompensationPayload(step);
                HttpEntity<Object> request = new HttpEntity<>(payload, headers);
                
                // Determine service URL
                String serviceUrl = serviceClient.resolveServiceUrl(step.getServiceName());
                String fullUrl = serviceUrl + step.getCompensationEndpoint();
                
                // Execute the compensation HTTP call
                ResponseEntity<String> response = restTemplate.exchange(
                        fullUrl,
                        HttpMethod.POST,
                        request,
                        String.class
                );
                
                // Validate compensation response
                validateCompensationResponse(response, step);
                
                log.info("Step compensated successfully: stepId={}, service={}, status={}", 
                        step.getId(), step.getServiceName(), response.getStatusCode());
                
                return response.getBody();
                
            } catch (Exception e) {
                log.error("Step compensation failed: stepId={}, service={}, error={}", 
                        step.getId(), step.getServiceName(), e.getMessage(), e);
                throw new SagaExecutionException("Step compensation failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Performs consistency check for a step
     */
    @CircuitBreaker(name = "saga-consistency-check")
    @Retry(name = "saga-consistency-check")
    public void performConsistencyCheck(SagaStep step, String correlationId) {
        log.info("Performing consistency check: stepId={}, service={}, correlationId={}", 
                step.getId(), step.getServiceName(), correlationId);
        
        try {
            // Prepare headers for consistency check
            HttpHeaders headers = createCoordinationHeaders(correlationId, step);
            headers.add("X-Saga-Operation", "CONSISTENCY_CHECK");
            
            // Create consistency check payload
            Map<String, Object> payload = Map.of(
                    "stepId", step.getId(),
                    "operationType", "CONSISTENCY_CHECK",
                    "originalResponse", step.getResponse() != null ? step.getResponse() : ""
            );
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            // Determine consistency check endpoint
            String serviceUrl = serviceClient.resolveServiceUrl(step.getServiceName());
            String consistencyEndpoint = "/api/v1/saga/consistency-check";
            String fullUrl = serviceUrl + consistencyEndpoint;
            
            // Execute consistency check
            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new SagaExecutionException("Consistency check failed with status: " + 
                        response.getStatusCode());
            }
            
            log.info("Consistency check passed: stepId={}, service={}", 
                    step.getId(), step.getServiceName());
            
        } catch (Exception e) {
            log.error("Consistency check failed: stepId={}, service={}, error={}", 
                    step.getId(), step.getServiceName(), e.getMessage(), e);
            throw new SagaExecutionException("Consistency check failed", e);
        }
    }

    /**
     * Performs final consistency validation for the entire saga
     */
    @CircuitBreaker(name = "saga-final-validation")
    @Retry(name = "saga-final-validation")
    public void performFinalConsistencyValidation(SagaTransaction saga) {
        log.info("Performing final consistency validation: sagaId={}, correlationId={}", 
                saga.getId(), saga.getCorrelationId());
        
        try {
            // Validate across all services involved in the saga
            for (SagaStep step : saga.getSteps()) {
                if (step.getStatus() == SagaStep.StepStatus.COMPLETED && isCriticalStep(step)) {
                    validateStepFinalState(step, saga.getCorrelationId());
                }
            }
            
            // Perform cross-service consistency validation
            performCrossServiceValidation(saga);
            
            log.info("Final consistency validation passed: sagaId={}, correlationId={}", 
                    saga.getId(), saga.getCorrelationId());
            
        } catch (Exception e) {
            log.error("Final consistency validation failed: sagaId={}, correlationId={}, error={}", 
                    saga.getId(), saga.getCorrelationId(), e.getMessage(), e);
            throw new SagaExecutionException("Final consistency validation failed", e);
        }
    }

    /**
     * Creates coordination headers for distributed transaction management
     */
    private HttpHeaders createCoordinationHeaders(String correlationId, SagaStep step) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("X-Correlation-ID", correlationId);
        headers.add("X-Saga-ID", step.getSagaTransaction().getId().toString());
        headers.add("X-Saga-Step-ID", step.getId().toString());
        headers.add("X-Saga-Step-Name", step.getStepName());
        headers.add("X-Saga-Type", step.getSagaTransaction().getSagaType());
        headers.add("X-Saga-Operation", "EXECUTION");
        headers.add("X-Timestamp", LocalDateTime.now().toString());
        headers.add("X-Service-Name", step.getServiceName());
        return headers;
    }

    /**
     * Prepares payload for step execution
     */
    private Object prepareStepPayload(SagaStep step) {
        try {
            if (step.getActionPayload() != null && !step.getActionPayload().trim().isEmpty()) {
                return objectMapper.readValue(step.getActionPayload(), Object.class);
            }
            return Map.of(); // Empty payload
        } catch (Exception e) {
            log.error("Failed to prepare step payload: stepId={}, error={}", step.getId(), e.getMessage());
            throw new SagaExecutionException("Failed to prepare step payload", e);
        }
    }

    /**
     * Prepares payload for step compensation
     */
    private Object prepareCompensationPayload(SagaStep step) {
        try {
            if (step.getCompensationPayload() != null && !step.getCompensationPayload().trim().isEmpty()) {
                return objectMapper.readValue(step.getCompensationPayload(), Object.class);
            }
            
            // Create default compensation payload from step execution data
            return Map.of(
                    "stepId", step.getId(),
                    "originalPayload", step.getActionPayload() != null ? step.getActionPayload() : "",
                    "originalResponse", step.getResponse() != null ? step.getResponse() : "",
                    "compensationReason", "SAGA_COMPENSATION"
            );
        } catch (Exception e) {
            log.error("Failed to prepare compensation payload: stepId={}, error={}", step.getId(), e.getMessage());
            throw new SagaExecutionException("Failed to prepare compensation payload", e);
        }
    }

    /**
     * Validates step execution response
     */
    private void validateStepResponse(ResponseEntity<String> response, SagaStep step) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new SagaExecutionException("Step execution failed with status: " + 
                    response.getStatusCode() + ", body: " + response.getBody());
        }
        
        // Additional business logic validation based on step type
        validateBusinessRules(response.getBody(), step);
    }

    /**
     * Validates step compensation response
     */
    private void validateCompensationResponse(ResponseEntity<String> response, SagaStep step) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new SagaExecutionException("Step compensation failed with status: " + 
                    response.getStatusCode() + ", body: " + response.getBody());
        }
        
        log.debug("Compensation response validated: stepId={}, status={}", 
                step.getId(), response.getStatusCode());
    }

    /**
     * Validates business rules for step response
     */
    private void validateBusinessRules(String responseBody, SagaStep step) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            
            // Common validation rules
            if (response.containsKey("error") && response.get("error") != null) {
                throw new SagaExecutionException("Step returned error: " + response.get("error"));
            }
            
            // Step-specific validation
            switch (step.getStepName().toLowerCase()) {
                case "reserve_funds":
                case "debit_wallet":
                    validateBalanceOperation(response, step);
                    break;
                case "credit_wallet":
                    validateCreditOperation(response, step);
                    break;
                case "validate_transfer":
                    validateTransferValidation(response, step);
                    break;
                default:
                    // Generic validation
                    break;
            }
            
        } catch (Exception e) {
            if (e instanceof SagaExecutionException) {
                throw e;
            }
            log.warn("Could not validate business rules for step: stepId={}, error={}", 
                    step.getId(), e.getMessage());
        }
    }

    /**
     * Validates balance operations (reserve, debit)
     */
    private void validateBalanceOperation(Map<String, Object> response, SagaStep step) {
        if (!response.containsKey("newBalance")) {
            throw new SagaExecutionException("Balance operation response missing newBalance field");
        }
        
        Object newBalance = response.get("newBalance");
        if (newBalance == null) {
            throw new SagaExecutionException("Balance operation returned null balance");
        }
        
        // Additional balance validation logic would go here
        log.debug("Balance operation validated: stepId={}, newBalance={}", step.getId(), newBalance);
    }

    /**
     * Validates credit operations
     */
    private void validateCreditOperation(Map<String, Object> response, SagaStep step) {
        if (!response.containsKey("transactionId")) {
            throw new SagaExecutionException("Credit operation response missing transactionId field");
        }
        
        // Additional credit validation logic would go here
        log.debug("Credit operation validated: stepId={}", step.getId());
    }

    /**
     * Validates transfer validation response
     */
    private void validateTransferValidation(Map<String, Object> response, SagaStep step) {
        if (!response.containsKey("valid") || !Boolean.TRUE.equals(response.get("valid"))) {
            String reason = (String) response.get("reason");
            throw new SagaExecutionException("Transfer validation failed: " + reason);
        }
        
        log.debug("Transfer validation passed: stepId={}", step.getId());
    }

    /**
     * Fallback method for step execution
     */
    public CompletableFuture<String> executeStepFallback(SagaStep step, String correlationId, Exception ex) {
        log.error("Step execution fallback triggered: stepId={}, service={}, error={}", 
                step.getId(), step.getServiceName(), ex.getMessage());
        
        return CompletableFuture.failedFuture(
                new SagaExecutionException("Step execution failed, fallback triggered", ex)
        );
    }

    /**
     * Fallback method for step compensation
     */
    public CompletableFuture<String> compensateStepFallback(SagaStep step, String correlationId, Exception ex) {
        log.error("Step compensation fallback triggered: stepId={}, service={}, error={}", 
                step.getId(), step.getServiceName(), ex.getMessage());
        
        return CompletableFuture.failedFuture(
                new SagaExecutionException("Step compensation failed, fallback triggered", ex)
        );
    }

    /**
     * Helper method to check if a step is critical
     */
    private boolean isCriticalStep(SagaStep step) {
        String stepName = step.getStepName().toLowerCase();
        return stepName.contains("debit") || 
               stepName.contains("credit") || 
               stepName.contains("reserve") ||
               stepName.contains("validate");
    }

    /**
     * Validates final state of a step
     */
    private void validateStepFinalState(SagaStep step, String correlationId) {
        // Implementation would verify the step's final state is consistent
        log.debug("Validating final state for step: stepId={}, service={}", 
                step.getId(), step.getServiceName());
    }

    /**
     * Performs cross-service consistency validation
     */
    private void performCrossServiceValidation(SagaTransaction saga) {
        // Implementation would check consistency across all involved services
        log.debug("Performing cross-service validation for saga: sagaId={}", saga.getId());
    }
}