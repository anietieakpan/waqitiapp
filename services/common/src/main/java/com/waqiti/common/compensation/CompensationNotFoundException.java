package com.waqiti.common.compensation;

/**
 * Exception thrown when a compensation transaction is not found.
 */
public class CompensationNotFoundException extends RuntimeException {

    public CompensationNotFoundException(String compensationId) {
        super("Compensation not found: " + compensationId);
    }
}
