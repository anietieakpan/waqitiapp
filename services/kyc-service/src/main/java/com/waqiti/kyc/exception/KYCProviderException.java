package com.waqiti.kyc.exception;

public class KYCProviderException extends KYCException {
    
    private final String provider;
    private final String providerErrorCode;
    
    public KYCProviderException(String message, String provider) {
        super(message, "KYC_PROVIDER_ERROR");
        this.provider = provider;
        this.providerErrorCode = null;
    }
    
    public KYCProviderException(String message, String provider, String providerErrorCode) {
        super(message, "KYC_PROVIDER_ERROR");
        this.provider = provider;
        this.providerErrorCode = providerErrorCode;
    }
    
    public KYCProviderException(String message, String provider, Throwable cause) {
        super(message, "KYC_PROVIDER_ERROR", cause);
        this.provider = provider;
        this.providerErrorCode = null;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public String getProviderErrorCode() {
        return providerErrorCode;
    }
    
    public static KYCProviderException providerUnavailable(String provider) {
        return new KYCProviderException("KYC provider unavailable: " + provider, provider);
    }
    
    public static KYCProviderException providerTimeout(String provider) {
        return new KYCProviderException("KYC provider timeout: " + provider, provider);
    }
    
    public static KYCProviderException invalidCredentials(String provider) {
        return new KYCProviderException("Invalid credentials for provider: " + provider, provider);
    }
}