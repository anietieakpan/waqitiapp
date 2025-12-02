package com.waqiti.reconciliation.exception;

/**
 * Base exception class for all reconciliation-related exceptions
 */
public class ReconciliationException extends RuntimeException {

    private String errorCode;
    private Object[] errorParameters;

    public ReconciliationException(String message) {
        super(message);
    }

    public ReconciliationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReconciliationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ReconciliationException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ReconciliationException(String message, String errorCode, Object[] errorParameters) {
        super(message);
        this.errorCode = errorCode;
        this.errorParameters = errorParameters;
    }

    public ReconciliationException(String message, String errorCode, Object[] errorParameters, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorParameters = errorParameters;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getErrorParameters() {
        return errorParameters;
    }
}