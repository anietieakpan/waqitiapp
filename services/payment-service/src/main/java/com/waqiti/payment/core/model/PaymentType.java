package com.waqiti.payment.core.model;

/**
 * Payment types supported by the unified payment service
 */
public enum PaymentType {
    // Person to Person
    P2P("Person to Person", true, false),
    
    // Group payments
    GROUP("Group Payment", true, true),
    SPLIT("Split Payment", true, true),
    
    // Merchant payments
    MERCHANT("Merchant Payment", true, false),
    BILL("Bill Payment", false, false),
    
    // Special types
    REQUEST("Payment Request", false, false),
    INTERNATIONAL("International Transfer", false, false),
    CRYPTO("Cryptocurrency", true, false),
    
    // Recurring and scheduled
    RECURRING("Recurring Payment", false, true),
    SCHEDULED("Scheduled Payment", false, false),
    
    // Instant payments
    INSTANT("Instant Payment", true, false),
    
    // Buy now pay later
    BNPL("Buy Now Pay Later", false, true),
    
    // Standard bank transfers
    STANDARD("Standard Transfer", false, false),
    WIRE("Wire Transfer", false, false),
    ACH("ACH Transfer", false, false);
    
    private final String displayName;
    private final boolean supportsInstant;
    private final boolean requiresSaga;
    
    PaymentType(String displayName, boolean supportsInstant, boolean requiresSaga) {
        this.displayName = displayName;
        this.supportsInstant = supportsInstant;
        this.requiresSaga = requiresSaga;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean supportsInstant() {
        return supportsInstant;
    }
    
    public boolean requiresSaga() {
        return requiresSaga;
    }
    
    public boolean isHighValue() {
        return this == WIRE || this == INTERNATIONAL;
    }
    
    public boolean requiresCompliance() {
        return this == INTERNATIONAL || this == WIRE || this == CRYPTO;
    }
}