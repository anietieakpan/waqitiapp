package com.waqiti.layer2.exception;

/**
 * Exception thrown when signature validation fails
 */
public class InvalidSignatureException extends Layer2ProcessingException {

    public InvalidSignatureException(String message) {
        super(message);
    }
}
