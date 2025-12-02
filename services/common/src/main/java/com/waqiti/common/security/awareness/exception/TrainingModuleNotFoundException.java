package com.waqiti.common.security.awareness.exception;

import java.util.UUID;

/**
 * Exception thrown when training module is not found
 *
 * @author Waqiti Platform Team
 */
public class TrainingModuleNotFoundException extends SecurityAwarenessException {

    public TrainingModuleNotFoundException(UUID moduleId) {
        super("Training module not found: " + moduleId);
    }

    public TrainingModuleNotFoundException(String message) {
        super(message);
    }
}