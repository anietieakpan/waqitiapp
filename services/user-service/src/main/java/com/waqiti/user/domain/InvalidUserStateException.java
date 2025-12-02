package com.waqiti.user.domain;

import java.util.UUID;
/**
 * Thrown when a user's account is not in the required state for an operation
 */
public class InvalidUserStateException extends RuntimeException {
    public InvalidUserStateException(String message) {
        super(message);
    }
}
