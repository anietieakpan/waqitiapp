package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidWithdrawalStatusException extends RuntimeException {
    public InvalidWithdrawalStatusException(String message) { super(message); }
}
