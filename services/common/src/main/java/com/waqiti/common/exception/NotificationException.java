package com.waqiti.common.exception;

/**
 * Exception thrown when notification operations fail
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
public class NotificationException extends RuntimeException {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationException(Throwable cause) {
        super(cause);
    }
}
