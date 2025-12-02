package com.waqiti.common.exception;

/**
 * Exception thrown when card limit is exceeded
 */
public class CardLimitExceededException extends BusinessException {
    
    public CardLimitExceededException(String message) {
        super(message);
    }
    
    public CardLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
    
    @Override
    public String getErrorCode() {
        return "CARD_LIMIT_EXCEEDED";
    }
}