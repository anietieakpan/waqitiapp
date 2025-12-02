package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCardStatusException extends RuntimeException {
    public InvalidCardStatusException(String message) { super(message); }
}
