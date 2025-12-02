package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidScheduledDate;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Validator for scheduled payment dates
 */
public class ScheduledDateValidator implements ConstraintValidator<ValidScheduledDate, LocalDateTime> {
    
    private int minDaysInFuture;
    private int maxDaysInFuture;
    
    @Override
    public void initialize(ValidScheduledDate constraintAnnotation) {
        this.minDaysInFuture = constraintAnnotation.minDaysInFuture();
        this.maxDaysInFuture = constraintAnnotation.maxDaysInFuture();
    }
    
    @Override
    public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Check if date is in the future
        if (value.isBefore(now)) {
            return false;
        }
        
        // Check minimum days in future
        if (ChronoUnit.DAYS.between(now, value) < minDaysInFuture) {
            return false;
        }
        
        // Check maximum days in future
        if (ChronoUnit.DAYS.between(now, value) > maxDaysInFuture) {
            return false;
        }
        
        return true;
    }
}