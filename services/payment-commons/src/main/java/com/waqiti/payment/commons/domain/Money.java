package com.waqiti.payment.commons.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Immutable Money value object representing monetary amounts
 * with currency information and precision handling
 */
@Getter
@ToString
@EqualsAndHashCode
public final class Money implements Comparable<Money> {
    
    @NotNull
    private final BigDecimal amount;
    
    @NotNull
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a valid 3-letter ISO code")
    private final String currencyCode;
    
    private final Currency currency;
    
    // Predefined common currencies
    public static final Currency USD = Currency.getInstance("USD");
    public static final Currency EUR = Currency.getInstance("EUR");
    public static final Currency GBP = Currency.getInstance("GBP");
    public static final Currency CAD = Currency.getInstance("CAD");
    
    @JsonCreator
    public Money(@JsonProperty("amount") BigDecimal amount, 
                 @JsonProperty("currencyCode") String currencyCode) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }
        
        this.currency = Currency.getInstance(currencyCode.toUpperCase());
        this.currencyCode = this.currency.getCurrencyCode();
        this.amount = amount.setScale(this.currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }
    
    public Money(BigDecimal amount, Currency currency) {
        this(amount, currency.getCurrencyCode());
    }

    /**
     * Constructor for long amounts (e.g., cents)
     * Use this for whole number amounts only
     */
    public Money(long amount, String currencyCode) {
        this(BigDecimal.valueOf(amount), currencyCode);
    }

    // REMOVED: public Money(double amount, String currencyCode)
    // REASON: Double has precision issues for financial calculations
    // MIGRATION: Use Money.of(BigDecimal.valueOf(amount), currencyCode) instead
    
    // Factory methods
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    // REMOVED: public static Money of(double amount, String currencyCode)
    // REASON: Double has precision issues - 0.1 + 0.2 != 0.3 in floating point
    // MIGRATION: Use Money.of(BigDecimal.valueOf(amount), currencyCode)
    //           or Money.of(new BigDecimal("0.10"), "USD") for literals

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, currencyCode);
    }
    
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }
    
    // Arithmetic operations
    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currencyCode);
    }
    
    public Money subtract(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currencyCode);
    }
    
    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currencyCode);
    }

    // REMOVED: public Money multiply(double factor)
    // MIGRATION: Use multiply(BigDecimal.valueOf(factor)) instead

    public Money divide(BigDecimal divisor) {
        return new Money(this.amount.divide(divisor, currency.getDefaultFractionDigits(), RoundingMode.HALF_UP),
                        this.currencyCode);
    }

    // REMOVED: public Money divide(double divisor)
    // MIGRATION: Use divide(BigDecimal.valueOf(divisor)) instead
    
    public Money negate() {
        return new Money(this.amount.negate(), this.currencyCode);
    }
    
    public Money abs() {
        return new Money(this.amount.abs(), this.currencyCode);
    }
    
    // Comparison methods
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public boolean isGreaterThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }
    
    public boolean isLessThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }
    
    public boolean isGreaterThanOrEqualTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }
    
    public boolean isLessThanOrEqualTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) <= 0;
    }
    
    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }
    
    // Utility methods
    public String getFormattedAmount() {
        return String.format("%." + currency.getDefaultFractionDigits() + "f", amount);
    }
    
    public String getDisplayString() {
        return currency.getSymbol() + getFormattedAmount();
    }
    
    // Conversion methods
    public long getAmountInCents() {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }
    
    public double getAmountAsDouble() {
        return amount.doubleValue();
    }
    
    // Validation
    private void validateSameCurrency(Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException(
                String.format("Currency mismatch: %s vs %s", this.currencyCode, other.currencyCode)
            );
        }
    }
    
    public void validatePositive() {
        if (!isPositive()) {
            throw new IllegalArgumentException("Amount must be positive: " + this);
        }
    }
    
    public void validateNonNegative() {
        if (isNegative()) {
            throw new IllegalArgumentException("Amount cannot be negative: " + this);
        }
    }
    
    public void validateMinimumAmount(Money minimum) {
        if (isLessThan(minimum)) {
            throw new IllegalArgumentException(
                String.format("Amount %s is below minimum %s", this, minimum)
            );
        }
    }
    
    public void validateMaximumAmount(Money maximum) {
        if (isGreaterThan(maximum)) {
            throw new IllegalArgumentException(
                String.format("Amount %s exceeds maximum %s", this, maximum)
            );
        }
    }
}