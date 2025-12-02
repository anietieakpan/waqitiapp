package com.waqiti.corebanking.exception;

import com.waqiti.common.exception.BusinessException;

/**
 * Exception thrown when transaction status update fails
 */
public class TransactionStatusUpdateException extends BusinessException {
    
    private final String transactionId;
    private final String targetStatus;
    
    public TransactionStatusUpdateException(String message, String transactionId, String targetStatus) {
        super(message);
        this.transactionId = transactionId;
        this.targetStatus = targetStatus;
    }
    
    public TransactionStatusUpdateException(String message, String transactionId, String targetStatus, Throwable cause) {
        super(message, cause);
        this.transactionId = transactionId;
        this.targetStatus = targetStatus;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public String getTargetStatus() {
        return targetStatus;
    }
}