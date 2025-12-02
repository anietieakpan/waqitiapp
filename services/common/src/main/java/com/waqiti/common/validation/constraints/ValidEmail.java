package com.waqiti.common.validation.constraints;

import com.waqiti.common.validation.validators.EmailValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Comprehensive email validation constraint with advanced features
 * Supports domain validation, disposable email detection, and business rules
 */
@Documented
@Constraint(validatedBy = EmailValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ValidEmail.List.class)
public @interface ValidEmail {
    String message() default "Invalid email address: ${validatedValue}";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Allow null values
     */
    boolean allowNull() default true;
    
    /**
     * Allow empty string values
     */
    boolean allowEmpty() default false;
    
    /**
     * Validate email format strictly according to RFC 5322
     */
    boolean strictRFC5322() default false;
    
    /**
     * Check DNS MX records for domain existence
     */
    boolean verifyDomain() default false;
    
    /**
     * Block disposable/temporary email addresses
     */
    boolean blockDisposable() default true;
    
    /**
     * Block known spam domains
     */
    boolean blockSpamDomains() default true;
    
    /**
     * Allow only specific domains (whitelist)
     */
    String[] allowedDomains() default {};
    
    /**
     * Block specific domains (blacklist)
     */
    String[] blockedDomains() default {};
    
    /**
     * Allow subdomains of allowed domains
     */
    boolean allowSubdomains() default true;
    
    /**
     * Maximum email length
     */
    int maxLength() default 254; // RFC 5321 limit
    
    /**
     * Minimum email length
     */
    int minLength() default 3; // a@b minimum
    
    /**
     * Allow IP addresses as domain (e.g., user@[192.168.1.1])
     */
    boolean allowIpDomain() default false;
    
    /**
     * Allow quoted strings in local part
     */
    boolean allowQuotedString() default false;
    
    /**
     * Allow international domain names (IDN)
     */
    boolean allowIDN() default true;
    
    /**
     * Allow special characters in local part
     */
    boolean allowSpecialCharacters() default true;
    
    /**
     * Check against known typo domains (gmial.com, etc.)
     */
    boolean suggestTypoCorrection() default true;
    
    /**
     * Validation mode
     */
    ValidationMode mode() default ValidationMode.STANDARD;
    
    /**
     * Custom validation provider
     */
    Class<? extends EmailValidationProvider> provider() default DefaultEmailValidationProvider.class;
    
    /**
     * Email validation modes
     */
    enum ValidationMode {
        BASIC,          // Simple regex validation
        STANDARD,       // Standard email validation
        STRICT,         // Strict RFC compliance
        BUSINESS,       // Business email only (no free providers)
        CORPORATE,      // Corporate domain required
        EDUCATIONAL,    // .edu domains only
        GOVERNMENT      // .gov domains only
    }
    
    /**
     * Interface for custom email validation
     */
    interface EmailValidationProvider {
        boolean isValid(String email, ValidEmail annotation);
        EmailInfo getEmailInfo(String email);
        String suggestCorrection(String email);
    }
    
    /**
     * Default implementation
     */
    class DefaultEmailValidationProvider implements EmailValidationProvider {
        @Override
        public boolean isValid(String email, ValidEmail annotation) {
            return true; // Implementation in validator
        }
        
        @Override
        public EmailInfo getEmailInfo(String email) {
            return null; // Implementation in validator
        }
        
        @Override
        public String suggestCorrection(String email) {
            return null; // Implementation in validator
        }
    }
    
    /**
     * Email information
     */
    interface EmailInfo {
        String getLocalPart();
        String getDomain();
        String getNormalizedEmail();
        boolean isDisposable();
        boolean isSpam();
        boolean isFreeProvider();
        boolean isBusinessEmail();
        boolean hasMxRecord();
        String getSuggestedCorrection();
        EmailProvider getProvider();
        double getQualityScore();
    }
    
    /**
     * Email provider information
     */
    interface EmailProvider {
        String getName();
        String getDomain();
        boolean isFree();
        boolean isBusiness();
        boolean isEducational();
        boolean isGovernment();
        boolean isDisposable();
        String getCountry();
    }
    
    /**
     * Container annotation for repeated constraints
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        ValidEmail[] value();
    }
}