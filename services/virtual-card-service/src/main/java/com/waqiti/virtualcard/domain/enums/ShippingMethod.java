package com.waqiti.virtualcard.domain.enums;

/**
 * Shipping Method enumeration for different delivery options
 */
public enum ShippingMethod {
    STANDARD("Standard shipping (7-10 business days)"),
    EXPRESS("Express shipping (3-5 business days)"),
    OVERNIGHT("Overnight shipping (1-2 business days)"),
    PRIORITY("Priority shipping (2-3 business days)"),
    CERTIFIED("Certified mail with tracking"),
    REGISTERED("Registered mail with signature required");
    
    private final String description;
    
    ShippingMethod(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isExpedited() {
        return this == EXPRESS || this == OVERNIGHT || this == PRIORITY;
    }
    
    public boolean requiresSignature() {
        return this == REGISTERED || this == CERTIFIED || this == OVERNIGHT;
    }
    
    public int getEstimatedBusinessDays() {
        switch (this) {
            case OVERNIGHT:
                return 1;
            case PRIORITY:
                return 2;
            case EXPRESS:
                return 4;
            case CERTIFIED:
            case REGISTERED:
                return 5;
            case STANDARD:
            default:
                return 8;
        }
    }
}