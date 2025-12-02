package com.waqiti.dispute.entity;

/**
 * Resolution type enumeration
 */
public enum ResolutionType {
    AUTOMATED("Resolved by automated system"),
    MANUAL("Resolved by agent"),
    CUSTOMER_AGREEMENT("Resolved through customer agreement"),
    MERCHANT_AGREEMENT("Resolved through merchant agreement"),
    MEDIATION("Resolved through mediation"),
    ARBITRATION("Resolved through arbitration"),
    CHARGEBACK_RESPONSE("Resolved via chargeback response"),
    TIMEOUT("Resolved due to timeout"),
    SYSTEM_ERROR("Resolved due to system error");

    private final String description;

    ResolutionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
