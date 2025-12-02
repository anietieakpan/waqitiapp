package com.waqiti.wallet.locking;

/**
 * Exception thrown when wallet lock operation fails
 *
 * This exception indicates a failure in distributed lock operations
 * for wallet concurrency control.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-01-16
 */
public class WalletLockException extends RuntimeException {

    public WalletLockException(String message) {
        super(message);
    }

    public WalletLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
