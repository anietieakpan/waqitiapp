package com.waqiti.familyaccount.exception;

/**
 * Exception thrown when family member is not found
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
public class FamilyMemberNotFoundException extends FamilyAccountException {

    public FamilyMemberNotFoundException(String userId) {
        super("Family member not found: " + userId);
    }

    public FamilyMemberNotFoundException(String familyId, String userId) {
        super("Family member not found in family " + familyId + ": " + userId);
    }
}
