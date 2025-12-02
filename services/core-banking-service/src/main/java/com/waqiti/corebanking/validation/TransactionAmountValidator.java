package com.waqiti.corebanking.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * Validator implementation for @TransactionAmount annotation
 *
 * Enforces transaction amount constraints for:
 * - Anti-money laundering (AML) compliance
 * - Fraud prevention
 * - Financial precision standards
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
public class TransactionAmountValidator implements ConstraintValidator<TransactionAmount, BigDecimal> {

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private int maxScale;

    @Override
    public void initialize(TransactionAmount constraintAnnotation) {
        this.minAmount = new BigDecimal(constraintAnnotation.min());
        this.maxAmount = new BigDecimal(constraintAnnotation.max());
        this.maxScale = constraintAnnotation.scale();
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Use @NotNull for null checks
        }

        // Check minimum amount
        if (value.compareTo(minAmount) < 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount must be at least %s", minAmount)
            ).addConstraintViolation();
            return false;
        }

        // Check maximum amount (AML compliance)
        if (value.compareTo(maxAmount) > 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount exceeds maximum limit of %s. Manual approval required for large transactions.", maxAmount)
            ).addConstraintViolation();
            return false;
        }

        // Check decimal places (financial precision)
        if (value.scale() > maxScale) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Amount cannot have more than %d decimal places", maxScale)
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
