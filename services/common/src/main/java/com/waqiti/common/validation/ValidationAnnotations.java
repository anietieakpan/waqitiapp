package com.waqiti.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Custom Validation Annotations for Waqiti Platform
 * 
 * Provides domain-specific validation annotations for financial data validation
 * and security-focused input validation across all microservices.
 * 
 * @author Waqiti Engineering Team
 */
public class ValidationAnnotations {

    // Currency validation
    @Documented
    @Constraint(validatedBy = CurrencyCodeValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidCurrencyCode {
        String message() default "Invalid currency code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Amount validation
    @Documented
    @Constraint(validatedBy = TransactionAmountValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidTransactionAmount {
        String message() default "Invalid transaction amount";
        double min() default 0.01;
        double max() default 100000.00;
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Account number validation
    @Documented
    @Constraint(validatedBy = AccountNumberValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidAccountNumber {
        String message() default "Invalid account number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Phone number validation
    @Documented
    @Constraint(validatedBy = PhoneNumberValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidPhoneNumber {
        String message() default "Invalid phone number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Security validation (injection prevention)
    @Documented
    @Constraint(validatedBy = SecuritySafeValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SecuritySafe {
        String message() default "Input contains potentially malicious content";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // SWIFT code validation
    @Documented
    @Constraint(validatedBy = SwiftCodeValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidSwiftCode {
        String message() default "Invalid SWIFT code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // IBAN validation
    @Documented
    @Constraint(validatedBy = IbanValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidIban {
        String message() default "Invalid IBAN";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Strong password validation
    @Documented
    @Constraint(validatedBy = StrongPasswordValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface StrongPassword {
        String message() default "Password does not meet security requirements";
        int minLength() default 8;
        int maxLength() default 128;
        boolean requireUppercase() default true;
        boolean requireLowercase() default true;
        boolean requireNumbers() default true;
        boolean requireSpecialChars() default true;
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Validator implementations

    public static class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {
        private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true; // Use @NotNull for required validation
            return CURRENCY_PATTERN.matcher(value).matches();
        }
    }

    public static class TransactionAmountValidator implements ConstraintValidator<ValidTransactionAmount, BigDecimal> {
        private BigDecimal min;
        private BigDecimal max;

        @Override
        public void initialize(ValidTransactionAmount constraintAnnotation) {
            this.min = BigDecimal.valueOf(constraintAnnotation.min());
            this.max = BigDecimal.valueOf(constraintAnnotation.max());
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) return true;
            
            if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
                return false;
            }
            
            // Check decimal places (max 2 for most currencies)
            return value.scale() <= 2;
        }
    }

    public static class AccountNumberValidator implements ConstraintValidator<ValidAccountNumber, String> {
        private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[0-9]{8,20}$");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            return ACCOUNT_PATTERN.matcher(value).matches();
        }
    }

    public static class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {
        private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            return PHONE_PATTERN.matcher(value).matches();
        }
    }

    public static class SecuritySafeValidator implements ConstraintValidator<SecuritySafe, String> {
        private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            ".*(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|vbscript|onload|onerror|eval|expression).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        private static final Pattern XSS_PATTERN = Pattern.compile(
            ".*(<script|</script|javascript:|vbscript:|onload=|onerror=|onclick=|onmouseover=|onfocus=|onblur=|onchange=|onsubmit=).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
            ".*(\\||&|;|`|\\$\\(|\\$\\{|\\\\|/etc/|/bin/|cmd\\.exe|powershell|bash|sh).*",
            Pattern.CASE_INSENSITIVE
        );

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            
            return !SQL_INJECTION_PATTERN.matcher(value).matches() &&
                   !XSS_PATTERN.matcher(value).matches() &&
                   !COMMAND_INJECTION_PATTERN.matcher(value).matches();
        }
    }

    public static class SwiftCodeValidator implements ConstraintValidator<ValidSwiftCode, String> {
        private static final Pattern SWIFT_PATTERN = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            return SWIFT_PATTERN.matcher(value).matches();
        }
    }

    public static class IbanValidator implements ConstraintValidator<ValidIban, String> {
        private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{4}[0-9]{7}([A-Z0-9]?){0,16}$");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            
            if (!IBAN_PATTERN.matcher(value).matches()) {
                return false;
            }
            
            // Basic IBAN checksum validation could be added here
            return true;
        }
    }

    public static class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {
        private int minLength;
        private int maxLength;
        private boolean requireUppercase;
        private boolean requireLowercase;
        private boolean requireNumbers;
        private boolean requireSpecialChars;

        @Override
        public void initialize(StrongPassword constraintAnnotation) {
            this.minLength = constraintAnnotation.minLength();
            this.maxLength = constraintAnnotation.maxLength();
            this.requireUppercase = constraintAnnotation.requireUppercase();
            this.requireLowercase = constraintAnnotation.requireLowercase();
            this.requireNumbers = constraintAnnotation.requireNumbers();
            this.requireSpecialChars = constraintAnnotation.requireSpecialChars();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            
            if (value.length() < minLength || value.length() > maxLength) {
                return false;
            }
            
            if (requireUppercase && !value.matches(".*[A-Z].*")) {
                return false;
            }
            
            if (requireLowercase && !value.matches(".*[a-z].*")) {
                return false;
            }
            
            if (requireNumbers && !value.matches(".*[0-9].*")) {
                return false;
            }
            
            if (requireSpecialChars && !value.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
                return false;
            }
            
            return true;
        }
    }
}