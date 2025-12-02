package com.waqiti.payment.core.model;

/**
 * Fraud risk level enumeration
 */
public enum FraudRiskLevel {
    MINIMAL("Minimal risk - proceed normally"),
    LOW("Low risk - monitor transaction"),
    MEDIUM("Medium risk - additional verification recommended"),
    HIGH("High risk - block or review required");
    
    private final String description;
    
    FraudRiskLevel(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isHigherThan(FraudRiskLevel other) {
        return this.ordinal() > other.ordinal();
    }
}