package com.waqiti.dispute.entity;

/**
 * Evidence type enumeration
 */
public enum EvidenceType {
    RECEIPT("Transaction receipt"),
    INVOICE("Invoice or bill"),
    SHIPPING_PROOF("Shipping or delivery proof"),
    COMMUNICATION("Customer communication records"),
    AUTHENTICATION_LOG("Authentication and authorization logs"),
    FRAUD_SCORE("Fraud detection score"),
    IP_GEOLOCATION("IP and geolocation data"),
    DEVICE_FINGERPRINT("Device fingerprint data"),
    CUSTOMER_HISTORY("Customer transaction history"),
    MERCHANT_RESPONSE("Merchant response statement"),
    BANK_STATEMENT("Bank statement"),
    PHOTO_EVIDENCE("Photographic evidence"),
    VIDEO_EVIDENCE("Video evidence"),
    CONTRACT("Service contract or agreement"),
    OTHER("Other evidence type");

    private final String description;

    EvidenceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemGenerated() {
        return this == AUTHENTICATION_LOG || this == FRAUD_SCORE ||
               this == IP_GEOLOCATION || this == DEVICE_FINGERPRINT;
    }
}
