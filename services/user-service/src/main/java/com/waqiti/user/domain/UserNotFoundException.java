package com.waqiti.user.domain;

import java.util.UUID;

/**
 * Thrown when a user is not found
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User not found with ID: " + id);
    }

    public UserNotFoundException(String identifier) {
        super("User not found with identifier: " + identifier);
    }
}