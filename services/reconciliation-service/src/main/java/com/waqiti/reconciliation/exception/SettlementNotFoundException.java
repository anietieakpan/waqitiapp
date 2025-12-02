package com.waqiti.reconciliation.exception;

import java.util.UUID;

/**
 * Exception thrown when a settlement cannot be found
 */
public class SettlementNotFoundException extends ReconciliationException {

    private UUID settlementId;
    private String externalReference;

    public SettlementNotFoundException(String message) {
        super(message, "SETTLEMENT_NOT_FOUND");
    }

    public SettlementNotFoundException(String message, UUID settlementId) {
        super(message, "SETTLEMENT_NOT_FOUND", new Object[]{settlementId});
        this.settlementId = settlementId;
    }

    public SettlementNotFoundException(String message, String externalReference) {
        super(message, "SETTLEMENT_NOT_FOUND", new Object[]{externalReference});
        this.externalReference = externalReference;
    }

    public SettlementNotFoundException(String message, Throwable cause) {
        super(message, "SETTLEMENT_NOT_FOUND", cause);
    }

    public UUID getSettlementId() {
        return settlementId;
    }

    public String getExternalReference() {
        return externalReference;
    }
}