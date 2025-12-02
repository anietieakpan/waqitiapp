package com.waqiti.payment.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * PRODUCTION VALIDATOR IMPLEMENTATION: Amount Validator
 *
 * Implements comprehensive validation logic for financial amounts to prevent:
 * - Negative amount exploits
 * - Precision loss attacks
 * - Money laundering (unrealistic amounts)
 * - Integer overflow attempts
 * - Decimal precision exploits
 *
 * VALIDATION RULES:
 * ----------------
 * 1. Amount must not be null
 * 2. Amount must be positive (or zero if allowed)
 * 3. Amount must not exceed maximum
 * 4. Amount must meet minimum
 * 5. Decimal places must not exceed limit
 * 6. Amount must be a valid number (not NaN or Infinite)
 *
 * ATTACK PREVENTION:
 * -----------------
 * - Negative Amount Attack: User tries to pay -$100 to receive $100
 * - Precision Attack: User sends $0.001 hoping system rounds up
 * - Overflow Attack: User sends $999999999999999999 to cause overflow
 * - Rounding Exploit: User exploits fractional cent rounding
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since November 17, 2025
 */
@Slf4j
public class AmountValidator implements ConstraintValidator<ValidAmount, BigDecimal> {

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private int maxDecimalPlaces;
    private boolean allowZero;

    @Override
    public void initialize(ValidAmount annotation) {
        this.minAmount = new BigDecimal(annotation.min());
        this.maxAmount = new BigDecimal(annotation.max());
        this.maxDecimalPlaces = annotation.maxDecimalPlaces();
        this.allowZero = annotation.allowZero();

        log.debug("Initialized AmountValidator: min={}, max={}, maxDecimals={}, allowZero={}",
            minAmount, maxAmount, maxDecimalPlaces, allowZero);
    }

    @Override
    public boolean isValid(BigDecimal amount, ConstraintValidatorContext context) {
        // Null check
        if (amount == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Amount is required and cannot be null"
            ).addConstraintViolation();
            return false;
        }

        // Zero check
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            if (!allowZero) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "Amount must be greater than zero"
                ).addConstraintViolation();
                return false;
            }
            return true; // Zero is valid if allowed
        }

        // Negative check (CRITICAL SECURITY)
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Amount cannot be negative. Attempted negative amount: " + amount
            ).addConstraintViolation();

            log.warn("🚨 SECURITY: Negative amount attempted: {}", amount);
            return false;
        }

        // Minimum amount check
        if (amount.compareTo(minAmount) < 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount must be at least %s (got: %s)", minAmount, amount)
            ).addConstraintViolation();
            return false;
        }

        // Maximum amount check (Anti-money laundering)
        if (amount.compareTo(maxAmount) > 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount must not exceed %s (got: %s)", maxAmount, amount)
            ).addConstraintViolation();

            log.warn("⚠️ COMPLIANCE: Large amount detected: {} (max: {})", amount, maxAmount);
            return false;
        }

        // Decimal places check (Precision control)
        int scale = amount.scale();
        if (scale > maxDecimalPlaces) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount has too many decimal places. Max: %d, Got: %d (amount: %s)",
                    maxDecimalPlaces, scale, amount)
            ).addConstraintViolation();

            log.warn("⚠️ PRECISION: Amount with {} decimal places attempted (max: {}): {}",
                scale, maxDecimalPlaces, amount);
            return false;
        }

        // Additional sanity checks
        if (amount.toString().length() > 20) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Amount value is unrealistically large"
            ).addConstraintViolation();

            log.warn("🚨 SECURITY: Unrealistic amount attempted: {}", amount);
            return false;
        }

        // All validations passed
        return true;
    }
}
