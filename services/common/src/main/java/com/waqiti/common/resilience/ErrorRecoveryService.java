package com.waqiti.common.resilience;

import com.waqiti.common.event.EventPublisher;
import com.waqiti.common.exception.ErrorCode;
import com.waqiti.common.exception.WaqitiException;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Comprehensive Error Recovery Service for handling failures and implementing recovery strategies
 * 
 * This service provides:
 * - Automatic error recovery with various strategies
 * - Dead letter queue processing
 * - Compensating transactions
 * - Saga pattern support
 * - Error tracking and analytics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorRecoveryService {

    private final EventPublisher eventPublisher;
    private final CircuitBreakerService circuitBreakerService;
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(10);
    private final ConcurrentHashMap<String, RecoveryStrategy> recoveryStrategies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ErrorMetrics> errorMetrics = new ConcurrentHashMap<>();
    private final Queue<FailedOperation> deadLetterQueue = new ConcurrentLinkedQueue<>();

    /**
     * Execute operation with automatic recovery
     */
    public <T> T executeWithRecovery(String operationName, 
                                     Supplier<T> operation, 
                                     RecoveryStrategy strategy) {
        log.debug("Executing operation {} with recovery strategy: {}", 
                operationName, strategy.getType());
        
        try {
            // Try the operation
            T result = operation.get();
            recordSuccess(operationName);
            return result;
            
        } catch (Exception e) {
            log.error("Operation {} failed, attempting recovery", operationName, e);
            recordFailure(operationName, e);
            
            // Execute recovery strategy
            return executeRecoveryStrategy(operationName, operation, strategy, e);
        }
    }

    /**
     * Execute operation with saga pattern support
     */
    @Transactional
    public <T> T executeWithSaga(String sagaName, List<SagaStep<T>> steps) {
        log.info("Executing saga: {} with {} steps", sagaName, steps.size());
        
        List<SagaStep<T>> executedSteps = new ArrayList<>();
        T result = null;
        
        try {
            // Execute each step in the saga
            for (SagaStep<T> step : steps) {
                log.debug("Executing saga step: {}", step.getName());
                
                result = step.getAction().get();
                executedSteps.add(step);
                
                // Publish step completed event
                publishSagaStepEvent(sagaName, step.getName(), "COMPLETED");
            }
            
            log.info("Saga {} completed successfully", sagaName);
            return result;
            
        } catch (Exception e) {
            log.error("Saga {} failed at step {}, initiating compensation", 
                    sagaName, executedSteps.size(), e);
            
            // Execute compensating transactions in reverse order
            List<SagaStep<?>> wildcardList = new ArrayList<>(executedSteps);
            compensateSaga(sagaName, wildcardList);
            
            throw new SagaFailedException("Saga failed: " + sagaName, e);
        }
    }

    /**
     * Retry operation with exponential backoff
     */
    public <T> T retryWithExponentialBackoff(String operationName,
                                            Callable<T> operation,
                                            int maxAttempts,
                                            long initialBackoffMs) {
        RetryTemplate retryTemplate = createRetryTemplate(maxAttempts, initialBackoffMs);
        
        try {
            return retryTemplate.execute(context -> {
                log.debug("Attempt {} for operation: {}", 
                        context.getRetryCount() + 1, operationName);
                return operation.call();
            });
        } catch (Exception e) {
            log.error("All retry attempts exhausted for operation: {}", operationName, e);
            recordFailure(operationName, e);
            throw new RecoveryFailedException("Failed after " + maxAttempts + " attempts", e);
        }
    }

    /**
     * Schedule recovery attempt
     */
    @Async
    public void scheduleRecovery(String operationName, 
                                Runnable recoveryAction, 
                                Duration delay) {
        log.info("Scheduling recovery for {} after {}", operationName, delay);
        
        scheduledExecutor.schedule(() -> {
            try {
                log.info("Executing scheduled recovery for: {}", operationName);
                recoveryAction.run();
                recordRecovery(operationName);
            } catch (Exception e) {
                log.error("Scheduled recovery failed for: {}", operationName, e);
                addToDeadLetterQueue(operationName, e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Process dead letter queue
     */
    @Async
    public void processDeadLetterQueue() {
        log.info("Processing dead letter queue, size: {}", deadLetterQueue.size());
        
        while (!deadLetterQueue.isEmpty()) {
            FailedOperation failedOp = deadLetterQueue.poll();
            if (failedOp != null) {
                processFailedOperation(failedOp);
            }
        }
    }

    /**
     * Implement compensating transaction
     */
    public void executeCompensation(String transactionId, 
                                   Runnable compensationAction) {
        log.info("Executing compensation for transaction: {}", transactionId);
        
        try {
            compensationAction.run();
            log.info("Compensation successful for transaction: {}", transactionId);
            
            // Publish compensation event
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("transactionId", transactionId);
            eventData.put("timestamp", Instant.now());
            eventPublisher.publish("COMPENSATION_COMPLETED", eventData);
            
        } catch (Exception e) {
            log.error("Compensation failed for transaction: {}", transactionId, e);
            
            // Add to manual intervention queue
            addToManualInterventionQueue(transactionId, e);
        }
    }

    /**
     * Register custom recovery strategy
     */
    public void registerRecoveryStrategy(String strategyName, RecoveryStrategy strategy) {
        recoveryStrategies.put(strategyName, strategy);
        log.info("Registered recovery strategy: {}", strategyName);
    }

    /**
     * Get error metrics for monitoring
     */
    public ErrorMetrics getErrorMetrics(String operationName) {
        return errorMetrics.getOrDefault(operationName, ErrorMetrics.builder()
                .operationName(operationName)
                .totalAttempts(0)
                .successCount(0)
                .failureCount(0)
                .recoveryCount(0)
                .build());
    }

    /**
     * Clear error metrics
     */
    public void clearErrorMetrics(String operationName) {
        errorMetrics.remove(operationName);
        log.info("Cleared error metrics for: {}", operationName);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private <T> T executeRecoveryStrategy(String operationName, 
                                         Supplier<T> operation,
                                         RecoveryStrategy strategy, 
                                         Exception originalError) {
        switch (strategy.getType()) {
            case RETRY:
                return executeRetryStrategy(operationName, operation, strategy);
                
            case FALLBACK:
                return executeFallbackStrategy(strategy);
                
            case CIRCUIT_BREAKER:
                return executeCircuitBreakerStrategy(operationName, operation, strategy);
                
            case COMPENSATE:
                executeCompensationStrategy(operationName, strategy);
                throw new RecoveryFailedException("Compensation executed", originalError);
                
            case DEAD_LETTER:
                addToDeadLetterQueue(operationName, originalError);
                throw new RecoveryFailedException("Added to dead letter queue", originalError);
                
            default:
                throw new RecoveryFailedException("Unknown recovery strategy", originalError);
        }
    }

    private <T> T executeRetryStrategy(String operationName, 
                                      Supplier<T> operation, 
                                      RecoveryStrategy strategy) {
        int attempts = 0;
        int maxAttempts = strategy.getMaxRetries();
        long backoffMs = strategy.getInitialBackoffMs();
        
        while (attempts < maxAttempts) {
            attempts++;
            
            try {
                Thread.sleep(backoffMs);
                T result = operation.get();
                recordRecovery(operationName);
                return result;
                
            } catch (Exception e) {
                log.warn("Retry attempt {} failed for {}", attempts, operationName);
                backoffMs *= strategy.getBackoffMultiplier();
                
                if (attempts >= maxAttempts) {
                    throw new RecoveryFailedException("Max retries exceeded", e);
                }
            }
        }
        
        throw new RecoveryFailedException("Retry strategy failed");
    }

    @SuppressWarnings("unchecked")
    private <T> T executeFallbackStrategy(RecoveryStrategy strategy) {
        log.info("Executing fallback strategy");
        return (T) strategy.getFallbackSupplier().get();
    }

    private <T> T executeCircuitBreakerStrategy(String operationName,
                                               Supplier<T> operation,
                                               RecoveryStrategy strategy) {
        try {
            return circuitBreakerService.executeWithCircuitBreaker(operationName, operation);
        } catch (Exception e) {
            log.warn("Circuit breaker triggered for {}, executing fallback", operationName);
            return (T) strategy.getFallbackSupplier().get();
        }
    }

    private void executeCompensationStrategy(String operationName, RecoveryStrategy strategy) {
        log.info("Executing compensation strategy for: {}", operationName);
        
        if (strategy.getCompensationAction() != null) {
            strategy.getCompensationAction().run();
        }
    }

    private void compensateSaga(String sagaName, List<SagaStep<?>> executedSteps) {
        Collections.reverse(executedSteps);
        
        for (SagaStep<?> step : executedSteps) {
            try {
                log.info("Compensating saga step: {}", step.getName());
                step.getCompensation().run();
                publishSagaStepEvent(sagaName, step.getName(), "COMPENSATED");
                
            } catch (Exception e) {
                log.error("Failed to compensate step: {}", step.getName(), e);
                // Continue with other compensations
            }
        }
    }

    private RetryTemplate createRetryTemplate(int maxAttempts, long initialBackoffMs) {
        RetryTemplate template = new RetryTemplate();
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        template.setRetryPolicy(retryPolicy);
        
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialBackoffMs);
        template.setBackOffPolicy(backOffPolicy);
        
        template.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(RetryContext context, 
                                                         RetryCallback<T, E> callback, 
                                                         Throwable throwable) {
                log.warn("Retry attempt {} failed", context.getRetryCount(), throwable);
            }
        });
        
        return template;
    }

    private void processFailedOperation(FailedOperation failedOp) {
        log.info("Processing failed operation from DLQ: {}", failedOp.getOperationName());
        
        // Implement custom processing logic
        // Could include manual intervention, alerting, or alternative processing
        
        // Publish DLQ processing event
        Map<String, Object> dlqEventData = new HashMap<>();
        dlqEventData.put("operationName", failedOp.getOperationName());
        dlqEventData.put("error", failedOp.getError());
        dlqEventData.put("timestamp", failedOp.getTimestamp());
        eventPublisher.publish("DLQ_PROCESSED", dlqEventData);
    }

    private void addToDeadLetterQueue(String operationName, Exception error) {
        FailedOperation failedOp = FailedOperation.builder()
                .operationName(operationName)
                .error(error.getMessage())
                .timestamp(Instant.now())
                .build();
        
        deadLetterQueue.offer(failedOp);
        log.warn("Added operation {} to dead letter queue", operationName);
    }

    private void addToManualInterventionQueue(String transactionId, Exception error) {
        // Implementation for manual intervention queue
        log.error("Transaction {} requires manual intervention: {}", 
                transactionId, error.getMessage());
        
        Map<String, Object> interventionEventData = new HashMap<>();
        interventionEventData.put("transactionId", transactionId);
        interventionEventData.put("error", error.getMessage());
        interventionEventData.put("timestamp", Instant.now());
        eventPublisher.publish("MANUAL_INTERVENTION_REQUIRED", interventionEventData);
    }

    private void publishSagaStepEvent(String sagaName, String stepName, String status) {
        Map<String, Object> sagaEventData = new HashMap<>();
        sagaEventData.put("sagaName", sagaName);
        sagaEventData.put("stepName", stepName);
        sagaEventData.put("status", status);
        sagaEventData.put("timestamp", Instant.now());
        eventPublisher.publish("SAGA_STEP_EVENT", sagaEventData);
    }

    private void recordSuccess(String operationName) {
        errorMetrics.compute(operationName, (k, v) -> {
            if (v == null) {
                v = ErrorMetrics.builder().operationName(operationName).build();
            }
            v.incrementSuccess();
            return v;
        });
    }

    private void recordFailure(String operationName, Exception error) {
        errorMetrics.compute(operationName, (k, v) -> {
            if (v == null) {
                v = ErrorMetrics.builder().operationName(operationName).build();
            }
            v.incrementFailure();
            v.setLastError(error.getMessage());
            v.setLastErrorTime(Instant.now());
            return v;
        });
    }

    private void recordRecovery(String operationName) {
        errorMetrics.compute(operationName, (k, v) -> {
            if (v == null) {
                v = ErrorMetrics.builder().operationName(operationName).build();
            }
            v.incrementRecovery();
            return v;
        });
    }

    // ==================== DATA CLASSES ====================

    @Data
    @Builder
    public static class RecoveryStrategy {
        private RecoveryType type;
        private int maxRetries;
        private long initialBackoffMs;
        private double backoffMultiplier;
        private Supplier<?> fallbackSupplier;
        private Runnable compensationAction;
        
        public static RecoveryStrategy retry(int maxRetries, long initialBackoffMs) {
            return RecoveryStrategy.builder()
                    .type(RecoveryType.RETRY)
                    .maxRetries(maxRetries)
                    .initialBackoffMs(initialBackoffMs)
                    .backoffMultiplier(2.0)
                    .build();
        }
        
        public static RecoveryStrategy fallback(Supplier<?> fallbackSupplier) {
            return RecoveryStrategy.builder()
                    .type(RecoveryType.FALLBACK)
                    .fallbackSupplier(fallbackSupplier)
                    .build();
        }
        
        public static RecoveryStrategy compensate(Runnable compensationAction) {
            return RecoveryStrategy.builder()
                    .type(RecoveryType.COMPENSATE)
                    .compensationAction(compensationAction)
                    .build();
        }
    }

    public enum RecoveryType {
        RETRY, FALLBACK, CIRCUIT_BREAKER, COMPENSATE, DEAD_LETTER
    }

    @Data
    @Builder
    public static class SagaStep<T> {
        private String name;
        private Supplier<T> action;
        private Runnable compensation;
    }

    @Data
    @Builder
    public static class FailedOperation {
        private String operationName;
        private String error;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class ErrorMetrics {
        private String operationName;
        private long totalAttempts;
        private long successCount;
        private long failureCount;
        private long recoveryCount;
        private String lastError;
        private Instant lastErrorTime;
        
        public void incrementSuccess() {
            this.totalAttempts++;
            this.successCount++;
        }
        
        public void incrementFailure() {
            this.totalAttempts++;
            this.failureCount++;
        }
        
        public void incrementRecovery() {
            this.recoveryCount++;
        }
        
        public double getSuccessRate() {
            return totalAttempts == 0 ? 0.0 : (double) successCount / totalAttempts;
        }
        
        public double getFailureRate() {
            return totalAttempts == 0 ? 0.0 : (double) failureCount / totalAttempts;
        }
    }

    // ==================== CUSTOM EXCEPTIONS ====================

    public static class RecoveryFailedException extends WaqitiException {
        public RecoveryFailedException(String message) {
            super(ErrorCode.RECOVERY_FAILED, message);
        }
        
        public RecoveryFailedException(String message, Throwable cause) {
            super(ErrorCode.RECOVERY_FAILED, message, cause);
        }
    }

    public static class SagaFailedException extends WaqitiException {
        public SagaFailedException(String message, Throwable cause) {
            super(ErrorCode.SAGA_FAILED, message, cause);
        }
    }
}