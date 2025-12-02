package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CardAlreadyBlockedException extends RuntimeException {
    public CardAlreadyBlockedException(String message) { super(message); }
}
