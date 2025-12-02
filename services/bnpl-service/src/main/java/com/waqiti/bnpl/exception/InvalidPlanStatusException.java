package com.waqiti.bnpl.exception;

/**
 * Exception thrown when plan status is invalid for requested operation
 */
public class InvalidPlanStatusException extends BnplException {

    public InvalidPlanStatusException(String message) {
        super(message);
    }

    public InvalidPlanStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}