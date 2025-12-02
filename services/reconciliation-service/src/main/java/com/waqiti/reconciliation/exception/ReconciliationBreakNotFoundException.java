package com.waqiti.reconciliation.exception;

import java.util.UUID;

/**
 * Exception thrown when a reconciliation break cannot be found
 */
public class ReconciliationBreakNotFoundException extends ReconciliationException {

    private UUID breakId;

    public ReconciliationBreakNotFoundException(String message) {
        super(message, "RECONCILIATION_BREAK_NOT_FOUND");
    }

    public ReconciliationBreakNotFoundException(String message, UUID breakId) {
        super(message, "RECONCILIATION_BREAK_NOT_FOUND", new Object[]{breakId});
        this.breakId = breakId;
    }

    public ReconciliationBreakNotFoundException(String message, Throwable cause) {
        super(message, "RECONCILIATION_BREAK_NOT_FOUND", cause);
    }

    public UUID getBreakId() {
        return breakId;
    }
}