package com.waqiti.familyaccount.exception;

/**
 * Exception thrown when user attempts unauthorized access
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
public class UnauthorizedAccessException extends FamilyAccountException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(String userId, String familyId) {
        super("User " + userId + " is not authorized to access family account " + familyId);
    }
}
