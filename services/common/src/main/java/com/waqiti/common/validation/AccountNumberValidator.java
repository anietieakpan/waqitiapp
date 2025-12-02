package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class AccountNumberValidator implements ConstraintValidator<ValidationConstraints.ValidAccountNumber, String> {
    private int minLength;
    private int maxLength;
    
    @Override
    public void initialize(ValidationConstraints.ValidAccountNumber annotation) {
        this.minLength = annotation.minLength();
        this.maxLength = annotation.maxLength();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        String cleaned = value.replaceAll("[^0-9]", "");
        return cleaned.length() >= minLength && cleaned.length() <= maxLength && cleaned.matches("^[0-9]+$");
    }
}