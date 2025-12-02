package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) { super(message); }
}
