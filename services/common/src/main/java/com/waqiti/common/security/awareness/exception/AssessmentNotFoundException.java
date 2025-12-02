package com.waqiti.common.security.awareness.exception;

import java.util.UUID;

/**
 * Exception thrown when assessment is not found
 *
 * @author Waqiti Platform Team
 */
public class AssessmentNotFoundException extends SecurityAwarenessException {

    public AssessmentNotFoundException(UUID assessmentId) {
        super("Assessment not found: " + assessmentId);
    }

    public AssessmentNotFoundException(String message) {
        super(message);
    }
}