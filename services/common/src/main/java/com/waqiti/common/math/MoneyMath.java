package com.waqiti.common.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CONSOLIDATED Money Math Utility - PRODUCTION GRADE
 *
 * This is the SINGLE SOURCE OF TRUTH for all financial calculations across Waqiti platform.
 * Replaces both old MoneyMath and MoneyUtils implementations.
 *
 * CRITICAL RULES:
 * 1. ALL money operations MUST use BigDecimal (never double/float)
 * 2. Currency amounts: 2 decimal places (HALF_UP rounding)
 * 3. ML features: Use toMLFeature() methods (converts to float safely)
 * 4. Fees/taxes: CEILING rounding (favor platform/government)
 * 5. Interest: FLOOR rounding (favor customer)
 *
 * MIGRATION FROM MoneyUtils:
 * - MoneyUtils used 4 decimal scale → Now standardized to 2 decimals for currency
 * - MoneyUtils ML methods → Preserved in this class
 * - All null-safe operations → Preserved
 *
 * @version 2.0 - Consolidated Implementation
 * @since October 30, 2025
 * @author Waqiti Platform Engineering
 */
public final class MoneyMath {

    // ========== STANDARD SCALES ==========

    /**
     * Standard currency scale: 2 decimal places (e.g., $10.99)
     * Used for: USD, EUR, GBP, and most fiat currencies
     */
    public static final int CURRENCY_SCALE = 2;

    /**
     * Percentage scale: 6 decimal places (e.g., 3.141592%)
     * Used for: Fee rates, interest rates, conversion rates
     */
    public static final int PERCENTAGE_SCALE = 6;

    /**
     * Exchange rate scale: 8 decimal places
     * Used for: Foreign exchange, cryptocurrency rates
     */
    public static final int EXCHANGE_RATE_SCALE = 8;

    /**
     * Intermediate calculation scale: 10 decimal places
     * Used for: Complex calculations before final rounding
     */
    public static final int INTERMEDIATE_SCALE = 10;

    /**
     * ML feature scale: 4 decimal places
     * Used for: Machine learning feature extraction
     * Preserves more precision for ML without storage overhead
     */
    public static final int ML_FEATURE_SCALE = 4;

    // ========== ROUNDING MODES ==========

    /**
     * Default rounding: HALF_UP (Banker's rounding)
     * 0.5 rounds up to 1, -0.5 rounds down to -1
     */
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    private MoneyMath() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Divide with currency precision (2 decimal places, HALF_UP rounding)
     */
    public static BigDecimal divideCurrency(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return dividend.divide(divisor, CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Divide with percentage precision (6 decimal places, HALF_UP rounding)
     */
    public static BigDecimal dividePercentage(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return dividend.divide(divisor, PERCENTAGE_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Divide with exchange rate precision (8 decimal places, HALF_UP rounding)
     */
    public static BigDecimal divideExchangeRate(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return dividend.divide(divisor, EXCHANGE_RATE_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Multiply and round to currency precision
     */
    public static BigDecimal multiplyCurrency(BigDecimal multiplicand, BigDecimal multiplier) {
        return multiplicand.multiply(multiplier).setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate percentage (e.g., 15/100 = 15%)
     */
    public static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, PERCENTAGE_SCALE, DEFAULT_ROUNDING)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculate fee (always round up to favor platform)
     */
    public static BigDecimal calculateFee(BigDecimal amount, BigDecimal feeRate) {
        return amount.multiply(feeRate).setScale(CURRENCY_SCALE, RoundingMode.CEILING);
    }

    /**
     * Calculate tax (always round up per tax law)
     */
    public static BigDecimal calculateTax(BigDecimal amount, BigDecimal taxRate) {
        return amount.multiply(taxRate).setScale(CURRENCY_SCALE, RoundingMode.CEILING);
    }

    /**
     * Calculate interest (always round down to favor customer)
     */
    public static BigDecimal calculateInterest(BigDecimal principal, BigDecimal interestRate) {
        return principal.multiply(interestRate).setScale(CURRENCY_SCALE, RoundingMode.FLOOR);
    }

    /**
     * Round to currency precision
     */
    public static BigDecimal roundCurrency(BigDecimal amount) {
        return amount.setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Check if two currency amounts are equal
     */
    public static boolean isEqual(BigDecimal amount1, BigDecimal amount2) {
        return amount1.compareTo(amount2) == 0;
    }

    /**
     * Check if amount1 > amount2
     */
    public static boolean isGreaterThan(BigDecimal amount1, BigDecimal amount2) {
        return amount1.compareTo(amount2) > 0;
    }

    /**
     * Sum array of BigDecimals with currency rounding
     */
    public static BigDecimal sum(BigDecimal... amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            total = total.add(amount);
        }
        return roundCurrency(total);
    }

    /**
     * Validate currency amount
     */
    public static void validateCurrencyAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.scale() > CURRENCY_SCALE) {
            throw new IllegalArgumentException(
                    String.format("Currency amount cannot have more than %d decimal places", CURRENCY_SCALE)
            );
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Currency amount cannot be negative");
        }
    }

    // ========== CONSOLIDATED METHODS FROM MoneyUtils ==========
    // The following methods were migrated from the deprecated MoneyUtils class

    /**
     * Convert BigDecimal to ML feature (float)
     *
     * SAFE conversion for machine learning features.
     * Use ONLY when passing to ML models, NEVER for business logic or financial calculations.
     *
     * @param amount Money amount as BigDecimal
     * @return Float value for ML feature (or 0.0f if null)
     */
    public static float toMLFeature(BigDecimal amount) {
        if (amount == null) {
            return 0.0f;
        }
        return amount.floatValue();
    }

    /**
     * Convert BigDecimal to ML feature with log transform
     *
     * Log transformation helps ML models handle wide range of amounts.
     * Common in fraud detection to normalize large differences.
     * Formula: log1p(x) = log(1 + x) - handles zero safely
     *
     * @param amount Money amount as BigDecimal
     * @return Log-transformed float value (or 0.0f if null/zero/negative)
     */
    public static float toMLFeatureLog(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0f;
        }
        // log1p(x) = log(1 + x) - handles zero safely
        return (float) Math.log1p(amount.doubleValue());
    }

    /**
     * Convert BigDecimal to ML feature (normalized 0-1)
     *
     * Normalizes amount to 0-1 range for ML models.
     * Useful for neural networks and gradient-based algorithms.
     *
     * @param amount Money amount as BigDecimal
     * @param maxAmount Maximum amount for normalization
     * @return Normalized float value (0.0 - 1.0)
     */
    public static float toMLFeatureNormalized(BigDecimal amount, BigDecimal maxAmount) {
        if (amount == null || maxAmount == null || maxAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0f;
        }

        if (amount.compareTo(maxAmount) >= 0) {
            return 1.0f;
        }

        return amount.divide(maxAmount, 6, DEFAULT_ROUNDING).floatValue();
    }

    /**
     * Create BigDecimal from double (safe)
     *
     * Converts double to BigDecimal with proper rounding.
     * Use when receiving money from external sources or legacy systems.
     *
     * @param amount Money amount as double
     * @return BigDecimal with proper currency scale (2 decimals)
     */
    public static BigDecimal fromDouble(double amount) {
        return BigDecimal.valueOf(amount).setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Create BigDecimal from string (safe)
     *
     * Parses string to BigDecimal with error handling.
     * Returns ZERO if string is null, empty, or invalid.
     *
     * @param amount Money amount as string
     * @return BigDecimal with proper currency scale (or ZERO if invalid)
     */
    public static BigDecimal fromString(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(amount).setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Round amount to standard currency scale
     *
     * @param amount Amount to round
     * @return Rounded amount with 2 decimal places (or ZERO if null)
     */
    public static BigDecimal round(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate percentage safely (part/total * 100)
     *
     * Returns percentage as BigDecimal (0-100 range, 2 decimal places).
     * Example: calculatePercentage(25, 100) = 25.00
     *
     * @param part Part value
     * @param total Total value
     * @return Percentage as BigDecimal (0-100), or ZERO if invalid
     */
    public static BigDecimal calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (part == null) {
            return BigDecimal.ZERO;
        }

        return part.divide(total, 6, DEFAULT_ROUNDING)
                   .multiply(new BigDecimal("100"))
                   .setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate ratio safely (part/total as 0.0 - 1.0)
     *
     * Returns ratio as BigDecimal (0.0 - 1.0 range).
     * Example: calculateRatio(25, 100) = 0.25
     *
     * @param part Part value
     * @param total Total value
     * @return Ratio as BigDecimal (0.0 - 1.0), or ZERO if invalid
     */
    public static BigDecimal calculateRatio(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (part == null) {
            return BigDecimal.ZERO;
        }

        return part.divide(total, 6, DEFAULT_ROUNDING)
                   .setScale(ML_FEATURE_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Check if amount is zero (null-safe)
     */
    public static boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if amount is positive (null-safe)
     */
    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if amount is negative (null-safe)
     */
    public static boolean isNegative(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Get maximum of two amounts (null-safe)
     */
    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Get minimum of two amounts (null-safe)
     */
    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Format amount for display (e.g., "1,234.56")
     *
     * @param amount Amount to format
     * @return Formatted string with thousand separators (or "0.00" if null)
     */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return String.format("%,.2f", amount);
    }

    /**
     * Validate amount is within valid range
     *
     * @param amount Amount to validate
     * @param min Minimum allowed (null = no minimum)
     * @param max Maximum allowed (null = no maximum)
     * @return true if valid, false otherwise
     */
    public static boolean isValidRange(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (amount == null) {
            return false;
        }
        if (min != null && amount.compareTo(min) < 0) {
            return false;
        }
        if (max != null && amount.compareTo(max) > 0) {
            return false;
        }
        return true;
    }

    /**
     * Calculate average safely (total/count)
     *
     * @param total Total amount
     * @param count Count of items
     * @return Average amount (or ZERO if invalid)
     */
    public static BigDecimal calculateAverage(BigDecimal total, long count) {
        if (total == null || count <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(count), CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Add amounts safely (null-safe)
     *
     * Treats null as ZERO.
     * Example: add(null, 5.00) = 5.00
     *
     * @param a First amount
     * @param b Second amount
     * @return Sum with currency scale
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        BigDecimal result = (a != null ? a : BigDecimal.ZERO)
                           .add(b != null ? b : BigDecimal.ZERO);
        return result.setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Subtract amounts safely (null-safe)
     *
     * Treats null as ZERO.
     * Example: subtract(10.00, null) = 10.00
     *
     * @param a First amount (minuend)
     * @param b Second amount (subtrahend)
     * @return Difference with currency scale
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        BigDecimal result = (a != null ? a : BigDecimal.ZERO)
                           .subtract(b != null ? b : BigDecimal.ZERO);
        return result.setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Multiply amounts safely (null-safe)
     *
     * Treats null as ZERO.
     * Example: multiply(5.00, null) = 0.00
     *
     * @param a First amount
     * @param b Second amount
     * @return Product with currency scale (or ZERO if either is null)
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return BigDecimal.ZERO;
        }
        return a.multiply(b).setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Divide amounts safely (null-safe, division-by-zero safe)
     *
     * Returns ZERO if divisor is null or zero.
     * Example: divide(10.00, 0) = 0.00
     *
     * @param a Dividend
     * @param b Divisor
     * @return Quotient with currency scale (or ZERO if invalid)
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return a.divide(b, CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    // ========== ADVANCED OPERATIONS ==========

    /**
     * Check if amount1 is less than amount2 (null-safe)
     */
    public static boolean isLessThan(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) {
            return false;
        }
        return amount1.compareTo(amount2) < 0;
    }

    /**
     * Check if amount1 is greater than or equal to amount2 (null-safe)
     */
    public static boolean isGreaterThanOrEqual(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) {
            return false;
        }
        return amount1.compareTo(amount2) >= 0;
    }

    /**
     * Check if amount1 is less than or equal to amount2 (null-safe)
     */
    public static boolean isLessThanOrEqual(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) {
            return false;
        }
        return amount1.compareTo(amount2) <= 0;
    }

    /**
     * Absolute value of amount (null-safe)
     */
    public static BigDecimal abs(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.abs().setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Negate amount (null-safe)
     */
    public static BigDecimal negate(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.negate().setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Sum multiple amounts (null-safe, varargs)
     *
     * Example: sumAll(10.50, 20.00, null, 5.50) = 36.00
     *
     * @param amounts Variable number of amounts
     * @return Sum with currency scale
     */
    public static BigDecimal sumAll(BigDecimal... amounts) {
        if (amounts == null || amounts.length == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            if (amount != null) {
                total = total.add(amount);
            }
        }
        return total.setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate compound interest
     *
     * Formula: A = P(1 + r/n)^(nt)
     * Where: P=principal, r=annual rate, n=compounds per year, t=years
     *
     * @param principal Starting principal
     * @param annualRate Annual interest rate (as decimal, e.g., 0.05 for 5%)
     * @param compoundsPerYear Number of times interest compounds per year
     * @param years Number of years
     * @return Final amount with compound interest
     */
    public static BigDecimal calculateCompoundInterest(BigDecimal principal, BigDecimal annualRate,
                                                       int compoundsPerYear, int years) {
        if (principal == null || annualRate == null || compoundsPerYear <= 0 || years <= 0) {
            return principal != null ? principal : BigDecimal.ZERO;
        }

        // r/n
        BigDecimal ratePerPeriod = annualRate.divide(
            BigDecimal.valueOf(compoundsPerYear), INTERMEDIATE_SCALE, DEFAULT_ROUNDING
        );

        // 1 + r/n
        BigDecimal onePlusRate = BigDecimal.ONE.add(ratePerPeriod);

        // nt
        int totalPeriods = compoundsPerYear * years;

        // (1 + r/n)^(nt)
        BigDecimal multiplier = onePlusRate.pow(totalPeriods);

        // P * multiplier
        return principal.multiply(multiplier).setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Apply discount to amount
     *
     * @param amount Original amount
     * @param discountPercentage Discount percentage (e.g., 15 for 15%)
     * @return Amount after discount
     */
    public static BigDecimal applyDiscount(BigDecimal amount, BigDecimal discountPercentage) {
        if (amount == null || discountPercentage == null) {
            return amount != null ? amount : BigDecimal.ZERO;
        }

        BigDecimal discountDecimal = discountPercentage.divide(
            BigDecimal.valueOf(100), PERCENTAGE_SCALE, DEFAULT_ROUNDING
        );
        BigDecimal discountAmount = amount.multiply(discountDecimal);

        return amount.subtract(discountAmount).setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate amount before tax (reverse calculation)
     *
     * If taxed amount is $115.00 and tax rate is 15%, returns $100.00
     *
     * @param taxedAmount Amount including tax
     * @param taxRate Tax rate (as decimal, e.g., 0.15 for 15%)
     * @return Amount before tax
     */
    public static BigDecimal calculateAmountBeforeTax(BigDecimal taxedAmount, BigDecimal taxRate) {
        if (taxedAmount == null || taxRate == null) {
            return taxedAmount != null ? taxedAmount : BigDecimal.ZERO;
        }

        BigDecimal divisor = BigDecimal.ONE.add(taxRate);
        return taxedAmount.divide(divisor, CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    // ==========================================================================
    // ADVANCED MONEY OPERATIONS (Consolidated from MoneyCalculator)
    // ==========================================================================

    /**
     * Splits an amount equally among multiple parties with proper remainder handling.
     * Ensures no money is lost due to rounding by distributing remainder to first party.
     *
     * This is critical for bill splitting, group payments, and expense distribution.
     * The sum of all splits is GUARANTEED to equal the original total amount.
     *
     * Example: Split $10.00 among 3 parties
     * - Result: [$3.34, $3.33, $3.33] (total = $10.00)
     *
     * @param totalAmount Total amount to split (must be positive)
     * @param numberOfParties Number of parties to split among (must be > 0)
     * @return Array of split amounts with remainder distributed
     * @throws IllegalArgumentException if numberOfParties <= 0
     */
    public static BigDecimal[] splitAmount(BigDecimal totalAmount, int numberOfParties) {
        if (totalAmount == null) {
            throw new IllegalArgumentException("Total amount cannot be null");
        }
        if (numberOfParties <= 0) {
            throw new IllegalArgumentException("Number of parties must be positive");
        }

        BigDecimal[] splits = new BigDecimal[numberOfParties];

        // Calculate base split amount
        BigDecimal baseAmount = totalAmount.divide(
            BigDecimal.valueOf(numberOfParties),
            CURRENCY_SCALE + 2, // Extra precision for intermediate calculation
            RoundingMode.DOWN
        );

        // Round to currency precision
        BigDecimal roundedBase = baseAmount.setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);

        // Calculate total after rounding
        BigDecimal totalRounded = roundedBase.multiply(BigDecimal.valueOf(numberOfParties));

        // Calculate remainder
        BigDecimal remainder = totalAmount.subtract(totalRounded);

        // Distribute base amount to all parties
        for (int i = 0; i < numberOfParties; i++) {
            splits[i] = roundedBase;
        }

        // Add remainder to first party (could be distributed across multiple if needed)
        if (remainder.compareTo(BigDecimal.ZERO) != 0) {
            splits[0] = splits[0].add(remainder);
        }

        // Verification: ensure total matches (critical for financial correctness)
        BigDecimal verifyTotal = sumAll(splits);
        if (verifyTotal.compareTo(totalAmount) != 0) {
            throw new ArithmeticException(
                String.format("Split amount verification failed: %s != %s", verifyTotal, totalAmount)
            );
        }

        return splits;
    }

    /**
     * Converts amount between currencies using an exchange rate.
     *
     * Formula: targetAmount = sourceAmount * exchangeRate
     *
     * Example: Convert $100.00 USD to EUR at rate 0.85
     * - Result: €85.00
     *
     * @param amount Amount in source currency (must be non-negative)
     * @param exchangeRate Exchange rate (source to target, must be positive)
     * @return Converted amount in target currency
     * @throws IllegalArgumentException if exchange rate is invalid
     */
    public static BigDecimal convertCurrency(BigDecimal amount, BigDecimal exchangeRate) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive");
        }

        BigDecimal converted = amount.multiply(exchangeRate);
        return converted.setScale(CURRENCY_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Validates that two amounts are equal within acceptable precision tolerance.
     *
     * Useful for comparing amounts that may have minor rounding differences
     * due to different calculation paths.
     *
     * Example: areAmountsEqual(10.001, 10.002, 0.01) = true
     *
     * @param amount1 First amount
     * @param amount2 Second amount
     * @param tolerance Acceptable difference (must be non-negative)
     * @return true if amounts are equal within tolerance
     * @throws IllegalArgumentException if tolerance is negative
     */
    public static boolean areAmountsEqual(BigDecimal amount1, BigDecimal amount2, BigDecimal tolerance) {
        if (amount1 == null || amount2 == null) {
            return amount1 == amount2; // Both null = equal
        }
        if (tolerance == null || tolerance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tolerance must be non-negative");
        }

        BigDecimal difference = amount1.subtract(amount2).abs();
        return difference.compareTo(tolerance) <= 0;
    }

    /**
     * Validates amount is within valid range.
     *
     * @param amount Amount to validate
     * @param min Minimum allowed (null = no minimum)
     * @param max Maximum allowed (null = no maximum)
     * @param inclusive Whether bounds are inclusive
     * @return true if valid, false otherwise
     */
    public static boolean isValidRange(BigDecimal amount, BigDecimal min, BigDecimal max, boolean inclusive) {
        if (amount == null) {
            return false;
        }

        if (min != null) {
            int cmp = amount.compareTo(min);
            if (inclusive ? cmp < 0 : cmp <= 0) {
                return false;
            }
        }

        if (max != null) {
            int cmp = amount.compareTo(max);
            if (inclusive ? cmp > 0 : cmp >= 0) {
                return false;
            }
        }

        return true;
    }
}
