package com.waqiti.dispute.entity;

/**
 * Dispute priority enumeration
 */
public enum DisputePriority {
    LOW("Low priority dispute"),
    MEDIUM("Medium priority dispute"),
    HIGH("High priority dispute"),
    CRITICAL("Critical priority - immediate attention required");

    private final String description;

    DisputePriority(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
