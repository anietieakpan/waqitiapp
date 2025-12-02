package com.waqiti.common.validation;

import com.waqiti.common.validation.validators.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Payment validation annotations for comprehensive input validation
 */
public class PaymentValidation {

    /**
     * Validates amount within acceptable range for payment type
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PaymentAmountValidator.class)
    public @interface ValidPaymentAmount {
        String message() default "Invalid payment amount";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        double min() default 0.01;
        double max() default 1000000.00;
        PaymentType paymentType() default PaymentType.STANDARD;
    }

    /**
     * Validates currency code and checks if supported
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CurrencyValidator.class)
    public @interface ValidCurrency {
        String message() default "Unsupported currency";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        String[] supported() default {"USD", "EUR", "GBP", "CAD", "AUD"};
        boolean checkPair() default false;
    }

    /**
     * Validates IBAN format
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = IBANValidator.class)
    public @interface ValidIBAN {
        String message() default "Invalid IBAN format";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Validates routing number (US banks)
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = RoutingNumberValidator.class)
    public @interface ValidRoutingNumber {
        String message() default "Invalid routing number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Validates account number format
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = AccountNumberValidator.class)
    public @interface ValidAccountNumber {
        String message() default "Invalid account number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        int minLength() default 4;
        int maxLength() default 17;
        boolean onlyDigits() default true;
    }

    /**
     * Validates SWIFT/BIC code
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = SwiftCodeValidator.class)
    public @interface ValidSwiftCode {
        String message() default "Invalid SWIFT/BIC code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Validates payment description for prohibited content
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PaymentDescriptionValidator.class)
    public @interface ValidPaymentDescription {
        String message() default "Invalid payment description";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        int maxLength() default 500;
        boolean checkProfanity() default true;
        boolean checkSuspiciousPatterns() default true;
    }

    /**
     * Validates card number using Luhn algorithm
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CardNumberValidator.class)
    public @interface ValidCardNumber {
        String message() default "Invalid card number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        CardType[] acceptedTypes() default {CardType.VISA, CardType.MASTERCARD, CardType.AMEX, CardType.DISCOVER};
    }

    /**
     * Validates CVV/CVC code
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CVVValidator.class)
    public @interface ValidCVV {
        String message() default "Invalid CVV";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Validates wallet address for cryptocurrency
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CryptoAddressValidator.class)
    public @interface ValidCryptoAddress {
        String message() default "Invalid cryptocurrency address";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        CryptoType cryptoType();
    }

    /**
     * Validates phone number for SMS payments
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PhoneNumberValidator.class)
    public @interface ValidPhoneNumber {
        String message() default "Invalid phone number";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        String[] countryCodes() default {};
        boolean international() default true;
    }

    /**
     * Validates email for email-based payments
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PaymentEmailValidator.class)
    public @interface ValidPaymentEmail {
        String message() default "Invalid email address";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        boolean checkDomain() default true;
        String[] blacklistedDomains() default {};
    }

    /**
     * Cross-field validation for payment requests
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PaymentRequestValidator.class)
    public @interface ValidPaymentRequest {
        String message() default "Invalid payment request";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Validates scheduled payment date
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = ScheduledDateValidator.class)
    public @interface ValidScheduledDate {
        String message() default "Invalid scheduled date";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        int minDaysInFuture() default 0;
        int maxDaysInFuture() default 365;
        boolean allowWeekends() default true;
        boolean allowHolidays() default true;
    }

    /**
     * Validates recurring payment frequency
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = RecurringFrequencyValidator.class)
    public @interface ValidRecurringFrequency {
        String message() default "Invalid recurring frequency";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        Frequency[] allowed() default {Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY};
    }

    /**
     * Validates payment reference/memo
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PaymentReferenceValidator.class)
    public @interface ValidPaymentReference {
        String message() default "Invalid payment reference";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        
        String pattern() default "^[A-Za-z0-9\\-\\/\\s]{1,35}$";
        boolean required() default false;
    }

    // Enums for validation
    
    public enum PaymentType {
        STANDARD,
        HIGH_VALUE,
        INTERNATIONAL,
        CRYPTO,
        MICRO
    }

    public enum CardType {
        VISA,
        MASTERCARD,
        AMEX,
        DISCOVER,
        DINERS,
        JCB,
        UNIONPAY
    }

    public enum CryptoType {
        BITCOIN,
        ETHEREUM,
        LITECOIN,
        RIPPLE,
        STELLAR
    }

    public enum Frequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }
}