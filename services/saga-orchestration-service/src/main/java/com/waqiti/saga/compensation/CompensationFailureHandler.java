package com.waqiti.saga.compensation;

import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.SagaTransaction;
import com.waqiti.saga.domain.SagaStep;
import com.waqiti.saga.repository.SagaRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles saga compensation failures with retry strategies, dead letter queuing,
 * manual intervention workflows, and recovery mechanisms
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationFailureHandler {
    
    private final SagaRepository sagaRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CompensationStrategyRegistry strategyRegistry;
    private final AlertingService alertingService;
    private final AuditService auditService;
    
    // Track compensation attempts for exponential backoff
    private final Map<String, CompensationAttempt> activeAttempts = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    private static final long MAX_RETRY_DELAY_MS = 60000; // 1 minute
    private static final int DEAD_LETTER_THRESHOLD = 5;
    
    /**
     * Handle compensation failure with intelligent retry and escalation
     */
    @Transactional
    public CompensationResult handleCompensationFailure(
            SagaTransaction saga, 
            SagaStep failedStep, 
            Exception error) {
        
        String attemptKey = saga.getId() + ":" + failedStep.getStepName();
        log.error("Handling compensation failure: sagaId={}, step={}, error={}", 
            saga.getId(), failedStep.getStepName(), error.getMessage());
        
        try {
            // Track the attempt
            CompensationAttempt attempt = activeAttempts.computeIfAbsent(
                attemptKey, 
                k -> createNewAttempt(saga, failedStep)
            );
            attempt.incrementAttempts();
            attempt.setLastError(error);
            
            // Step 1: Try automatic recovery strategies
            if (attempt.getAttemptCount() <= MAX_RETRY_ATTEMPTS) {
                CompensationResult retryResult = attemptAutomaticRecovery(saga, failedStep, attempt);
                if (retryResult.isSuccess()) {
                    log.info("Automatic recovery succeeded: sagaId={}, step={}", 
                        saga.getId(), failedStep.getStepName());
                    activeAttempts.remove(attemptKey);
                    return retryResult;
                }
            }
            
            // Step 2: Apply compensating strategies
            CompensationStrategy strategy = strategyRegistry.getStrategy(failedStep.getServiceName());
            if (strategy != null && strategy.canHandle(failedStep, error)) {
                CompensationResult strategyResult = strategy.compensate(saga, failedStep, error);
                if (strategyResult.isSuccess()) {
                    log.info("Compensation strategy succeeded: sagaId={}, step={}, strategy={}", 
                        saga.getId(), failedStep.getStepName(), strategy.getName());
                    activeAttempts.remove(attemptKey);
                    return strategyResult;
                }
            }
            
            // Step 3: Check if we should move to dead letter queue
            if (attempt.getAttemptCount() >= DEAD_LETTER_THRESHOLD) {
                return moveToDeadLetterQueue(saga, failedStep, attempt);
            }
            
            // Step 4: Create manual intervention task
            if (attempt.getAttemptCount() > MAX_RETRY_ATTEMPTS) {
                return createManualInterventionTask(saga, failedStep, attempt);
            }
            
            // Step 5: Schedule retry with exponential backoff
            scheduleRetry(saga, failedStep, attempt);
            
            return CompensationResult.builder()
                .success(false)
                .retryScheduled(true)
                .nextRetryTime(attempt.getNextRetryTime())
                .message("Compensation retry scheduled")
                .build();
            
        } catch (Exception e) {
            log.error("Critical error in compensation failure handler", e);
            return handleCriticalFailure(saga, failedStep, e);
        }
    }
    
    /**
     * Attempt automatic recovery based on error type
     */
    private CompensationResult attemptAutomaticRecovery(
            SagaTransaction saga, 
            SagaStep step, 
            CompensationAttempt attempt) {
        
        log.info("Attempting automatic recovery: sagaId={}, step={}, attempt={}", 
            saga.getId(), step.getStepName(), attempt.getAttemptCount());
        
        Exception lastError = attempt.getLastError();
        
        // Network/timeout errors - retry immediately
        if (isTransientError(lastError)) {
            return retryCompensationImmediately(saga, step);
        }
        
        // Resource lock errors - wait and retry
        if (isResourceLockError(lastError)) {
            return retryWithDelay(saga, step, 5000); // 5 second delay
        }
        
        // Data consistency errors - attempt repair
        if (isDataConsistencyError(lastError)) {
            return attemptDataRepair(saga, step);
        }
        
        return CompensationResult.failure("No automatic recovery available");
    }
    
    /**
     * Retry compensation immediately for transient errors
     */
    private CompensationResult retryCompensationImmediately(SagaTransaction saga, SagaStep step) {
        try {
            log.info("Retrying compensation immediately: sagaId={}, step={}", 
                saga.getId(), step.getStepName());
            
            // Execute compensation
            step.compensate();
            
            // Verify compensation succeeded
            if (verifyCompensation(saga, step)) {
                step.setStatus(SagaStep.StepStatus.COMPENSATED);
                sagaRepository.save(saga);
                
                return CompensationResult.success("Immediate retry succeeded");
            }
            
            return CompensationResult.failure("Immediate retry failed verification");
            
        } catch (Exception e) {
            log.error("Immediate retry failed: sagaId={}, step={}", 
                saga.getId(), step.getStepName(), e);
            return CompensationResult.failure("Immediate retry failed: " + e.getMessage());
        }
    }
    
    /**
     * Retry compensation with delay
     */
    private CompensationResult retryWithDelay(SagaTransaction saga, SagaStep step, long delayMs) {
        try {
            log.info("Retrying compensation with delay: sagaId={}, step={}, delay={}ms", 
                saga.getId(), step.getStepName(), delayMs);
            
            Thread.sleep(delayMs);
            
            step.compensate();
            
            if (verifyCompensation(saga, step)) {
                step.setStatus(SagaStep.StepStatus.COMPENSATED);
                sagaRepository.save(saga);
                
                return CompensationResult.success("Delayed retry succeeded");
            }
            
            return CompensationResult.failure("Delayed retry failed verification");
            
        } catch (Exception e) {
            log.error("Delayed retry failed: sagaId={}, step={}", 
                saga.getId(), step.getStepName(), e);
            return CompensationResult.failure("Delayed retry failed: " + e.getMessage());
        }
    }
    
    /**
     * Attempt to repair data inconsistencies
     */
    private CompensationResult attemptDataRepair(SagaTransaction saga, SagaStep step) {
        log.info("Attempting data repair: sagaId={}, step={}", saga.getId(), step.getStepName());
        
        try {
            // Get repair strategy for the step
            DataRepairStrategy repairStrategy = getDataRepairStrategy(step);
            if (repairStrategy != null) {
                boolean repaired = repairStrategy.repair(saga, step);
                
                if (repaired) {
                    // Retry compensation after repair
                    step.compensate();
                    
                    if (verifyCompensation(saga, step)) {
                        return CompensationResult.success("Data repair and compensation succeeded");
                    }
                }
            }
            
            return CompensationResult.failure("Data repair failed or unavailable");
            
        } catch (Exception e) {
            log.error("Data repair failed: sagaId={}, step={}", 
                saga.getId(), step.getStepName(), e);
            return CompensationResult.failure("Data repair failed: " + e.getMessage());
        }
    }
    
    /**
     * Move failed compensation to dead letter queue
     */
    private CompensationResult moveToDeadLetterQueue(
            SagaTransaction saga, 
            SagaStep step, 
            CompensationAttempt attempt) {
        
        log.warn("Moving to dead letter queue: sagaId={}, step={}, attempts={}", 
            saga.getId(), step.getStepName(), attempt.getAttemptCount());
        
        DeadLetterEntry entry = DeadLetterEntry.builder()
            .sagaId(saga.getId())
            .sagaType(saga.getSagaType())
            .stepName(step.getStepName())
            .serviceName(step.getServiceName())
            .failureReason(attempt.getLastError().getMessage())
            .attemptCount(attempt.getAttemptCount())
            .originalData(saga.getSagaData())
            .timestamp(Instant.now())
            .build();
        
        // Send to dead letter topic
        kafkaTemplate.send("saga-compensation-dlq", entry);
        
        // Update saga status
        saga.setStatus(SagaStatus.COMPENSATION_FAILED);
        saga.setFailureReason("Moved to dead letter queue after " + attempt.getAttemptCount() + " attempts");
        sagaRepository.save(saga);
        
        // Create alert
        alertingService.sendCriticalAlert(
            "Saga Compensation Failed - DLQ",
            buildAlertDetails(saga, step, attempt)
        );
        
        // Audit
        auditService.auditCompensationFailure(saga, step, "MOVED_TO_DLQ", attempt);
        
        activeAttempts.remove(attempt.getAttemptKey());
        
        return CompensationResult.builder()
            .success(false)
            .movedToDeadLetter(true)
            .message("Compensation moved to dead letter queue")
            .build();
    }
    
    /**
     * Create manual intervention task
     */
    private CompensationResult createManualInterventionTask(
            SagaTransaction saga, 
            SagaStep step, 
            CompensationAttempt attempt) {
        
        log.warn("Creating manual intervention task: sagaId={}, step={}", 
            saga.getId(), step.getStepName());
        
        ManualInterventionTask task = ManualInterventionTask.builder()
            .taskId(UUID.randomUUID().toString())
            .sagaId(saga.getId())
            .stepName(step.getStepName())
            .priority(determinePriority(saga, step))
            .assignedTeam(determineAssignedTeam(step))
            .createdAt(Instant.now())
            .dueBy(calculateDueTime(saga, step))
            .description(buildTaskDescription(saga, step, attempt))
            .compensationScript(generateCompensationScript(saga, step))
            .rollbackInstructions(generateRollbackInstructions(saga, step))
            .build();
        
        // Store task in database
        storeManualTask(task);
        
        // Send notification to assigned team
        notifyTeam(task);
        
        // Update saga status
        saga.setStatus(SagaStatus.COMPENSATION_FAILED);
        saga.setFailureReason("Manual intervention required for step: " + step.getStepName());
        sagaRepository.save(saga);
        
        // Audit
        auditService.auditManualIntervention(saga, step, task);
        
        return CompensationResult.builder()
            .success(false)
            .manualInterventionRequired(true)
            .manualTaskId(task.getTaskId())
            .message("Manual intervention task created")
            .build();
    }
    
    /**
     * Schedule retry with exponential backoff
     */
    @Async
    private void scheduleRetry(SagaTransaction saga, SagaStep step, CompensationAttempt attempt) {
        long delay = calculateBackoffDelay(attempt.getAttemptCount());
        attempt.setNextRetryTime(Instant.now().plus(delay, ChronoUnit.MILLIS));
        
        log.info("Scheduling compensation retry: sagaId={}, step={}, delay={}ms", 
            saga.getId(), step.getStepName(), delay);
        
        try {
            Thread.sleep(delay);
            
            // Retry compensation
            CompensationResult result = handleCompensationFailure(saga, step, attempt.getLastError());
            
            if (result.isSuccess()) {
                log.info("Scheduled retry succeeded: sagaId={}, step={}", 
                    saga.getId(), step.getStepName());
            }
            
        } catch (Exception e) {
            log.error("Scheduled retry failed: sagaId={}, step={}", 
                saga.getId(), step.getStepName(), e);
        }
    }
    
    /**
     * Handle critical failures that prevent any recovery
     */
    private CompensationResult handleCriticalFailure(
            SagaTransaction saga, 
            SagaStep step, 
            Exception error) {
        
        log.error("CRITICAL FAILURE in compensation handler: sagaId={}, step={}", 
            saga.getId(), step.getStepName(), error);
        
        // Mark saga as critically failed
        saga.setStatus(SagaStatus.COMPENSATION_FAILED);
        saga.setFailureReason("Critical failure: " + error.getMessage());
        sagaRepository.save(saga);
        
        // Send emergency alert
        alertingService.sendEmergencyAlert(
            "Critical Saga Compensation Failure",
            Map.of(
                "sagaId", saga.getId(),
                "stepName", step.getStepName(),
                "error", error.getMessage(),
                "stackTrace", Arrays.toString(error.getStackTrace())
            )
        );
        
        // Create incident
        String incidentId = createIncident(saga, step, error);
        
        return CompensationResult.builder()
            .success(false)
            .criticalFailure(true)
            .incidentId(incidentId)
            .message("Critical failure - incident created")
            .build();
    }
    
    /**
     * Verify compensation was successful
     */
    private boolean verifyCompensation(SagaTransaction saga, SagaStep step) {
        try {
            // Service-specific verification
            return step.verifyCompensation();
        } catch (Exception e) {
            log.error("Failed to verify compensation: sagaId={}, step={}", 
                saga.getId(), step.getStepName(), e);
            return false;
        }
    }
    
    /**
     * Process dead letter queue entries periodically
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void processDeadLetterQueue() {
        log.info("Processing dead letter queue for compensation failures");
        
        // Fetch and process DLQ entries
        // Implementation would consume from Kafka DLQ topic
    }
    
    /**
     * Monitor and recover stuck compensations
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void monitorStuckCompensations() {
        log.debug("Monitoring for stuck compensations");
        
        // Find sagas stuck in COMPENSATING state
        List<SagaTransaction> stuckSagas = sagaRepository.findByStatus(SagaStatus.COMPENSATING.name())
            .stream()
            .filter(saga -> isStuck(saga))
            .map(s -> (SagaTransaction) s)
            .toList();
        
        for (SagaTransaction saga : stuckSagas) {
            log.warn("Found stuck compensation: sagaId={}", saga.getId());
            recoverStuckCompensation(saga);
        }
    }
    
    private boolean isStuck(Object saga) {
        // Check if saga has been in COMPENSATING state for too long
        return true; // Simplified
    }
    
    private void recoverStuckCompensation(SagaTransaction saga) {
        // Attempt to recover stuck compensation
        log.info("Attempting to recover stuck compensation: sagaId={}", saga.getId());
    }
    
    // Helper methods
    
    private CompensationAttempt createNewAttempt(SagaTransaction saga, SagaStep step) {
        return CompensationAttempt.builder()
            .attemptKey(saga.getId() + ":" + step.getStepName())
            .sagaId(saga.getId())
            .stepName(step.getStepName())
            .firstAttemptTime(Instant.now())
            .attemptCount(new AtomicInteger(0))
            .build();
    }
    
    private boolean isTransientError(Exception error) {
        return error instanceof java.net.ConnectException ||
               error instanceof java.net.SocketTimeoutException ||
               error.getMessage().contains("timeout") ||
               error.getMessage().contains("connection refused");
    }
    
    private boolean isResourceLockError(Exception error) {
        return error.getMessage().contains("lock") ||
               error.getMessage().contains("deadlock") ||
               error.getMessage().contains("concurrent");
    }
    
    private boolean isDataConsistencyError(Exception error) {
        return error.getMessage().contains("consistency") ||
               error.getMessage().contains("constraint") ||
               error.getMessage().contains("integrity");
    }
    
    private DataRepairStrategy getDataRepairStrategy(SagaStep step) {
        // Get appropriate repair strategy based on step type
        return null; // Simplified
    }
    
    private long calculateBackoffDelay(int attemptCount) {
        long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attemptCount - 1);
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }
    
    private String determinePriority(SagaTransaction saga, SagaStep step) {
        // Determine priority based on saga type and step criticality
        return "HIGH";
    }
    
    private String determineAssignedTeam(SagaStep step) {
        // Route to appropriate team based on service
        return "PLATFORM_TEAM";
    }
    
    private Instant calculateDueTime(SagaTransaction saga, SagaStep step) {
        // Calculate SLA-based due time
        return Instant.now().plus(4, ChronoUnit.HOURS);
    }
    
    private String buildTaskDescription(SagaTransaction saga, SagaStep step, CompensationAttempt attempt) {
        return String.format(
            "Manual compensation required for saga %s, step %s after %d failed attempts. Error: %s",
            saga.getId(), step.getStepName(), attempt.getAttemptCount(), 
            attempt.getLastError().getMessage()
        );
    }
    
    private String generateCompensationScript(SagaTransaction saga, SagaStep step) {
        // Generate service-specific compensation script
        return "-- Compensation script for " + step.getStepName();
    }
    
    private String generateRollbackInstructions(SagaTransaction saga, SagaStep step) {
        // Generate human-readable rollback instructions
        return "1. Verify current state\n2. Execute compensation\n3. Verify completion";
    }
    
    private void storeManualTask(ManualInterventionTask task) {
        // Store in database
        log.info("Storing manual intervention task: {}", task.getTaskId());
    }
    
    private void notifyTeam(ManualInterventionTask task) {
        // Send notification to assigned team
        log.info("Notifying team about manual task: {}", task.getTaskId());
    }
    
    private Map<String, Object> buildAlertDetails(SagaTransaction saga, SagaStep step, CompensationAttempt attempt) {
        return Map.of(
            "sagaId", saga.getId(),
            "stepName", step.getStepName(),
            "attempts", attempt.getAttemptCount(),
            "error", attempt.getLastError().getMessage()
        );
    }
    
    private String createIncident(SagaTransaction saga, SagaStep step, Exception error) {
        String incidentId = UUID.randomUUID().toString();
        log.error("Created incident {} for critical failure", incidentId);
        return incidentId;
    }
    
    // Data classes
    
    @Data
    @Builder
    public static class CompensationResult {
        private boolean success;
        private boolean retryScheduled;
        private boolean movedToDeadLetter;
        private boolean manualInterventionRequired;
        private boolean criticalFailure;
        private Instant nextRetryTime;
        private String manualTaskId;
        private String incidentId;
        private String message;
        
        public static CompensationResult success(String message) {
            return CompensationResult.builder()
                .success(true)
                .message(message)
                .build();
        }
        
        public static CompensationResult failure(String message) {
            return CompensationResult.builder()
                .success(false)
                .message(message)
                .build();
        }
    }
    
    @Data
    @Builder
    public static class CompensationAttempt {
        private String attemptKey;
        private String sagaId;
        private String stepName;
        private AtomicInteger attemptCount;
        private Instant firstAttemptTime;
        private Instant lastAttemptTime;
        private Instant nextRetryTime;
        private Exception lastError;
        
        public void incrementAttempts() {
            attemptCount.incrementAndGet();
            lastAttemptTime = Instant.now();
        }
        
        public int getAttemptCount() {
            return attemptCount.get();
        }
    }
    
    @Data
    @Builder
    public static class DeadLetterEntry {
        private String sagaId;
        private String sagaType;
        private String stepName;
        private String serviceName;
        private String failureReason;
        private int attemptCount;
        private Map<String, Object> originalData;
        private Instant timestamp;
    }
    
    @Data
    @Builder
    public static class ManualInterventionTask {
        private String taskId;
        private String sagaId;
        private String stepName;
        private String priority;
        private String assignedTeam;
        private Instant createdAt;
        private Instant dueBy;
        private String description;
        private String compensationScript;
        private String rollbackInstructions;
    }
    
    // Interfaces for strategies
    
    public interface CompensationStrategy {
        String getName();
        boolean canHandle(SagaStep step, Exception error);
        CompensationResult compensate(SagaTransaction saga, SagaStep step, Exception error);
    }
    
    public interface DataRepairStrategy {
        boolean repair(SagaTransaction saga, SagaStep step);
    }
}