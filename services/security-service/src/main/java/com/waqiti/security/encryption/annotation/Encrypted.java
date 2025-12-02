package com.waqiti.security.encryption.annotation;

import com.waqiti.security.encryption.PCIFieldEncryptionService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PCI DSS Compliant Field Encryption Annotation
 * 
 * CRITICAL SECURITY: Marks fields for automatic PCI DSS compliant encryption
 * 
 * This annotation enables automatic field-level encryption for sensitive data
 * in compliance with PCI DSS v4.0 requirements:
 * 
 * - Automatically encrypts annotated fields before database storage
 * - Automatically decrypts fields when loading from database
 * - Uses strong AES-256-GCM encryption with separate keys per field type
 * - Provides comprehensive audit trails for all encryption operations
 * - Ensures data is encrypted both in transit and at rest
 * 
 * SUPPORTED FIELD TYPES:
 * - PAN (Primary Account Number) - Credit card numbers
 * - CVV (Card Verification Value) - Security codes
 * - CARDHOLDER_NAME - Card holder names
 * - TRACK_DATA - Magnetic stripe data (if needed)
 * - PIN_BLOCK - PIN verification data (if needed)
 * 
 * USAGE EXAMPLES:
 * 
 * @Entity
 * public class PaymentCard {
 *     @Encrypted(keyType = KeyType.PAN_ENCRYPTION)
 *     private String cardNumber;
 *     
 *     @Encrypted(keyType = KeyType.CVV_ENCRYPTION)
 *     private String cvv;
 *     
 *     @Encrypted(keyType = KeyType.CARDHOLDER_NAME)
 *     private String cardholderName;
 * }
 * 
 * SECURITY CONSIDERATIONS:
 * - Only use for truly sensitive data that requires encryption
 * - Ensure proper key management and rotation policies
 * - Monitor encryption performance impact on queries
 * - Use tokenization for PAN where possible to reduce scope
 * - Consider database-level encryption for additional protection
 * 
 * NON-COMPLIANCE PENALTIES:
 * - Unencrypted cardholder data: $5,000 - $500,000 per month
 * - Data breach with unencrypted data: $50 - $90 per record
 * - Loss of payment processing privileges
 * - Criminal liability for executives
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypted {
    
    /**
     * The type of encryption key to use for this field
     * Different sensitive data types use different encryption keys
     * for enhanced security and compliance
     */
    PCIFieldEncryptionService.KeyType keyType();
    
    /**
     * Whether to enable tokenization for this field
     * Tokenization replaces sensitive data with non-sensitive tokens
     * Recommended for PAN fields to reduce PCI DSS scope
     */
    boolean tokenize() default false;
    
    /**
     * Context identifier for audit trails
     * Helps track encryption operations for compliance reporting
     */
    String auditContext() default "DEFAULT";
    
    /**
     * Whether this field requires special handling for searches
     * Encrypted fields cannot be searched without decryption
     * Consider creating separate searchable hash fields if needed
     */
    boolean searchable() default false;
    
    /**
     * Maximum length of the encrypted data
     * Used for database column sizing
     * Encrypted data is typically 30-50% larger than original
     */
    int maxLength() default 1000;
    
    /**
     * Whether to validate the field format before encryption
     * Enables format validation (e.g., PAN format, CVV format)
     * Helps catch data quality issues before encryption
     */
    boolean validateFormat() default true;
    
    /**
     * Custom validation pattern for field format
     * Used when validateFormat is true
     * Leave empty to use default validation for the key type
     */
    String validationPattern() default "";
    
    /**
     * Whether to mask this field in logs and error messages
     * Prevents accidental exposure of sensitive data in logs
     * Always enabled for PAN and CVV fields
     */
    boolean maskInLogs() default true;
    
    /**
     * Backup key type for key rotation scenarios
     * Allows graceful key rotation without data loss
     * System will attempt primary key first, then backup key
     */
    PCIFieldEncryptionService.KeyType backupKeyType() default PCIFieldEncryptionService.KeyType.PAN_ENCRYPTION;
}