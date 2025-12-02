package com.waqiti.ledger.exception;

/**
 * CRITICAL exception thrown when ledger integrity is compromised
 * This exception triggers emergency freeze of all ledger operations
 * Requires immediate CFO/CTO intervention
 */
public class CriticalLedgerException extends RuntimeException {

    private final String errorCode;
    private final Object details;

    public CriticalLedgerException(String message) {
        super(message);
        this.errorCode = "CRITICAL_LEDGER_FAILURE";
        this.details = null;
    }

    public CriticalLedgerException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CRITICAL_LEDGER_FAILURE";
        this.details = null;
    }

    public CriticalLedgerException(String errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
