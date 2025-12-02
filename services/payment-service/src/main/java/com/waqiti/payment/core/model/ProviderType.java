package com.waqiti.payment.core.model;

/**
 * Payment provider types supported by the platform
 */
public enum ProviderType {
    // Primary providers
    STRIPE("Stripe", "stripe", true),
    PAYPAL("PayPal", "paypal", true),
    DWOLLA("Dwolla", "dwolla", true),
    WISE("Wise", "wise", true),
    
    // Secondary providers
    SQUARE("Square", "square", true),
    ADYEN("Adyen", "adyen", true),
    PLAID("Plaid", "plaid", true),
    
    // Crypto providers
    BITCOIN("Bitcoin", "bitcoin", true),
    ETHEREUM("Ethereum", "ethereum", true),
    
    // Banking providers
    WIRE("Wire Transfer", "wire", false),
    ACH("ACH Transfer", "ach", true),
    
    // Internal
    INTERNAL("Internal", "internal", true),
    WALLET("Wallet", "wallet", true);
    
    private final String displayName;
    private final String code;
    private final boolean supportsInstant;
    
    ProviderType(String displayName, String code, boolean supportsInstant) {
        this.displayName = displayName;
        this.code = code;
        this.supportsInstant = supportsInstant;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    public boolean supportsInstant() {
        return supportsInstant;
    }
    
    public static ProviderType fromCode(String code) {
        for (ProviderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return INTERNAL;
    }
}