package com.waqiti.common.saga;

import com.waqiti.common.events.FinancialEventPublisher;
import com.waqiti.common.events.SagaEvent;
import com.waqiti.common.saga.SagaEventType;
import com.waqiti.common.resilience.CircuitBreakerService;
import com.waqiti.common.locking.DistributedLockingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * Enterprise-grade Saga Orchestrator for managing distributed transactions
 * across microservices in the Waqiti platform.
 * 
 * Features:
 * - Orchestrates complex multi-service transactions
 * - Automatic compensation on failures
 * - Timeout handling and retry logic
 * - Comprehensive monitoring and auditing
 * - Parallel step execution where possible
 * - Recovery from failures and system restarts
 */
@Service
@Slf4j
public class SagaOrchestrator {

    @Lazy
    private final SagaOrchestrator self;
    private final FinancialEventPublisher eventPublisher;
    private final SagaStateRepository sagaStateRepository;
    private final SagaStepExecutor stepExecutor;
    private final SagaCompensationService compensationService;
    private final SagaTimeoutService timeoutService;
    private final DistributedLockingService distributedLockingService;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerService circuitBreakerService;
    private final SagaRegistry sagaRegistry;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public SagaOrchestrator(
            @Lazy SagaOrchestrator self,
            FinancialEventPublisher eventPublisher,
            SagaStateRepository sagaStateRepository,
            SagaStepExecutor stepExecutor,
            SagaCompensationService compensationService,
            SagaTimeoutService timeoutService,
            DistributedLockingService distributedLockingService,
            ObjectMapper objectMapper,
            @org.springframework.lang.Nullable CircuitBreakerService circuitBreakerService,
            SagaRegistry sagaRegistry) {
        this.self = self;
        this.eventPublisher = eventPublisher;
        this.sagaStateRepository = sagaStateRepository;
        this.stepExecutor = stepExecutor;
        this.compensationService = compensationService;
        this.timeoutService = timeoutService;
        this.distributedLockingService = distributedLockingService;
        this.objectMapper = objectMapper;
        this.circuitBreakerService = circuitBreakerService;
        this.sagaRegistry = sagaRegistry;
    }

    // Active saga instances
    private final Map<String, SagaExecution> activeSagas = new ConcurrentHashMap<>();
    
    // Saga timeout tracking
    private final Map<String, SagaTimeoutHandle> sagaTimeouts = new ConcurrentHashMap<>();
    
    // Executor service for parallel step execution
    private final ExecutorService parallelExecutor = Executors.newWorkStealingPool();
    
    // Step execution strategies
    private final Map<String, Function<List<SagaStep>, CompletableFuture<List<StepResult>>>> executionStrategies = new ConcurrentHashMap<>();

    /**
     * Start a new saga execution
     */
    public CompletableFuture<SagaResult> executeSaga(SagaDefinition definition, Map<String, Object> initialData) {
        String sagaId = UUID.randomUUID().toString();
        
        log.info("Starting saga execution: sagaId={}, type={}, steps={}", 
            sagaId, definition.getSagaType(), definition.getSteps().size());
        
        try {
            // Create saga state
            SagaState sagaState = new SagaState(
                sagaId,
                definition.getSagaType(),
                SagaStatus.RUNNING,
                initialData,
                LocalDateTime.now()
            );
            
            // Persist initial state
            sagaStateRepository.save(sagaState);
            
            // Create execution context
            SagaExecution execution = new SagaExecution(sagaId, definition, sagaState);
            activeSagas.put(sagaId, execution);
            
            // Set up timeout
            setupSagaTimeout(sagaId, definition.getTimeoutMinutes());
            
            // Publish saga started event
            publishSagaEvent(sagaId, SagaEventType.SAGA_STARTED, "Saga execution started", null);
            
            // Start execution
            return executeSagaSteps(execution)
                .thenApply(result -> {
                    cleanupSaga(sagaId);
                    return result;
                })
                .exceptionally(throwable -> {
                    log.error("Saga execution failed: sagaId=" + sagaId, throwable);
                    handleSagaFailure(sagaId, throwable);
                    return SagaResult.builder().sagaId(sagaId).status(SagaStatus.FAILED).message(throwable.getMessage()).build();
                });
                
        } catch (Exception e) {
            log.error("Failed to start saga: sagaId=" + sagaId, e);
            return CompletableFuture.completedFuture(
                SagaResult.builder().sagaId(sagaId).status(SagaStatus.FAILED).message("Failed to start saga: " + e.getMessage()).build());
        }
    }

    /**
     * Resume saga execution from a saved state (for recovery)
     */
    public CompletableFuture<SagaResult> resumeSaga(String sagaId) {
        log.info("Resuming saga execution: sagaId={}", sagaId);
        
        try {
            SagaState sagaState = sagaStateRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
            
            if (sagaState.getStatus() != SagaStatus.RUNNING) {
                return CompletableFuture.completedFuture(
                    SagaResult.builder().sagaId(sagaId).status(sagaState.getStatus()).message("Saga not in running state").build());
            }
            
            // Recreate saga definition from state
            SagaDefinition definition = recreateSagaDefinition(sagaState);
            
            // Create execution context
            SagaExecution execution = new SagaExecution(sagaId, definition, sagaState);
            activeSagas.put(sagaId, execution);
            
            // Set up timeout
            setupSagaTimeout(sagaId, definition.getTimeoutMinutes());
            
            // Continue execution from where it left off
            return continueSagaExecution(execution)
                .thenApply(result -> {
                    cleanupSaga(sagaId);
                    return result;
                })
                .exceptionally(throwable -> {
                    log.error("Saga resume failed: sagaId=" + sagaId, throwable);
                    handleSagaFailure(sagaId, throwable);
                    return SagaResult.builder().sagaId(sagaId).status(SagaStatus.FAILED).message(throwable.getMessage()).build();
                });
                
        } catch (Exception e) {
            log.error("Failed to resume saga: sagaId=" + sagaId, e);
            return CompletableFuture.completedFuture(
                SagaResult.builder().sagaId(sagaId).status(SagaStatus.FAILED).message("Failed to resume saga: " + e.getMessage()).build());
        }
    }

    /**
     * Cancel a running saga
     */
    public CompletableFuture<Void> cancelSaga(String sagaId, String reason) {
        log.info("Cancelling saga: sagaId={}, reason={}", sagaId, reason);
        
        return CompletableFuture.runAsync(() -> {
            SagaExecution execution = activeSagas.get(sagaId);
            if (execution != null) {
                execution.cancel(reason);
                
                // Start compensation
                startCompensation(sagaId, "Saga cancelled: " + reason)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Compensation failed for cancelled saga: " + sagaId, throwable);
                        }
                        cleanupSaga(sagaId);
                    });
            }
        });
    }

    /**
     * Get saga execution status
     */
    public SagaStatus getSagaStatus(String sagaId) {
        SagaExecution execution = activeSagas.get(sagaId);
        if (execution != null) {
            return execution.getStatus();
        }
        
        return sagaStateRepository.findById(sagaId)
            .map(SagaState::getStatus)
            .orElse(SagaStatus.NOT_FOUND);
    }

    /**
     * Get detailed saga information
     */
    public Optional<SagaInfo> getSagaInfo(String sagaId) {
        SagaExecution execution = activeSagas.get(sagaId);
        if (execution != null) {
            return Optional.of(createSagaInfo(execution));
        }
        
        return sagaStateRepository.findById(sagaId)
            .map(this::createSagaInfoFromState);
    }

    /**
     * List all active sagas
     */
    public List<SagaInfo> getActiveSagas() {
        return activeSagas.values().stream()
            .map(this::createSagaInfo)
            .collect(Collectors.toList());
    }

    /**
     * Handle step completion callback
     */
    public void handleStepCompletion(String sagaId, String stepId, StepResult result) {
        SagaExecution execution = activeSagas.get(sagaId);
        if (execution == null) {
            log.warn("Received step completion for unknown saga: sagaId={}, stepId={}", sagaId, stepId);
            return;
        }
        
        execution.handleStepCompletion(stepId, result);
        
        // Continue saga execution
        continueSagaExecution(execution)
            .whenComplete((sagaResult, throwable) -> {
                if (throwable != null) {
                    log.error("Error continuing saga after step completion: sagaId=" + sagaId, throwable);
                    handleSagaFailure(sagaId, throwable);
                } else if (sagaResult.getStatus() != SagaStatus.RUNNING) {
                    cleanupSaga(sagaId);
                }
            });
    }

    // Private implementation methods

    private CompletableFuture<SagaResult> executeSagaSteps(SagaExecution execution) {
        return CompletableFuture.supplyAsync(() -> {
            String sagaId = execution.getSagaId();
            SagaDefinition definition = execution.getDefinition();
            
            try {
                while (execution.getStatus() == SagaStatus.RUNNING && execution.hasNextStep()) {
                    List<SagaStep> nextSteps = execution.getNextSteps();
                    
                    if (nextSteps.isEmpty()) {
                        break;
                    }
                    
                    // Group steps for optimal execution
                    Map<Boolean, List<SagaStep>> stepGroups = groupStepsForExecution(nextSteps);
                    List<SagaStep> parallelSteps = stepGroups.getOrDefault(true, new ArrayList<>());
                    List<SagaStep> sequentialSteps = stepGroups.getOrDefault(false, new ArrayList<>());
                    
                    // Execute parallel steps first if any
                    if (!parallelSteps.isEmpty()) {
                        List<StepResult> parallelResults = executeParallelSteps(sagaId, parallelSteps, 
                            execution.getSagaData(), definition.getStepTimeoutMinutes());
                        
                        for (int i = 0; i < parallelSteps.size(); i++) {
                            SagaStep step = parallelSteps.get(i);
                            StepResult result = parallelResults.get(i);
                            
                            execution.handleStepCompletion(step.getStepId(), result);
                            
                            if (result.getStatus() == StepStatus.FAILED && step.isCritical()) {
                                throw new SagaExecutionException("Critical step failed: " + step.getStepId() + 
                                    " - " + result.getErrorMessage());
                            }
                        }
                    }
                    
                    // Execute sequential steps
                    for (SagaStep step : sequentialSteps) {
                        StepResult result = executeStepWithCircuitBreaker(sagaId, step, execution.getSagaData())
                            .get(step.getTimeoutSeconds(), TimeUnit.SECONDS);
                        
                        execution.handleStepCompletion(step.getStepId(), result);
                        
                        if (result.getStatus() == StepStatus.FAILED && step.isCritical()) {
                            throw new SagaExecutionException("Critical step failed: " + step.getStepId() + 
                                " - " + result.getErrorMessage());
                        }
                    }
                    
                    // Update saga state after each batch
                    self.updateSagaState(execution);
                }
                
                // Check final status
                if (execution.isCompleted()) {
                    self.updateSagaStatus(sagaId, SagaStatus.COMPLETED);
                    publishSagaEvent(sagaId, SagaEventType.SAGA_COMPLETED, 
                        "Saga completed successfully", execution.getSagaData());
                    
                    log.info("Saga completed successfully: sagaId={}", sagaId);
                    return SagaResult.builder().sagaId(sagaId).status(SagaStatus.COMPLETED).message("Saga completed successfully")
                        .resultData(execution.getSagaData()).executionTimeMs(System.currentTimeMillis() - execution.getStartTime()).completedAt(LocalDateTime.now()).build();
                } else {
                    // Saga is still running (async steps)
                    return SagaResult.builder().sagaId(sagaId).status(SagaStatus.RUNNING).message("Saga is running").build();
                }
                
            } catch (Exception e) {
                log.error("Saga execution error: sagaId=" + sagaId, e);
                throw new SagaExecutionException("Saga execution failed", e);
            }
        }, parallelExecutor);
    }
    
    /**
     * Execute multiple steps in parallel with optimized resource usage
     */
    private List<StepResult> executeParallelSteps(String sagaId, List<SagaStep> steps, 
                                                  Map<String, Object> sagaData, int timeoutMinutes) 
                                                  throws InterruptedException, ExecutionException, TimeoutException {
        log.info("Executing {} steps in parallel for saga: {}", steps.size(), sagaId);
        
        // Create futures for parallel execution
        List<CompletableFuture<StepResult>> stepFutures = steps.stream()
            .map(step -> executeStepWithCircuitBreaker(sagaId, step, sagaData))
            .collect(Collectors.toList());
        
        // Use allOf to wait for all futures with timeout
        CompletableFuture<Void> allSteps = CompletableFuture.allOf(
            stepFutures.toArray(new CompletableFuture[0]));
        
        try {
            allSteps.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            // Cancel remaining futures on timeout
            stepFutures.forEach(future -> future.cancel(true));
            throw new SagaTimeoutException("Parallel step execution timeout after " + timeoutMinutes + " minutes");
        }
        
        // Collect results
        List<StepResult> results = new ArrayList<>();
        for (CompletableFuture<StepResult> future : stepFutures) {
            results.add(future.get());
        }
        
        return results;
    }
    
    /**
     * Group steps based on whether they can be executed in parallel
     */
    private Map<Boolean, List<SagaStep>> groupStepsForExecution(List<SagaStep> steps) {
        // Steps with no dependencies between them can run in parallel
        Set<String> stepIds = steps.stream()
            .map(SagaStep::getStepId)
            .collect(Collectors.toSet());
        
        return steps.stream()
            .collect(Collectors.partitioningBy(step -> 
                step.getDependencies().stream()
                    .noneMatch(stepIds::contains)));
    }
    
    /**
     * Execute step with circuit breaker protection
     */
    private CompletableFuture<StepResult> executeStepWithCircuitBreaker(String sagaId, SagaStep step, 
                                                                       Map<String, Object> sagaData) {
        if (circuitBreakerService != null && step.isUseCircuitBreaker()) {
            String circuitBreakerKey = "saga_" + step.getServiceEndpoint().replaceAll("[^a-zA-Z0-9]", "_");
            
            return CompletableFuture.supplyAsync(() -> 
                circuitBreakerService.executeWithFullResilience(
                    circuitBreakerKey,
                    () -> {
                        try {
                            return executeStep(sagaId, step, sagaData).get();
                        } catch (Exception e) {
                            throw new RuntimeException("Step execution failed", e);
                        }
                    },
                    () -> StepResult.builder()
                        .stepId(step.getStepId())
                        .status(StepStatus.FAILED)
                        .errorMessage("Circuit breaker open for: " + step.getServiceEndpoint())
                        .errorCode("CIRCUIT_BREAKER_OPEN")
                        .build()
                ), parallelExecutor);
        } else {
            return executeStep(sagaId, step, sagaData);
        }
    }

    private CompletableFuture<SagaResult> continueSagaExecution(SagaExecution execution) {
        // Similar to executeSagaSteps but continues from current state
        return executeSagaSteps(execution);
    }

    private CompletableFuture<StepResult> executeStep(String sagaId, SagaStep step, Map<String, Object> sagaData) {
        log.debug("Executing saga step: sagaId={}, stepId={}, type={}", 
            sagaId, step.getStepId(), step.getStepType());
        
        publishSagaEvent(sagaId, SagaEventType.STEP_STARTED, 
            "Step started: " + step.getStepId(), Map.of("stepId", step.getStepId()));
        
        return stepExecutor.executeStep(sagaId, step, sagaData)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Step execution failed: sagaId={}, stepId={}", sagaId, step.getStepId(), throwable);
                    publishSagaEvent(sagaId, SagaEventType.STEP_FAILED, 
                        "Step failed: " + step.getStepId(), 
                        Map.of("stepId", step.getStepId(), "error", throwable.getMessage()));
                } else {
                    log.debug("Step completed: sagaId={}, stepId={}, status={}", 
                        sagaId, step.getStepId(), result.getStatus());
                    publishSagaEvent(sagaId, SagaEventType.STEP_COMPLETED, 
                        "Step completed: " + step.getStepId(), 
                        Map.of("stepId", step.getStepId(), "status", result.getStatus()));
                }
            });
    }

    private CompletableFuture<Void> startCompensation(String sagaId, String reason) {
        log.info("Starting compensation: sagaId={}, reason={}", sagaId, reason);
        
        self.updateSagaStatus(sagaId, SagaStatus.COMPENSATING);
        publishSagaEvent(sagaId, SagaEventType.COMPENSATION_STARTED, 
            "Compensation started: " + reason, null);
        
        return compensationService.compensateSaga(sagaId)
            .thenRun(() -> {
                self.updateSagaStatus(sagaId, SagaStatus.COMPENSATED);
                publishSagaEvent(sagaId, SagaEventType.COMPENSATION_COMPLETED, 
                    "Compensation completed", null);
                log.info("Compensation completed: sagaId={}", sagaId);
            })
            .exceptionally(throwable -> {
                log.error("Compensation failed: sagaId=" + sagaId, throwable);
                self.updateSagaStatus(sagaId, SagaStatus.COMPENSATION_FAILED);
                publishSagaEvent(sagaId, SagaEventType.COMPENSATION_FAILED, 
                    "Compensation failed: " + throwable.getMessage(), null);
                return null;
            });
    }

    private void handleSagaFailure(String sagaId, Throwable throwable) {
        log.error("Handling saga failure: sagaId={}", sagaId, throwable);
        
        self.updateSagaStatus(sagaId, SagaStatus.FAILED);
        publishSagaEvent(sagaId, SagaEventType.SAGA_FAILED, 
            "Saga failed: " + throwable.getMessage(), null);
        
        // Start compensation
        startCompensation(sagaId, "Saga failed: " + throwable.getMessage())
            .whenComplete((result, compensationError) -> {
                if (compensationError != null) {
                    log.error("Compensation failed for failed saga: " + sagaId, compensationError);
                }
                cleanupSaga(sagaId);
            });
    }

    private void setupSagaTimeout(String sagaId, int timeoutMinutes) {
        SagaTimeoutHandle timeoutHandle = timeoutService.scheduleTimeout(sagaId, timeoutMinutes, () -> {
            log.warn("Saga timeout: sagaId={}", sagaId);
            
            SagaExecution execution = activeSagas.get(sagaId);
            if (execution != null && execution.getStatus() == SagaStatus.RUNNING) {
                execution.timeout();
                handleSagaFailure(sagaId, new SagaTimeoutException("Saga timeout after " + timeoutMinutes + " minutes"));
            }
        });
        
        sagaTimeouts.put(sagaId, timeoutHandle);
    }

    private void cleanupSaga(String sagaId) {
        activeSagas.remove(sagaId);
        
        // Cancel timeout
        SagaTimeoutHandle timeoutHandle = sagaTimeouts.remove(sagaId);
        if (timeoutHandle != null) {
            timeoutHandle.cancel();
        }
        
        log.debug("Saga cleanup completed: sagaId={}", sagaId);
    }

    /**
     * CRITICAL SECURITY FIX - Thread-safe saga state updates with distributed locking
     * Prevents race conditions and data corruption in financial transactions
     */
    @Transactional
    private void updateSagaState(SagaExecution execution) {
        String lockKey = "saga:state:" + execution.getSagaId();
        
        try {
            // CRITICAL: Use distributed lock to prevent race conditions
            distributedLockingService.executeWithLock(lockKey, 
                java.time.Duration.ofMinutes(2), () -> {
                    try {
                        SagaState state = execution.getSagaState();
                        
                        // Validate saga exists and is in valid state
                        if (state == null) {
                            throw new SagaStateException("Saga state is null for sagaId: " + execution.getSagaId());
                        }
                        
                        // Audit previous state
                        SagaStatus previousStatus = state.getStatus();
                        Map<String, Object> previousStepStates = new HashMap<>(state.getStepStates());
                        
                        // Update state atomically
                        state.setStepStates(execution.getStepStates());
                        state.setSagaData(execution.getSagaData());
                        state.setLastUpdated(LocalDateTime.now());
                        
                        // Save with version check (optimistic locking)
                        sagaStateRepository.save(state);
                        
                        // Comprehensive audit logging for compliance
                        log.info("AUDIT: Saga state updated - ID: {}, PreviousStatus: {}, Steps Changed: {}, Version: {}", 
                            execution.getSagaId(), 
                            previousStatus,
                            !previousStepStates.equals(execution.getStepStates()),
                            state.getVersion());
                        
                        // Publish state change event for monitoring
                        publishSagaStateUpdateEvent(execution.getSagaId(), 
                            new HashMap<>(previousStepStates), 
                            new HashMap<>(execution.getStepStates()));
                        
                    } catch (Exception e) {
                        log.error("CRITICAL: Failed to update saga state for saga: {} - TRANSACTION INTEGRITY AT RISK", 
                            execution.getSagaId(), e);
                        throw new SagaStateException("Failed to update saga state", e);
                    }
                });
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to acquire lock for saga state update: {} - POTENTIAL DATA CORRUPTION", 
                execution.getSagaId(), e);
            throw new SagaStateException("Failed to acquire lock for saga state update", e);
        }
    }

    @Transactional
    private void updateSagaStatus(String sagaId, SagaStatus status) {
        try {
            sagaStateRepository.findById(sagaId).ifPresent(state -> {
                state.setStatus(status);
                state.setLastUpdated(LocalDateTime.now());
                if (status.isTerminal()) {
                    state.setCompletedAt(LocalDateTime.now());
                }
                sagaStateRepository.save(state);
            });
            
        } catch (Exception e) {
            log.error("Failed to update saga status: sagaId=" + sagaId, e);
        }
    }

    private void publishSagaEvent(String sagaId, SagaEventType eventType, String message, Map<String, Object> data) {
        try {
            // Convert com.waqiti.common.saga.SagaEventType to com.waqiti.common.events.SagaEventType
            com.waqiti.common.events.SagaEventType eventsSagaEventType;
            try {
                eventsSagaEventType = com.waqiti.common.events.SagaEventType.valueOf(eventType.name());
            } catch (IllegalArgumentException e) {
                eventsSagaEventType = com.waqiti.common.events.SagaEventType.SAGA_STEP_UPDATED;
            }
            
            SagaEvent event = SagaEvent.builder()
                .sagaId(sagaId)
                .eventType(eventsSagaEventType.name())
                .message(message)
                .eventData(data)
                .timestamp(Instant.now())
                .build();
            eventPublisher.publishSagaEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to publish saga event: sagaId=" + sagaId + ", eventType=" + eventType, e);
        }
    }

    private SagaDefinition recreateSagaDefinition(SagaState sagaState) {
        // Reconstruct saga definition from persisted state
        String sagaType = sagaState.getSagaType();
        
        // Retrieve saga definition from registry based on type
        SagaDefinition definition = sagaRegistry.getDefinition(sagaType);
        if (definition != null) {
            return definition;
        }
        
        // Fallback: Reconstruct from state metadata
        log.info("Recreating saga definition for saga {} of type {}", sagaState.getSagaId(), sagaType);
        
        SagaDefinition.SagaDefinitionBuilder builder = SagaDefinition.builder()
                .sagaType(sagaType)
                .timeoutMinutes(30); // Default timeout
        
        // Reconstruct steps from state
        Map<String, Object> metadata = sagaState.getMetadata();
        if (metadata != null && metadata.containsKey("steps")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stepsData = (List<Map<String, Object>>) metadata.get("steps");
            
            List<SagaStep> steps = new ArrayList<>();
            for (Map<String, Object> stepData : stepsData) {
                SagaStep step = reconstructStep(stepData);
                steps.add(step);
            }
            builder.steps(steps);
        } else {
            // Use default steps based on saga type
            List<SagaStep> defaultSteps = createDefaultStepsForType(sagaType);
            builder.steps(defaultSteps);
        }
        
        // Set retry policy from metadata or defaults
        if (metadata != null && metadata.containsKey("maxRetries")) {
            builder.maxRetries((Integer) metadata.get("maxRetries"));
        } else {
            builder.maxRetries(3);
        }
        
        SagaDefinition reconstructed = builder.build();
        
        // Cache the reconstructed definition
        sagaRegistry.registerDefinition(sagaType, reconstructed);
        
        return reconstructed;
    }

    private SagaInfo createSagaInfo(SagaExecution execution) {
        return new SagaInfo(
            execution.getSagaId(),
            execution.getDefinition().getSagaType(),
            execution.getStatus(),
            execution.getSagaState().getCreatedAt(),
            execution.getSagaState().getLastUpdated(),
            execution.getCompletedSteps().size(),
            execution.getDefinition().getSteps().size(),
            execution.getSagaData()
        );
    }

    private SagaInfo createSagaInfoFromState(SagaState state) {
        return new SagaInfo(
            state.getSagaId(),
            state.getSagaType(),
            state.getStatus(),
            state.getCreatedAt(),
            state.getLastUpdated(),
            state.getStepStates().size(),
            state.getStepStates().size(), // Approximation
            state.getSagaData()
        );
    }

    /**
     * Saga execution context
     */
    private static class SagaExecution {
        private final String sagaId;
        private final SagaDefinition definition;
        private final SagaState sagaState;
        private final Map<String, StepState> stepStates;
        private volatile SagaStatus status;
        private final long startTime;
        
        public SagaExecution(String sagaId, SagaDefinition definition, SagaState sagaState) {
            this.sagaId = sagaId;
            this.definition = definition;
            this.sagaState = sagaState;
            this.stepStates = new ConcurrentHashMap<>(sagaState.getStepStates());
            this.status = sagaState.getStatus();
            this.startTime = System.currentTimeMillis();
        }
        
        public String getSagaId() { return sagaId; }
        public SagaDefinition getDefinition() { return definition; }
        public SagaState getSagaState() { return sagaState; }
        public SagaStatus getStatus() { return status; }
        public Map<String, Object> getSagaData() { return sagaState.getSagaData(); }
        public Map<String, StepState> getStepStates() { return stepStates; }
        public long getStartTime() { return startTime; }
        
        public boolean hasNextStep() {
            return getNextSteps().size() > 0;
        }
        
        public List<SagaStep> getNextSteps() {
            // Get steps that haven't been executed and have satisfied dependencies
            List<SagaStep> eligibleSteps = definition.getSteps().stream()
                .filter(step -> !stepStates.containsKey(step.getStepId()) || 
                               stepStates.get(step.getStepId()).getStatus() == StepStatus.PENDING)
                .filter(step -> areDependenciesSatisfied(step))
                .collect(Collectors.toList());
            
            // If parallel execution is enabled, return all eligible steps
            if (definition.isParallelExecution()) {
                return eligibleSteps;
            }
            
            // Otherwise, return only the first eligible step for sequential execution
            return eligibleSteps.isEmpty() ? eligibleSteps : 
                   Collections.singletonList(eligibleSteps.get(0));
        }
        
        public List<SagaStep> getCompletedSteps() {
            return definition.getSteps().stream()
                .filter(step -> stepStates.containsKey(step.getStepId()) && 
                               stepStates.get(step.getStepId()).getStatus() == StepStatus.COMPLETED)
                .collect(Collectors.toList());
        }
        
        public boolean isCompleted() {
            return definition.getSteps().stream()
                .allMatch(step -> stepStates.containsKey(step.getStepId()) && 
                                 stepStates.get(step.getStepId()).getStatus() == StepStatus.COMPLETED);
        }
        
        public void handleStepCompletion(String stepId, StepResult result) {
            StepState stepState = stepStates.computeIfAbsent(stepId, 
                id -> new StepState(id, StepStatus.PENDING));
            
            stepState.setStatus(result.getStatus());
            stepState.setResult(result);
            stepState.setCompletedAt(LocalDateTime.now());
            
            if (result.getData() != null) {
                sagaState.getSagaData().putAll(result.getData());
            }
        }
        
        public void cancel(String reason) {
            this.status = SagaStatus.CANCELLED;
        }
        
        public void timeout() {
            this.status = SagaStatus.TIMED_OUT;
        }
        
        private boolean areDependenciesSatisfied(SagaStep step) {
            return step.getDependencies().stream()
                .allMatch(depId -> stepStates.containsKey(depId) && 
                                  stepStates.get(depId).getStatus() == StepStatus.COMPLETED);
        }
    }
    
    /**
     * Publish saga state update event for monitoring and compliance
     */
    private void publishSagaStateUpdateEvent(String sagaId, Map<String, Object> previousStepStates, 
                                           Map<String, Object> newStepStates) {
        try {
            SagaEvent event = SagaEvent.builder()
                .sagaId(sagaId)
                .eventType(com.waqiti.common.events.SagaEventType.SAGA_STEP_STATE_UPDATED.name())
                .previousData(previousStepStates)
                .newData(newStepStates)
                .timestamp(Instant.now())
                .build();
                
            eventPublisher.publishSagaEvent(event);
        } catch (Exception e) {
            // Don't fail the saga for event publishing errors
            log.warn("Failed to publish saga state update event for saga: {}", sagaId, e);
        }
    }
    
    /**
     * Reconstruct a saga step from persisted data
     */
    private SagaStep reconstructStep(Map<String, Object> stepData) {
        String stepName = (String) stepData.get("name");
        String serviceName = (String) stepData.get("service");
        String action = (String) stepData.get("action");
        String compensationAction = (String) stepData.get("compensationAction");
        Boolean canRunInParallel = (Boolean) stepData.getOrDefault("parallel", false);
        Integer maxRetries = (Integer) stepData.getOrDefault("maxRetries", 3);
        Integer timeoutSeconds = (Integer) stepData.getOrDefault("timeoutSeconds", 60);
        
        return SagaStep.builder()
                .stepName(stepName)
                .serviceName(serviceName)
                .action(action)
                .compensationAction(compensationAction)
                .canRunInParallel(canRunInParallel)
                .maxRetries(maxRetries)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
    
    /**
     * Create default steps based on saga type
     */
    private List<SagaStep> createDefaultStepsForType(String sagaType) {
        List<SagaStep> steps = new ArrayList<>();
        
        switch (sagaType) {
            case "PAYMENT_TRANSFER":
                steps.add(createStep("VALIDATE_PAYMENT", "payment-service", "validatePayment", "cancelValidation"));
                steps.add(createStep("DEBIT_SENDER", "wallet-service", "debitWallet", "creditWallet"));
                steps.add(createStep("CREDIT_RECEIVER", "wallet-service", "creditWallet", "debitWallet"));
                steps.add(createStep("UPDATE_LEDGER", "ledger-service", "recordTransaction", "reverseTransaction"));
                steps.add(createStep("SEND_NOTIFICATION", "notification-service", "sendNotification", null));
                break;
                
            case "MERCHANT_PAYMENT":
                steps.add(createStep("VALIDATE_MERCHANT", "merchant-service", "validateMerchant", null));
                steps.add(createStep("PROCESS_PAYMENT", "payment-service", "processPayment", "refundPayment"));
                steps.add(createStep("UPDATE_MERCHANT_BALANCE", "merchant-service", "updateBalance", "reverseBalance"));
                steps.add(createStep("RECORD_TRANSACTION", "transaction-service", "recordTransaction", "cancelTransaction"));
                break;
                
            case "CRYPTO_EXCHANGE":
                steps.add(createStep("VALIDATE_CRYPTO_PAIR", "crypto-service", "validatePair", null));
                steps.add(createStep("LOCK_FUNDS", "wallet-service", "lockFunds", "unlockFunds"));
                steps.add(createStep("EXECUTE_TRADE", "crypto-service", "executeTrade", "reverseTrade"));
                steps.add(createStep("UPDATE_BALANCES", "wallet-service", "updateBalances", "reverseBalances"));
                break;
                
            case "LOAN_DISBURSEMENT":
                steps.add(createStep("VALIDATE_LOAN", "loan-service", "validateLoan", null));
                steps.add(createStep("CHECK_CREDIT", "credit-service", "checkCredit", null));
                steps.add(createStep("DISBURSE_FUNDS", "payment-service", "disburseFunds", "recallFunds"));
                steps.add(createStep("CREATE_REPAYMENT_SCHEDULE", "loan-service", "createSchedule", "cancelSchedule"));
                break;
                
            default:
                // Generic saga steps
                steps.add(createStep("INITIALIZE", "common-service", "initialize", "cleanup"));
                steps.add(createStep("PROCESS", "common-service", "process", "rollback"));
                steps.add(createStep("FINALIZE", "common-service", "finalize", null));
        }
        
        return steps;
    }
    
    /**
     * Helper method to create a saga step
     */
    private SagaStep createStep(String name, String service, String action, String compensation) {
        return SagaStep.builder()
                .stepName(name)
                .serviceName(service)
                .action(action)
                .compensationAction(compensation)
                .maxRetries(3)
                .timeout(Duration.ofSeconds(30))
                .canRunInParallel(false)
                .build();
    }
}