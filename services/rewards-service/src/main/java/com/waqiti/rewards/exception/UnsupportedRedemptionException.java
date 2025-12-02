package com.waqiti.rewards.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UnsupportedRedemptionException extends RuntimeException {
    
    public UnsupportedRedemptionException(String message) {
        super(message);
    }
    
    public UnsupportedRedemptionException(String message, Throwable cause) {
        super(message, cause);
    }
}