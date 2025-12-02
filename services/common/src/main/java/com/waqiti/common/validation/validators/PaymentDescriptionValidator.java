package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidPaymentDescription;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for payment descriptions
 */
public class PaymentDescriptionValidator implements ConstraintValidator<ValidPaymentDescription, String> {
    
    private int maxLength;
    private boolean checkProfanity;
    private boolean checkSuspiciousPatterns;
    
    @Override
    public void initialize(ValidPaymentDescription constraintAnnotation) {
        this.maxLength = constraintAnnotation.maxLength();
        this.checkProfanity = constraintAnnotation.checkProfanity();
        this.checkSuspiciousPatterns = constraintAnnotation.checkSuspiciousPatterns();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        String trimmed = value.trim();
        
        // Check length
        if (trimmed.length() > maxLength) {
            return false;
        }
        
        // Check for prohibited content
        if (checkSuspiciousPatterns && containsProhibitedContent(trimmed)) {
            return false;
        }
        
        // Check for profanity (basic implementation)
        if (checkProfanity && containsProfanity(trimmed)) {
            return false;
        }
        
        return true;
    }
    
    private boolean containsProhibitedContent(String value) {
        // Check for SQL injection patterns
        String lowerValue = value.toLowerCase();
        return lowerValue.contains("drop table") ||
               lowerValue.contains("delete from") ||
               lowerValue.contains("insert into") ||
               lowerValue.contains("<script>") ||
               lowerValue.contains("javascript:");
    }
    
    private boolean containsProfanity(String value) {
        // Basic profanity check - in production this would use a comprehensive dictionary
        String lowerValue = value.toLowerCase();
        String[] basicBadWords = {"spam", "scam", "fraud", "illegal", "drugs"};
        
        for (String badWord : basicBadWords) {
            if (lowerValue.contains(badWord)) {
                return true;
            }
        }
        
        return false;
    }
}