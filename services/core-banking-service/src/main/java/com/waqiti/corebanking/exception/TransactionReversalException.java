package com.waqiti.corebanking.exception;

import com.waqiti.common.exception.BusinessException;

/**
 * Exception thrown when transaction reversal fails
 */
public class TransactionReversalException extends BusinessException {
    
    private final String originalTransactionId;
    private final String reason;
    
    public TransactionReversalException(String message, String originalTransactionId, String reason) {
        super(message);
        this.originalTransactionId = originalTransactionId;
        this.reason = reason;
    }
    
    public TransactionReversalException(String message, String originalTransactionId, String reason, Throwable cause) {
        super(message, cause);
        this.originalTransactionId = originalTransactionId;
        this.reason = reason;
    }
    
    public String getOriginalTransactionId() {
        return originalTransactionId;
    }
    
    public String getReason() {
        return reason;
    }
}