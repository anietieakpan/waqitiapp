package com.waqiti.common.exception;

/**
 * Exception thrown when financial calculations fail or produce invalid results.
 * This exception is critical for preventing monetary losses in the system.
 */
public class FinancialCalculationException extends RuntimeException {

    /**
     * Constructs a new financial calculation exception with the specified detail message.
     *
     * @param message the detail message
     */
    public FinancialCalculationException(String message) {
        super(message);
    }

    /**
     * Constructs a new financial calculation exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public FinancialCalculationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new financial calculation exception with the specified cause.
     *
     * @param cause the cause
     */
    public FinancialCalculationException(Throwable cause) {
        super(cause);
    }
}