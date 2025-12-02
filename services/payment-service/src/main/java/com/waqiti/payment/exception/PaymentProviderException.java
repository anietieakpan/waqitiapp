package com.waqiti.payment.exception;

/**
 * Exception thrown when there are issues with payment providers
 */
public class PaymentProviderException extends RuntimeException {
    
    private final String providerName;
    private final String errorCode;
    private final String providerErrorCode;
    
    public PaymentProviderException(String message) {
        super(message);
        this.providerName = null;
        this.errorCode = "PROVIDER_ERROR";
        this.providerErrorCode = null;
    }
    
    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
        this.providerName = null;
        this.errorCode = "PROVIDER_ERROR";
        this.providerErrorCode = null;
    }
    
    public PaymentProviderException(String message, String providerName) {
        super(message);
        this.providerName = providerName;
        this.errorCode = "PROVIDER_ERROR";
        this.providerErrorCode = null;
    }
    
    public PaymentProviderException(String message, String providerName, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
        this.errorCode = "PROVIDER_ERROR";
        this.providerErrorCode = null;
    }
    
    public PaymentProviderException(String message, String providerName, String errorCode, String providerErrorCode) {
        super(message);
        this.providerName = providerName;
        this.errorCode = errorCode;
        this.providerErrorCode = providerErrorCode;
    }
    
    public PaymentProviderException(String message, String providerName, String errorCode, String providerErrorCode, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
        this.errorCode = errorCode;
        this.providerErrorCode = providerErrorCode;
    }
    
    public String getProviderName() {
        return providerName;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getProviderErrorCode() {
        return providerErrorCode;
    }
}