package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

/**
 * Production-ready minimum age validator
 */
@Component
public class MinimumAgeValidator implements ConstraintValidator<ValidationConstraints.MinimumAge, Object> {
    
    private int minimumAge;
    
    @Override
    public void initialize(ValidationConstraints.MinimumAge annotation) {
        this.minimumAge = annotation.value();
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }
        
        LocalDate birthDate = convertToLocalDate(value);
        if (birthDate == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid date format").addConstraintViolation();
            return false;
        }
        
        // Check if date is not in the future
        if (birthDate.isAfter(LocalDate.now())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Birth date cannot be in the future").addConstraintViolation();
            return false;
        }
        
        // Check if date is not too far in the past (e.g., over 150 years)
        if (birthDate.isBefore(LocalDate.now().minusYears(150))) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid birth date").addConstraintViolation();
            return false;
        }
        
        // Calculate age
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        
        if (age < minimumAge) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Must be at least %d years old", minimumAge)
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private LocalDate convertToLocalDate(Object date) {
        if (date instanceof LocalDate) {
            return (LocalDate) date;
        } else if (date instanceof Date) {
            return ((Date) date).toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        } else if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        } else if (date instanceof String) {
            try {
                return LocalDate.parse((String) date);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}