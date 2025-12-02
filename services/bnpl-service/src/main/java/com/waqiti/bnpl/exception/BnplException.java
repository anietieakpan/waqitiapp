package com.waqiti.bnpl.exception;

/**
 * Base exception for BNPL service
 */
public class BnplException extends RuntimeException {

    public BnplException(String message) {
        super(message);
    }

    public BnplException(String message, Throwable cause) {
        super(message, cause);
    }
}