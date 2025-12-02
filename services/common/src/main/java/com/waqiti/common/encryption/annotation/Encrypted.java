package com.waqiti.common.encryption.annotation;

import com.waqiti.common.encryption.EncryptedFieldConverter;

import jakarta.persistence.Convert;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields for automatic encryption/decryption.
 * 
 * Fields annotated with @Encrypted will be automatically encrypted before
 * storing in the database and decrypted when loading from the database.
 * 
 * This annotation combines with JPA's @Convert annotation to provide
 * seamless field-level encryption with minimal code changes.
 * 
 * Example usage:
 * <pre>
 * {@code
 * @Entity
 * public class Customer {
 *     @Encrypted(fieldType = "SSN")
 *     @Column(name = "ssn", length = 500) // Larger length for encrypted data
 *     private String socialSecurityNumber;
 *     
 *     @Encrypted(fieldType = "BANK_ACCOUNT")
 *     @Column(name = "bank_account", length = 500)
 *     private String bankAccountNumber;
 * }
 * }
 * </pre>
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Convert(converter = EncryptedFieldConverter.class)
public @interface Encrypted {
    
    /**
     * The type of field being encrypted.
     * This is used for audit logging and key management.
     * 
     * Common values:
     * - "SSN" - Social Security Numbers
     * - "BANK_ACCOUNT" - Bank account numbers
     * - "CREDIT_CARD" - Credit card numbers (use separate PCI-compliant storage)
     * - "PII" - General personally identifiable information
     * 
     * @return the field type for encryption context
     */
    String fieldType();
    
    /**
     * Whether this field contains highly sensitive data requiring additional protection.
     * Highly sensitive data uses stronger encryption parameters and more frequent key rotation.
     * 
     * @return true if highly sensitive, false otherwise
     */
    boolean highlySensitive() default false;
    
    /**
     * Custom encryption algorithm to use for this field.
     * If not specified, uses the system default (AES-256-GCM).
     * 
     * @return the encryption algorithm name
     */
    String algorithm() default "";
    
    /**
     * Whether to enable audit logging for access to this field.
     * When enabled, all encryption/decryption operations are logged for compliance.
     * 
     * @return true to enable audit logging, false otherwise
     */
    boolean auditAccess() default true;
}