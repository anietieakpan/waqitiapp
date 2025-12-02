package com.waqiti.saga.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * Validator to ensure financial amounts use BigDecimal with correct precision
 *
 * CRITICAL: Prevents precision loss in financial calculations
 *
 * Validates:
 * - Amount is BigDecimal (not Double/Float)
 * - Scale is 4 (for currency precision - supports up to 0.0001 units)
 * - Precision is sufficient (up to 999999999999999.9999)
 * - Amount is positive (if required)
 *
 * Usage:
 * <pre>
 * public class TransferRequest {
 *     @ValidFinancialAmount
 *     private BigDecimal amount;
 * }
 * </pre>
 *
 * @see ValidFinancialAmount
 */
public class FinancialPrecisionValidator implements ConstraintValidator<ValidFinancialAmount, BigDecimal> {

    private static final int REQUIRED_SCALE = 4;
    private static final int MAX_PRECISION = 19;
    private static final BigDecimal MIN_AMOUNT = BigDecimal.ZERO;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999.9999");

    private boolean allowNegative;
    private boolean allowZero;

    @Override
    public void initialize(ValidFinancialAmount constraintAnnotation) {
        this.allowNegative = constraintAnnotation.allowNegative();
        this.allowZero = constraintAnnotation.allowZero();
    }

    @Override
    public boolean isValid(BigDecimal amount, ConstraintValidatorContext context) {
        // Null check (use @NotNull separately)
        if (amount == null) {
            return true;
        }

        // Validate scale (decimal places)
        if (amount.scale() > REQUIRED_SCALE) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount must have at most %d decimal places, found: %d",
                    REQUIRED_SCALE, amount.scale())
            ).addConstraintViolation();
            return false;
        }

        // Validate precision (total digits)
        if (amount.precision() > MAX_PRECISION) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount exceeds maximum precision of %d digits", MAX_PRECISION)
            ).addConstraintViolation();
            return false;
        }

        // Validate range
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount exceeds maximum allowed value: %s", MAX_AMOUNT)
            ).addConstraintViolation();
            return false;
        }

        // Validate positive/zero constraints
        if (!allowZero && amount.compareTo(BigDecimal.ZERO) == 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Amount must be greater than zero"
            ).addConstraintViolation();
            return false;
        }

        if (!allowNegative && amount.compareTo(BigDecimal.ZERO) < 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Amount cannot be negative"
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
