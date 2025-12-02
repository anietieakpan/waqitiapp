package com.waqiti.common.security.awareness.model;

/**
 * Assessment Status Enum
 *
 * Status of quarterly security assessment.
 */
public enum AssessmentStatus {
    DRAFT,          // Assessment created but not published
    PUBLISHED,      // Assessment available to employees
    IN_PROGRESS,    // Assessment currently active
    COMPLETED,      // Assessment period ended
    ARCHIVED        // Assessment archived for historical reference
}