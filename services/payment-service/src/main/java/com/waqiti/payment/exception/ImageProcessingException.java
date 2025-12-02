package com.waqiti.payment.exception.checkdeposit;

import com.waqiti.payment.exception.CheckDepositException;

/**
 * Exception thrown when image processing fails for check deposits
 */
public class ImageProcessingException extends CheckDepositException {
    
    public ImageProcessingException(String message) {
        super(message);
    }
    
    public ImageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ImageProcessingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}