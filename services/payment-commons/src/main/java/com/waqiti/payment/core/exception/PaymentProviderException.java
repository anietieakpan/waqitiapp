package com.waqiti.payment.core.exception;

import com.waqiti.common.exception.BusinessException;

/**
 * Exception thrown when payment provider API calls fail
 */
public class PaymentProviderException extends BusinessException {
    
    private final String provider;
    private final String operation;
    private final String providerErrorCode;
    
    public PaymentProviderException(String message, String provider, String operation) {
        super(message);
        this.provider = provider;
        this.operation = operation;
        this.providerErrorCode = null;
    }
    
    public PaymentProviderException(String message, String provider, String operation, String providerErrorCode) {
        super(message);
        this.provider = provider;
        this.operation = operation;
        this.providerErrorCode = providerErrorCode;
    }
    
    public PaymentProviderException(String message, String provider, String operation, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.operation = operation;
        this.providerErrorCode = null;
    }
    
    public PaymentProviderException(String message, String provider, String operation, String providerErrorCode, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.operation = operation;
        this.providerErrorCode = providerErrorCode;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public String getProviderErrorCode() {
        return providerErrorCode;
    }
}