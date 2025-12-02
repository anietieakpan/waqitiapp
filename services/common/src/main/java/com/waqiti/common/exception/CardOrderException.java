package com.waqiti.common.exception;

/**
 * Exception thrown when card order processing fails
 */
public class CardOrderException extends BusinessException {
    
    public CardOrderException(String message) {
        super(message);
    }
    
    public CardOrderException(String message, Throwable cause) {
        super(message, cause);
    }
    
    @Override
    public String getErrorCode() {
        return ErrorCode.CARD_ORDER_FAILED.name();
    }
}