package com.waqiti.corebanking.exception;

import com.waqiti.common.exception.BusinessException;

/**
 * Exception thrown when a transaction fails compliance validation
 */
public class ComplianceValidationException extends BusinessException {
    
    private final String complianceReason;
    private final String transactionId;
    
    public ComplianceValidationException(String message, String transactionId, String complianceReason) {
        super(message);
        this.transactionId = transactionId;
        this.complianceReason = complianceReason;
    }
    
    public ComplianceValidationException(String message, String transactionId, String complianceReason, Throwable cause) {
        super(message, cause);
        this.transactionId = transactionId;
        this.complianceReason = complianceReason;
    }
    
    public String getComplianceReason() {
        return complianceReason;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
}