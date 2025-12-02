package com.waqiti.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class DailyLimitExceededException extends RuntimeException {
    public DailyLimitExceededException(String message) { super(message); }
}
