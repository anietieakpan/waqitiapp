package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidPhoneNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for phone numbers
 */
public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {
    
    private String[] countryCodes;
    private boolean international;
    
    @Override
    public void initialize(ValidPhoneNumber constraintAnnotation) {
        this.countryCodes = constraintAnnotation.countryCodes();
        this.international = constraintAnnotation.international();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // Remove common formatting characters
        String cleaned = value.replaceAll("[\\s\\-\\(\\)\\.]", "");
        
        // Check for international format
        if (cleaned.startsWith("+")) {
            return cleaned.matches("^\\+[1-9]\\d{1,14}$"); // E.164 format
        }
        
        // Country-specific validation
        if (countryCodes.length == 0) {
            return true; // No specific country validation
        }
        
        for (String countryCode : countryCodes) {
            boolean valid = switch (countryCode) {
                case "US" -> cleaned.matches("^1?\\d{10}$"); // US numbers
                case "UK" -> cleaned.matches("^(44|0)\\d{10}$"); // UK numbers
                case "IN" -> cleaned.matches("^(91|0)?[6-9]\\d{9}$"); // Indian numbers
                default -> cleaned.matches("^\\d{7,15}$"); // General format
            };
            if (valid) {
                return true; // Valid for at least one country code
            }
        }
        
        return false; // Not valid for any specified country code
    }
}