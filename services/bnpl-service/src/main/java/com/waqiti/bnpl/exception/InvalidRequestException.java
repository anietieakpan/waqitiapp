package com.waqiti.bnpl.exception;

/**
 * Exception thrown for invalid requests
 */
public class InvalidRequestException extends BnplException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}