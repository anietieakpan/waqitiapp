package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class WithdrawalExpiredException extends RuntimeException {
    public WithdrawalExpiredException(String message) { super(message); }
}
