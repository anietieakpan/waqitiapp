package com.waqiti.common.exception;

/**
 * Exception thrown when data constraint violations occur.
 * Used for database constraints, validation rules, and business constraints.
 *
 * Examples:
 * - Unique constraint violations
 * - Foreign key violations
 * - Check constraint violations
 * - Business rule violations
 *
 * @author Waqiti Platform
 */
public class ConstraintViolationException extends RuntimeException {

    private final String constraintName;
    private final Object invalidValue;

    /**
     * Creates exception with message
     */
    public ConstraintViolationException(String message) {
        super(message);
        this.constraintName = null;
        this.invalidValue = null;
    }

    /**
     * Creates exception with message and cause
     */
    public ConstraintViolationException(String message, Throwable cause) {
        super(message, cause);
        this.constraintName = null;
        this.invalidValue = null;
    }

    /**
     * Creates exception with detailed constraint information
     */
    public ConstraintViolationException(String message, String constraintName, Object invalidValue) {
        super(message);
        this.constraintName = constraintName;
        this.invalidValue = invalidValue;
    }

    /**
     * Creates exception with detailed constraint information and cause
     */
    public ConstraintViolationException(String message, String constraintName, Object invalidValue, Throwable cause) {
        super(message, cause);
        this.constraintName = constraintName;
        this.invalidValue = invalidValue;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public Object getInvalidValue() {
        return invalidValue;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (constraintName != null) {
            sb.append(", constraint=").append(constraintName);
        }
        if (invalidValue != null) {
            sb.append(", invalidValue=").append(invalidValue);
        }
        return sb.toString();
    }
}
