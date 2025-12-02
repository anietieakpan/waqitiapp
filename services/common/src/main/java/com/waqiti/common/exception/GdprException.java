package com.waqiti.common.exception;

/**
 * Exception indicating a GDPR compliance violation.
 * Used for data protection and privacy requirement failures.
 */
public class GdprException extends RuntimeException {

    private final String violationType;
    private final boolean requiresBreachNotification;

    public GdprException(String message) {
        super(message);
        this.violationType = "GDPR_VIOLATION";
        this.requiresBreachNotification = false;
    }

    public GdprException(String message, Throwable cause) {
        super(message, cause);
        this.violationType = "GDPR_VIOLATION";
        this.requiresBreachNotification = false;
    }

    public GdprException(String message, String violationType, boolean requiresBreachNotification) {
        super(message);
        this.violationType = violationType;
        this.requiresBreachNotification = requiresBreachNotification;
    }

    public String getViolationType() {
        return violationType;
    }

    public boolean requiresBreachNotification() {
        return requiresBreachNotification;
    }
}
