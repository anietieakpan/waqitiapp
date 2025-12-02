package com.waqiti.payment.exception;

/**
 * General Escrow Exception
 * Thrown when escrow operations fail
 */
public class EscrowException extends RuntimeException {
    
    public EscrowException(String message) {
        super(message);
    }
    
    public EscrowException(String message, Throwable cause) {
        super(message, cause);
    }
}