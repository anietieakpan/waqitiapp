package com.waqiti.payment.exception;

import java.util.UUID;

/**
 * Exception thrown when a duplicate check is detected
 */
public class DuplicateCheckException extends CheckDepositException {
    
    private final UUID existingDepositId;
    
    public DuplicateCheckException(String message, UUID existingDepositId) {
        super(message);
        this.existingDepositId = existingDepositId;
    }
    
    public UUID getExistingDepositId() {
        return existingDepositId;
    }
}