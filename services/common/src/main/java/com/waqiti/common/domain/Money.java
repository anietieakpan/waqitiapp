package com.waqiti.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Production-ready Money value object for financial operations.
 * Implements immutable monetary values with currency support and financial arithmetic.
 * 
 * Features:
 * - Immutable design for thread safety
 * - Precise decimal arithmetic using BigDecimal
 * - Currency validation and support
 * - Financial operations (add, subtract, multiply, divide)
 * - Comparison operations
 * - JSON serialization support
 * - JPA embeddable for database storage
 * - Comprehensive validation
 */
@Embeddable
public class Money implements Serializable, Comparable<Money> {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_SCALE = 4; // 4 decimal places for precision
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be positive")
    @Digits(integer = 12, fraction = 4, message = "Amount precision invalid")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private final BigDecimal amount;

    @NotBlank(message = "Currency cannot be blank")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    @Column(name = "currency", nullable = false, length = 3)
    private final String currencyCode;

    // Cached currency instance for performance
    private transient Currency currency;

    /**
     * Private constructor for validation and immutability
     */
    private Money(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }
        if (!currencyCode.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Currency code must be 3-letter ISO code: " + currencyCode);
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative: " + amount);
        }

        this.amount = amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        this.currencyCode = currencyCode.toUpperCase();
        
        // Validate currency code
        try {
            this.currency = Currency.getInstance(this.currencyCode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid currency code: " + currencyCode, e);
        }
    }

    /**
     * Default constructor for JPA
     */
    protected Money() {
        this.amount = BigDecimal.ZERO;
        this.currencyCode = "USD";
    }

    /**
     * Create Money instance from BigDecimal and currency code
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }

    /**
     * Create Money instance from double and currency code
     */
    public static Money of(double amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), currencyCode);
    }

    /**
     * Create Money instance from long (cents) and currency code
     */
    public static Money ofCents(long cents, String currencyCode) {
        return new Money(BigDecimal.valueOf(cents, 2), currencyCode);
    }

    /**
     * Create zero money with specified currency
     */
    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, currencyCode);
    }

    /**
     * Create USD money instance
     */
    public static Money usd(BigDecimal amount) {
        return new Money(amount, "USD");
    }

    /**
     * Create USD money instance from double
     */
    public static Money usd(double amount) {
        return new Money(BigDecimal.valueOf(amount), "USD");
    }

    /**
     * JSON creation method
     */
    @JsonCreator
    public static Money fromJson(@JsonProperty("amount") BigDecimal amount, 
                                @JsonProperty("currency") String currencyCode) {
        return new Money(amount, currencyCode);
    }

    /**
     * Get amount value
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Get currency code
     */
    public String getCurrencyCode() {
        return currencyCode;
    }

    /**
     * Get Currency instance
     */
    public Currency getCurrency() {
        if (currency == null) {
            currency = Currency.getInstance(currencyCode);
        }
        return currency;
    }

    /**
     * Add money (same currency only)
     */
    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currencyCode);
    }

    /**
     * Subtract money (same currency only)
     */
    public Money subtract(Money other) {
        validateSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Result cannot be negative: " + result);
        }
        return new Money(result, this.currencyCode);
    }

    /**
     * Multiply by factor
     */
    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("Factor cannot be null");
        }
        if (factor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Factor cannot be negative: " + factor);
        }
        return new Money(this.amount.multiply(factor), this.currencyCode);
    }

    /**
     * Multiply by double factor
     */
    public Money multiply(double factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    /**
     * Divide by divisor
     */
    public Money divide(BigDecimal divisor) {
        if (divisor == null) {
            throw new IllegalArgumentException("Divisor cannot be null");
        }
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        if (divisor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Divisor cannot be negative: " + divisor);
        }
        return new Money(this.amount.divide(divisor, DEFAULT_SCALE, DEFAULT_ROUNDING), this.currencyCode);
    }

    /**
     * Divide by double divisor
     */
    public Money divide(double divisor) {
        return divide(BigDecimal.valueOf(divisor));
    }

    /**
     * Divide and return array of Money (for splitting)
     */
    public Money[] divide(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("Parts must be positive: " + parts);
        }
        
        Money[] result = new Money[parts];
        BigDecimal partAmount = this.amount.divide(BigDecimal.valueOf(parts), DEFAULT_SCALE, DEFAULT_ROUNDING);
        
        // Handle remainder
        BigDecimal totalAllocated = partAmount.multiply(BigDecimal.valueOf(parts));
        BigDecimal remainder = this.amount.subtract(totalAllocated);
        
        for (int i = 0; i < parts; i++) {
            BigDecimal allocation = partAmount;
            // Add remainder to last part
            if (i == parts - 1) {
                allocation = allocation.add(remainder);
            }
            result[i] = new Money(allocation, this.currencyCode);
        }
        
        return result;
    }

    /**
     * Percentage calculation
     */
    public Money percentage(double percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100: " + percentage);
        }
        return multiply(BigDecimal.valueOf(percentage / 100.0));
    }

    /**
     * Calculate percentage of total
     */
    public double percentageOf(Money total) {
        validateSameCurrency(total);
        if (total.isZero()) {
            return 0.0;
        }
        return this.amount.divide(total.amount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /**
     * Absolute value
     */
    public Money abs() {
        return new Money(this.amount.abs(), this.currencyCode);
    }

    /**
     * Negate amount (for reversals)
     */
    public Money negate() {
        return new Money(this.amount.negate(), this.currencyCode);
    }

    /**
     * Round to specified scale
     */
    public Money round(int scale, RoundingMode roundingMode) {
        return new Money(this.amount.setScale(scale, roundingMode), this.currencyCode);
    }

    /**
     * Round to currency's default fraction digits
     */
    public Money roundToCurrencyScale() {
        int scale = getCurrency().getDefaultFractionDigits();
        return round(scale, DEFAULT_ROUNDING);
    }

    /**
     * Check if amount is zero
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if amount is positive
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if amount is negative
     */
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Check if greater than other money
     */
    public boolean isGreaterThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Check if greater than or equal to other money
     */
    public boolean isGreaterThanOrEqual(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    /**
     * Check if less than other money
     */
    public boolean isLessThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    /**
     * Check if less than or equal to other money
     */
    public boolean isLessThanOrEqual(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) <= 0;
    }

    /**
     * Check if equal amount (same currency)
     */
    public boolean isEqualAmount(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) == 0;
    }

    /**
     * Get minimum of two money amounts
     */
    public Money min(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) <= 0 ? this : other;
    }

    /**
     * Get maximum of two money amounts
     */
    public Money max(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0 ? this : other;
    }

    /**
     * Convert to different currency (requires exchange rate)
     */
    public Money convertTo(String targetCurrencyCode, BigDecimal exchangeRate) {
        if (targetCurrencyCode == null || targetCurrencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Target currency code cannot be null or empty");
        }
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive: " + exchangeRate);
        }
        
        BigDecimal convertedAmount = this.amount.multiply(exchangeRate);
        return new Money(convertedAmount, targetCurrencyCode);
    }

    /**
     * Allocate money proportionally
     */
    public Money[] allocate(BigDecimal... ratios) {
        if (ratios == null || ratios.length == 0) {
            throw new IllegalArgumentException("Ratios cannot be null or empty");
        }
        
        // Validate ratios sum to 1.0
        BigDecimal ratioSum = Arrays.stream(ratios).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ratioSum.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Ratios must sum to 1.0, got: " + ratioSum);
        }
        
        Money[] result = new Money[ratios.length];
        BigDecimal totalAllocated = BigDecimal.ZERO;
        
        for (int i = 0; i < ratios.length; i++) {
            BigDecimal allocation;
            if (i == ratios.length - 1) {
                // Last allocation gets remainder to ensure exact total
                allocation = this.amount.subtract(totalAllocated);
            } else {
                allocation = this.amount.multiply(ratios[i]).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
                totalAllocated = totalAllocated.add(allocation);
            }
            result[i] = new Money(allocation, this.currencyCode);
        }
        
        return result;
    }

    /**
     * Format money for display
     */
    public String format() {
        return String.format("%s %s", getCurrency().getSymbol(), 
                           amount.setScale(getCurrency().getDefaultFractionDigits(), DEFAULT_ROUNDING));
    }

    /**
     * Format money with custom scale
     */
    public String format(int scale) {
        return String.format("%s %s", getCurrency().getSymbol(), 
                           amount.setScale(scale, DEFAULT_ROUNDING));
    }

    /**
     * Get amount in smallest currency unit (cents)
     */
    public long getAmountInSmallestUnit() {
        int scale = getCurrency().getDefaultFractionDigits();
        return amount.movePointRight(scale).longValue();
    }

    /**
     * Create Money from smallest currency unit
     */
    public static Money fromSmallestUnit(long amountInSmallestUnit, String currencyCode) {
        Currency curr = Currency.getInstance(currencyCode);
        BigDecimal amount = BigDecimal.valueOf(amountInSmallestUnit, curr.getDefaultFractionDigits());
        return new Money(amount, currencyCode);
    }

    /**
     * Check if same currency
     */
    public boolean isSameCurrency(Money other) {
        return other != null && this.currencyCode.equals(other.currencyCode);
    }

    /**
     * Validate same currency for operations
     */
    private void validateSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Money cannot be null");
        }
        if (!isSameCurrency(other)) {
            throw new IllegalArgumentException(
                String.format("Currency mismatch: %s vs %s", this.currencyCode, other.currencyCode));
        }
    }

    /**
     * JSON serialization value
     */
    @JsonValue
    public Map<String, Object> toJson() {
        Map<String, Object> json = new HashMap<>();
        json.put("amount", amount);
        json.put("currency", currencyCode);
        return json;
    }

    /**
     * Implement Comparable interface
     */
    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    /**
     * Equals implementation
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Money money = (Money) obj;
        return Objects.equals(amount, money.amount) && 
               Objects.equals(currencyCode, money.currencyCode);
    }

    /**
     * HashCode implementation
     */
    @Override
    public int hashCode() {
        return Objects.hash(amount, currencyCode);
    }

    /**
     * String representation
     */
    @Override
    public String toString() {
        return String.format("Money{amount=%s, currency=%s}", amount, currencyCode);
    }

    // Static factory methods for common currencies
    public static Money usd(String amount) {
        return new Money(new BigDecimal(amount), "USD");
    }

    public static Money eur(BigDecimal amount) {
        return new Money(amount, "EUR");
    }

    public static Money eur(double amount) {
        return new Money(BigDecimal.valueOf(amount), "EUR");
    }

    public static Money gbp(BigDecimal amount) {
        return new Money(amount, "GBP");
    }

    public static Money gbp(double amount) {
        return new Money(BigDecimal.valueOf(amount), "GBP");
    }

    public static Money jpy(BigDecimal amount) {
        return new Money(amount, "JPY");
    }

    public static Money jpy(double amount) {
        return new Money(BigDecimal.valueOf(amount), "JPY");
    }

    public static Money ngn(BigDecimal amount) {
        return new Money(amount, "NGN");
    }

    public static Money ngn(double amount) {
        return new Money(BigDecimal.valueOf(amount), "NGN");
    }

    // Convenience methods for common operations
    public Money addAmount(BigDecimal additionalAmount) {
        return new Money(this.amount.add(additionalAmount), this.currencyCode);
    }

    public Money subtractAmount(BigDecimal subtractAmount) {
        BigDecimal result = this.amount.subtract(subtractAmount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Result cannot be negative");
        }
        return new Money(result, this.currencyCode);
    }

    public Money multiplyByFactor(BigDecimal factor) {
        return multiply(factor);
    }

    public Money divideBy(BigDecimal divisor) {
        return divide(divisor);
    }

    /**
     * Financial rounding for specific currencies
     */
    public Money roundForCurrency() {
        return roundToCurrencyScale();
    }

    /**
     * Check if amount is within range
     */
    public boolean isWithinRange(Money min, Money max) {
        validateSameCurrency(min);
        validateSameCurrency(max);
        return this.compareTo(min) >= 0 && this.compareTo(max) <= 0;
    }

    /**
     * Calculate percentage difference
     */
    public double percentageDifference(Money other) {
        validateSameCurrency(other);
        if (other.isZero()) {
            return this.isZero() ? 0.0 : Double.POSITIVE_INFINITY;
        }
        
        BigDecimal difference = this.amount.subtract(other.amount).abs();
        return difference.divide(other.amount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
    }

    /**
     * Split money into equal parts with remainder handling
     */
    public List<Money> splitEqually(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("Parts must be positive: " + parts);
        }
        
        List<Money> result = new ArrayList<>();
        Money[] divided = divide(parts);
        
        for (Money part : divided) {
            result.add(part);
        }
        
        return result;
    }

    /**
     * Validate money amount constraints
     */
    public boolean isValidForTransaction() {
        // Check if amount is within reasonable transaction limits
        return amount.compareTo(BigDecimal.valueOf(0.01)) >= 0 && 
               amount.compareTo(BigDecimal.valueOf(999999999.99)) <= 0;
    }

    /**
     * Get formatted amount only (without currency symbol)
     */
    public String getFormattedAmount() {
        return amount.setScale(getCurrency().getDefaultFractionDigits(), DEFAULT_ROUNDING).toString();
    }

    /**
     * Create Money with validation for specific transaction types
     */
    public static Money forTransaction(BigDecimal amount, String currencyCode, String transactionType) {
        Money money = new Money(amount, currencyCode);
        
        // Additional validations based on transaction type
        switch (transactionType) {
            case "INTERNATIONAL":
                if (money.amount.compareTo(BigDecimal.valueOf(50000)) > 0) {
                    throw new IllegalArgumentException("International transfer limit exceeded");
                }
                break;
            case "CRYPTO":
                if (money.amount.compareTo(BigDecimal.valueOf(100000)) > 0) {
                    throw new IllegalArgumentException("Crypto transaction limit exceeded");
                }
                break;
            case "ATM_WITHDRAWAL":
                if (money.amount.compareTo(BigDecimal.valueOf(1000)) > 0) {
                    throw new IllegalArgumentException("ATM withdrawal limit exceeded");
                }
                break;
        }
        
        return money;
    }

    /**
     * Fee calculation helper
     */
    public Money calculateFee(BigDecimal feeRate, Money minimumFee, Money maximumFee) {
        validateSameCurrency(minimumFee);
        validateSameCurrency(maximumFee);
        
        Money calculatedFee = this.multiply(feeRate);
        return calculatedFee.min(maximumFee).max(minimumFee);
    }
}