package com.waqiti.wallet.exception;

/**
 * Exception thrown when reservation is in invalid state for operation
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
public class InvalidReservationStateException extends RuntimeException {
    public InvalidReservationStateException(String message) {
        super(message);
    }
}
