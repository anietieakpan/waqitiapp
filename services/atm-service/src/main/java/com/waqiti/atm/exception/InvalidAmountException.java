package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Withdrawal Exceptions
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message) { super(message); }
}
