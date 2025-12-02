package com.waqiti.investment.domain.enums;

public enum TransferStatus {
    PENDING("Pending"),
    PROCESSING("Processing"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    REVERSED("Reversed");

    private final String displayName;

    TransferStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPending() {
        return this == PENDING || this == PROCESSING;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == REVERSED;
    }

    public boolean isSuccessful() {
        return this == COMPLETED;
    }
}