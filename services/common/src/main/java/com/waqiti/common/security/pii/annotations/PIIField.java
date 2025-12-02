package com.waqiti.common.security.pii.annotations;

import com.waqiti.common.security.pii.ComprehensivePIIProtectionService.PIIClassification;
import com.waqiti.common.security.pii.ComprehensivePIIProtectionService.ProtectionMode;

import java.lang.annotation.*;

/**
 * Annotation to mark fields containing PII data requiring protection
 * 
 * Usage:
 * @PIIField(classification = PIIClassification.CREDIT_CARD)
 * private String creditCardNumber;
 * 
 * @PIIField(classification = PIIClassification.EMAIL, mode = ProtectionMode.TOKENIZE)
 * private String email;
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PIIField {
    
    /**
     * PII classification type
     */
    PIIClassification classification();
    
    /**
     * Protection mode to use
     */
    ProtectionMode mode() default ProtectionMode.ENCRYPT;
    
    /**
     * Purpose of PII collection (for GDPR compliance)
     */
    String purpose() default "business-operation";
    
    /**
     * Data retention period in days (0 = indefinite)
     */
    int retentionDays() default 90;
    
    /**
     * Whether to audit access to this field
     */
    boolean auditAccess() default true;
    
    /**
     * Whether format-preserving encryption should be used
     */
    boolean preserveFormat() default true;
    
    /**
     * Minimum required role to access unmasked data
     */
    String minRole() default "ROLE_USER";
    
    /**
     * Data residency region constraint
     */
    String[] allowedRegions() default {};
    
    /**
     * Whether field is subject to GDPR right to erasure
     */
    boolean erasable() default true;
}