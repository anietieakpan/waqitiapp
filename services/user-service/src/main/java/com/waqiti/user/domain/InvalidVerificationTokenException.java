package com.waqiti.user.domain;

import java.util.UUID;


/**
 * Thrown when a verification token is invalid
 */
public class InvalidVerificationTokenException extends RuntimeException {
    public InvalidVerificationTokenException(String message) {
        super(message);
    }
}