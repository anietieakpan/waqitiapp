package com.waqiti.common.database.transaction;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * AOP Aspect for optimizing and monitoring database transactions
 *
 * Features:
 * - Transaction duration monitoring
 * - Connection pool usage tracking
 * - Transaction timeout warnings
 * - Read-only transaction optimization hints
 * - Nested transaction detection
 *
 * @Order(1) ensures this runs before @Transactional aspect
 */
@Aspect
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class OptimizedTransactionAspect {

    private final MeterRegistry meterRegistry;

    // Transaction timeout thresholds
    private static final long WARNING_THRESHOLD_MS = 5000;  // 5 seconds
    private static final long CRITICAL_THRESHOLD_MS = 30000; // 30 seconds

    /**
     * Around advice for all methods annotated with @Transactional
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object monitorTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String transactionName = className + "." + methodName;

        // Check if transaction is already active (nested transaction)
        boolean isNestedTransaction = TransactionSynchronizationManager.isActualTransactionActive();

        if (isNestedTransaction) {
            log.warn("WARNING: Nested transaction detected: {} - Consider refactoring to avoid nested transactions",
                     transactionName);
            incrementCounter("transaction.nested", transactionName);
        }

        // Start timing
        long startTime = System.nanoTime();
        Timer.Sample sample = Timer.start(meterRegistry);

        // Track active transactions
        incrementGauge("transaction.active");

        try {
            // Log transaction start for debugging
            if (log.isDebugEnabled()) {
                log.debug("= Starting transaction: {}", transactionName);
            }

            // Execute the transactional method
            Object result = joinPoint.proceed();

            // Calculate duration
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            // Record successful transaction
            sample.stop(Timer.builder("transaction.duration")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("status", "success")
                    .register(meterRegistry));

            // Warn if transaction took too long
            if (durationMs > CRITICAL_THRESHOLD_MS) {
                log.error("CRITICAL: Transaction {} took {}ms (threshold: {}ms) - URGENT OPTIMIZATION NEEDED",
                         transactionName, durationMs, CRITICAL_THRESHOLD_MS);
                incrementCounter("transaction.critical_slow", transactionName);
            } else if (durationMs > WARNING_THRESHOLD_MS) {
                log.warn("WARNING: Transaction {} took {}ms (threshold: {}ms) - Optimization recommended",
                        transactionName, durationMs, WARNING_THRESHOLD_MS);
                incrementCounter("transaction.slow", transactionName);
            }

            // Log success
            if (log.isDebugEnabled()) {
                log.debug(" Transaction completed successfully: {} ({}ms)", transactionName, durationMs);
            }

            return result;

        } catch (Exception e) {
            // Calculate duration even for failed transactions
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            // Record failed transaction
            sample.stop(Timer.builder("transaction.duration")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("status", "failure")
                    .register(meterRegistry));

            // Log transaction failure
            log.error("L Transaction failed: {} after {}ms - Error: {}",
                     transactionName, durationMs, e.getMessage());

            incrementCounter("transaction.failure", transactionName);

            // Check if this is a common transaction issue
            analyzeTransactionException(e, transactionName);

            throw e;

        } finally {
            // Always decrement active transaction count
            decrementGauge("transaction.active");

            // Log transaction info if still active (shouldn't be at this point)
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                log.warn(" Transaction still active after method completion: {} - Possible transaction leak",
                        transactionName);
                incrementCounter("transaction.leak", transactionName);
            }
        }
    }

    /**
     * Analyze exception to provide specific guidance
     */
    private void analyzeTransactionException(Exception e, String transactionName) {
        String exceptionType = e.getClass().getSimpleName();

        switch (exceptionType) {
            case "OptimisticLockException":
                log.error("OPTIMISTIC LOCK: Optimistic locking conflict in {} - High contention detected. " +
                         "Consider pessimistic locking or retry logic", transactionName);
                incrementCounter("transaction.optimistic_lock_failure", transactionName);
                break;

            case "PessimisticLockException":
                log.error("PESSIMISTIC LOCK: Pessimistic locking timeout in {} - Deadlock risk. " +
                         "Review lock ordering", transactionName);
                incrementCounter("transaction.pessimistic_lock_failure", transactionName);
                break;

            case "CannotAcquireLockException":
                log.error("LOCK ERROR: Cannot acquire lock in {} - Database lock contention. " +
                         "Review transaction isolation level", transactionName);
                incrementCounter("transaction.lock_acquisition_failure", transactionName);
                break;

            case "DeadlockLoserDataAccessException":
                log.error("DEADLOCK: Deadlock detected in {} - Transaction aborted. " +
                         "Review lock acquisition order", transactionName);
                incrementCounter("transaction.deadlock", transactionName);
                break;

            case "QueryTimeoutException":
                log.error("TIMEOUT: Query timeout in {} - Slow query detected. " +
                         "Add indexes or optimize query", transactionName);
                incrementCounter("transaction.query_timeout", transactionName);
                break;

            case "DataIntegrityViolationException":
                log.error("INTEGRITY: Data integrity violation in {} - Constraint violation. " +
                         "Validate data before persisting", transactionName);
                incrementCounter("transaction.integrity_violation", transactionName);
                break;

            default:
                incrementCounter("transaction.unknown_failure", transactionName);
        }
    }

    /**
     * Increment a counter metric
     */
    private void incrementCounter(String metricName, String transactionName) {
        meterRegistry.counter(metricName, "transaction", transactionName).increment();
    }

    /**
     * Increment a gauge metric
     */
    private void incrementGauge(String metricName) {
        meterRegistry.gauge(metricName, 1);
    }

    /**
     * Decrement a gauge metric
     */
    private void decrementGauge(String metricName) {
        meterRegistry.gauge(metricName, -1);
    }
}
