package com.waqiti.common.security.awareness.model;

/**
 * Training Status Enum
 *
 * Status of employee training module progress.
 */
public enum TrainingStatus {
    NOT_STARTED,    // Training assigned but not started
    IN_PROGRESS,    // Training in progress
    COMPLETED,      // Training completed successfully
    FAILED,         // Training failed (score below passing)
    EXPIRED         // Training certificate expired (annual renewal required)
}