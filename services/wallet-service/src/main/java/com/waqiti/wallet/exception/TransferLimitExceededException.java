package com.waqiti.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class TransferLimitExceededException extends RuntimeException {
    
    public TransferLimitExceededException(String message) {
        super(message);
    }
    
    public TransferLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}