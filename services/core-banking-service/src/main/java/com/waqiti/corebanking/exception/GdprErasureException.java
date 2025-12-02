package com.waqiti.corebanking.exception;

/**
 * Exception thrown when GDPR data erasure cannot be performed
 *
 * Common scenarios:
 * - User has active transactions
 * - User has non-zero account balances
 * - User account is under compliance hold
 * - User data not found
 * - Regulatory restrictions prevent erasure
 *
 * @author Core Banking Team
 * @since 1.0
 */
public class GdprErasureException extends RuntimeException {

    public GdprErasureException(String message) {
        super(message);
    }

    public GdprErasureException(String message, Throwable cause) {
        super(message, cause);
    }
}
