package com.waqiti.investment.exception;

public class KycRequiredException extends InvestmentException {
    
    public KycRequiredException() {
        super("KYC verification is required before trading");
    }
    
    public KycRequiredException(String message) {
        super(message);
    }
}