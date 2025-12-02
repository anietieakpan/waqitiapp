package com.waqiti.payment.exception;

/**
 * Invalid Escrow State Exception
 * Thrown when an operation is attempted on an escrow account in an invalid state
 */
public class InvalidEscrowStateException extends EscrowException {
    
    public InvalidEscrowStateException(String message) {
        super(message);
    }
    
    public InvalidEscrowStateException(String message, Throwable cause) {
        super(message, cause);
    }
}