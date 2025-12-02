package com.waqiti.rewards.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class RedemptionException extends RuntimeException {
    
    public RedemptionException(String message) {
        super(message);
    }
    
    public RedemptionException(String message, Throwable cause) {
        super(message, cause);
    }
}