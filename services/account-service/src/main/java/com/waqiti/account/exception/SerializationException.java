package com.waqiti.account.exception;

/**
 * Exception thrown when JSON serialization or deserialization fails.
 *
 * This is a runtime exception that indicates a critical error in data
 * transformation that should not be silently ignored.
 *
 * Common causes:
 * - Invalid JSON format
 * - Type mismatch during deserialization
 * - Circular references in object graph
 * - Missing Jackson modules for specific types
 *
 * CRITICAL FIX P0-4: Replaces silent null returns with explicit exceptions
 * to prevent data corruption and improve debuggability.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
public class SerializationException extends RuntimeException {

    private final String dataType;
    private final String operation;

    public SerializationException(String message) {
        super(message);
        this.dataType = null;
        this.operation = null;
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
        this.dataType = null;
        this.operation = null;
    }

    public SerializationException(String message, String dataType, String operation, Throwable cause) {
        super(message, cause);
        this.dataType = dataType;
        this.operation = operation;
    }

    public String getDataType() {
        return dataType;
    }

    public String getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (dataType != null) {
            sb.append(" [dataType=").append(dataType).append("]");
        }
        if (operation != null) {
            sb.append(" [operation=").append(operation).append("]");
        }
        return sb.toString();
    }
}
