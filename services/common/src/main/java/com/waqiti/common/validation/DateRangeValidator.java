package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Production-ready date range validator
 */
@Slf4j
@Component
public class DateRangeValidator implements ConstraintValidator<ValidationConstraints.ValidDateRange, Object> {
    
    private String startDateField;
    private String endDateField;
    private int maxDays;
    
    @Override
    public void initialize(ValidationConstraints.ValidDateRange constraintAnnotation) {
        this.startDateField = constraintAnnotation.startDateField();
        this.endDateField = constraintAnnotation.endDateField();
        this.maxDays = constraintAnnotation.maxDays();
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return true;
        
        try {
            Field startField = value.getClass().getDeclaredField(startDateField);
            Field endField = value.getClass().getDeclaredField(endDateField);
            
            startField.setAccessible(true);
            endField.setAccessible(true);
            
            Object startDate = startField.get(value);
            Object endDate = endField.get(value);
            
            if (startDate == null || endDate == null) return true;
            
            LocalDateTime start = convertToLocalDateTime(startDate);
            LocalDateTime end = convertToLocalDateTime(endDate);
            
            if (start == null || end == null) return false;
            
            // End date must be after start date
            if (!end.isAfter(start)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("End date must be after start date").addConstraintViolation();
                return false;
            }
            
            // Check maximum date range
            long daysBetween = ChronoUnit.DAYS.between(start, end);
            if (daysBetween > maxDays) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    String.format("Date range cannot exceed %d days", maxDays)
                ).addConstraintViolation();
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating date range", e);
            return false;
        }
    }
    
    private LocalDateTime convertToLocalDateTime(Object date) {
        if (date instanceof LocalDateTime) {
            return (LocalDateTime) date;
        } else if (date instanceof LocalDate) {
            return ((LocalDate) date).atStartOfDay();
        } else if (date instanceof java.util.Date) {
            return LocalDateTime.ofInstant(((java.util.Date) date).toInstant(), 
                java.time.ZoneId.systemDefault());
        }
        return null;
    }
}