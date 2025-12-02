package com.waqiti.wallet.exception;

/**
 * Exception thrown when attempting to use expired reservation
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
public class ReservationExpiredException extends RuntimeException {
    public ReservationExpiredException(String message) {
        super(message);
    }
}
