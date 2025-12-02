package com.waqiti.virtualcard.domain.enums;

/**
 * Shipping Status enumeration for tracking shipment progress
 */
public enum ShippingStatus {
    PREPARING("Preparing for shipment"),
    READY_TO_SHIP("Ready to ship"),
    SHIPPED("Shipped"),
    IN_TRANSIT("In transit"),
    OUT_FOR_DELIVERY("Out for delivery"),
    DELIVERED("Delivered"),
    DELIVERY_ATTEMPTED("Delivery attempted"),
    RETURNED_TO_SENDER("Returned to sender"),
    EXCEPTION("Delivery exception"),
    LOST("Package lost"),
    DAMAGED("Package damaged");
    
    private final String description;
    
    ShippingStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isActive() {
        return this != DELIVERED && this != RETURNED_TO_SENDER && this != LOST && this != DAMAGED;
    }
    
    public boolean isDelivered() {
        return this == DELIVERED;
    }
    
    public boolean isInTransit() {
        return this == SHIPPED || this == IN_TRANSIT || this == OUT_FOR_DELIVERY;
    }
    
    public boolean hasIssue() {
        return this == EXCEPTION || this == DELIVERY_ATTEMPTED || 
               this == RETURNED_TO_SENDER || this == LOST || this == DAMAGED;
    }
    
    public boolean requiresAction() {
        return this == DELIVERY_ATTEMPTED || this == EXCEPTION;
    }
}