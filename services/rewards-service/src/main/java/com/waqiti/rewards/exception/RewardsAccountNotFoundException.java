package com.waqiti.rewards.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RewardsAccountNotFoundException extends RuntimeException {
    
    public RewardsAccountNotFoundException(String message) {
        super(message);
    }
    
    public RewardsAccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}