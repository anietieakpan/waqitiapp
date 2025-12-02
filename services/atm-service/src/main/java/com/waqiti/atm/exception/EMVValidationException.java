package com.waqiti.atm.exception;

/**
 * Exception for EMV validation failures
 */
public class EMVValidationException extends ATMException {

    public EMVValidationException(String message) {
        super(message);
    }

    public EMVValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
