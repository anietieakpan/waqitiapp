package com.waqiti.common.exception;

/**
 * Exception thrown when compliance processing operations fail.
 * This exception should be used for cases where compliance checks,
 * sanctions screening, or regulatory processes cannot be completed safely.
 */
public class ComplianceProcessingException extends BusinessException {
    
    private final String complianceCheckId;
    private final String entityId;
    private final String checkType;
    
    public ComplianceProcessingException(String message) {
        super(ErrorCode.COMPLIANCE_PROCESSING_ERROR, message);
        this.complianceCheckId = null;
        this.entityId = null;
        this.checkType = null;
    }
    
    public ComplianceProcessingException(String message, Throwable cause) {
        super(ErrorCode.COMPLIANCE_PROCESSING_ERROR, message, cause);
        this.complianceCheckId = null;
        this.entityId = null;
        this.checkType = null;
    }
    
    public ComplianceProcessingException(String message, String entityId, String checkType) {
        super(ErrorCode.COMPLIANCE_PROCESSING_ERROR, message);
        this.complianceCheckId = null;
        this.entityId = entityId;
        this.checkType = checkType;
    }
    
    public ComplianceProcessingException(String message, String complianceCheckId, String entityId, String checkType) {
        super(ErrorCode.COMPLIANCE_PROCESSING_ERROR, message);
        this.complianceCheckId = complianceCheckId;
        this.entityId = entityId;
        this.checkType = checkType;
    }
    
    public ComplianceProcessingException(String message, Throwable cause, String entityId, String checkType) {
        super(ErrorCode.COMPLIANCE_PROCESSING_ERROR, message, cause);
        this.complianceCheckId = null;
        this.entityId = entityId;
        this.checkType = checkType;
    }
    
    public String getComplianceCheckId() {
        return complianceCheckId;
    }
    
    public String getEntityId() {
        return entityId;
    }
    
    public String getCheckType() {
        return checkType;
    }
}