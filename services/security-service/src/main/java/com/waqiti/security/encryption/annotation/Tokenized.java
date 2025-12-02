package com.waqiti.security.encryption.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PCI DSS Compliant Tokenization Annotation
 * 
 * CRITICAL SECURITY: Marks fields for automatic PCI DSS compliant tokenization
 * 
 * This annotation enables automatic tokenization for sensitive data in compliance
 * with PCI DSS v4.0 requirements. Tokenization replaces sensitive data with
 * non-sensitive tokens, significantly reducing PCI DSS compliance scope:
 * 
 * TOKENIZATION BENEFITS:
 * - Removes sensitive data from most systems and databases
 * - Maintains format for existing system compatibility
 * - Reduces PCI DSS compliance scope and costs
 * - Enables secure analytics and reporting on tokens
 * - Provides secure token lifecycle management
 * 
 * TOKEN CHARACTERISTICS:
 * - Cryptographically strong and unpredictable
 * - Format-preserving (same length as original data)
 * - No mathematical relationship to original data
 * - Reversible only through secure token vault
 * - Complete audit trail for token operations
 * 
 * USAGE EXAMPLES:
 * 
 * @Entity
 * public class Customer {
 *     @Tokenized(formatPreserving = true, contextId = "customer")
 *     private String cardNumber;
 *     
 *     @Tokenized(contextId = "account")
 *     private String accountNumber;
 * }
 * 
 * TOKEN SECURITY:
 * - Tokens are stored in encrypted token vault
 * - Access to token vault is strictly controlled
 * - Token-to-data mapping is encrypted at rest
 * - Regular token rotation and cleanup
 * - Comprehensive access logging and monitoring
 * 
 * COMPLIANCE IMPACT:
 * - Significantly reduces PCI DSS scope
 * - Eliminates need for encryption in most systems
 * - Reduces compliance costs and complexity
 * - Enables secure data sharing with partners
 * - Simplifies regulatory audits and reporting
 * 
 * NON-COMPLIANCE PENALTIES:
 * - Improper tokenization: $25,000 - $100,000 per month
 * - Token vault compromise: $1M+ in fines and remediation
 * - Loss of payment processing privileges
 * - Increased insurance premiums and bond requirements
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Tokenized {
    
    /**
     * Whether to use format-preserving tokenization
     * Format-preserving tokens maintain the same format as the original data
     * This enables existing systems to work with tokens without modification
     */
    boolean formatPreserving() default true;
    
    /**
     * Context identifier for token operations
     * Used for audit trails and token lifecycle management
     * Helps track token usage across different business contexts
     */
    String contextId();
    
    /**
     * Token expiration time in days
     * Tokens automatically expire after this period
     * Set to 0 for tokens that never expire
     * Recommended: 90 days for most use cases
     */
    int expirationDays() default 90;
    
    /**
     * Whether to enable token reuse for the same input data
     * When true, the same input always produces the same token
     * When false, each tokenization generates a new token
     * Recommended: true for data analytics, false for maximum security
     */
    boolean reuseTokens() default true;
    
    /**
     * Maximum number of token accesses before rotation
     * Token is automatically rotated after this many accesses
     * Set to 0 to disable access-based rotation
     * Recommended: 1000 for high-usage tokens
     */
    int maxAccessCount() default 0;
    
    /**
     * Whether to enable token search capabilities
     * Creates searchable hash of original data for indexed searches
     * Note: This maintains some data linkability
     * Use only when search functionality is required
     */
    boolean searchable() default false;
    
    /**
     * Minimum token length
     * Ensures generated tokens meet minimum length requirements
     * Must be at least as long as original data for format-preserving tokens
     */
    int minTokenLength() default 13;
    
    /**
     * Maximum token length
     * Prevents tokens from exceeding database column limits
     * Should account for format-preserving requirements
     */
    int maxTokenLength() default 19;
    
    /**
     * Token validation pattern
     * Regular expression to validate token format
     * Used to ensure tokens meet system requirements
     */
    String tokenValidationPattern() default "";
    
    /**
     * Whether to mask tokens in logs and error messages
     * Prevents token exposure in application logs
     * Recommended: true for sensitive tokens
     */
    boolean maskInLogs() default true;
    
    /**
     * Token namespace for multi-tenant environments
     * Ensures tokens are unique across different tenants
     * Leave empty for single-tenant deployments
     */
    String namespace() default "";
    
    /**
     * Whether to enable token analytics
     * Creates anonymized metrics on token usage patterns
     * Helps with capacity planning and security monitoring
     */
    boolean enableAnalytics() default true;
    
    /**
     * Custom token generation algorithm
     * Allows specification of custom tokenization algorithms
     * Leave empty to use default secure tokenization
     */
    String algorithm() default "";
    
    /**
     * Whether to require two-person authorization for detokenization
     * Implements dual control for accessing original data
     * Recommended for highly sensitive data
     */
    boolean requireDualControl() default false;
    
    /**
     * Backup tokenization strategy for disaster recovery
     * Specifies how tokens should be handled during system failures
     * Options: FAIL_CLOSED, FAIL_OPEN, USE_BACKUP_VAULT
     */
    String backupStrategy() default "FAIL_CLOSED";
}