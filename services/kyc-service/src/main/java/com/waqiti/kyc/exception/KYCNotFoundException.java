package com.waqiti.kyc.exception;

public class KYCNotFoundException extends KYCException {
    
    public KYCNotFoundException(String message) {
        super(message, "KYC_NOT_FOUND");
    }
    
    public KYCNotFoundException(String message, String userId) {
        super(message, "KYC_NOT_FOUND", userId);
    }
    
    public static KYCNotFoundException verificationNotFound(String verificationId) {
        return new KYCNotFoundException("KYC verification not found: " + verificationId);
    }
    
    public static KYCNotFoundException documentNotFound(String documentId) {
        return new KYCNotFoundException("Document not found: " + documentId);
    }
    
    public static KYCNotFoundException userVerificationNotFound(String userId) {
        return new KYCNotFoundException("No KYC verification found for user: " + userId, userId);
    }
}