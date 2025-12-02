package com.waqiti.common.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles compensation (rollback) of saga steps when a saga fails.
 * Implements various compensation strategies and handles compensation failures.
 */
@Service
@Slf4j
public class SagaCompensationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SagaStateRepository sagaStateRepository;
    private final SagaMetricsService metricsService;
    
    public SagaCompensationService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            SagaStateRepository sagaStateRepository,
            SagaMetricsService metricsService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.sagaStateRepository = sagaStateRepository;
        this.metricsService = metricsService;
    }

    /**
     * Compensate all completed steps of a saga
     */
    public CompletableFuture<CompensationResult> compensateSaga(String sagaId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting compensation for saga: {}", sagaId);
            
            try {
                // Get saga state
                SagaState sagaState = sagaStateRepository.findById(sagaId)
                    .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
                
                // Get steps that need compensation
                List<StepState> stepsToCompensate = getStepsNeedingCompensation(sagaState);
                
                if (stepsToCompensate.isEmpty()) {
                    log.info("No steps require compensation for saga: {}", sagaId);
                    return CompensationResult.success(sagaId, Collections.emptyList());
                }
                
                // Determine compensation strategy
                CompensationStrategy strategy = determineCompensationStrategy(sagaState);
                
                // Execute compensation
                return executeCompensation(sagaId, stepsToCompensate, strategy);
                
            } catch (Exception e) {
                log.error("Failed to start compensation for saga: " + sagaId, e);
                return CompensationResult.failure(sagaId, "Failed to start compensation: " + e.getMessage());
            }
        });
    }

    /**
     * Compensate a specific step
     */
    public CompletableFuture<StepCompensationResult> compensateStep(String sagaId, String stepId, 
                                                                   Map<String, Object> compensationData) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Compensating step: sagaId={}, stepId={}", sagaId, stepId);
            
            try {
                // Get saga state
                SagaState sagaState = sagaStateRepository.findById(sagaId)
                    .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
                
                // Get step state
                StepState stepState = sagaState.getStepState(stepId);
                if (stepState == null) {
                    throw new IllegalArgumentException("Step not found: " + stepId);
                }
                
                // Check if step needs compensation
                if (stepState.getStatus() != StepStatus.COMPLETED) {
                    log.info("Step {} does not need compensation (status: {})", stepId, stepState.getStatus());
                    return StepCompensationResult.skipped(stepId, "Step not completed");
                }
                
                // Execute compensation
                return executeStepCompensation(sagaId, stepState, compensationData);
                
            } catch (Exception e) {
                log.error("Failed to compensate step: sagaId={}, stepId={}", sagaId, stepId, e);
                return StepCompensationResult.failure(stepId, e.getMessage());
            }
        });
    }

    /**
     * Check compensation status for a saga
     */
    public CompensationStatus getCompensationStatus(String sagaId) {
        try {
            SagaState sagaState = sagaStateRepository.findById(sagaId)
                .orElse(null);
            
            if (sagaState == null) {
                return CompensationStatus.NOT_FOUND;
            }
            
            switch (sagaState.getStatus()) {
                case COMPENSATING:
                    return CompensationStatus.IN_PROGRESS;
                case COMPENSATED:
                    return CompensationStatus.COMPLETED;
                case COMPENSATION_FAILED:
                    return CompensationStatus.FAILED;
                default:
                    return CompensationStatus.NOT_REQUIRED;
            }
            
        } catch (Exception e) {
            log.error("Error checking compensation status for saga: " + sagaId, e);
            return CompensationStatus.UNKNOWN;
        }
    }

    // Private implementation methods

    private List<StepState> getStepsNeedingCompensation(SagaState sagaState) {
        return sagaState.getStepStates().values().stream()
            .filter(stepState -> stepState.getStatus() == StepStatus.COMPLETED)
            .filter(stepState -> hasCompensationEndpoint(stepState))
            .collect(Collectors.toList());
    }

    private boolean hasCompensationEndpoint(StepState stepState) {
        // In a real implementation, this would check if the step definition has a compensation endpoint
        // For now, assume all completed steps need compensation
        return true;
    }

    private CompensationStrategy determineCompensationStrategy(SagaState sagaState) {
        // In a real implementation, this would get the strategy from the saga definition
        // For now, use reverse order as default
        return CompensationStrategy.REVERSE_ORDER;
    }

    private CompensationResult executeCompensation(String sagaId, List<StepState> stepsToCompensate, 
                                                 CompensationStrategy strategy) {
        log.info("Executing compensation: sagaId={}, steps={}, strategy={}", 
            sagaId, stepsToCompensate.size(), strategy);
        
        List<StepCompensationResult> compensationResults = new ArrayList<>();
        
        try {
            switch (strategy) {
                case REVERSE_ORDER:
                    compensationResults = compensateInReverseOrder(sagaId, stepsToCompensate);
                    break;
                    
                case PARALLEL:
                    compensationResults = compensateInParallel(sagaId, stepsToCompensate);
                    break;
                    
                case CUSTOM_ORDER:
                    compensationResults = compensateInCustomOrder(sagaId, stepsToCompensate);
                    break;
                    
                case MANUAL:
                    log.info("Manual compensation required for saga: {}", sagaId);
                    return CompensationResult.manual(sagaId, "Manual compensation required");
                    
                default:
                    throw new IllegalArgumentException("Unknown compensation strategy: " + strategy);
            }
            
            // Check overall compensation result
            boolean allSuccessful = compensationResults.stream()
                .allMatch(result -> result.getStatus() == CompensationStatus.COMPLETED);
            
            if (allSuccessful) {
                metricsService.recordCompensationSuccess(sagaId);
                return CompensationResult.success(sagaId, compensationResults);
            } else {
                metricsService.recordCompensationFailure(sagaId);
                return CompensationResult.partialFailure(sagaId, compensationResults);
            }
            
        } catch (Exception e) {
            log.error("Compensation execution failed for saga: " + sagaId, e);
            metricsService.recordCompensationFailure(sagaId);
            return CompensationResult.failure(sagaId, e.getMessage());
        }
    }

    private List<StepCompensationResult> compensateInReverseOrder(String sagaId, List<StepState> steps) {
        List<StepCompensationResult> results = new ArrayList<>();
        
        // Reverse the order of steps
        List<StepState> reversedSteps = new ArrayList<>(steps);
        Collections.reverse(reversedSteps);
        
        for (StepState step : reversedSteps) {
            try {
                StepCompensationResult result = executeStepCompensation(sagaId, step, step.getCompensationData());
                results.add(result);
                
                // Stop on first failure in sequential compensation
                if (result.getStatus() == CompensationStatus.FAILED) {
                    log.warn("Stopping compensation due to step failure: sagaId={}, stepId={}", 
                        sagaId, step.getStepId());
                    break;
                }
                
            } catch (Exception e) {
                log.error("Error compensating step: sagaId={}, stepId={}", sagaId, step.getStepId(), e);
                results.add(StepCompensationResult.failure(step.getStepId(), e.getMessage()));
                break;
            }
        }
        
        return results;
    }

    private List<StepCompensationResult> compensateInParallel(String sagaId, List<StepState> steps) {
        log.info("Executing parallel compensation for {} steps", steps.size());
        
        List<CompletableFuture<StepCompensationResult>> futures = steps.stream()
            .map(step -> CompletableFuture.supplyAsync(() -> 
                executeStepCompensation(sagaId, step, step.getCompensationData())))
            .collect(Collectors.toList());
        
        // Wait for all compensations to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        
        try {
            allFutures.get(5, java.util.concurrent.TimeUnit.MINUTES); // Wait for completion with timeout

            return futures.stream()
                .map(f -> {
                    try {
                        return f.get(1, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("Failed to retrieve saga compensation result", e);
                        return StepCompensationResult.failure("unknown", "Failed to retrieve result: " + e.getMessage());
                    }
                })
                .collect(Collectors.toList());

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Saga parallel compensation timed out after 5 minutes: sagaId=" + sagaId, e);
            futures.forEach(f -> f.cancel(true));

            // Collect results from completed futures
            return futures.stream()
                .map(future -> {
                    try {
                        return future.getNow(StepCompensationResult.failure("unknown", "Compensation timed out"));
                    } catch (Exception ex) {
                        return StepCompensationResult.failure("unknown", ex.getMessage());
                    }
                })
                .collect(Collectors.toList());
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Saga parallel compensation execution failed: sagaId=" + sagaId, e.getCause());

            // Collect results from completed futures
            return futures.stream()
                .map(future -> {
                    try {
                        return future.getNow(StepCompensationResult.failure("unknown", "Execution failed"));
                    } catch (Exception ex) {
                        return StepCompensationResult.failure("unknown", ex.getMessage());
                    }
                })
                .collect(Collectors.toList());
        } catch (java.util.concurrent.InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Saga parallel compensation interrupted: sagaId=" + sagaId, e);

            // Collect results from completed futures
            return futures.stream()
                .map(future -> {
                    try {
                        return future.getNow(StepCompensationResult.failure("unknown", "Interrupted"));
                    } catch (Exception ex) {
                        return StepCompensationResult.failure("unknown", ex.getMessage());
                    }
                })
                .collect(Collectors.toList());
        }
    }

    private List<StepCompensationResult> compensateInCustomOrder(String sagaId, List<StepState> steps) {
        // Sort by priority (if available) or use reverse order as fallback
        List<StepState> sortedSteps = steps.stream()
            .sorted((s1, s2) -> Integer.compare(getPriority(s2), getPriority(s1))) // Higher priority first
            .collect(Collectors.toList());
        
        return compensateInReverseOrder(sagaId, sortedSteps);
    }

    private int getPriority(StepState stepState) {
        // In a real implementation, this would get priority from step definition
        return 0;
    }

    private StepCompensationResult executeStepCompensation(String sagaId, StepState stepState, 
                                                          Map<String, Object> compensationData) {
        String stepId = stepState.getStepId();
        long startTime = System.currentTimeMillis();
        
        log.info("Executing compensation for step: sagaId={}, stepId={}", sagaId, stepId);
        
        try {
            // Get compensation endpoint (would come from step definition in real implementation)
            String compensationEndpoint = getCompensationEndpoint(stepState);
            
            if (compensationEndpoint == null || compensationEndpoint.isEmpty()) {
                log.warn("No compensation endpoint for step: {}", stepId);
                return StepCompensationResult.skipped(stepId, "No compensation endpoint");
            }
            
            // Prepare compensation request
            HttpEntity<Object> request = createCompensationRequest(sagaId, stepState, compensationData);
            
            // Execute compensation call
            ResponseEntity<String> response = restTemplate.exchange(
                compensationEndpoint,
                HttpMethod.POST, // Default to POST for compensation
                request,
                String.class
            );
            
            // Process response
            if (response.getStatusCode().is2xxSuccessful()) {
                long duration = System.currentTimeMillis() - startTime;
                
                // Mark step as compensated
                stepState.markCompensated();
                
                log.info("Step compensation successful: sagaId={}, stepId={}, duration={}ms", 
                    sagaId, stepId, duration);
                
                return StepCompensationResult.success(stepId, duration);
            } else {
                String errorMessage = "Compensation failed with status: " + response.getStatusCode();
                log.error("Step compensation failed: sagaId={}, stepId={}, error={}", 
                    sagaId, stepId, errorMessage);
                
                return StepCompensationResult.failure(stepId, errorMessage);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error executing step compensation: sagaId={}, stepId={}", sagaId, stepId, e);
            
            return StepCompensationResult.failure(stepId, e.getMessage());
        }
    }

    private String getCompensationEndpoint(StepState stepState) {
        // In a real implementation, this would get the endpoint from the step definition
        // For now, construct a default endpoint
        return "/compensation/" + stepState.getStepId();
    }

    private HttpEntity<Object> createCompensationRequest(String sagaId, StepState stepState, 
                                                        Map<String, Object> compensationData) {
        // Create headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Saga-ID", sagaId);
        headers.add("X-Step-ID", stepState.getStepId());
        headers.add("X-Compensation-ID", UUID.randomUUID().toString());
        
        // Prepare request body
        Map<String, Object> requestBody = new HashMap<>();
        
        // Add compensation data
        if (compensationData != null) {
            requestBody.putAll(compensationData);
        }
        
        // Add step result data for context
        if (stepState.getResultData() != null) {
            requestBody.put("originalResult", stepState.getResultData());
        }
        
        // Add metadata
        requestBody.put("_compensation", Map.of(
            "sagaId", sagaId,
            "stepId", stepState.getStepId(),
            "originalTimestamp", stepState.getCompletedAt(),
            "reason", "saga_compensation"
        ));
        
        return new HttpEntity<>(requestBody, headers);
    }
}

/**
 * Result of compensation execution
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
class CompensationResult {
    private String sagaId;
    private CompensationStatus status;
    private String message;
    private List<StepCompensationResult> stepResults;
    private LocalDateTime timestamp;
    private long totalDuration;
    
    public static CompensationResult success(String sagaId, List<StepCompensationResult> stepResults) {
        return CompensationResult.builder()
            .sagaId(sagaId)
            .status(CompensationStatus.COMPLETED)
            .message("Compensation completed successfully")
            .stepResults(stepResults)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static CompensationResult failure(String sagaId, String message) {
        return CompensationResult.builder()
            .sagaId(sagaId)
            .status(CompensationStatus.FAILED)
            .message(message)
            .stepResults(new ArrayList<>())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static CompensationResult partialFailure(String sagaId, List<StepCompensationResult> stepResults) {
        return CompensationResult.builder()
            .sagaId(sagaId)
            .status(CompensationStatus.PARTIAL_FAILURE)
            .message("Some compensation steps failed")
            .stepResults(stepResults)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static CompensationResult manual(String sagaId, String message) {
        return CompensationResult.builder()
            .sagaId(sagaId)
            .status(CompensationStatus.MANUAL_REQUIRED)
            .message(message)
            .stepResults(new ArrayList<>())
            .timestamp(LocalDateTime.now())
            .build();
    }
}

/**
 * Result of individual step compensation
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
class StepCompensationResult {
    private String stepId;
    private CompensationStatus status;
    private String message;
    private long duration;
    private LocalDateTime timestamp;
    
    public static StepCompensationResult success(String stepId, long duration) {
        return StepCompensationResult.builder()
            .stepId(stepId)
            .status(CompensationStatus.COMPLETED)
            .message("Step compensated successfully")
            .duration(duration)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static StepCompensationResult failure(String stepId, String message) {
        return StepCompensationResult.builder()
            .stepId(stepId)
            .status(CompensationStatus.FAILED)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static StepCompensationResult skipped(String stepId, String reason) {
        return StepCompensationResult.builder()
            .stepId(stepId)
            .status(CompensationStatus.SKIPPED)
            .message(reason)
            .timestamp(LocalDateTime.now())
            .build();
    }
}

/**
 * Compensation status enumeration
 */
enum CompensationStatus {
    /**
     * Compensation not required
     */
    NOT_REQUIRED,
    
    /**
     * Compensation in progress
     */
    IN_PROGRESS,
    
    /**
     * Compensation completed successfully
     */
    COMPLETED,
    
    /**
     * Compensation failed
     */
    FAILED,
    
    /**
     * Some steps compensated, others failed
     */
    PARTIAL_FAILURE,
    
    /**
     * Manual intervention required
     */
    MANUAL_REQUIRED,
    
    /**
     * Step compensation was skipped
     */
    SKIPPED,
    
    /**
     * Status unknown
     */
    UNKNOWN,
    
    /**
     * Saga not found
     */
    NOT_FOUND
}