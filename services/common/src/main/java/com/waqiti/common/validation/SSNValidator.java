package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator for Social Security Numbers (SSN)
 */
public class SSNValidator implements ConstraintValidator<ValidationConstraints.ValidSSN, String> {
    
    // SSN pattern: XXX-XX-XXXX or XXXXXXXXX
    private static final Pattern SSN_PATTERN = Pattern.compile("^(?!000|666|9\\d{2})\\d{3}[-]?(?!00)\\d{2}[-]?(?!0000)\\d{4}$");
    
    @Override
    public void initialize(ValidationConstraints.ValidSSN constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Let @NotNull/@NotEmpty handle null/empty validation
        }
        
        // Remove hyphens and spaces
        String ssn = value.replaceAll("[\\s-]", "");
        
        // Must be exactly 9 digits
        if (ssn.length() != 9) {
            return false;
        }
        
        // Check if all characters are digits
        if (!ssn.matches("\\d{9}")) {
            return false;
        }
        
        // Validate SSN rules
        String area = ssn.substring(0, 3);
        String group = ssn.substring(3, 5);
        String serial = ssn.substring(5, 9);
        
        // Area number validation
        int areaNum = Integer.parseInt(area);
        if (areaNum == 0 || areaNum == 666 || areaNum >= 900) {
            return false; // Invalid area numbers
        }
        
        // Group number validation
        if (group.equals("00")) {
            return false; // Group cannot be 00
        }
        
        // Serial number validation
        if (serial.equals("0000")) {
            return false; // Serial cannot be 0000
        }
        
        // Check against known invalid SSNs
        if (isKnownInvalidSSN(ssn)) {
            return false;
        }
        
        return true;
    }
    
    private boolean isKnownInvalidSSN(String ssn) {
        // Known invalid SSNs used in examples
        return ssn.equals("123456789") ||
               ssn.equals("111111111") ||
               ssn.equals("222222222") ||
               ssn.equals("333333333") ||
               ssn.equals("444444444") ||
               ssn.equals("555555555") ||
               ssn.equals("666666666") ||
               ssn.equals("777777777") ||
               ssn.equals("888888888") ||
               ssn.equals("999999999") ||
               ssn.equals("000000000") ||
               ssn.equals("123121234");
    }
}