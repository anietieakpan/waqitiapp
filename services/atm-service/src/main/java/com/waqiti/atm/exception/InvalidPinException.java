package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// PIN Exceptions
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPinException extends RuntimeException {
    public InvalidPinException(String message) { super(message); }
}
