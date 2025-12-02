package com.waqiti.dispute.exception;

/**
 * Exception thrown when a database operation fails
 *
 * HTTP Status: 500 Internal Server Error
 *
 * @author Waqiti Dispute Team
 */
public class DatabaseOperationException extends DisputeServiceException {

    public DatabaseOperationException(String message) {
        super(message, "DATABASE_OPERATION_ERROR", 500);
    }

    public DatabaseOperationException(String message, Throwable cause) {
        super(message, cause, "DATABASE_OPERATION_ERROR", 500);
    }
}
