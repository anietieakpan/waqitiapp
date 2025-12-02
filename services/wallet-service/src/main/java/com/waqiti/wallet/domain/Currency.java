package com.waqiti.wallet.domain;

/**
 * Supported currencies for wallet transactions.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
public enum Currency {
    USD("US Dollar", "$", 2),
    EUR("Euro", "€", 2),
    GBP("British Pound", "£", 2),
    NGN("Nigerian Naira", "₦", 2),
    KES("Kenyan Shilling", "KSh", 2),
    ZAR("South African Rand", "R", 2),
    GHS("Ghanaian Cedi", "₵", 2),
    EGP("Egyptian Pound", "E£", 2),
    MAD("Moroccan Dirham", "د.م.", 2),
    XOF("West African CFA Franc", "CFA", 0),
    XAF("Central African CFA Franc", "FCFA", 0),
    AED("UAE Dirham", "د.إ", 2),
    SAR("Saudi Riyal", "﷼", 2),
    QAR("Qatari Riyal", "ر.ق", 2),
    BTC("Bitcoin", "₿", 8),
    ETH("Ethereum", "Ξ", 18),
    USDT("Tether", "₮", 6),
    USDC("USD Coin", "USDC", 6);
    
    private final String displayName;
    private final String symbol;
    private final int decimalPlaces;
    
    Currency(String displayName, String symbol, int decimalPlaces) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public int getDecimalPlaces() {
        return decimalPlaces;
    }
    
    public boolean isCryptoCurrency() {
        return this == BTC || this == ETH || this == USDT || this == USDC;
    }
    
    public boolean isFiatCurrency() {
        return !isCryptoCurrency();
    }
}