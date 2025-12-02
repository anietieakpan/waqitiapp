package com.waqiti.ledger.exception;

/**
 * Exception thrown when attempting ledger operations while system is in emergency freeze
 * All ledger operations are blocked until manual reconciliation and unfreezing by CFO/CTO
 */
public class LedgerFrozenException extends RuntimeException {

    private final String freezeReason;
    private final String freezeTimestamp;

    public LedgerFrozenException(String message) {
        super(message);
        this.freezeReason = "UNKNOWN";
        this.freezeTimestamp = "UNKNOWN";
    }

    public LedgerFrozenException(String freezeReason, String freezeTimestamp) {
        super(String.format(
            "Ledger operations are FROZEN. Reason: %s. Frozen since: %s. " +
            "Contact CFO/CTO for manual reconciliation.",
            freezeReason, freezeTimestamp
        ));
        this.freezeReason = freezeReason;
        this.freezeTimestamp = freezeTimestamp;
    }

    public String getFreezeReason() {
        return freezeReason;
    }

    public String getFreezeTimestamp() {
        return freezeTimestamp;
    }
}
