package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class UsernameValidator implements ConstraintValidator<ValidationConstraints.ValidUsername, String> {
    private int minLength;
    private int maxLength;
    private boolean allowSpecialChars;
    
    @Override
    public void initialize(ValidationConstraints.ValidUsername annotation) {
        this.minLength = annotation.minLength();
        this.maxLength = annotation.maxLength();
        this.allowSpecialChars = annotation.allowSpecialChars();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (value.length() < minLength || value.length() > maxLength) return false;
        String pattern = allowSpecialChars ? "^[a-zA-Z0-9._-]+$" : "^[a-zA-Z0-9]+$";
        return value.matches(pattern);
    }
}