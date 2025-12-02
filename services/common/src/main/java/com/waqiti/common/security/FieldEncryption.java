package com.waqiti.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields that should be encrypted at rest.
 * Used in conjunction with JPA AttributeConverter for automatic encryption/decryption.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldEncryption {
    
    /**
     * The type of data being encrypted.
     * This can be used for different encryption strategies or compliance requirements.
     */
    DataType dataType() default DataType.GENERAL;
    
    /**
     * Whether to log access to this field for audit purposes.
     */
    boolean auditAccess() default false;
    
    enum DataType {
        GENERAL,        // General sensitive data
        PII,           // Personally Identifiable Information
        FINANCIAL,     // Financial data (account numbers, etc.)
        MEDICAL,       // Medical/health information
        BIOMETRIC      // Biometric data
    }
}