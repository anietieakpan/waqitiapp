package com.waqiti.common.retry;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.function.Supplier;

/**
 * Service for handling optimistic locking retries in financial operations.
 * 
 * CRITICAL: When concurrent updates occur, OptimisticLockException is thrown.
 * This service provides intelligent retry logic with exponential backoff.
 * 
 * Use this for all financial operations that might experience concurrent updates:
 * - Wallet balance updates
 * - Transaction status changes
 * - Payment processing
 * - Account balance modifications
 * 
 * Example Usage:
 * <pre>
 * {@code
 * Wallet wallet = optimisticLockingRetryService.executeWithRetry(
 *     () -> {
 *         Wallet w = walletRepository.findById(walletId)
 *             .orElseThrow(() -> new WalletNotFoundException(walletId));
 *         w.setBalance(w.getBalance().add(amount));
 *         return walletRepository.save(w);
 *     },
 *     "updateWalletBalance",
 *     5  // max retries
 * );
 * }
 * </pre>
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Service
@Slf4j
public class OptimisticLockingRetryService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 100;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 5000;

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Execute operation with optimistic locking retry logic (default 3 retries)
     */
    public <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        return executeWithRetry(operation, operationName, DEFAULT_MAX_RETRIES);
    }

    /**
     * Execute operation with optimistic locking retry logic (custom retry count)
     * 
     * @param operation The operation to execute
     * @param operationName Name for logging
     * @param maxRetries Maximum number of retries
     * @return The result of the operation
     * @throws OptimisticLockException if all retries are exhausted
     */
    @Transactional
    public <T> T executeWithRetry(Supplier<T> operation, String operationName, int maxRetries) {
        int attempt = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (true) {
            try {
                attempt++;
                
                if (attempt > 1) {
                    log.info("Retrying operation '{}' - attempt {}/{}", 
                        operationName, attempt, maxRetries + 1);
                }

                // Execute the operation
                T result = operation.get();
                
                if (attempt > 1) {
                    log.info("Operation '{}' succeeded on attempt {}", operationName, attempt);
                }
                
                return result;

            } catch (OptimisticLockException e) {
                if (attempt > maxRetries) {
                    log.error("Operation '{}' failed after {} attempts due to optimistic locking conflict",
                        operationName, attempt);
                    throw new OptimisticLockRetryExhaustedException(
                        String.format("Failed to complete operation '%s' after %d attempts", 
                            operationName, attempt), e);
                }

                log.warn("Optimistic locking conflict detected for operation '{}' - attempt {}/{}. " +
                        "Retrying after {}ms backoff",
                    operationName, attempt, maxRetries + 1, backoffMs);

                // Exponential backoff with jitter
                try {
                    // SECURITY FIX: Use SecureRandom instead of Math.random()
                    long jitter = (long) (secureRandom.nextDouble() * backoffMs * 0.1); // 10% jitter
                    Thread.sleep(backoffMs + jitter);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }

                // Increase backoff for next retry (exponential)
                backoffMs = (long) Math.min(backoffMs * BACKOFF_MULTIPLIER, MAX_BACKOFF_MS);

            } catch (Exception e) {
                // Non-optimistic locking exceptions are not retried
                log.error("Operation '{}' failed with non-retryable exception", operationName, e);
                throw e;
            }
        }
    }

    /**
     * Execute operation with retry, returning success boolean
     */
    public boolean executeWithRetryBoolean(Runnable operation, String operationName) {
        return executeWithRetryBoolean(operation, operationName, DEFAULT_MAX_RETRIES);
    }

    /**
     * Execute operation with retry, returning success boolean (custom retry count)
     */
    public boolean executeWithRetryBoolean(Runnable operation, String operationName, int maxRetries) {
        try {
            executeWithRetry(() -> {
                operation.run();
                return null;
            }, operationName, maxRetries);
            return true;
        } catch (OptimisticLockRetryExhaustedException e) {
            return false;
        }
    }

    /**
     * Execute operation with custom exception handling
     */
    public <T> T executeWithRetryAndRecovery(
            Supplier<T> operation,
            String operationName,
            Supplier<T> recoveryOperation) {
        
        try {
            return executeWithRetry(operation, operationName);
        } catch (OptimisticLockRetryExhaustedException e) {
            log.warn("Attempting recovery operation for '{}'", operationName);
            return recoveryOperation.get();
        }
    }

    /**
     * Custom exception for when retry attempts are exhausted
     */
    public static class OptimisticLockRetryExhaustedException extends RuntimeException {
        public OptimisticLockRetryExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}