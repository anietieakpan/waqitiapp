package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// ATM Location Exceptions
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ATMNotFoundException extends RuntimeException {
    public ATMNotFoundException(String message) { super(message); }
}

