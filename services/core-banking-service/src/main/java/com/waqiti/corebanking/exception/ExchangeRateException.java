package com.waqiti.corebanking.exception;

/**
 * Exception thrown when exchange rate operations fail
 */
public class ExchangeRateException extends RuntimeException {

    public ExchangeRateException(String message) {
        super(message);
    }

    public ExchangeRateException(String message, Throwable cause) {
        super(message, cause);
    }
}