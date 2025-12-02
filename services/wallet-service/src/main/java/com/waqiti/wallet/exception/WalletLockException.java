package com.waqiti.wallet.exception;

/**
 * Exception thrown when distributed lock acquisition fails for wallet operations.
 *
 * <p>This exception indicates:
 * <ul>
 *   <li>Lock timeout - failed to acquire lock within specified time</li>
 *   <li>Lock contention - high concurrent access to the same wallet</li>
 *   <li>Redis connectivity issues preventing lock acquisition</li>
 *   <li>Interrupted lock acquisition attempt</li>
 * </ul>
 *
 * <p><b>Retry Strategy:</b>
 * This exception is retryable. Callers should implement exponential backoff
 * retry logic for transient lock contention scenarios.
 *
 * <p><b>Monitoring:</b>
 * High frequency of this exception indicates:
 * <ul>
 *   <li>Excessive concurrent access to wallets (scale horizontally)</li>
 *   <li>Long-running transactions holding locks (optimize transaction duration)</li>
 *   <li>Redis performance issues (check Redis metrics)</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-18
 */
public class WalletLockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new WalletLockException with the specified detail message.
     *
     * @param message the detail message
     */
    public WalletLockException(String message) {
        super(message);
    }

    /**
     * Constructs a new WalletLockException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public WalletLockException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new WalletLockException with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public WalletLockException(Throwable cause) {
        super(cause);
    }
}
