package com.waqiti.payment.commons.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Standardized payment methods across all payment services
 */
public enum PaymentMethod {
    
    // Digital wallet methods
    WALLET("wallet", "Digital Wallet", true, false),
    DIGITAL_WALLET("digital_wallet", "Digital Wallet", true, false),
    
    // Bank account methods
    BANK_TRANSFER("bank_transfer", "Bank Transfer", false, true),
    ACH("ach", "ACH Transfer", false, true),
    WIRE_TRANSFER("wire_transfer", "Wire Transfer", false, true),
    SEPA("sepa", "SEPA Transfer", false, true),
    
    // Card methods
    CREDIT_CARD("credit_card", "Credit Card", true, false),
    DEBIT_CARD("debit_card", "Debit Card", true, false),
    PREPAID_CARD("prepaid_card", "Prepaid Card", true, false),
    VIRTUAL_CARD("virtual_card", "Virtual Card", true, false),
    
    // Alternative payment methods
    PAYPAL("paypal", "PayPal", true, false),
    APPLE_PAY("apple_pay", "Apple Pay", true, false),
    GOOGLE_PAY("google_pay", "Google Pay", true, false),
    SAMSUNG_PAY("samsung_pay", "Samsung Pay", true, false),
    
    // Cryptocurrency
    BITCOIN("bitcoin", "Bitcoin", true, false),
    ETHEREUM("ethereum", "Ethereum", true, false),
    LITECOIN("litecoin", "Litecoin", true, false),
    CRYPTO("crypto", "Cryptocurrency", true, false),
    
    // Buy Now Pay Later
    BNPL("bnpl", "Buy Now Pay Later", false, true),
    KLARNA("klarna", "Klarna", false, true),
    AFTERPAY("afterpay", "Afterpay", false, true),
    
    // Cash and check
    CASH("cash", "Cash", true, false),
    CHECK("check", "Check", false, true),
    MOBILE_CHECK("mobile_check", "Mobile Check Deposit", false, true),
    
    // QR Code and NFC
    QR_CODE("qr_code", "QR Code Payment", true, false),
    NFC("nfc", "NFC Payment", true, false),
    
    // P2P specific
    P2P_INSTANT("p2p_instant", "Instant P2P Transfer", true, false),
    P2P_STANDARD("p2p_standard", "Standard P2P Transfer", false, false),
    
    // Business methods
    INVOICE("invoice", "Invoice Payment", false, true),
    SUBSCRIPTION("subscription", "Subscription Payment", false, true),
    RECURRING("recurring", "Recurring Payment", false, true),
    
    // International
    SWIFT("swift", "SWIFT Transfer", false, true),
    REMITTANCE("remittance", "Remittance Transfer", false, true),
    
    // Other
    GIFT_CARD("gift_card", "Gift Card", true, false),
    LOYALTY_POINTS("loyalty_points", "Loyalty Points", true, false),
    UNKNOWN("unknown", "Unknown Method", false, false);
    
    private final String code;
    private final String displayName;
    private final boolean instantSettlement;
    private final boolean requiresClearing;
    
    PaymentMethod(String code, String displayName, boolean instantSettlement, boolean requiresClearing) {
        this.code = code;
        this.displayName = displayName;
        this.instantSettlement = instantSettlement;
        this.requiresClearing = requiresClearing;
    }
    
    @JsonValue
    public String getCode() {
        return code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isInstantSettlement() {
        return instantSettlement;
    }
    
    public boolean requiresClearing() {
        return requiresClearing;
    }
    
    @JsonCreator
    public static PaymentMethod fromCode(String code) {
        for (PaymentMethod method : values()) {
            if (method.code.equals(code)) {
                return method;
            }
        }
        return UNKNOWN;
    }
    
    // Category checks
    public boolean isCard() {
        return this == CREDIT_CARD || this == DEBIT_CARD || 
               this == PREPAID_CARD || this == VIRTUAL_CARD;
    }
    
    public boolean isDigitalWallet() {
        return this == WALLET || this == DIGITAL_WALLET || 
               this == PAYPAL || this == APPLE_PAY || this == GOOGLE_PAY || this == SAMSUNG_PAY;
    }
    
    public boolean isBankTransfer() {
        return this == BANK_TRANSFER || this == ACH || 
               this == WIRE_TRANSFER || this == SEPA || this == SWIFT;
    }
    
    public boolean isCryptocurrency() {
        return this == BITCOIN || this == ETHEREUM || 
               this == LITECOIN || this == CRYPTO;
    }
    
    public boolean isBNPL() {
        return this == BNPL || this == KLARNA || this == AFTERPAY;
    }
    
    public boolean isCheck() {
        return this == CHECK || this == MOBILE_CHECK;
    }
    
    public boolean isContactless() {
        return this == NFC || this == QR_CODE || 
               this == APPLE_PAY || this == GOOGLE_PAY || this == SAMSUNG_PAY;
    }
    
    public boolean isP2P() {
        return this == P2P_INSTANT || this == P2P_STANDARD;
    }
    
    public boolean isRecurring() {
        return this == SUBSCRIPTION || this == RECURRING;
    }
    
    public boolean isInternational() {
        return this == SWIFT || this == REMITTANCE || this == WIRE_TRANSFER;
    }
    
    public boolean supportsRefunds() {
        return isCard() || isDigitalWallet() || isBankTransfer();
    }
    
    public boolean supportsChargebacks() {
        return isCard();
    }
    
    public boolean requiresKYC() {
        return isBankTransfer() || isInternational() || isCryptocurrency();
    }
    
    public boolean supportsScheduling() {
        return isBankTransfer() || this == WALLET || this == DIGITAL_WALLET;
    }
    
    // Get typical processing time in minutes
    public int getTypicalProcessingTimeMinutes() {
        if (instantSettlement) {
            return 0;
        }
        
        switch (this) {
            case ACH:
                return 2880; // 2 days
            case WIRE_TRANSFER:
                return 1440; // 1 day
            case SEPA:
                return 1440; // 1 day
            case CHECK:
            case MOBILE_CHECK:
                return 4320; // 3 days
            case BNPL:
            case KLARNA:
            case AFTERPAY:
                return 60; // 1 hour
            case SWIFT:
                return 2880; // 2 days
            case REMITTANCE:
                return 1440; // 1 day
            default:
                return 30; // 30 minutes
        }
    }
    
    @Override
    public String toString() {
        return code;
    }
}