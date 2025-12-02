package com.waqiti.common.validation.constraints;

import com.waqiti.common.validation.validators.WalletOwnershipValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * SECURITY VALIDATION: Ensures user owns the wallet they're trying to access
 * 
 * This annotation validates that the authenticated user is the owner of the specified wallet.
 * CRITICAL for preventing unauthorized access to other users' wallets.
 * 
 * Usage:
 * <pre>
 * public ResponseEntity<?> transfer(
 *     @ValidWalletOwnership String walletId,
 *     @AuthenticationPrincipal UserDetails user
 * ) { ... }
 * </pre>
 * 
 * PRODUCTION-READY: Prevents unauthorized wallet access attacks
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WalletOwnershipValidator.class)
@Documented
public @interface ValidWalletOwnership {
    
    String message() default "Unauthorized wallet access: You do not own this wallet";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Optionally specify if null values should be allowed
     */
    boolean allowNull() default false;
}