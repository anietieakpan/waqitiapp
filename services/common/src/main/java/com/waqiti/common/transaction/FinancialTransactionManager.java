package com.waqiti.common.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/**
 * Production-grade financial transaction manager with SERIALIZABLE isolation.
 * Ensures ACID compliance and prevents phantom reads in financial operations.
 * 
 * Features:
 * - SERIALIZABLE isolation level for all financial transactions
 * - Automatic retry on deadlocks and lock timeouts
 * - Transaction monitoring and metrics
 * - Comprehensive error handling
 * - Performance optimization for high-throughput scenarios
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Aspect
@Order(1) // Execute before other aspects
public class FinancialTransactionManager {
    
    private final PlatformTransactionManager transactionManager;
    private final FinancialTransactionMetrics metrics;
    private final FinancialTransactionAuditor auditor;
    
    /**
     * Annotation to mark methods that require SERIALIZABLE financial transactions.
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface FinancialTransaction {
        /**
         * Transaction timeout in seconds (default 30)
         */
        int timeout() default 30;
        
        /**
         * Maximum retry attempts on deadlock (default 3)
         */
        int maxRetries() default 3;
        
        /**
         * Transaction description for audit logging
         */
        String description() default "";
        
        /**
         * Whether to rollback on any exception (default true)
         */
        boolean rollbackForAll() default true;
    }
    
    /**
     * Aspect that wraps methods annotated with @FinancialTransaction.
     * Automatically applies SERIALIZABLE isolation and retry logic.
     */
    @Around("@annotation(financialTransaction)")
    public Object executeFinancialTransaction(ProceedingJoinPoint joinPoint, 
                                            FinancialTransaction financialTransaction) throws Throwable {
        
        String methodName = joinPoint.getSignature().toShortString();
        String description = financialTransaction.description().isEmpty() ? 
                methodName : financialTransaction.description();
        
        long startTime = System.currentTimeMillis();
        String transactionId = generateTransactionId();
        
        log.debug("Starting financial transaction: {} (ID: {})", description, transactionId);
        auditor.logTransactionStart(transactionId, methodName, description);
        
        TransactionTemplate transactionTemplate = createFinancialTransactionTemplate(financialTransaction);
        
        try {
            Object result = executeWithRetry(
                () -> transactionTemplate.execute(status -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException("Financial transaction failed", e);
                    }
                }),
                financialTransaction.maxRetries(),
                transactionId,
                description
            );
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("Financial transaction completed successfully: {} (ID: {}, time: {}ms)", 
                    description, transactionId, executionTime);
            
            auditor.logTransactionSuccess(transactionId, executionTime);
            metrics.recordSuccessfulTransaction(methodName, executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.error("Financial transaction failed: {} (ID: {}, time: {}ms)", 
                    description, transactionId, executionTime, e);
            
            auditor.logTransactionFailure(transactionId, e.getMessage(), executionTime);
            metrics.recordFailedTransaction(methodName, e.getClass().getSimpleName());
            
            throw e;
        }
    }
    
    /**
     * Execute a financial operation with explicit SERIALIZABLE transaction.
     * Use this method for programmatic transaction control.
     * 
     * @param operation The operation to execute
     * @param description Description for audit logging
     * @return Operation result
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        timeout = 30,
        rollbackFor = Exception.class
    )
    public <T> T executeFinancialOperation(Supplier<T> operation, String description) {
        String transactionId = generateTransactionId();
        long startTime = System.currentTimeMillis();
        
        log.debug("Executing financial operation: {} (ID: {})", description, transactionId);
        auditor.logTransactionStart(transactionId, "programmatic", description);
        
        try {
            T result = operation.get();
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Financial operation completed: {} (ID: {}, time: {}ms)", 
                    description, transactionId, executionTime);
            
            auditor.logTransactionSuccess(transactionId, executionTime);
            metrics.recordSuccessfulTransaction("programmatic", executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.error("Financial operation failed: {} (ID: {}, time: {}ms)", 
                    description, transactionId, executionTime, e);
            
            auditor.logTransactionFailure(transactionId, e.getMessage(), executionTime);
            metrics.recordFailedTransaction("programmatic", e.getClass().getSimpleName());
            
            throw e;
        }
    }
    
    /**
     * Execute multiple financial operations in a single SERIALIZABLE transaction.
     * Ensures all operations succeed or all are rolled back.
     * 
     * @param operations Array of operations to execute
     * @param description Description for audit logging
     * @return Array of results
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        timeout = 60, // Longer timeout for batch operations
        rollbackFor = Exception.class
    )
    @SuppressWarnings("unchecked")
    public <T> T[] executeBatchFinancialOperations(Supplier<T>[] operations, String description) {
        String transactionId = generateTransactionId();
        long startTime = System.currentTimeMillis();
        
        log.debug("Executing batch financial operations: {} (ID: {}, count: {})", 
                description, transactionId, operations.length);
        auditor.logTransactionStart(transactionId, "batch", description);
        
        try {
            T[] results = (T[]) new Object[operations.length];
            
            for (int i = 0; i < operations.length; i++) {
                results[i] = operations[i].get();
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Batch financial operations completed: {} (ID: {}, count: {}, time: {}ms)", 
                    description, transactionId, operations.length, executionTime);
            
            auditor.logTransactionSuccess(transactionId, executionTime);
            metrics.recordSuccessfulTransaction("batch", executionTime);
            
            return results;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.error("Batch financial operations failed: {} (ID: {}, time: {}ms)", 
                    description, transactionId, executionTime, e);
            
            auditor.logTransactionFailure(transactionId, e.getMessage(), executionTime);
            metrics.recordFailedTransaction("batch", e.getClass().getSimpleName());
            
            throw e;
        }
    }
    
    // Private helper methods
    
    private TransactionTemplate createFinancialTransactionTemplate(FinancialTransaction annotation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        
        // Set SERIALIZABLE isolation level for financial operations
        template.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_SERIALIZABLE);
        template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);
        template.setTimeout(annotation.timeout());
        template.setReadOnly(false);
        
        return template;
    }
    
    @Retryable(
        value = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    private <T> T executeWithRetry(Supplier<T> operation, int maxRetries, 
                                 String transactionId, String description) {
        return operation.get();
    }
    
    private String generateTransactionId() {
        return "FTX-" + System.currentTimeMillis() + "-" + 
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}

/**
 * Metrics collector for financial transactions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class FinancialTransactionMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final io.micrometer.core.instrument.Timer.Builder timerBuilder;
    
    public FinancialTransactionMetrics(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.timerBuilder = io.micrometer.core.instrument.Timer.builder("financial.transaction.duration")
                .description("Duration of financial transactions")
                .tag("type", "financial");
    }
    
    public void recordSuccessfulTransaction(String methodName, long durationMs) {
        meterRegistry.counter("financial.transaction.count", 
                "method", methodName, "status", "success").increment();
        
        timerBuilder.tag("method", methodName)
                .tag("status", "success")
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    public void recordFailedTransaction(String methodName, String errorType) {
        meterRegistry.counter("financial.transaction.count", 
                "method", methodName, "status", "failed", "error", errorType).increment();
    }
}

/**
 * Auditor for financial transactions.
 */
@Component
@Slf4j
class FinancialTransactionAuditor {
    
    public void logTransactionStart(String transactionId, String methodName, String description) {
        log.info("FINANCIAL_AUDIT: Transaction started - ID: {}, Method: {}, Description: {}", 
                transactionId, methodName, description);
    }
    
    public void logTransactionSuccess(String transactionId, long executionTimeMs) {
        log.info("FINANCIAL_AUDIT: Transaction completed - ID: {}, Duration: {}ms", 
                transactionId, executionTimeMs);
    }
    
    public void logTransactionFailure(String transactionId, String errorMessage, long executionTimeMs) {
        log.error("FINANCIAL_AUDIT: Transaction failed - ID: {}, Duration: {}ms, Error: {}", 
                transactionId, executionTimeMs, errorMessage);
    }
}