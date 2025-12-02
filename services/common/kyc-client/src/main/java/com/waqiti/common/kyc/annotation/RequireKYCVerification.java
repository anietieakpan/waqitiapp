package com.waqiti.common.kyc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that require KYC verification
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireKYCVerification {
    
    /**
     * Required verification level
     */
    VerificationLevel level() default VerificationLevel.BASIC;
    
    /**
     * Required action capability
     */
    String action() default "";
    
    /**
     * Custom error message when verification fails
     */
    String message() default "KYC verification required";
    
    /**
     * Whether to allow the operation if KYC service is unavailable
     */
    boolean allowOnServiceUnavailable() default false;
    
    enum VerificationLevel {
        BASIC,
        INTERMEDIATE, 
        ADVANCED
    }
}