package com.waqiti.common.validation.constraints;

import com.waqiti.common.validation.validators.PaymentLimitValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * FINANCIAL VALIDATION: Validates payment amount against user's transaction limits
 * 
 * This annotation checks:
 * 1. Amount doesn't exceed daily limit
 * 2. Amount doesn't exceed per-transaction limit
 * 3. User hasn't reached monthly limit
 * 4. KYC level supports the transaction amount
 * 
 * CRITICAL for compliance and fraud prevention
 * 
 * Usage:
 * <pre>
 * &#64;PostMapping("/transfer")
 * &#64;ValidPaymentLimit(maxAmount = "50000.00")
 * public ResponseEntity<?> transfer(
 *     &#64;RequestBody TransferRequest request
 * ) { ... }
 * </pre>
 * 
 * PRODUCTION-READY: Enforces regulatory transaction limits
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PaymentLimitValidator.class)
@Documented
public @interface ValidPaymentLimit {
    
    String message() default "Payment amount exceeds allowed limit";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Maximum amount allowed for this operation
     */
    String maxAmount() default "1000000.00";
    
    /**
     * Whether to check daily limits
     */
    boolean checkDailyLimit() default true;
    
    /**
     * Whether to check monthly limits
     */
    boolean checkMonthlyLimit() default true;
    
    /**
     * Whether to check KYC requirements for amount
     */
    boolean checkKYCLevel() default true;
}