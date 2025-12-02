package com.waqiti.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * UNAUTHORIZED EXCEPTION
 *
 * Thrown when user lacks required permissions or authentication.
 *
 * SECURITY:
 * - HTTP 401 Unauthorized response
 * - Used by authorization framework
 * - Logged by audit system
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-01-01
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
