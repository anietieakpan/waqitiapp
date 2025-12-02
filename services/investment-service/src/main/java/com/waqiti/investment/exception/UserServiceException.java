package com.waqiti.investment.exception;

/**
 * User Service Exception - thrown when user-service integration fails
 *
 * This exception is thrown when:
 * - User service is unavailable (circuit breaker open)
 * - User profile not found
 * - Tax information not available
 * - Network communication errors
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
public class UserServiceException extends RuntimeException {

    public UserServiceException(String message) {
        super(message);
    }

    public UserServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
