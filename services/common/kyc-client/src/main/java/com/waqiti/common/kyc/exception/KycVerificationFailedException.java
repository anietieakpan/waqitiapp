package com.waqiti.common.kyc.exception;

/**
 * Exception thrown when KYC verification fails
 */
public class KycVerificationFailedException extends RuntimeException {
    
    private final String reason;
    private final String userId;
    
    public KycVerificationFailedException(String message) {
        super(message);
        this.reason = null;
        this.userId = null;
    }
    
    public KycVerificationFailedException(String message, String reason, String userId) {
        super(message);
        this.reason = reason;
        this.userId = userId;
    }
    
    public KycVerificationFailedException(String message, Throwable cause) {
        super(message, cause);
        this.reason = null;
        this.userId = null;
    }
    
    public String getReason() {
        return reason;
    }
    
    public String getUserId() {
        return userId;
    }
}