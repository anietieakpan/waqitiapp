package com.waqiti.dispute.exception;

/**
 * Exception thrown when external service call fails with a retriable error.
 * Indicates transient failure that may succeed on retry.
 *
 * Examples:
 * - 408 Request Timeout (network timeout)
 * - 429 Too Many Requests (rate limit exceeded)
 * - 500 Internal Server Error (temporary server issue)
 * - 502 Bad Gateway (upstream server error)
 * - 503 Service Unavailable (service temporarily down)
 * - 504 Gateway Timeout (upstream timeout)
 *
 * This exception should trigger retry logic via Resilience4j @Retry annotation.
 *
 * @author Waqiti Development Team
 * @since 1.0.0
 */
public class RetryableException extends RuntimeException {

    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
