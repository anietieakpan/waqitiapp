package com.waqiti.common.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Immutable representation of a monetary amount with precise decimal handling.
 * Ensures financial accuracy and prevents rounding errors in calculations.
 */
public class PreciseMonetaryAmount {
    
    private final BigDecimal amount;
    private final String currencyCode;
    private final int precision;
    private final RoundingMode roundingMode;
    private final Instant createdAt;
    
    private PreciseMonetaryAmount(BigDecimal amount, String currencyCode, int precision, 
                                 RoundingMode roundingMode, Instant createdAt) {
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.precision = precision;
        this.roundingMode = roundingMode;
        this.createdAt = createdAt;
    }
    
    public static PreciseMonetaryAmountBuilder builder() {
        return new PreciseMonetaryAmountBuilder();
    }
    
    // Getters
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public int getPrecision() { return precision; }
    public RoundingMode getRoundingMode() { return roundingMode; }
    public Instant getCreatedAt() { return createdAt; }
    
    /**
     * Check if this amount is zero
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
    
    /**
     * Check if this amount is positive
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this amount is negative
     */
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * Get absolute value of this amount
     */
    public PreciseMonetaryAmount abs() {
        if (isNegative()) {
            return PreciseMonetaryAmount.builder()
                    .amount(amount.abs())
                    .currencyCode(currencyCode)
                    .precision(precision)
                    .roundingMode(roundingMode)
                    .createdAt(Instant.now())
                    .build();
        }
        return this;
    }
    
    /**
     * Negate this amount
     */
    public PreciseMonetaryAmount negate() {
        return PreciseMonetaryAmount.builder()
                .amount(amount.negate())
                .currencyCode(currencyCode)
                .precision(precision)
                .roundingMode(roundingMode)
                .createdAt(Instant.now())
                .build();
    }
    
    /**
     * Compare this amount to another amount of the same currency
     */
    public int compareTo(PreciseMonetaryAmount other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Cannot compare amounts of different currencies");
        }
        return this.amount.compareTo(other.amount);
    }
    
    /**
     * Check if this amount equals another amount
     */
    public boolean equals(PreciseMonetaryAmount other) {
        if (other == null) {
            return false;
        }
        return this.currencyCode.equals(other.currencyCode) && 
               this.amount.compareTo(other.amount) == 0;
    }
    
    /**
     * Format amount for display with proper currency precision
     */
    public String toDisplayString() {
        BigDecimal displayAmount = amount.setScale(precision, roundingMode);
        return String.format("%s %.%df", currencyCode, precision, displayAmount);
    }
    
    /**
     * Convert to string with full precision
     */
    public String toPreciseString() {
        return String.format("%s %s", currencyCode, amount.toPlainString());
    }
    
    @Override
    public String toString() {
        return String.format("PreciseMonetaryAmount{amount=%s, currency=%s, precision=%d}", 
                amount.toPlainString(), currencyCode, precision);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        PreciseMonetaryAmount that = (PreciseMonetaryAmount) obj;
        return precision == that.precision &&
               amount.equals(that.amount) &&
               currencyCode.equals(that.currencyCode) &&
               roundingMode == that.roundingMode;
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(amount, currencyCode, precision, roundingMode);
    }
    
    public static class PreciseMonetaryAmountBuilder {
        private BigDecimal amount;
        private String currencyCode;
        private int precision;
        private RoundingMode roundingMode;
        private Instant createdAt;
        
        public PreciseMonetaryAmountBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }
        
        public PreciseMonetaryAmountBuilder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }
        
        public PreciseMonetaryAmountBuilder precision(int precision) {
            this.precision = precision;
            return this;
        }
        
        public PreciseMonetaryAmountBuilder roundingMode(RoundingMode roundingMode) {
            this.roundingMode = roundingMode;
            return this;
        }
        
        public PreciseMonetaryAmountBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public PreciseMonetaryAmount build() {
            return new PreciseMonetaryAmount(amount, currencyCode, precision, roundingMode, createdAt);
        }
    }
}