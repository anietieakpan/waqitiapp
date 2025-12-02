package com.waqiti.audit.domain;

/**
 * Alert status levels
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    ESCALATED
}