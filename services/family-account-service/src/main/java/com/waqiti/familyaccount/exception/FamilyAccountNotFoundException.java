package com.waqiti.familyaccount.exception;

/**
 * Exception thrown when family account is not found
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
public class FamilyAccountNotFoundException extends FamilyAccountException {

    public FamilyAccountNotFoundException(String familyId) {
        super("Family account not found: " + familyId);
    }
}
