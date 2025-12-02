package com.waqiti.virtualcard.domain.enums;

/**
 * Order Status enumeration for tracking card order states
 */
public enum OrderStatus {
    PENDING("Order received, awaiting processing"),
    SUBMITTED("Order submitted to card provider"),
    IN_PRODUCTION("Card is being manufactured"),
    READY_TO_SHIP("Card manufactured, ready for shipping"),
    SHIPPED("Card has been shipped"),
    DELIVERED("Card has been delivered"),
    COMPLETED("Order completed successfully"),
    CANCELLED("Order cancelled"),
    FAILED("Order processing failed"),
    EXPIRED("Order expired before completion");
    
    private final String description;
    
    OrderStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isActive() {
        return this != CANCELLED && this != FAILED && this != EXPIRED && this != COMPLETED;
    }
    
    public boolean isCompleted() {
        return this == COMPLETED || this == DELIVERED;
    }
    
    public boolean isCancellable() {
        return this == PENDING || this == SUBMITTED;
    }
    
    public boolean isInProgress() {
        return this == IN_PRODUCTION || this == READY_TO_SHIP || this == SHIPPED;
    }
}