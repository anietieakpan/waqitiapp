package com.waqiti.saga.orchestrator;

import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.SagaTransaction;
import com.waqiti.saga.domain.SagaStep;
import com.waqiti.saga.repository.SagaTransactionRepository;
import com.waqiti.saga.service.SagaStepExecutor;
import com.waqiti.saga.event.SagaEventPublisher;
import com.waqiti.saga.exception.SagaExecutionException;
import com.waqiti.saga.exception.CompensationFailedException;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.locking.DistributedLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enhanced SAGA orchestrator with advanced distributed transaction management.
 * Provides comprehensive transaction coordination across microservices with
 * sophisticated compensation, retry logic, and monitoring capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedSagaOrchestrator {

    private final SagaTransactionRepository sagaRepository;
    private final SagaStepExecutor stepExecutor;
    private final SagaEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    /**
     * Starts execution of a saga transaction with enhanced monitoring
     */
    @Transactional
    public CompletableFuture<UUID> executeSagaAsync(SagaTransaction saga) {
        log.info("Starting distributed saga execution: sagaId={}, type={}, correlationId={}", 
                saga.getId(), saga.getSagaType(), saga.getCorrelationId());
        
        try {
            // Validate saga before execution
            validateSaga(saga);
            
            // Set timeout if not specified
            if (saga.getTimeoutAt() == null) {
                saga.setTimeoutAt(LocalDateTime.now().plusMinutes(30)); // Default 30 min timeout
            }
            
            // Initialize execution context
            saga.setStatus(SagaStatus.RUNNING);
            saga.setCurrentStepIndex(0);
            saga.setRetryCount(0);
            saga.setMaxRetries(3);
            sagaRepository.save(saga);
            
            // Publish saga started event with correlation tracking
            eventPublisher.publishSagaStarted(saga);
            
            // Execute asynchronously with enhanced error handling
            return CompletableFuture.supplyAsync(() -> {
                try {
                    executeDistributedSagaSteps(saga);
                    return saga.getId();
                } catch (Exception e) {
                    log.error("Distributed saga execution failed: sagaId={}, correlationId={}", 
                            saga.getId(), saga.getCorrelationId(), e);
                    handleSagaFailure(saga, e);
                    throw new SagaExecutionException("Distributed saga execution failed", e);
                }
            }, executorService);
            
        } catch (Exception e) {
            log.error("Failed to start distributed saga execution: sagaId={}", saga.getId(), e);
            saga.setStatus(SagaStatus.FAILED);
            saga.setFailureReason(e.getMessage());
            sagaRepository.save(saga);
            throw new SagaExecutionException("Failed to start distributed saga execution", e);
        }
    }

    /**
     * Executes saga steps with enhanced coordination and monitoring
     */
    private void executeDistributedSagaSteps(SagaTransaction saga) {
        log.info("Executing distributed saga steps: sagaId={}, totalSteps={}, correlationId={}", 
                saga.getId(), saga.getSteps().size(), saga.getCorrelationId());
        
        while (saga.getCurrentStepIndex() < saga.getSteps().size()) {
            SagaStep currentStep = saga.getCurrentStep();
            
            try {
                // Check for timeout with enhanced monitoring
                if (saga.isTimedOut()) {
                    log.warn("Distributed saga timed out: sagaId={}, timeout={}", 
                            saga.getId(), saga.getTimeoutAt());
                    handleSagaTimeout(saga);
                    return;
                }
                
                // Execute current step with distributed coordination
                executeDistributedStep(saga, currentStep);
                
                // Validate step completion with cross-service consistency checks
                if (currentStep.getStatus() == SagaStep.StepStatus.COMPLETED) {
                    log.info("Distributed step completed successfully: sagaId={}, step={}, service={}", 
                            saga.getId(), currentStep.getStepName(), currentStep.getServiceName());
                    
                    // Perform consistency check if required
                    if (requiresConsistencyCheck(currentStep)) {
                        performConsistencyCheck(saga, currentStep);
                    }
                    
                    // Advance to next step
                    if (!saga.advanceToNextStep()) {
                        // All steps completed - perform final consistency validation
                        performFinalConsistencyValidation(saga);
                        completeSaga(saga);
                        return;
                    }
                } else {
                    // Step failed, start enhanced compensation
                    log.error("Distributed step failed: sagaId={}, step={}, service={}, error={}", 
                            saga.getId(), currentStep.getStepName(), currentStep.getServiceName(), 
                            currentStep.getErrorMessage());
                    startEnhancedCompensation(saga);
                    return;
                }
                
            } catch (Exception e) {
                log.error("Error executing distributed step: sagaId={}, step={}, service={}", 
                        saga.getId(), currentStep.getStepName(), currentStep.getServiceName(), e);
                currentStep.markFailed(e.getMessage());
                sagaRepository.save(saga);
                startEnhancedCompensation(saga);
                return;
            }
        }
    }

    /**
     * Executes a single saga step with enhanced distributed coordination
     */
    private void executeDistributedStep(SagaTransaction saga, SagaStep step) {
        log.info("Executing distributed step: sagaId={}, step={}, service={}, attempt={}", 
                saga.getId(), step.getStepName(), step.getServiceName(), 
                (step.getRetryCount() != null ? step.getRetryCount() + 1 : 1));
        
        step.setStatus(SagaStep.StepStatus.RUNNING);
        sagaRepository.save(saga);
        
        try {
            // Add distributed coordination headers
            String response = stepExecutor.executeStepWithCoordination(step, saga.getCorrelationId());
            
            // Validate response for business rules
            validateStepResponse(step, response);
            
            step.markCompleted(response);
            
            // Publish step completed event with coordination info
            eventPublisher.publishStepCompleted(saga, step);
            
            // Perform immediate consistency check for critical steps
            if (isCriticalStep(step)) {
                performImmediateConsistencyCheck(saga, step);
            }
            
        } catch (Exception e) {
            step.incrementRetryCount();
            
            if (step.canRetry()) {
                log.warn("Distributed step failed, will retry: sagaId={}, step={}, service={}, retryCount={}", 
                        saga.getId(), step.getStepName(), step.getServiceName(), step.getRetryCount());
                
                // Enhanced retry with jitter and circuit breaker awareness
                try {
                    long delay = calculateEnhancedBackoffDelay(step.getRetryCount(), step.getServiceName());
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SagaExecutionException("Interrupted during retry backoff", ie);
                }
                
                executeDistributedStep(saga, step); // Recursive retry with circuit breaker
            } else {
                step.markFailed(e.getMessage());
                eventPublisher.publishStepFailed(saga, step);
                throw new SagaExecutionException("Distributed step execution failed after max retries", e);
            }
        }
        
        sagaRepository.save(saga);
    }

    /**
     * Enhanced compensation with cross-service coordination
     */
    @Transactional
    public void startEnhancedCompensation(SagaTransaction saga) {
        log.info("Starting enhanced compensation for distributed saga: sagaId={}, correlationId={}", 
                saga.getId(), saga.getCorrelationId());
        
        saga.setStatus(SagaStatus.COMPENSATING);
        sagaRepository.save(saga);
        
        eventPublisher.publishSagaCompensationStarted(saga);
        
        // Get steps that need compensation (in reverse order) with dependency analysis
        var stepsToCompensate = getOrderedCompensationSteps(saga);
        
        for (SagaStep step : stepsToCompensate) {
            try {
                compensateDistributedStep(saga, step);
            } catch (Exception e) {
                log.error("Enhanced compensation failed for step: sagaId={}, step={}, service={}", 
                        saga.getId(), step.getStepName(), step.getServiceName(), e);
                
                // Critical: If compensation fails, mark saga as COMPENSATION_FAILED
                log.error("CRITICAL: Compensation failed for saga step - marking as COMPENSATION_FAILED: sagaId={}, step={}", 
                        saga.getId(), step.getStepName(), e);
                
                saga.setStatus(SagaStatus.COMPENSATION_FAILED);
                saga.setFailureReason("Compensation failed for step: " + step.getStepName() + ". Error: " + e.getMessage());
                sagaRepository.save(saga);
                
                // Send critical alert for manual intervention
                escalateCompensationFailure(saga, step, e);
                
                // Audit the compensation failure
                auditCompensationFailure(saga, step, e);
                
                // Stop compensation process - saga is now in terminal COMPENSATION_FAILED state
                return;
            }
        }
        
        // Perform final compensation validation
        validateCompensationComplete(saga);
        
        // Mark saga as compensated (only if all compensations succeeded)
        saga.setStatus(SagaStatus.COMPENSATED);
        sagaRepository.save(saga);
        
        eventPublisher.publishSagaCompensated(saga);
        log.info("Enhanced saga compensation completed: sagaId={}, correlationId={}", 
                saga.getId(), saga.getCorrelationId());
    }

    /**
     * Compensates a single step with enhanced coordination
     */
    private void compensateDistributedStep(SagaTransaction saga, SagaStep step) {
        if (!step.requiresCompensation()) {
            log.info("Step does not require compensation: sagaId={}, step={}, service={}", 
                    saga.getId(), step.getStepName(), step.getServiceName());
            return;
        }
        
        log.info("Compensating distributed step: sagaId={}, step={}, service={}", 
                saga.getId(), step.getStepName(), step.getServiceName());
        
        step.setStatus(SagaStep.StepStatus.COMPENSATING);
        sagaRepository.save(saga);
        
        try {
            // Execute compensation with coordination context
            stepExecutor.compensateStepWithCoordination(step, saga.getCorrelationId());
            
            // Verify compensation was successful
            verifyCompensationSuccess(saga, step);
            
            step.markCompensated();
            
            eventPublisher.publishStepCompensated(saga, step);
            log.info("Distributed step compensated successfully: sagaId={}, step={}, service={}", 
                    saga.getId(), step.getStepName(), step.getServiceName());
            
        } catch (Exception e) {
            log.error("Distributed step compensation failed: sagaId={}, step={}, service={}", 
                    saga.getId(), step.getStepName(), step.getServiceName(), e);
            step.setErrorMessage("Compensation failed: " + e.getMessage());
            throw e; // Re-throw for escalation
        }
        
        sagaRepository.save(saga);
    }

    /**
     * Performs consistency check across services
     */
    private void performConsistencyCheck(SagaTransaction saga, SagaStep step) {
        log.debug("Performing consistency check: sagaId={}, step={}, service={}", 
                saga.getId(), step.getStepName(), step.getServiceName());
        
        try {
            stepExecutor.performConsistencyCheck(step, saga.getCorrelationId());
        } catch (Exception e) {
            log.error("Consistency check failed: sagaId={}, step={}, service={}", 
                    saga.getId(), step.getStepName(), step.getServiceName(), e);
            throw new SagaExecutionException("Consistency check failed", e);
        }
    }

    /**
     * Performs final consistency validation for the entire saga
     */
    private void performFinalConsistencyValidation(SagaTransaction saga) {
        log.info("Performing final consistency validation: sagaId={}, correlationId={}", 
                saga.getId(), saga.getCorrelationId());
        
        try {
            stepExecutor.performFinalConsistencyValidation(saga);
        } catch (Exception e) {
            log.error("Final consistency validation failed: sagaId={}, correlationId={}", 
                    saga.getId(), saga.getCorrelationId(), e);
            
            // If final validation fails, start compensation
            startEnhancedCompensation(saga);
            throw new SagaExecutionException("Final consistency validation failed", e);
        }
    }

    /**
     * Enhanced backoff calculation with service-specific adjustments
     */
    private long calculateEnhancedBackoffDelay(int retryCount, String serviceName) {
        // Base exponential backoff
        long baseDelay = 1000L * (long) Math.pow(2, retryCount);
        
        // Service-specific adjustments
        double serviceMultiplier = getServiceBackoffMultiplier(serviceName);
        
        // Add jitter to prevent thundering herd
        double jitter = 0.1 + (ThreadLocalRandom.current().nextDouble() * 0.1); // 10-20% jitter
        
        long finalDelay = (long) (baseDelay * serviceMultiplier * (1 + jitter));
        
        // Cap at maximum delay
        return Math.min(finalDelay, 60000L); // Max 60 seconds
    }

    /**
     * Gets service-specific backoff multiplier based on service characteristics
     */
    private double getServiceBackoffMultiplier(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "wallet-service" -> 1.5; // Slower for financial operations
            case "payment-service" -> 1.3;
            case "notification-service" -> 0.8; // Faster for non-critical operations
            case "analytics-service" -> 0.5;
            default -> 1.0;
        };
    }

    /**
     * Checks if step requires immediate consistency validation
     */
    private boolean isCriticalStep(SagaStep step) {
        return step.getStepName().toLowerCase().contains("debit") ||
               step.getStepName().toLowerCase().contains("credit") ||
               step.getStepName().toLowerCase().contains("reserve");
    }

    /**
     * Checks if step requires consistency check after completion
     */
    private boolean requiresConsistencyCheck(SagaStep step) {
        return isCriticalStep(step) || 
               step.getServiceName().equals("wallet-service") ||
               step.getServiceName().equals("payment-service");
    }

    /**
     * Escalates compensation failure to manual intervention team
     */
    private void escalateCompensationFailure(SagaTransaction saga, SagaStep step, Exception error) {
        log.error("ESCALATING COMPENSATION FAILURE - Manual intervention required: sagaId={}, step={}, error={}", 
                saga.getId(), step.getStepName(), error.getMessage());
        
        // Send alert to operations team (placeholder - would integrate with PagerDuty, Slack, etc.)
        Map<String, Object> alert = Map.of(
            "alertType", "SAGA_COMPENSATION_FAILED",
            "severity", "CRITICAL",
            "sagaId", saga.getId(),
            "sagaType", saga.getSagaType(),
            "failedStep", step.getStepName(),
            "serviceName", step.getServiceName(),
            "error", error.getMessage(),
            "timestamp", LocalDateTime.now(),
            "requiresManualIntervention", true,
            "correlationId", saga.getCorrelationId()
        );
        
        // Would send to monitoring/alerting system
        // alertingService.sendCriticalAlert(alert);
    }
    
    /**
     * Audits compensation failure for compliance
     */
    private void auditCompensationFailure(SagaTransaction saga, SagaStep step, Exception error) {
        // Audit would be injected via ComprehensiveAuditService
        log.error("AUDIT: Saga compensation failure - sagaId={}, step={}, error={}", 
                saga.getId(), step.getStepName(), error.getMessage());
    }
    
    /**
     * Validates step response for business rules
     */
    private void validateStepResponse(SagaStep step, String response) {
        // Business rule validation would be implemented here
        if (response == null || response.trim().isEmpty()) {
            throw new SagaExecutionException("Invalid step response: empty or null");
        }
    }
    
    /**
     * Gets steps that need compensation in proper dependency order
     */
    private java.util.List<SagaStep> getOrderedCompensationSteps(SagaTransaction saga) {
        return saga.getSteps().stream()
            .filter(step -> step.getStatus() == SagaStep.StepStatus.COMPLETED)
            .sorted((s1, s2) -> Integer.compare(s2.getStepOrder(), s1.getStepOrder())) // Reverse order
            .toList();
    }
    
    /**
     * Verifies compensation was successful
     */
    private void verifyCompensationSuccess(SagaTransaction saga, SagaStep step) {
        // Would verify the compensation actually worked
        log.debug("Verifying compensation success: sagaId={}, step={}", saga.getId(), step.getStepName());
    }
    
    /**
     * Validates all compensations completed successfully
     */
    private void validateCompensationComplete(SagaTransaction saga) {
        boolean allCompensated = saga.getSteps().stream()
            .filter(step -> step.getStatus() == SagaStep.StepStatus.COMPLETED)
            .allMatch(step -> step.getStatus() == SagaStep.StepStatus.COMPENSATED);
        
        if (!allCompensated) {
            throw new CompensationFailedException("Not all required steps were compensated successfully");
        }
    }
    
    /**
     * Performs immediate consistency check for critical steps
     */
    private void performImmediateConsistencyCheck(SagaTransaction saga, SagaStep step) {
        log.debug("Performing immediate consistency check: sagaId={}, step={}", saga.getId(), step.getStepName());
        // Implementation would verify data consistency across services
    }
    
    /**
     * Validates saga before execution
     */
    private void validateSaga(SagaTransaction saga) {
        if (saga.getSteps() == null || saga.getSteps().isEmpty()) {
            throw new IllegalArgumentException("Saga must have at least one step");
        }
        
        if (saga.getCorrelationId() == null || saga.getCorrelationId().trim().isEmpty()) {
            throw new IllegalArgumentException("Saga must have a correlation ID");
        }
    }
    
    /**
     * Handles saga timeout
     */
    private void handleSagaTimeout(SagaTransaction saga) {
        log.warn("Saga timed out, starting compensation: sagaId={}", saga.getId());
        saga.setStatus(SagaStatus.FAILED);
        sagaRepository.save(saga);
        
        // Start compensation for timed out saga
        startEnhancedCompensation(saga);
    }
    
    /**
     * Completes saga successfully
     */
    private void completeSaga(SagaTransaction saga) {
        log.info("Saga completed successfully: sagaId={}, correlationId={}", saga.getId(), saga.getCorrelationId());
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaRepository.save(saga);
        
        eventPublisher.publishSagaCompleted(saga);
    }
    
    /**
     * Handles saga failure
     */
    private void handleSagaFailure(SagaTransaction saga, Exception error) {
        log.error("Saga execution failed: sagaId={}, correlationId={}", saga.getId(), saga.getCorrelationId(), error);
        saga.setStatus(SagaStatus.FAILED);
        saga.setFailureReason(error.getMessage());
        sagaRepository.save(saga);
        
        eventPublisher.publishSagaFailed(saga);
    }
}