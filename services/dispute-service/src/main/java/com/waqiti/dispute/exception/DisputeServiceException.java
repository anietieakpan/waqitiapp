package com.waqiti.dispute.exception;

/**
 * Base exception for all dispute service exceptions
 *
 * This is the root of the exception hierarchy for the dispute service.
 * All custom exceptions should extend this class or one of its subclasses.
 *
 * @author Waqiti Dispute Team
 * @version 1.0.0
 */
public class DisputeServiceException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public DisputeServiceException(String message) {
        super(message);
        this.errorCode = "DISPUTE_ERROR";
        this.httpStatus = 500;
    }

    public DisputeServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "DISPUTE_ERROR";
        this.httpStatus = 500;
    }

    public DisputeServiceException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public DisputeServiceException(String message, Throwable cause, String errorCode, int httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
