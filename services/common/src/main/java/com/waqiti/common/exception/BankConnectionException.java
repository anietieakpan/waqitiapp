package com.waqiti.common.exception;

/**
 * Exception thrown when connection to external bank systems fails.
 * This is a recoverable exception that should trigger circuit breaker and retry logic.
 */
public class BankConnectionException extends RuntimeException {

    private final String bankCode;
    private final String connectionId;

    public BankConnectionException(String message) {
        super(message);
        this.bankCode = null;
        this.connectionId = null;
    }

    public BankConnectionException(String message, Throwable cause) {
        super(message, cause);
        this.bankCode = null;
        this.connectionId = null;
    }

    public BankConnectionException(String message, String bankCode, String connectionId) {
        super(message);
        this.bankCode = bankCode;
        this.connectionId = connectionId;
    }

    public BankConnectionException(String message, String bankCode, String connectionId, Throwable cause) {
        super(message, cause);
        this.bankCode = bankCode;
        this.connectionId = connectionId;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getConnectionId() {
        return connectionId;
    }
}
