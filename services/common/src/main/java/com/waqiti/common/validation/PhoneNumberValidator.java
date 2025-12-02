package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator for phone numbers with international format support
 */
public class PhoneNumberValidator implements ConstraintValidator<ValidationConstraints.PhoneNumber, String> {
    
    private String region;
    
    // International phone number pattern (E.164 format)
    private static final Pattern INTERNATIONAL_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    
    // US/Canada phone pattern
    private static final Pattern US_PATTERN = Pattern.compile("^(\\+1)?[2-9]\\d{2}[2-9]\\d{6}$");
    
    // UK phone pattern
    private static final Pattern UK_PATTERN = Pattern.compile("^(\\+44)?[1-9]\\d{9,10}$");
    
    // Nigeria phone pattern
    private static final Pattern NG_PATTERN = Pattern.compile("^(\\+234)?[789]\\d{9}$");
    
    @Override
    public void initialize(ValidationConstraints.PhoneNumber constraintAnnotation) {
        this.region = constraintAnnotation.region();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Let @NotNull/@NotEmpty handle null/empty validation
        }
        
        // Remove common formatting characters
        String phoneNumber = value.replaceAll("[\\s\\-\\(\\)\\.]", "");
        
        // Validate based on region
        switch (region.toUpperCase()) {
            case "US":
            case "CA":
                return validateUSPhone(phoneNumber);
            case "UK":
            case "GB":
                return validateUKPhone(phoneNumber);
            case "NG":
                return validateNigeriaPhone(phoneNumber);
            case "INTERNATIONAL":
            case "ANY":
                return validateInternational(phoneNumber);
            default:
                // Default to international validation
                return validateInternational(phoneNumber);
        }
    }
    
    private boolean validateInternational(String phoneNumber) {
        // Must start with + and country code
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+" + phoneNumber;
        }
        
        // Check E.164 format
        if (!INTERNATIONAL_PATTERN.matcher(phoneNumber).matches()) {
            return false;
        }
        
        // Additional validation: total length should be between 7 and 15 digits
        String digitsOnly = phoneNumber.substring(1); // Remove +
        return digitsOnly.length() >= 7 && digitsOnly.length() <= 15;
    }
    
    private boolean validateUSPhone(String phoneNumber) {
        // Remove country code if present
        if (phoneNumber.startsWith("+1")) {
            phoneNumber = phoneNumber.substring(2);
        } else if (phoneNumber.startsWith("1") && phoneNumber.length() == 11) {
            phoneNumber = phoneNumber.substring(1);
        }
        
        // Should be 10 digits
        if (phoneNumber.length() != 10) {
            return false;
        }
        
        // Area code cannot start with 0 or 1
        if (phoneNumber.charAt(0) < '2') {
            return false;
        }
        
        // Exchange code cannot start with 0 or 1
        if (phoneNumber.charAt(3) < '2') {
            return false;
        }
        
        return phoneNumber.matches("\\d{10}");
    }
    
    private boolean validateUKPhone(String phoneNumber) {
        // Remove country code if present
        if (phoneNumber.startsWith("+44")) {
            phoneNumber = "0" + phoneNumber.substring(3);
        } else if (phoneNumber.startsWith("44")) {
            phoneNumber = "0" + phoneNumber.substring(2);
        }
        
        // Should start with 0
        if (!phoneNumber.startsWith("0")) {
            return false;
        }
        
        // Should be 10 or 11 digits
        return phoneNumber.matches("0\\d{9,10}");
    }
    
    private boolean validateNigeriaPhone(String phoneNumber) {
        // Remove country code if present
        if (phoneNumber.startsWith("+234")) {
            phoneNumber = "0" + phoneNumber.substring(4);
        } else if (phoneNumber.startsWith("234")) {
            phoneNumber = "0" + phoneNumber.substring(3);
        }
        
        // Should start with 0 followed by 7, 8, or 9
        if (!phoneNumber.startsWith("0")) {
            return false;
        }
        
        // Should be 11 digits total
        if (phoneNumber.length() != 11) {
            return false;
        }
        
        // Second digit should be 7, 8, or 9 (mobile prefixes)
        char secondDigit = phoneNumber.charAt(1);
        if (secondDigit != '7' && secondDigit != '8' && secondDigit != '9') {
            return false;
        }
        
        return phoneNumber.matches("0[789]\\d{9}");
    }
}