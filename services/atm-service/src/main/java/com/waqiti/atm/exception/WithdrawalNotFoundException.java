package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Cardless Withdrawal Exceptions
@ResponseStatus(HttpStatus.NOT_FOUND)
public class WithdrawalNotFoundException extends RuntimeException {
    public WithdrawalNotFoundException(String message) { super(message); }
}
