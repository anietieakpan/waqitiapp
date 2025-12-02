package com.waqiti.layer2.exception;

/**
 * Exception thrown when an invalid Ethereum address is provided
 */
public class InvalidAddressException extends Layer2ProcessingException {

    public InvalidAddressException(String message) {
        super(message);
    }
}
