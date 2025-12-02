package com.waqiti.compliance.domain;

/**
 * Status of asset freeze
 */
public enum FreezeStatus {
    ACTIVE("Active - Assets currently frozen"),
    PENDING("Pending - Freeze being applied"),
    RELEASED("Released - Freeze lifted"),
    PARTIAL("Partial - Some assets frozen"),
    EXPIRED("Expired - Freeze period ended"),
    APPEALED("Appealed - Under appeal review"),
    SUSPENDED("Suspended - Temporarily suspended");

    private final String description;

    FreezeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
