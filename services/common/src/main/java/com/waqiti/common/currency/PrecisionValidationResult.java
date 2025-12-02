package com.waqiti.common.currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result of precision validation for financial calculations.
 * Contains validation status and detailed information for audit and debugging.
 */
public class PrecisionValidationResult {
    
    private final boolean valid;
    private final String message;
    private final PreciseMonetaryAmount actualAmount;
    private final PreciseMonetaryAmount expectedAmount;
    private final BigDecimal difference;
    private final BigDecimal tolerance;
    private final BigDecimal tolerancePercentage;
    private final Instant validatedAt;
    
    private PrecisionValidationResult(boolean valid, String message, PreciseMonetaryAmount actualAmount,
                                    PreciseMonetaryAmount expectedAmount, BigDecimal difference,
                                    BigDecimal tolerance, BigDecimal tolerancePercentage, 
                                    Instant validatedAt) {
        this.valid = valid;
        this.message = message;
        this.actualAmount = actualAmount;
        this.expectedAmount = expectedAmount;
        this.difference = difference;
        this.tolerance = tolerance;
        this.tolerancePercentage = tolerancePercentage;
        this.validatedAt = validatedAt;
    }
    
    public static PrecisionValidationResult valid(String message) {
        return new PrecisionValidationResult(true, message, null, null, null, null, null, Instant.now());
    }
    
    public static PrecisionValidationResult invalid(String message) {
        return new PrecisionValidationResult(false, message, null, null, null, null, null, Instant.now());
    }
    
    public static PrecisionValidationResultBuilder builder() {
        return new PrecisionValidationResultBuilder();
    }
    
    // Getters
    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
    public PreciseMonetaryAmount getActualAmount() { return actualAmount; }
    public PreciseMonetaryAmount getExpectedAmount() { return expectedAmount; }
    public BigDecimal getDifference() { return difference; }
    public BigDecimal getTolerance() { return tolerance; }
    public BigDecimal getTolerancePercentage() { return tolerancePercentage; }
    public Instant getValidatedAt() { return validatedAt; }
    
    /**
     * Get difference as percentage of expected amount
     */
    public BigDecimal getDifferencePercentage() {
        if (expectedAmount == null || expectedAmount.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return difference.multiply(new BigDecimal("100"))
                .divide(expectedAmount.getAmount().abs(), java.math.RoundingMode.HALF_EVEN);
    }
    
    /**
     * Check if difference is within acceptable tolerance
     */
    public boolean isWithinTolerance() {
        if (tolerance == null || difference == null) {
            return valid;
        }
        return difference.compareTo(tolerance) <= 0;
    }
    
    @Override
    public String toString() {
        if (actualAmount != null && expectedAmount != null) {
            return String.format("PrecisionValidationResult{valid=%s, actual=%s, expected=%s, diff=%s, tolerance=%s%%}", 
                    valid, actualAmount.toPreciseString(), expectedAmount.toPreciseString(), 
                    difference.toPlainString(), tolerancePercentage.toPlainString());
        } else {
            return String.format("PrecisionValidationResult{valid=%s, message='%s'}", valid, message);
        }
    }
    
    public static class PrecisionValidationResultBuilder {
        private boolean valid;
        private String message;
        private PreciseMonetaryAmount actualAmount;
        private PreciseMonetaryAmount expectedAmount;
        private BigDecimal difference;
        private BigDecimal tolerance;
        private BigDecimal tolerancePercentage;
        private Instant validatedAt;
        
        public PrecisionValidationResultBuilder valid(boolean valid) {
            this.valid = valid;
            return this;
        }
        
        public PrecisionValidationResultBuilder message(String message) {
            this.message = message;
            return this;
        }
        
        public PrecisionValidationResultBuilder actualAmount(PreciseMonetaryAmount actualAmount) {
            this.actualAmount = actualAmount;
            return this;
        }
        
        public PrecisionValidationResultBuilder expectedAmount(PreciseMonetaryAmount expectedAmount) {
            this.expectedAmount = expectedAmount;
            return this;
        }
        
        public PrecisionValidationResultBuilder difference(BigDecimal difference) {
            this.difference = difference;
            return this;
        }
        
        public PrecisionValidationResultBuilder tolerance(BigDecimal tolerance) {
            this.tolerance = tolerance;
            return this;
        }
        
        public PrecisionValidationResultBuilder tolerancePercentage(BigDecimal tolerancePercentage) {
            this.tolerancePercentage = tolerancePercentage;
            return this;
        }
        
        public PrecisionValidationResultBuilder validatedAt(Instant validatedAt) {
            this.validatedAt = validatedAt;
            return this;
        }
        
        public PrecisionValidationResult build() {
            return new PrecisionValidationResult(valid, message, actualAmount, expectedAmount, 
                    difference, tolerance, tolerancePercentage, validatedAt);
        }
    }
}