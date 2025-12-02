/**
 * File: ./common/src/main/java/com/waqiti/common/exception/TransactionFailedException.java
 */
package com.waqiti.common.exception;

import java.util.UUID;

/**
 * Exception thrown when a transaction fails to complete
 */
public class TransactionFailedException extends BusinessException {
    private UUID transactionId;
    private String errorCode;

    public TransactionFailedException(String message) {
        super(message);
    }

    public TransactionFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransactionFailedException(UUID transactionId, String message) {
        super(message);
        this.transactionId = transactionId;
    }

    public TransactionFailedException(UUID transactionId, String errorCode, String message) {
        super(message);
        this.transactionId = transactionId;
        this.errorCode = errorCode;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getErrorCode() {
        return errorCode;
    }
    
    public String getFailureReason() {
        return getMessage();
    }
}