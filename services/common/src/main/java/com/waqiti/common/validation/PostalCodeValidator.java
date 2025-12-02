package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class PostalCodeValidator implements ConstraintValidator<ValidationConstraints.ValidPostalCode, String> {
    private String country;
    
    @Override
    public void initialize(ValidationConstraints.ValidPostalCode annotation) {
        this.country = annotation.country();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        
        switch (country.toUpperCase()) {
            case "US":
                return value.matches("^\\d{5}(-\\d{4})?$");
            case "CA":
                return value.matches("^[A-Za-z]\\d[A-Za-z] ?\\d[A-Za-z]\\d$");
            case "UK":
            case "GB":
                return value.matches("^[A-Za-z]{1,2}\\d[A-Za-z\\d]? ?\\d[A-Za-z]{2}$");
            default:
                return value.matches("^[A-Za-z0-9\\s-]{3,10}$");
        }
    }
}