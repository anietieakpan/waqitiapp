package com.waqiti.wallet.locking;

/**
 * Exception thrown when wallet lock acquisition times out
 *
 * This exception indicates high contention on a wallet,
 * meaning multiple concurrent operations are competing for access.
 *
 * RESPONSE STRATEGY:
 * - Retry the operation after a brief delay
 * - Alert monitoring if timeouts are frequent
 * - Consider implementing queue-based processing for high-contention wallets
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-01-16
 */
public class WalletLockTimeoutException extends WalletLockException {

    public WalletLockTimeoutException(String message) {
        super(message);
    }

    public WalletLockTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
