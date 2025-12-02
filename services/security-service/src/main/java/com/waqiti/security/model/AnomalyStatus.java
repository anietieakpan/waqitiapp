package com.waqiti.security.model;

/**
 * Anomaly Status Enum
 * Represents the current status of a detected anomaly
 */
public enum AnomalyStatus {
    ACTIVE,
    RESOLVED,
    FALSE_POSITIVE,
    UNDER_INVESTIGATION,
    IGNORED,
    ESCALATED
}
