package com.waqiti.dispute.exception;

/**
 * Exception thrown when dispute processing fails
 *
 * HTTP Status: 500 Internal Server Error
 *
 * @author Waqiti Dispute Team
 */
public class DisputeProcessingException extends DisputeServiceException {

    public DisputeProcessingException(String message) {
        super(message, "DISPUTE_PROCESSING_ERROR", 500);
    }

    public DisputeProcessingException(String message, Throwable cause) {
        super(message, cause, "DISPUTE_PROCESSING_ERROR", 500);
    }
}
