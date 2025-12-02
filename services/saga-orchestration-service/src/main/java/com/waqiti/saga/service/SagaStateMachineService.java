package com.waqiti.saga.service;

import com.waqiti.common.saga.SagaStepEvent;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.domain.SagaStatus;
import com.waqiti.saga.domain.SagaStepStatus;
import com.waqiti.saga.kafka.SagaStepProducer;
import com.waqiti.saga.repository.SagaExecutionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX: Saga State Machine Service
 *
 * Manages saga state transitions and orchestrates step execution.
 * This service was missing the logic to properly handle saga-step-results
 * and trigger next steps or compensation.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @since 2.0.0
 * @priority P0-CRITICAL
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SagaStateMachineService {

    private final SagaExecutionRepository sagaExecutionRepository;
    private final SagaStepProducer sagaStepProducer;
    private final MeterRegistry meterRegistry;

    /**
     * Process successful step execution.
     *
     * @return true if saga is complete, false if more steps remain
     */
    @Transactional
    public boolean processStepSuccess(String sagaId, String stepName,
                                     Map<String, Object> stepData,
                                     LocalDateTime completedAt) {
        log.info("Processing step success: sagaId={}, step={}", sagaId, stepName);

        // Load saga execution
        SagaExecution saga = sagaExecutionRepository.findBySagaId(sagaId)
            .orElseThrow(() -> new IllegalStateException("Saga not found: " + sagaId));

        // Update step status
        saga.updateStepStatus(stepName, SagaStepStatus.COMPLETED, null, stepData);
        saga.setLastUpdatedAt(completedAt);

        // Check if all steps complete
        if (saga.areAllStepsCompleted()) {
            saga.setStatus(SagaStatus.COMPLETED);
            saga.setCompletedAt(LocalDateTime.now());
            sagaExecutionRepository.save(saga);

            log.info("✅ All saga steps completed successfully: sagaId={}", sagaId);
            return true;
        }

        // Proceed to next step
        String nextStep = saga.getNextPendingStep();
        if (nextStep != null) {
            saga.updateStepStatus(nextStep, SagaStepStatus.RUNNING, null, null);
            sagaExecutionRepository.save(saga);

            // Publish next step event
            SagaStepEvent nextStepEvent = buildStepEvent(saga, nextStep, false);
            sagaStepProducer.publishStepEvent(nextStepEvent);

            log.info("Published next saga step: sagaId={}, step={}", sagaId, nextStep);
        } else {
            log.warn("No more pending steps but saga not complete: sagaId={}", sagaId);
        }

        return false;
    }

    /**
     * Process step failure and trigger compensation.
     *
     * @return true if compensation was triggered
     */
    @Transactional
    public boolean processStepFailure(String sagaId, String stepName,
                                     String errorMessage, String errorCode,
                                     LocalDateTime failedAt) {
        log.error("Processing step failure: sagaId={}, step={}, error={}",
                sagaId, stepName, errorMessage);

        // Load saga execution
        SagaExecution saga = sagaExecutionRepository.findBySagaId(sagaId)
            .orElseThrow(() -> new IllegalStateException("Saga not found: " + sagaId));

        // Update step status to failed
        saga.updateStepStatus(stepName, SagaStepStatus.FAILED, errorMessage, null);
        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setLastUpdatedAt(failedAt);
        saga.setErrorMessage(errorMessage);
        saga.setErrorCode(errorCode);

        sagaExecutionRepository.save(saga);

        // Trigger compensation for all completed steps (in reverse order)
        List<String> completedSteps = saga.getCompletedStepsInReverseOrder();

        if (completedSteps.isEmpty()) {
            // No steps to compensate, mark saga as failed
            saga.setStatus(SagaStatus.FAILED);
            saga.setCompletedAt(LocalDateTime.now());
            sagaExecutionRepository.save(saga);

            log.info("No compensation needed, saga marked as failed: sagaId={}", sagaId);
            meterRegistry.counter("saga.failed.no_compensation").increment();
            return false;
        }

        // Publish compensation events for all completed steps
        for (String completedStep : completedSteps) {
            SagaStepEvent compensationEvent = buildStepEvent(saga, completedStep, true);
            sagaStepProducer.publishStepEvent(compensationEvent);

            saga.updateStepStatus(completedStep, SagaStepStatus.COMPENSATING, null, null);

            log.info("Published compensation event: sagaId={}, step={}", sagaId, completedStep);
        }

        sagaExecutionRepository.save(saga);
        meterRegistry.counter("saga.compensation.triggered").increment();

        log.warn("Compensation triggered for {} steps: sagaId={}", completedSteps.size(), sagaId);
        return true;
    }

    /**
     * Process step retry request.
     */
    @Transactional
    public void processStepRetry(String sagaId, String stepName, String errorMessage) {
        log.info("Processing step retry: sagaId={}, step={}", sagaId, stepName);

        SagaExecution saga = sagaExecutionRepository.findBySagaId(sagaId)
            .orElseThrow(() -> new IllegalStateException("Saga not found: " + sagaId));

        int currentAttempt = saga.incrementStepAttempt(stepName);
        int maxAttempts = saga.getMaxAttemptsForStep(stepName);

        if (currentAttempt >= maxAttempts) {
            // Max retries exceeded, treat as failure
            log.error("Max retry attempts exceeded: sagaId={}, step={}, attempts={}/{}",
                    sagaId, stepName, currentAttempt, maxAttempts);

            processStepFailure(sagaId, stepName,
                "Max retry attempts exceeded: " + errorMessage,
                "MAX_RETRIES_EXCEEDED",
                LocalDateTime.now());
            return;
        }

        // Retry the step
        saga.updateStepStatus(stepName, SagaStepStatus.RETRYING, errorMessage, null);
        sagaExecutionRepository.save(saga);

        // Publish retry event (with backoff delay handled by Kafka retry topic)
        SagaStepEvent retryEvent = buildStepEvent(saga, stepName, false);
        retryEvent.setAttemptNumber(currentAttempt + 1);
        sagaStepProducer.publishStepEvent(retryEvent);

        log.info("Published retry event: sagaId={}, step={}, attempt={}/{}",
                sagaId, stepName, currentAttempt + 1, maxAttempts);
    }

    /**
     * Build saga step event for publication.
     */
    private SagaStepEvent buildStepEvent(SagaExecution saga, String stepName, boolean compensation) {
        return SagaStepEvent.builder()
            .sagaId(saga.getSagaId())
            .sagaType(saga.getSagaType().name())
            .stepName(stepName)
            .serviceName(saga.getServiceNameForStep(stepName))
            .operation(saga.getOperationForStep(stepName))
            .data(saga.getDataForStep(stepName))
            .compensation(compensation)
            .attemptNumber(saga.getStepAttempt(stepName))
            .maxAttempts(saga.getMaxAttemptsForStep(stepName))
            .correlationId(saga.getCorrelationId())
            .transactionId(saga.getTransactionId())
            .timestamp(LocalDateTime.now())
            .build();
    }
}
