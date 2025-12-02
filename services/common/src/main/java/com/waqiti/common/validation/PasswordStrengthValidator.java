package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordStrengthValidator implements ConstraintValidator<ValidationConstraints.StrongPassword, String> {
    
    private int minLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigit;
    private boolean requireSpecial;
    
    @Override
    public void initialize(ValidationConstraints.StrongPassword annotation) {
        this.minLength = annotation.minLength();
        this.requireUppercase = annotation.requireUppercase();
        this.requireLowercase = annotation.requireLowercase();
        this.requireDigit = annotation.requireDigit();
        this.requireSpecial = annotation.requireSpecial();
    }
    
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) return false;
        
        if (password.length() < minLength) return false;
        if (requireUppercase && !Pattern.compile("[A-Z]").matcher(password).find()) return false;
        if (requireLowercase && !Pattern.compile("[a-z]").matcher(password).find()) return false;
        if (requireDigit && !Pattern.compile("[0-9]").matcher(password).find()) return false;
        if (requireSpecial && !Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':/.?]").matcher(password).find()) return false;
        
        return true;
    }
}