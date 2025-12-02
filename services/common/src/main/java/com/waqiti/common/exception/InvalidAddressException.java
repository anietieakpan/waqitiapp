package com.waqiti.common.exception;

/**
 * Exception thrown when address validation fails
 */
public class InvalidAddressException extends ValidationException {
    
    public InvalidAddressException(String message) {
        super(message);
    }
    
    public InvalidAddressException(String message, Throwable cause) {
        super(message, cause);
    }
    
    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.INVALID_ADDRESS;
    }
}