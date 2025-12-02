package com.waqiti.wallet.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when attempting to create duplicate reservation with same idempotency key
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
@Getter
public class DuplicateReservationException extends RuntimeException {

    private final UUID existingReservationId;

    public DuplicateReservationException(String message, UUID existingReservationId) {
        super(message);
        this.existingReservationId = existingReservationId;
    }
}
