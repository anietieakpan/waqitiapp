package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidSwiftCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for SWIFT/BIC codes
 */
public class SwiftCodeValidator implements ConstraintValidator<ValidSwiftCode, String> {
    
    @Override
    public void initialize(ValidSwiftCode constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // SWIFT code format: 8 or 11 characters
        // Format: AAAA BB CC (DDD)
        // AAAA: Bank code (4 letters)
        // BB: Country code (2 letters)
        // CC: Location code (2 alphanumeric)
        // DDD: Optional branch code (3 alphanumeric)
        return value.matches("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    }
}