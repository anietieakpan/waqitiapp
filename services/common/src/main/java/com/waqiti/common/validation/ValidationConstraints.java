package com.waqiti.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.*;
import java.lang.annotation.*;

/**
 * Custom validation constraints for financial operations
 */
public class ValidationConstraints {
    
    /**
     * Validates currency amount with precision
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CurrencyAmountValidator.class)
    @Documented
    public @interface ValidCurrencyAmount {
        String message() default "Invalid currency amount";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String currency() default "USD";
        String min() default "0.01";
        String max() default "999999999.99";
        int scale() default 2;
    }
    
    /**
     * Validates IBAN format
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = IBANValidator.class)
    @Documented
    public @interface ValidIBAN {
        String message() default "Invalid IBAN format";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validates SWIFT/BIC code
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = SwiftCodeValidator.class)
    @Documented
    public @interface ValidSwiftCode {
        String message() default "Invalid SWIFT/BIC code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validates CVV/CVC code
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CVVValidator.class)
    @Documented
    public @interface CVV {
        String message() default "Invalid CVV/CVC code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validates cryptocurrency address
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CryptoAddressValidator.class)
    @Documented
    public @interface ValidCryptoAddress {
        String message() default "Invalid cryptocurrency address";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        CryptoType crypto() default CryptoType.BITCOIN;
        String currency() default "BTC";
    }
    
    /**
     * Validates phone number with international format
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PhoneNumberValidator.class)
    @Documented
    public @interface ValidPhoneNumber {
        String message() default "Invalid phone number format";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        boolean international() default true;
    }
    
    /**
     * Validates phone number (alias for ValidPhoneNumber)
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PhoneNumberValidator.class)
    @Documented
    public @interface PhoneNumber {
        String message() default "Invalid phone number format";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        boolean international() default true;
    }
    
    /**
     * Validates SSN format (US Social Security Number)
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = SSNValidator.class)
    @Documented
    public @interface ValidSSN {
        String message() default "Invalid SSN format";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        boolean masked() default false;
    }
    
    /**
     * Validates routing number (US banks)
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = RoutingNumberValidator.class)
    @Documented
    public @interface ValidRoutingNumber {
        String message() default "Invalid routing number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validates credit/debit card number using Luhn algorithm
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CardNumberValidator.class)
    @Documented
    public @interface ValidCardNumber {
        String message() default "Invalid card number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        CardType[] supportedTypes() default {CardType.VISA, CardType.MASTERCARD, CardType.AMEX};
    }
    
    /**
     * Validates CVV/CVC code
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CVVValidator.class)
    @Documented
    public @interface ValidCVV {
        String message() default "Invalid CVV/CVC code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validates transaction reference
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = TransactionReferenceValidator.class)
    @Documented
    public @interface ValidTransactionReference {
        String message() default "Invalid transaction reference";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String pattern() default "^TXN-[A-Z0-9]{10,20}$";
    }
    
    /**
     * Validates date range
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = DateRangeValidator.class)
    @Documented
    public @interface ValidDateRange {
        String message() default "Invalid date range";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String startDateField() default "startDate";
        String endDateField() default "endDate";
        int maxDays() default 365;
    }
    
    /**
     * Validates password strength
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PasswordStrengthValidator.class)
    @Documented
    public @interface StrongPassword {
        String message() default "Password does not meet security requirements";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int minLength() default 8;
        boolean requireUppercase() default true;
        boolean requireLowercase() default true;
        boolean requireDigit() default true;
        boolean requireSpecial() default true;
    }
    
    /**
     * Validates account number format
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = AccountNumberValidator.class)
    @Documented
    public @interface ValidAccountNumber {
        String message() default "Invalid account number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int minLength() default 8;
        int maxLength() default 17;
    }
    
    /**
     * Validates username format
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = UsernameValidator.class)
    @Documented
    public @interface ValidUsername {
        String message() default "Invalid username format";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int minLength() default 3;
        int maxLength() default 20;
        boolean allowSpecialChars() default false;
    }
    
    /**
     * Validates ZIP/Postal code
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PostalCodeValidator.class)
    @Documented
    public @interface ValidPostalCode {
        String message() default "Invalid postal code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String country() default "US";
    }
    
    /**
     * Prevents SQL injection
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = NoSQLInjectionValidator.class)
    @Documented
    public @interface NoSQLInjection {
        String message() default "Input contains potentially dangerous characters";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Prevents XSS attacks
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = NoXSSValidator.class)
    @Documented
    public @interface NoXSS {
        String message() default "Input contains potentially dangerous HTML/JavaScript";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validates business identifier (EIN, VAT, etc.)
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = BusinessIdentifierValidator.class)
    @Documented
    public @interface ValidBusinessIdentifier {
        String message() default "Invalid business identifier";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        BusinessIdType type() default BusinessIdType.EIN;
    }
    
    /**
     * Validates age requirement
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = MinimumAgeValidator.class)
    @Documented
    public @interface MinimumAge {
        String message() default "Does not meet minimum age requirement";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int value() default 18;
    }
    
    /**
     * Validates file upload
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = FileUploadValidator.class)
    @Documented
    public @interface ValidFileUpload {
        String message() default "Invalid file upload";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String[] allowedExtensions() default {".pdf", ".jpg", ".jpeg", ".png"};
        long maxSize() default 5242880; // 5MB
        String[] allowedMimeTypes() default {"application/pdf", "image/jpeg", "image/png"};
    }
    
    // Enums for validation
    
    public enum CryptoType {
        BITCOIN,
        ETHEREUM,
        LITECOIN,
        RIPPLE,
        STELLAR
    }
    
    public enum CardType {
        VISA,
        MASTERCARD,
        AMEX,
        DISCOVER,
        DINERS,
        JCB
    }
    
    public enum BusinessIdType {
        EIN,  // US Employer Identification Number
        VAT,  // EU VAT Number
        GST,  // Goods and Services Tax
        ABN   // Australian Business Number
    }
}