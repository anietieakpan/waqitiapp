package com.waqiti.rewards.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RewardsAccountExistsException extends RuntimeException {
    
    public RewardsAccountExistsException(String message) {
        super(message);
    }
    
    public RewardsAccountExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}