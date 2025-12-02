package com.waqiti.investment.domain.enums;

public enum OrderStatus {
    NEW("New"),
    PENDING_SUBMIT("Pending Submit"),
    ACCEPTED("Accepted"),
    PENDING_CANCEL("Pending Cancel"),
    PARTIALLY_FILLED("Partially Filled"),
    FILLED("Filled"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    REJECTED("Rejected"),
    FAILED("Failed");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return this == NEW || 
               this == PENDING_SUBMIT || 
               this == ACCEPTED || 
               this == PARTIALLY_FILLED;
    }

    public boolean isTerminal() {
        return this == FILLED || 
               this == CANCELLED || 
               this == EXPIRED || 
               this == REJECTED || 
               this == FAILED;
    }

    public boolean isCancellable() {
        return this == NEW || 
               this == PENDING_SUBMIT || 
               this == ACCEPTED || 
               this == PARTIALLY_FILLED;
    }
}