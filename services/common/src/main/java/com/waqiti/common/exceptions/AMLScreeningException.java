package com.waqiti.common.exceptions;

/**
 * Exception thrown when AML (Anti-Money Laundering) screening fails.
 * This is a critical exception that indicates a failure in compliance screening.
 */
public class AMLScreeningException extends RuntimeException {

    private String entityId;
    private String screeningId;
    private String errorCode;

    public AMLScreeningException(String message) {
        super(message);
    }

    public AMLScreeningException(String message, Throwable cause) {
        super(message, cause);
    }

    public AMLScreeningException(String message, String entityId, String screeningId) {
        super(message);
        this.entityId = entityId;
        this.screeningId = screeningId;
    }

    public AMLScreeningException(String message, String entityId, String screeningId, String errorCode) {
        super(message);
        this.entityId = entityId;
        this.screeningId = screeningId;
        this.errorCode = errorCode;
    }

    public AMLScreeningException(String message, Throwable cause, String entityId, String screeningId) {
        super(message, cause);
        this.entityId = entityId;
        this.screeningId = screeningId;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getScreeningId() {
        return screeningId;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
