package com.waqiti.dispute.exception;

/**
 * Exception thrown when dispute validation fails
 *
 * HTTP Status: 400 Bad Request
 *
 * @author Waqiti Dispute Team
 */
public class DisputeValidationException extends DisputeServiceException {

    public DisputeValidationException(String message) {
        super(message, "DISPUTE_VALIDATION_ERROR", 400);
    }

    public DisputeValidationException(String message, Throwable cause) {
        super(message, cause, "DISPUTE_VALIDATION_ERROR", 400);
    }
}
