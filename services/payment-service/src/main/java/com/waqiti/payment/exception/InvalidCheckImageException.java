package com.waqiti.payment.exception;

/**
 * Exception thrown when check images are invalid or cannot be processed
 */
public class InvalidCheckImageException extends CheckDepositException {
    
    private final String imageType; // "front" or "back"
    private final String reason;
    
    public InvalidCheckImageException(String message, String imageType, String reason) {
        super(message);
        this.imageType = imageType;
        this.reason = reason;
    }
    
    public String getImageType() {
        return imageType;
    }
    
    public String getReason() {
        return reason;
    }
}