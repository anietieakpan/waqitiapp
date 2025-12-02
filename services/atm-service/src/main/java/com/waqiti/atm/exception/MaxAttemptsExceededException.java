package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class MaxAttemptsExceededException extends RuntimeException {
    public MaxAttemptsExceededException(String message) { super(message); }
}
