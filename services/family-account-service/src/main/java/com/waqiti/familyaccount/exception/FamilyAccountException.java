package com.waqiti.familyaccount.exception;

/**
 * Base exception for Family Account operations
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
public class FamilyAccountException extends RuntimeException {

    public FamilyAccountException(String message) {
        super(message);
    }

    public FamilyAccountException(String message, Throwable cause) {
        super(message, cause);
    }
}
