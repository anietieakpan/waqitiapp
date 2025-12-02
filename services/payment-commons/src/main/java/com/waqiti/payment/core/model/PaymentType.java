package com.waqiti.payment.core.model;

/**
 * Enumeration of all payment types supported by the platform
 */
public enum PaymentType {
    P2P("Peer-to-Peer Payment"),
    MERCHANT("Merchant Payment"),
    SPLIT("Split Payment"),
    GROUP("Group Payment"),
    RECURRING("Recurring Payment"),
    BNPL("Buy Now Pay Later"),
    CRYPTO("Cryptocurrency Payment"),
    CHECK_DEPOSIT("Check Deposit"),
    WIRE_TRANSFER("Wire Transfer"),
    ACH("ACH Transfer"),
    BANK_TRANSFER("Bank Transfer"),
    CARD("Card Payment"),
    IN_STORE("In-Store Payment"),
    WALLET("Digital Wallet"),
    QR_CODE("QR Code Payment"),
    NFC("NFC Payment"),
    VOICE("Voice Payment"),
    SOCIAL("Social Payment"),
    INTERNATIONAL("International Transfer"),
    INVESTMENT("Investment Payment"),
    LOAN("Loan Payment"),
    BILL_PAY("Bill Payment"),
    REFUND("Refund"),
    REVERSAL("Payment Reversal"),
    CHARGEBACK("Chargeback"),
    MOBILE_PAYMENT("Mobile Payment"),
    IN_APP_PURCHASE("In-App Purchase"),
    WEB_PAYMENT("Web Payment");
    
    private final String description;
    
    PaymentType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isInstantSettlement() {
        return switch (this) {
            case P2P, NFC, QR_CODE, VOICE, IN_STORE -> true;
            case WIRE_TRANSFER, CHECK_DEPOSIT, ACH, BANK_TRANSFER -> false;
            default -> true;
        };
    }
    
    public boolean requiresKYC() {
        return switch (this) {
            case WIRE_TRANSFER, INTERNATIONAL, CRYPTO -> true;
            default -> false;
        };
    }
    
    public boolean supportsSplitting() {
        return switch (this) {
            case P2P, MERCHANT, GROUP -> true;
            default -> false;
        };
    }
}