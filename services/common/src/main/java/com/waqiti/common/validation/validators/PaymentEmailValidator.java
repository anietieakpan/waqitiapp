package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidPaymentEmail;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Enhanced email validator for payment systems
 */
public class PaymentEmailValidator implements ConstraintValidator<ValidPaymentEmail, String> {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
        "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    private boolean checkDomain;
    private String[] blacklistedDomains;
    
    @Override
    public void initialize(ValidPaymentEmail constraintAnnotation) {
        this.checkDomain = constraintAnnotation.checkDomain();
        this.blacklistedDomains = constraintAnnotation.blacklistedDomains();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        String email = value.toLowerCase().trim();
        
        // Basic format validation
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return false;
        }
        
        // Check domain if enabled
        if (checkDomain) {
            String domain = email.substring(email.lastIndexOf('@') + 1);
            
            // Check blacklisted domains
            for (String blacklisted : blacklistedDomains) {
                if (domain.equalsIgnoreCase(blacklisted)) {
                    return false;
                }
            }
            
            // Check for common disposable email domains
            if (isDisposableEmail(domain)) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isDisposableEmail(String domain) {
        // Simple check for common disposable domains
        return domain.equalsIgnoreCase("tempmail.com") ||
               domain.equalsIgnoreCase("throwaway.email") ||
               domain.equalsIgnoreCase("guerrillamail.com") ||
               domain.equalsIgnoreCase("mailinator.com") ||
               domain.equalsIgnoreCase("10minutemail.com");
    }
}