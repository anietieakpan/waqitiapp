package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator for SWIFT/BIC codes
 */
public class SwiftCodeValidator implements ConstraintValidator<ValidationConstraints.ValidSwiftCode, String> {
    
    // SWIFT code pattern: 8 or 11 characters
    // Format: AAAABBCC or AAAABBCCDDD
    // AAAA: Bank code (4 letters)
    // BB: Country code (2 letters)
    // CC: Location code (2 letters or digits)
    // DDD: Branch code (3 letters or digits) - optional
    private static final Pattern SWIFT_PATTERN = Pattern.compile("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    
    @Override
    public void initialize(ValidationConstraints.ValidSwiftCode constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Let @NotNull/@NotEmpty handle null/empty validation
        }
        
        // Convert to uppercase for validation
        String swiftCode = value.toUpperCase().trim();
        
        // Check pattern
        if (!SWIFT_PATTERN.matcher(swiftCode).matches()) {
            return false;
        }
        
        // Additional validation rules
        String bankCode = swiftCode.substring(0, 4);
        String countryCode = swiftCode.substring(4, 6);
        String locationCode = swiftCode.substring(6, 8);
        
        // Bank code should be all letters
        if (!bankCode.matches("[A-Z]{4}")) {
            return false;
        }
        
        // Country code should be valid ISO country code (simplified check)
        if (!isValidCountryCode(countryCode)) {
            return false;
        }
        
        // Location code validation
        if (locationCode.equals("00")) {
            return false; // Invalid location code
        }
        
        // If branch code exists, validate it
        if (swiftCode.length() == 11) {
            String branchCode = swiftCode.substring(8, 11);
            // Branch code "XXX" is used for primary office
            // All zeros is invalid
            if (branchCode.equals("000")) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isValidCountryCode(String code) {
        // Simplified country code validation
        // In production, this should check against ISO 3166-1 alpha-2 codes
        return code.matches("[A-Z]{2}") && 
               !code.equals("00") && 
               !code.equals("XX") &&
               !code.equals("ZZ");
    }
}