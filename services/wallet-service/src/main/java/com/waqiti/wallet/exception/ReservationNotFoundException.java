package com.waqiti.wallet.exception;

/**
 * Exception thrown when reservation not found
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String message) {
        super(message);
    }
}
