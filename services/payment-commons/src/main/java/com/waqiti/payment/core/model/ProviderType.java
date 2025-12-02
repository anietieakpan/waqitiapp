package com.waqiti.payment.core.model;

/**
 * Payment provider enumeration
 */
public enum ProviderType {
    INTERNAL("Internal Waqiti Processing"),
    STRIPE("Stripe"),
    PAYPAL("PayPal"),
    PLAID("Plaid"),
    DWOLLA("Dwolla"),
    SQUARE("Square"),
    ADYEN("Adyen"),
    BRAINTREE("Braintree"),
    CHASE("Chase QuickPay"),
    WELLS_FARGO("Wells Fargo"),
    BANK_OF_AMERICA("Bank of America"),
    VENMO("Venmo"),
    ZELLE("Zelle"),
    APPLE_PAY("Apple Pay"),
    GOOGLE_PAY("Google Pay"),
    SAMSUNG_PAY("Samsung Pay"),
    BITCOIN("Bitcoin"),
    ETHEREUM("Ethereum"),
    STABLECOIN("Stablecoin"),
    SWIFT("SWIFT Network"),
    SEPA("SEPA"),
    FEDWIRE("Fedwire"),
    CLEARING_HOUSE("The Clearing House"),
    VISA_DIRECT("Visa Direct"),
    MASTERCARD_SEND("Mastercard Send"),
    WESTERN_UNION("Western Union"),
    MONEYGRAM("MoneyGram"),
    WISE("Wise (formerly TransferWise)");
    
    private final String displayName;
    
    ProviderType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isRealTime() {
        return switch (this) {
            case INTERNAL, VENMO, ZELLE, APPLE_PAY, GOOGLE_PAY, SAMSUNG_PAY -> true;
            case PLAID, DWOLLA, SWIFT, SEPA, FEDWIRE -> false;
            default -> true;
        };
    }
    
    public boolean isCryptocurrency() {
        return switch (this) {
            case BITCOIN, ETHEREUM, STABLECOIN -> true;
            default -> false;
        };
    }
    
    public boolean isInternational() {
        return switch (this) {
            case SWIFT, SEPA, WESTERN_UNION, MONEYGRAM, WISE -> true;
            default -> false;
        };
    }
    
    public boolean supportsRefunds() {
        return switch (this) {
            case BITCOIN, ETHEREUM, STABLECOIN, ZELLE, WISE -> false; // Wise supports cancellation, not refunds
            default -> true;
        };
    }
}