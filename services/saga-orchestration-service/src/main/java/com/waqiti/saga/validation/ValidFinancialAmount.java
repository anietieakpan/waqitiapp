package com.waqiti.saga.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Annotation to validate financial amounts
 *
 * CRITICAL: Ensures BigDecimal is used with correct precision for money
 *
 * Prevents:
 * - Using Double/Float for money (precision loss)
 * - Incorrect scale (decimal places)
 * - Negative amounts (when not allowed)
 * - Zero amounts (when not allowed)
 * - Overflow (amounts exceeding max precision)
 *
 * Example:
 * <pre>
 * public class TransferRequest {
 *     @ValidFinancialAmount
 *     @NotNull
 *     private BigDecimal amount;
 *
 *     @ValidFinancialAmount(allowNegative = true)
 *     private BigDecimal adjustmentAmount;
 * }
 * </pre>
 *
 * Default constraints:
 * - Scale: 4 decimal places (e.g., 100.0000)
 * - Precision: 19 total digits (e.g., 999999999999999.9999)
 * - Min: 0.0001 (by default)
 * - Max: 999999999999999.9999
 * - No negative amounts
 * - Zero allowed
 *
 * @see FinancialPrecisionValidator
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FinancialPrecisionValidator.class)
@Documented
public @interface ValidFinancialAmount {

    String message() default "Invalid financial amount: must be BigDecimal with scale <= 4 and precision <= 19";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Allow negative amounts (default: false)
     * Set to true for refunds, adjustments, or reversals
     */
    boolean allowNegative() default false;

    /**
     * Allow zero amounts (default: true)
     * Set to false for transfers that must have a positive amount
     */
    boolean allowZero() default true;
}
