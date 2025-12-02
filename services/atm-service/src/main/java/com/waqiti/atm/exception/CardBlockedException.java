package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class CardBlockedException extends RuntimeException {
    public CardBlockedException(String message) { super(message); }
}
