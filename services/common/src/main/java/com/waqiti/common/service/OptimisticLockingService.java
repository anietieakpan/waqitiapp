package com.waqiti.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import java.security.SecureRandom;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Production-Grade Optimistic Locking Service
 * 
 * Provides robust handling of optimistic locking failures with:
 * - Automatic retry mechanisms with exponential backoff
 * - Comprehensive exception handling
 * - Performance monitoring and logging
 * - Configurable retry strategies
 * - Transaction boundary management
 * 
 * This service is critical for preventing data corruption in high-concurrency
 * financial operations where optimistic locking is used.
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptimisticLockingService {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Execute operation with optimistic locking retry
     * 
     * @param operation The operation to execute
     * @param entityName Name of entity for logging
     * @param <T> Return type
     * @return Result of operation
     * @throws OptimisticLockingFailureException if all retries are exhausted
     */
    @Retryable(
        retryFor = {
            OptimisticLockException.class,
            OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 1000)
    )
    @Transactional
    public <T> T executeWithOptimisticLocking(Callable<T> operation, String entityName) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("OPTIMISTIC_LOCK: Executing operation for entity: {}", entityName);
            
            T result = operation.call();
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("OPTIMISTIC_LOCK: Operation completed successfully for entity: {}, duration: {}ms", 
                entityName, duration);
            
            return result;
            
        } catch (OptimisticLockException | OptimisticLockingFailureException | ObjectOptimisticLockingFailureException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("OPTIMISTIC_LOCK: Optimistic locking conflict for entity: {}, duration: {}ms, will retry", 
                entityName, duration, e);
            throw e;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("OPTIMISTIC_LOCK: Unexpected error for entity: {}, duration: {}ms", 
                entityName, duration, e);
            throw new RuntimeException("Operation failed for entity: " + entityName, e);
        }
    }
    
    /**
     * Execute operation with optimistic locking retry (void return)
     * 
     * @param operation The operation to execute
     * @param entityName Name of entity for logging
     */
    @Retryable(
        retryFor = {
            OptimisticLockException.class,
            OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 1000)
    )
    @Transactional
    public void executeWithOptimisticLocking(Runnable operation, String entityName) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("OPTIMISTIC_LOCK: Executing void operation for entity: {}", entityName);
            
            operation.run();
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("OPTIMISTIC_LOCK: Void operation completed successfully for entity: {}, duration: {}ms", 
                entityName, duration);
            
        } catch (OptimisticLockException | OptimisticLockingFailureException | ObjectOptimisticLockingFailureException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("OPTIMISTIC_LOCK: Optimistic locking conflict for entity: {}, duration: {}ms, will retry", 
                entityName, duration, e);
            throw e;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("OPTIMISTIC_LOCK: Unexpected error for entity: {}, duration: {}ms", 
                entityName, duration, e);
            throw new RuntimeException("Operation failed for entity: " + entityName, e);
        }
    }
    
    /**
     * Execute operation with custom retry configuration
     * 
     * @param operation The operation to execute
     * @param entityName Name of entity for logging
     * @param maxRetries Maximum number of retries
     * @param baseDelay Base delay in milliseconds
     * @param <T> Return type
     * @return Result of operation
     */
    @Transactional
    public <T> T executeWithCustomRetry(Callable<T> operation, String entityName, 
                                       int maxRetries, long baseDelay) {
        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                log.debug("OPTIMISTIC_LOCK: Executing operation for entity: {}, attempt: {}/{}", 
                    entityName, attempt, maxRetries + 1);
                
                T result = operation.call();
                
                long duration = System.currentTimeMillis() - startTime;
                log.debug("OPTIMISTIC_LOCK: Operation completed successfully for entity: {}, attempt: {}, duration: {}ms", 
                    entityName, attempt, duration);
                
                return result;
                
            } catch (OptimisticLockException | OptimisticLockingFailureException | ObjectOptimisticLockingFailureException e) {
                lastException = e;
                
                if (attempt <= maxRetries) {
                    long delay = calculateDelay(baseDelay, attempt);
                    log.warn("OPTIMISTIC_LOCK: Optimistic locking conflict for entity: {}, attempt: {}, retrying in {}ms", 
                        entityName, attempt, delay);
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                } else {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("OPTIMISTIC_LOCK: All retry attempts exhausted for entity: {}, total attempts: {}, duration: {}ms", 
                        entityName, attempt, duration);
                    throw new OptimisticLockingFailureException("All retry attempts exhausted for entity: " + entityName, e);
                }
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("OPTIMISTIC_LOCK: Unexpected error for entity: {}, attempt: {}, duration: {}ms", 
                    entityName, attempt, duration, e);
                throw new RuntimeException("Operation failed for entity: " + entityName, e);
            }
        }
        
        // This should never be reached, but included for completeness
        throw new OptimisticLockingFailureException("Unexpected end of retry loop for entity: " + entityName, lastException);
    }
    
    /**
     * Execute critical financial operation with enhanced retry strategy
     * 
     * @param operation The critical financial operation to execute
     * @param entityName Name of entity for logging
     * @param <T> Return type
     * @return Result of operation
     */
    @Transactional
    public <T> T executeCriticalFinancialOperation(Callable<T> operation, String entityName) {
        return executeWithCustomRetry(operation, entityName, 5, 50); // More retries, shorter initial delay
    }
    
    /**
     * Execute operation with fallback
     * 
     * @param primaryOperation Primary operation to try
     * @param fallbackOperation Fallback operation if primary fails
     * @param entityName Name of entity for logging
     * @param <T> Return type
     * @return Result of operation
     */
    @Transactional
    public <T> T executeWithFallback(Callable<T> primaryOperation, Supplier<T> fallbackOperation, String entityName) {
        try {
            return executeWithOptimisticLocking(primaryOperation, entityName);
            
        } catch (OptimisticLockingFailureException e) {
            log.warn("OPTIMISTIC_LOCK: Primary operation failed for entity: {}, executing fallback", entityName, e);
            
            try {
                T result = fallbackOperation.get();
                log.info("OPTIMISTIC_LOCK: Fallback operation succeeded for entity: {}", entityName);
                return result;
                
            } catch (Exception fallbackException) {
                log.error("OPTIMISTIC_LOCK: Fallback operation also failed for entity: {}", entityName, fallbackException);
                throw new RuntimeException("Both primary and fallback operations failed for entity: " + entityName, fallbackException);
            }
        }
    }
    
    /**
     * Check if exception is an optimistic locking failure
     * 
     * @param exception Exception to check
     * @return true if optimistic locking failure
     */
    public boolean isOptimisticLockingFailure(Exception exception) {
        return exception instanceof OptimisticLockException ||
               exception instanceof OptimisticLockingFailureException ||
               exception instanceof ObjectOptimisticLockingFailureException ||
               (exception.getCause() != null && isOptimisticLockingFailure((Exception) exception.getCause()));
    }
    
    /**
     * Calculate exponential backoff delay
     * 
     * @param baseDelay Base delay in milliseconds
     * @param attempt Attempt number (1-based)
     * @return Calculated delay
     */
    private long calculateDelay(long baseDelay, int attempt) {
        // Exponential backoff: baseDelay * 2^(attempt-1)
        long delay = baseDelay * (long) Math.pow(2, attempt - 1);

        // Add jitter to prevent thundering herd
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        long jitter = (long) (delay * 0.1 * secureRandom.nextDouble());

        return Math.min(delay + jitter, 5000); // Cap at 5 seconds
    }
}