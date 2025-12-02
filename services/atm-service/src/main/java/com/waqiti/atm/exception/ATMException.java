package com.waqiti.atm.exception;

/**
 * Base exception for ATM service errors
 */
public class ATMException extends RuntimeException {

    public ATMException(String message) {
        super(message);
    }

    public ATMException(String message, Throwable cause) {
        super(message, cause);
    }
}
