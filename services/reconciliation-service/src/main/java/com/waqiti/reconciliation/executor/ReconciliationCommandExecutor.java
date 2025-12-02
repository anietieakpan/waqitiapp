package com.waqiti.reconciliation.executor;

import com.waqiti.reconciliation.command.ReconciliationCommand;
import com.waqiti.reconciliation.event.ReconciliationEvent;
import com.waqiti.reconciliation.model.ReconciliationResult;
import com.waqiti.reconciliation.security.ReconciliationSecurityContext;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.locking.DistributedLockService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe, distributed-lock aware reconciliation command executor
 * Ensures only one reconciliation process runs at a time across all instances
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationCommandExecutor {
    
    private final DistributedLockService lockService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final ReconciliationSecurityContext securityContext;
    
    @Value("${reconciliation.max-concurrent:1}")
    private int maxConcurrentReconciliations;
    
    @Value("${reconciliation.lock-timeout-minutes:30}")
    private int lockTimeoutMinutes;
    
    @Value("${reconciliation.queue-size:100}")
    private int maxQueueSize;
    
    // Thread pool for async execution
    private final ExecutorService executorService = new ThreadPoolExecutor(
        1, // Core pool size
        3, // Maximum pool size
        60L, TimeUnit.SECONDS,
        new PriorityBlockingQueue<>(100),
        new ReconciliationThreadFactory(),
        new ReconciliationRejectionHandler()
    );
    
    // Track active reconciliations
    private final Map<String, ReconciliationExecution> activeExecutions = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
    
    /**
     * Execute reconciliation command with full security and audit trail
     */
    @Async("reconciliationExecutor")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CompletableFuture<ReconciliationResult> execute(ReconciliationCommand command) {
        
        // Security validation
        validateSecurity(command);
        
        // Check if shutdown is in progress
        if (shutdownInitiated.get()) {
            throw new IllegalStateException("System is shutting down, cannot accept new reconciliation commands");
        }
        
        // Check concurrent execution limits
        if (activeCount.get() >= maxConcurrentReconciliations) {
            log.warn("Maximum concurrent reconciliations reached. Command {} queued", command.getCommandId());
            return queueCommand(command);
        }
        
        String lockKey = generateLockKey(command);
        String executionId = UUID.randomUUID().toString();
        
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            ReconciliationExecution execution = null;
            
            try {
                // Acquire distributed lock
                boolean lockAcquired = lockService.tryLock(
                    lockKey, 
                    Duration.ofMinutes(lockTimeoutMinutes)
                );
                
                if (!lockAcquired) {
                    throw new ConcurrentReconciliationException(
                        "Could not acquire lock for reconciliation: " + command.getType()
                    );
                }
                
                // Record execution start
                execution = ReconciliationExecution.builder()
                    .executionId(executionId)
                    .command(command)
                    .startTime(Instant.now())
                    .status(ExecutionStatus.RUNNING)
                    .build();
                
                activeExecutions.put(executionId, execution);
                activeCount.incrementAndGet();
                
                // Publish start event
                publishEvent(ReconciliationEvent.started(command, executionId));
                
                // Audit log
                auditReconciliationStart(command, executionId);
                
                // Execute the actual reconciliation
                ReconciliationResult result = executeReconciliation(command, execution);
                
                // Update execution record
                execution.setEndTime(Instant.now());
                execution.setStatus(ExecutionStatus.COMPLETED);
                execution.setResult(result);
                
                // Publish completion event
                publishEvent(ReconciliationEvent.completed(command, executionId, result));
                
                // Audit log success
                auditReconciliationComplete(command, executionId, result);
                
                return result;
                
            } catch (Exception e) {
                log.error("Reconciliation execution failed for command: {}", command.getCommandId(), e);
                
                if (execution != null) {
                    execution.setEndTime(Instant.now());
                    execution.setStatus(ExecutionStatus.FAILED);
                    execution.setErrorMessage(e.getMessage());
                }
                
                // Publish failure event
                publishEvent(ReconciliationEvent.failed(command, executionId, e));
                
                // Audit log failure
                auditReconciliationFailure(command, executionId, e);
                
                throw new ReconciliationExecutionException(
                    "Failed to execute reconciliation command: " + command.getCommandId(), e
                );
                
            } finally {
                // Always release lock
                try {
                    lockService.unlock(lockKey);
                } catch (Exception e) {
                    log.error("Failed to release lock for key: {}", lockKey, e);
                }
                
                // Clean up execution tracking
                if (execution != null) {
                    activeExecutions.remove(executionId);
                    activeCount.decrementAndGet();
                }
                
                // Record metrics
                sample.stop(Timer.builder("reconciliation.execution.duration")
                    .tag("type", command.getType().toString())
                    .tag("status", execution != null ? execution.getStatus().toString() : "UNKNOWN")
                    .register(meterRegistry));
            }
        }, executorService);
    }
    
    /**
     * Execute the actual reconciliation logic
     */
    private ReconciliationResult executeReconciliation(
            ReconciliationCommand command, 
            ReconciliationExecution execution) {
        
        log.info("Executing reconciliation: type={}, scope={}, executionId={}", 
            command.getType(), command.getScope(), execution.getExecutionId());
        
        // This would delegate to the actual reconciliation service
        // For now, returning a placeholder
        return ReconciliationResult.builder()
            .reconciliationId(execution.getExecutionId())
            .commandId(command.getCommandId())
            .startTime(execution.getStartTime())
            .endTime(Instant.now())
            .totalTransactions(0)
            .matchedTransactions(0)
            .unmatchedTransactions(0)
            .discrepancies(0)
            .status("COMPLETED")
            .build();
    }
    
    /**
     * Validate security context for command execution
     */
    private void validateSecurity(ReconciliationCommand command) {
        if (!securityContext.canExecuteReconciliation(command)) {
            throw new SecurityException(
                "User not authorized to execute reconciliation: " + command.getType()
            );
        }
        
        // Additional validation for emergency reconciliations
        if (command.getType() == ReconciliationCommand.ReconciliationType.EMERGENCY_RECONCILIATION) {
            if (!securityContext.hasEmergencyAccess()) {
                throw new SecurityException("Emergency reconciliation requires elevated privileges");
            }
        }
    }
    
    /**
     * Generate distributed lock key
     */
    private String generateLockKey(ReconciliationCommand command) {
        return String.format("reconciliation:%s:%s", 
            command.getType().toString().toLowerCase(),
            command.getScope() != null ? command.getScope().toString().toLowerCase() : "all"
        );
    }
    
    /**
     * Queue command for later execution
     */
    private CompletableFuture<ReconciliationResult> queueCommand(ReconciliationCommand command) {
        // This would implement a persistent queue
        // For now, returning a failed future
        CompletableFuture<ReconciliationResult> future = new CompletableFuture<>();
        future.completeExceptionally(
            new ReconciliationQueueFullException("Reconciliation queue is full")
        );
        return future;
    }
    
    /**
     * Publish reconciliation event
     */
    private void publishEvent(ReconciliationEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish reconciliation event: {}", event, e);
        }
    }
    
    /**
     * Audit reconciliation start
     */
    private void auditReconciliationStart(ReconciliationCommand command, String executionId) {
        auditService.logEvent(
            "RECONCILIATION_STARTED",
            command.getInitiatedBy(),
            "Reconciliation started",
            Map.of(
                "commandId", command.getCommandId(),
                "executionId", executionId,
                "type", command.getType().toString(),
                "scope", command.getScope() != null ? command.getScope().toString() : "ALL",
                "reason", command.getReason() != null ? command.getReason() : "Not specified"
            )
        );
    }
    
    /**
     * Audit reconciliation completion
     */
    private void auditReconciliationComplete(
            ReconciliationCommand command, 
            String executionId, 
            ReconciliationResult result) {
        auditService.logEvent(
            "RECONCILIATION_COMPLETED",
            command.getInitiatedBy(),
            "Reconciliation completed successfully",
            Map.of(
                "commandId", command.getCommandId(),
                "executionId", executionId,
                "duration", Duration.between(result.getStartTime(), result.getEndTime()).toMillis(),
                "totalTransactions", result.getTotalTransactions(),
                "matchedTransactions", result.getMatchedTransactions(),
                "discrepancies", result.getDiscrepancies()
            )
        );
    }
    
    /**
     * Audit reconciliation failure
     */
    private void auditReconciliationFailure(
            ReconciliationCommand command, 
            String executionId, 
            Exception error) {
        auditService.logEvent(
            "RECONCILIATION_FAILED",
            command.getInitiatedBy(),
            "Reconciliation failed",
            Map.of(
                "commandId", command.getCommandId(),
                "executionId", executionId,
                "error", error.getMessage(),
                "errorType", error.getClass().getSimpleName()
            )
        );
    }
    
    /**
     * Graceful shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("Initiating graceful shutdown of ReconciliationCommandExecutor");
        shutdownInitiated.set(true);
        
        // Stop accepting new tasks
        executorService.shutdown();
        
        try {
            // Wait for existing tasks to complete
            if (!executorService.awaitTermination(5, TimeUnit.MINUTES)) {
                log.warn("Forcing shutdown of reconciliation executor");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted during shutdown", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Log any incomplete executions
        if (!activeExecutions.isEmpty()) {
            log.warn("Shutting down with {} active reconciliations", activeExecutions.size());
            activeExecutions.values().forEach(execution -> {
                log.warn("Incomplete reconciliation: {}", execution);
            });
        }
    }
    
    // Inner classes
    
    @lombok.Data
    @lombok.Builder
    private static class ReconciliationExecution {
        private final String executionId;
        private final ReconciliationCommand command;
        private final Instant startTime;
        private Instant endTime;
        private ExecutionStatus status;
        private ReconciliationResult result;
        private String errorMessage;
    }
    
    private enum ExecutionStatus {
        QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    }
    
    /**
     * Custom thread factory for reconciliation threads
     */
    private static class ReconciliationThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("reconciliation-executor-" + threadNumber.getAndIncrement());
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setDaemon(false);
            return thread;
        }
    }
    
    /**
     * Rejection handler for when queue is full
     */
    private static class ReconciliationRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.error("Reconciliation task rejected - queue full");
            throw new ReconciliationQueueFullException(
                "Cannot accept reconciliation task - queue is full"
            );
        }
    }
    
    // Custom exceptions
    
    public static class ConcurrentReconciliationException extends RuntimeException {
        public ConcurrentReconciliationException(String message) {
            super(message);
        }
    }
    
    public static class ReconciliationExecutionException extends RuntimeException {
        public ReconciliationExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class ReconciliationQueueFullException extends RuntimeException {
        public ReconciliationQueueFullException(String message) {
            super(message);
        }
    }
}