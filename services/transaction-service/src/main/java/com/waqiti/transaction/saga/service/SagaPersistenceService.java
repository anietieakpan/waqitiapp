package com.waqiti.transaction.saga.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.transaction.saga.domain.SagaExecution;
import com.waqiti.transaction.saga.domain.SagaStepExecution;
import com.waqiti.transaction.saga.repository.SagaExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRITICAL SECURITY SERVICE: Saga state persistence and recovery
 * Ensures sagas can be resumed or compensated after crashes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaPersistenceService {

    private final SagaExecutionRepository sagaExecutionRepository;
    private final ObjectMapper objectMapper;

    // Configuration
    private static final int SAGA_STALE_MINUTES = 5;  // Consider saga stale after 5 minutes of no updates
    private static final int SAGA_RETENTION_DAYS = 30;  // Keep completed sagas for 30 days
    private static final long SAGA_TIMEOUT_SECONDS = 300;  // 5 minutes default timeout

    /**
     * Create and persist a new saga execution
     */
    @Transactional
    public SagaExecution createSaga(UUID transactionId, String sagaType, int totalSteps, Object payload) {
        return createSaga(transactionId, sagaType, totalSteps, payload, SAGA_TIMEOUT_SECONDS);
    }

    /**
     * Create and persist a new saga execution with custom timeout
     */
    @Transactional
    public SagaExecution createSaga(UUID transactionId, String sagaType, int totalSteps, Object payload, long timeoutSeconds) {
        try {
            LocalDateTime now = LocalDateTime.now();

            SagaExecution saga = SagaExecution.builder()
                .sagaId(UUID.randomUUID())
                .transactionId(transactionId)
                .sagaType(sagaType)
                .status(SagaExecution.SagaStatus.PENDING)
                .currentStep(0)
                .totalSteps(totalSteps)
                .payload(objectMapper.writeValueAsString(payload))
                .retryCount(0)
                .maxRetries(3)
                .timeoutAt(now.plusSeconds(timeoutSeconds))
                .createdAt(now)
                .lastUpdatedAt(now)
                .build();

            saga = sagaExecutionRepository.save(saga);

            log.info("SAGA PERSISTENCE: Created saga {} for transaction {} with {} steps, timeout at {}",
                saga.getSagaId(), transactionId, totalSteps, saga.getTimeoutAt());

            return saga;

        } catch (JsonProcessingException e) {
            log.error("SAGA PERSISTENCE ERROR: Failed to serialize saga payload for transaction {}", transactionId, e);
            throw new RuntimeException("Failed to create saga", e);
        }
    }

    /**
     * Add a step to saga
     */
    @Transactional
    public void addStep(UUID sagaId, int stepNumber, String stepName, Object inputData, String idempotencyKey) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        try {
            SagaStepExecution step = SagaStepExecution.builder()
                .stepNumber(stepNumber)
                .stepName(stepName)
                .status(SagaStepExecution.StepStatus.PENDING)
                .inputData(objectMapper.writeValueAsString(inputData))
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();

            saga.addStep(step);
            sagaExecutionRepository.save(saga);

            log.debug("SAGA PERSISTENCE: Added step {} '{}' to saga {}", stepNumber, stepName, sagaId);

        } catch (JsonProcessingException e) {
            log.error("SAGA PERSISTENCE ERROR: Failed to serialize step input data for saga {}", sagaId, e);
            throw new RuntimeException("Failed to add saga step", e);
        }
    }

    /**
     * Start saga execution
     */
    @Transactional
    public void startSaga(UUID sagaId) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        saga.start();
        sagaExecutionRepository.save(saga);

        log.info("SAGA PERSISTENCE: Started saga {} for transaction {}", sagaId, saga.getTransactionId());
    }

    /**
     * Mark saga step as started
     */
    @Transactional
    public void startStep(UUID sagaId, int stepNumber) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        SagaStepExecution step = saga.getSteps().stream()
            .filter(s -> s.getStepNumber() == stepNumber)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepNumber));

        step.start();
        saga.setCurrentStep(stepNumber);
        saga.setLastUpdatedAt(LocalDateTime.now());

        sagaExecutionRepository.save(saga);

        log.debug("SAGA PERSISTENCE: Started step {} in saga {}", stepNumber, sagaId);
    }

    /**
     * Mark saga step as completed
     */
    @Transactional
    public void completeStep(UUID sagaId, int stepNumber, Object output) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        try {
            String outputJson = output != null ? objectMapper.writeValueAsString(output) : null;
            saga.completeStep(stepNumber, outputJson);

            sagaExecutionRepository.save(saga);

            log.debug("SAGA PERSISTENCE: Completed step {} in saga {}", stepNumber, sagaId);

        } catch (JsonProcessingException e) {
            log.error("SAGA PERSISTENCE ERROR: Failed to serialize step output for saga {}", sagaId, e);
            throw new RuntimeException("Failed to complete saga step", e);
        }
    }

    /**
     * Mark saga step as failed
     */
    @Transactional
    public void failStep(UUID sagaId, int stepNumber, String error) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        saga.failStep(stepNumber, error);
        sagaExecutionRepository.save(saga);

        log.warn("SAGA PERSISTENCE: Failed step {} in saga {}: {}", stepNumber, sagaId, error);
    }

    /**
     * Start saga compensation
     */
    @Transactional
    public void startCompensation(UUID sagaId) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        saga.startCompensation();
        sagaExecutionRepository.save(saga);

        log.warn("SAGA PERSISTENCE: Started compensation for saga {}", sagaId);
    }

    /**
     * Complete saga successfully
     */
    @Transactional
    public void completeSaga(UUID sagaId) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        saga.complete();
        sagaExecutionRepository.save(saga);

        log.info("SAGA PERSISTENCE: Completed saga {} for transaction {}", sagaId, saga.getTransactionId());
    }

    /**
     * Mark saga as compensated (successfully rolled back)
     */
    @Transactional
    public void compensateSaga(UUID sagaId) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        saga.compensate();
        sagaExecutionRepository.save(saga);

        log.warn("SAGA PERSISTENCE: Compensated saga {} for transaction {}", sagaId, saga.getTransactionId());
    }

    /**
     * Mark saga as failed
     */
    @Transactional
    public void failSaga(UUID sagaId, String error) {
        SagaExecution saga = sagaExecutionRepository.findById(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        saga.fail(error);
        sagaExecutionRepository.save(saga);

        log.error("SAGA PERSISTENCE: Failed saga {} for transaction {}: {}", sagaId, saga.getTransactionId(), error);
    }

    /**
     * Get saga execution by ID
     */
    @Transactional(readOnly = true)
    public Optional<SagaExecution> getSagaExecution(UUID sagaId) {
        return sagaExecutionRepository.findById(sagaId);
    }

    /**
     * Get saga execution by transaction ID
     */
    @Transactional(readOnly = true)
    public Optional<SagaExecution> getSagaByTransactionId(UUID transactionId) {
        return sagaExecutionRepository.findByTransactionId(transactionId);
    }

    /**
     * CRITICAL: Recover stale sagas (for crash recovery)
     * Scheduled to run every minute
     */
    @Scheduled(fixedRate = 60000)  // Every minute
    @Transactional
    public void recoverStaleSagas() {
        try {
            LocalDateTime staleSince = LocalDateTime.now().minusMinutes(SAGA_STALE_MINUTES);
            List<SagaExecution> staleSagas = sagaExecutionRepository.findStaleSagas(staleSince);

            if (!staleSagas.isEmpty()) {
                log.warn("SAGA RECOVERY: Found {} stale sagas that need recovery", staleSagas.size());

                for (SagaExecution saga : staleSagas) {
                    recoverSaga(saga);
                }
            }

        } catch (Exception e) {
            log.error("SAGA RECOVERY ERROR: Failed to recover stale sagas", e);
        }
    }

    /**
     * CRITICAL: Handle timed out sagas
     * Scheduled to run every minute
     */
    @Scheduled(fixedRate = 60000)  // Every minute
    @Transactional
    public void handleTimedOutSagas() {
        try {
            List<SagaExecution> timedOutSagas = sagaExecutionRepository.findTimedOutSagas(LocalDateTime.now());

            if (!timedOutSagas.isEmpty()) {
                log.warn("SAGA TIMEOUT: Found {} timed out sagas", timedOutSagas.size());

                for (SagaExecution saga : timedOutSagas) {
                    saga.timeout();
                    saga.startCompensation();
                    sagaExecutionRepository.save(saga);

                    log.error("SAGA TIMEOUT: Saga {} timed out, starting compensation", saga.getSagaId());
                }
            }

        } catch (Exception e) {
            log.error("SAGA TIMEOUT ERROR: Failed to handle timed out sagas", e);
        }
    }

    /**
     * Clean up old completed sagas
     * Scheduled to run daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldSagas() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(SAGA_RETENTION_DAYS);
            int deleted = sagaExecutionRepository.deleteCompletedSagasOlderThan(cutoff);

            if (deleted > 0) {
                log.info("SAGA CLEANUP: Deleted {} completed sagas older than {} days", deleted, SAGA_RETENTION_DAYS);
            }

        } catch (Exception e) {
            log.error("SAGA CLEANUP ERROR: Failed to cleanup old sagas", e);
        }
    }

    /**
     * Recover a specific saga after crash
     */
    private void recoverSaga(SagaExecution saga) {
        log.warn("SAGA RECOVERY: Attempting to recover saga {} (type: {}, current step: {}/{})",
            saga.getSagaId(), saga.getSagaType(), saga.getCurrentStep(), saga.getTotalSteps());

        // Check if saga has timed out
        if (saga.hasTimedOut()) {
            saga.timeout();
            saga.startCompensation();
            sagaExecutionRepository.save(saga);
            log.error("SAGA RECOVERY: Saga {} has timed out, marked for compensation", saga.getSagaId());
            return;
        }

        // Check if saga has exceeded retries
        if (saga.hasExceededRetries()) {
            saga.fail("Exceeded maximum retry attempts");
            sagaExecutionRepository.save(saga);
            log.error("SAGA RECOVERY: Saga {} exceeded max retries, marked as failed", saga.getSagaId());
            return;
        }

        // Increment retry counter
        saga.incrementRetry();

        // For now, mark for manual intervention
        // In production, integrate with saga orchestrator to resume execution
        log.warn("SAGA RECOVERY: Saga {} marked for manual recovery (retry {}/{})",
            saga.getSagaId(), saga.getRetryCount(), saga.getMaxRetries());

        sagaExecutionRepository.save(saga);
    }
}
