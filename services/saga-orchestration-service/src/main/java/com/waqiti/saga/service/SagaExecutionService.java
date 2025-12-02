package com.waqiti.saga.service;

import com.waqiti.saga.client.AnalyticsServiceClient;
import com.waqiti.saga.client.NotificationServiceClient;
import com.waqiti.saga.domain.Saga;
import com.waqiti.saga.domain.SagaStatus;
import com.waqiti.saga.domain.SagaStep;
import com.waqiti.saga.domain.StepStatus;
import com.waqiti.saga.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for executing and managing sagas
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaExecutionService {

    private final SagaRepository sagaRepository;
    private final SagaStepExecutor stepExecutor;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AnalyticsServiceClient analyticsClient;
    private final NotificationServiceClient notificationClient;

    /**
     * Execute a saga with all its steps
     */
    @Transactional
    public CompletableFuture<SagaExecutionResult> executeSaga(String sagaId) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.info("Starting saga execution: {}", sagaId);

                Saga saga = sagaRepository.findById(UUID.fromString(sagaId))
                    .orElseThrow(() -> new RuntimeException("Saga not found: " + sagaId));

                // Update saga status
                saga.setStatus(SagaStatus.EXECUTING);
                saga.setStartedAt(LocalDateTime.now());
                saga = sagaRepository.save(saga);

                // Execute steps in order
                SagaExecutionResult result = executeStepsSequentially(saga);
                
                // Update final saga status
                saga.setStatus(result.isSuccess() ? SagaStatus.COMPLETED : SagaStatus.FAILED);
                saga.setCompletedAt(LocalDateTime.now());
                saga.setFailureReason(result.getFailureReason());
                sagaRepository.save(saga);

                long executionTime = System.currentTimeMillis() - startTime;
                
                // Record analytics
                analyticsClient.recordSagaExecution(
                    sagaId, 
                    saga.getType(), 
                    saga.getStatus().toString(),
                    executionTime,
                    result.getStepsCompleted(),
                    saga.getSteps().size()
                );

                // Send completion notification if needed
                if (result.isSuccess()) {
                    sendSagaCompletionNotification(saga);
                } else {
                    sendSagaFailureNotification(saga, result.getFailureReason());
                }

                // Publish saga completion event
                publishSagaEvent(saga, result.isSuccess() ? "SAGA_COMPLETED" : "SAGA_FAILED");

                log.info("Saga execution completed: sagaId={}, success={}, executionTime={}ms", 
                    sagaId, result.isSuccess(), executionTime);

                return result;

            } catch (Exception e) {
                log.error("Error executing saga: {}", sagaId, e);
                
                // Update saga as failed
                try {
                    Saga saga = sagaRepository.findById(UUID.fromString(sagaId)).orElse(null);
                    if (saga != null) {
                        saga.setStatus(SagaStatus.FAILED);
                        saga.setCompletedAt(LocalDateTime.now());
                        saga.setFailureReason(e.getMessage());
                        sagaRepository.save(saga);
                    }
                } catch (Exception updateError) {
                    log.error("Failed to update saga status after execution error", updateError);
                }

                return SagaExecutionResult.builder()
                    .sagaId(sagaId)
                    .success(false)
                    .failureReason(e.getMessage())
                    .stepsCompleted(0)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            }
        });
    }

    /**
     * Execute saga steps sequentially
     */
    private SagaExecutionResult executeStepsSequentially(Saga saga) {
        List<SagaStep> steps = saga.getSteps();
        int stepsCompleted = 0;
        
        try {
            for (SagaStep step : steps) {
                log.debug("Executing saga step: sagaId={}, stepName={}", 
                    saga.getId(), step.getStepName());

                // Update step status
                step.setStatus(StepStatus.EXECUTING);
                step.setStartedAt(LocalDateTime.now());

                // Execute the step
                StepExecutionResult stepResult = stepExecutor.executeStep(step, saga.getContext());

                if (stepResult.isSuccess()) {
                    step.setStatus(StepStatus.COMPLETED);
                    step.setCompletedAt(LocalDateTime.now());
                    step.setResponse(stepResult.getResponse());
                    stepsCompleted++;
                    
                    // Update saga context with step results
                    if (stepResult.getContextUpdates() != null) {
                        saga.getContext().putAll(stepResult.getContextUpdates());
                    }
                    
                    log.debug("Saga step completed: sagaId={}, stepName={}", 
                        saga.getId(), step.getStepName());
                } else {
                    step.setStatus(StepStatus.FAILED);
                    step.setCompletedAt(LocalDateTime.now());
                    step.setErrorMessage(stepResult.getErrorMessage());
                    
                    log.error("Saga step failed: sagaId={}, stepName={}, error={}", 
                        saga.getId(), step.getStepName(), stepResult.getErrorMessage());
                    
                    // Execute compensation for completed steps
                    compensateCompletedSteps(saga, stepsCompleted);
                    
                    return SagaExecutionResult.builder()
                        .sagaId(saga.getId().toString())
                        .success(false)
                        .failureReason(stepResult.getErrorMessage())
                        .stepsCompleted(stepsCompleted)
                        .failedStep(step.getStepName())
                        .build();
                }
            }

            return SagaExecutionResult.builder()
                .sagaId(saga.getId().toString())
                .success(true)
                .stepsCompleted(stepsCompleted)
                .build();

        } catch (Exception e) {
            log.error("Unexpected error during saga execution: sagaId={}", saga.getId(), e);
            
            // Execute compensation for completed steps
            compensateCompletedSteps(saga, stepsCompleted);
            
            return SagaExecutionResult.builder()
                .sagaId(saga.getId().toString())
                .success(false)
                .failureReason("Unexpected error: " + e.getMessage())
                .stepsCompleted(stepsCompleted)
                .build();
        }
    }

    /**
     * Compensate completed steps in reverse order
     */
    private void compensateCompletedSteps(Saga saga, int stepsCompleted) {
        log.info("Starting compensation for saga: sagaId={}, stepsToCompensate={}", 
            saga.getId(), stepsCompleted);

        List<SagaStep> completedSteps = saga.getSteps().subList(0, stepsCompleted);
        
        // Compensate in reverse order
        for (int i = completedSteps.size() - 1; i >= 0; i--) {
            SagaStep step = completedSteps.get(i);
            
            try {
                log.debug("Compensating saga step: sagaId={}, stepName={}", 
                    saga.getId(), step.getStepName());

                StepExecutionResult compensationResult = stepExecutor.compensateStep(step, saga.getContext());
                
                if (compensationResult.isSuccess()) {
                    step.setStatus(StepStatus.COMPENSATED);
                    log.debug("Saga step compensation completed: sagaId={}, stepName={}", 
                        saga.getId(), step.getStepName());
                } else {
                    step.setStatus(StepStatus.COMPENSATION_FAILED);
                    log.error("Saga step compensation failed: sagaId={}, stepName={}, error={}", 
                        saga.getId(), step.getStepName(), compensationResult.getErrorMessage());
                }

            } catch (Exception e) {
                log.error("Error during saga step compensation: sagaId={}, stepName={}", 
                    saga.getId(), step.getStepName(), e);
                step.setStatus(StepStatus.COMPENSATION_FAILED);
            }
        }

        saga.setStatus(SagaStatus.COMPENSATED);
        sagaRepository.save(saga);
        
        log.info("Saga compensation completed: sagaId={}", saga.getId());
    }

    /**
     * Get saga execution status
     */
    public SagaExecutionStatus getSagaStatus(String sagaId) {
        try {
            Saga saga = sagaRepository.findById(UUID.fromString(sagaId))
                .orElseThrow(() -> new RuntimeException("Saga not found: " + sagaId));

            int totalSteps = saga.getSteps().size();
            int completedSteps = (int) saga.getSteps().stream()
                .mapToLong(step -> StepStatus.COMPLETED.equals(step.getStatus()) ? 1 : 0)
                .sum();

            return SagaExecutionStatus.builder()
                .sagaId(sagaId)
                .status(saga.getStatus().toString())
                .totalSteps(totalSteps)
                .completedSteps(completedSteps)
                .currentStep(getCurrentStepName(saga))
                .startedAt(saga.getStartedAt())
                .completedAt(saga.getCompletedAt())
                .failureReason(saga.getFailureReason())
                .build();

        } catch (Exception e) {
            log.error("Error getting saga status: {}", sagaId, e);
            return SagaExecutionStatus.builder()
                .sagaId(sagaId)
                .status("ERROR")
                .failureReason(e.getMessage())
                .build();
        }
    }

    private String getCurrentStepName(Saga saga) {
        return saga.getSteps().stream()
            .filter(step -> StepStatus.EXECUTING.equals(step.getStatus()) || 
                           StepStatus.PENDING.equals(step.getStatus()))
            .map(SagaStep::getStepName)
            .findFirst()
            .orElse(null);
    }

    private void sendSagaCompletionNotification(Saga saga) {
        try {
            String userId = (String) saga.getContext().get("userId");
            if (userId != null) {
                notificationClient.sendTransactionCompletionNotification(
                    userId, 
                    saga.getTransactionId(), 
                    saga.getType(),
                    "COMPLETED"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send saga completion notification: sagaId={}", saga.getId(), e);
        }
    }

    private void sendSagaFailureNotification(Saga saga, String reason) {
        try {
            String userId = (String) saga.getContext().get("userId");
            if (userId != null) {
                notificationClient.sendTransactionFailureNotification(
                    userId, 
                    saga.getTransactionId(), 
                    saga.getType(),
                    reason
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send saga failure notification: sagaId={}", saga.getId(), e);
        }
    }

    private void publishSagaEvent(Saga saga, String eventType) {
        try {
            Map<String, Object> event = Map.of(
                "sagaId", saga.getId().toString(),
                "transactionId", saga.getTransactionId(),
                "sagaType", saga.getType(),
                "eventType", eventType,
                "timestamp", LocalDateTime.now().toString(),
                "context", saga.getContext()
            );

            kafkaTemplate.send("saga-events", event);
            log.debug("Published saga event: sagaId={}, eventType={}", saga.getId(), eventType);

        } catch (Exception e) {
            log.warn("Failed to publish saga event: sagaId={}", saga.getId(), e);
        }
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class SagaExecutionResult {
        private String sagaId;
        private boolean success;
        private String failureReason;
        private String failedStep;
        private int stepsCompleted;
        private long executionTimeMs;
    }

    @lombok.Data
    @lombok.Builder  
    public static class SagaExecutionStatus {
        private String sagaId;
        private String status;
        private int totalSteps;
        private int completedSteps;
        private String currentStep;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String failureReason;
    }

    @lombok.Data
    @lombok.Builder
    public static class StepExecutionResult {
        private boolean success;
        private String errorMessage;
        private Map<String, Object> response;
        private Map<String, Object> contextUpdates;
    }
}