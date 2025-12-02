package com.waqiti.accounting.exception;

/**
 * Exception thrown during settlement processing errors
 */
public class SettlementException extends AccountingException {

    private final String settlementId;

    public SettlementException(String settlementId, String message) {
        super("SETTLEMENT_ERROR", message, settlementId);
        this.settlementId = settlementId;
    }

    public SettlementException(String settlementId, String message, Throwable cause) {
        super("SETTLEMENT_ERROR", message, cause, settlementId);
        this.settlementId = settlementId;
    }

    public String getSettlementId() {
        return settlementId;
    }
}
