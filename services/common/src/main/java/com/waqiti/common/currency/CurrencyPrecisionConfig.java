package com.waqiti.common.currency;

import java.math.RoundingMode;

/**
 * Configuration class for currency-specific precision rules.
 * Defines decimal places, rounding modes, and other precision-related settings per currency.
 */
public class CurrencyPrecisionConfig {
    
    private final String currencyCode;
    private final int decimalPlaces;
    private final RoundingMode roundingMode;
    private final boolean isFiatCurrency;
    
    public CurrencyPrecisionConfig(String currencyCode, int decimalPlaces, 
                                  RoundingMode roundingMode, boolean isFiatCurrency) {
        this.currencyCode = currencyCode;
        this.decimalPlaces = decimalPlaces;
        this.roundingMode = roundingMode;
        this.isFiatCurrency = isFiatCurrency;
    }
    
    // Getters
    public String getCurrencyCode() { return currencyCode; }
    public int getDecimalPlaces() { return decimalPlaces; }
    public RoundingMode getRoundingMode() { return roundingMode; }
    public boolean isFiatCurrency() { return isFiatCurrency; }
    
    /**
     * Check if this currency requires special precision handling
     */
    public boolean requiresSpecialPrecision() {
        return decimalPlaces != 2; // Most currencies use 2 decimal places
    }
    
    /**
     * Check if this is a cryptocurrency
     */
    public boolean isCryptoCurrency() {
        return !isFiatCurrency;
    }
    
    @Override
    public String toString() {
        return String.format("CurrencyPrecisionConfig{currency=%s, decimals=%d, rounding=%s, fiat=%s}", 
                currencyCode, decimalPlaces, roundingMode, isFiatCurrency);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CurrencyPrecisionConfig that = (CurrencyPrecisionConfig) obj;
        return decimalPlaces == that.decimalPlaces &&
               isFiatCurrency == that.isFiatCurrency &&
               currencyCode.equals(that.currencyCode) &&
               roundingMode == that.roundingMode;
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(currencyCode, decimalPlaces, roundingMode, isFiatCurrency);
    }
}