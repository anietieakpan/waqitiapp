package com.waqiti.atm.exception;

/**
 * Exception for check imaging failures
 */
public class CheckImagingException extends ATMException {

    public CheckImagingException(String message) {
        super(message);
    }

    public CheckImagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
