package com.waqiti.kyc.exception;

import java.util.Map;

public class KYCValidationException extends KYCException {
    
    private final Map<String, String> validationErrors;
    
    public KYCValidationException(String message) {
        super(message, "KYC_VALIDATION_ERROR");
        this.validationErrors = null;
    }
    
    public KYCValidationException(String message, Map<String, String> validationErrors) {
        super(message, "KYC_VALIDATION_ERROR");
        this.validationErrors = validationErrors;
    }
    
    public KYCValidationException(String message, String userId, Map<String, String> validationErrors) {
        super(message, "KYC_VALIDATION_ERROR", userId);
        this.validationErrors = validationErrors;
    }
    
    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }
    
    public static KYCValidationException invalidDocument(String reason) {
        return new KYCValidationException("Invalid document: " + reason);
    }
    
    public static KYCValidationException duplicateVerification(String userId) {
        return new KYCValidationException("Active verification already exists for user", userId, null);
    }
    
    public static KYCValidationException invalidStatus(String currentStatus, String requestedStatus) {
        return new KYCValidationException(
            String.format("Cannot transition from %s to %s", currentStatus, requestedStatus)
        );
    }
}