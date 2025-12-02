package com.waqiti.layer2.exception;

/**
 * Exception thrown when Layer 2 transaction processing fails
 */
public class Layer2ProcessingException extends RuntimeException {

    public Layer2ProcessingException(String message) {
        super(message);
    }

    public Layer2ProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
