package com.waqiti.common.model.incident;

public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    public boolean isResolved() {
        return this == RESOLVED || this == CLOSED;
    }

    public boolean isActive() {
        return this == OPEN || this == ACKNOWLEDGED || this == IN_PROGRESS;
    }
}
