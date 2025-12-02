package com.waqiti.common.exception;

/**
 * Exception indicating a recoverable error that should trigger retry logic.
 * Used in DLQ consumers to signal that the message processing can be retried.
 */
public class RecoverableException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public RecoverableException(String message) {
        super(message);
        this.errorCode = "RECOVERABLE_ERROR";
        this.retryable = true;
    }

    public RecoverableException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "RECOVERABLE_ERROR";
        this.retryable = true;
    }

    public RecoverableException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = true;
    }

    public RecoverableException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = true;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
