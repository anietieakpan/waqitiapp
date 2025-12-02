package com.waqiti.recurringpayment.dto;

import com.waqiti.recurringpayment.domain.RecurringFrequency;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Data
public class UpdateRecurringPaymentRequest {
    
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 4, message = "Invalid amount format")
    private BigDecimal amount;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    private RecurringFrequency frequency;
    
    private Instant endDate;
    
    @Min(value = 1, message = "Maximum occurrences must be at least 1")
    @Max(value = 1000, message = "Maximum occurrences cannot exceed 1000")
    private Integer maxOccurrences;
    
    private Boolean reminderEnabled;
    
    private Set<@Min(0) @Max(30) Integer> reminderDays;
    
    private String paymentMethod;
    
    private Set<@Size(max = 50) String> tags;
}