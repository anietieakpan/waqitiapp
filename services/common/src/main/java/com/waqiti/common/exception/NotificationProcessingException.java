package com.waqiti.common.exception;

/**
 * Exception thrown when notification processing fails
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
public class NotificationProcessingException extends RuntimeException {

    public NotificationProcessingException(String message) {
        super(message);
    }

    public NotificationProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
