package com.waqiti.recurringpayment.dto;

import com.waqiti.recurringpayment.domain.FailureAction;
import com.waqiti.recurringpayment.domain.MonthlyPattern;
import com.waqiti.recurringpayment.domain.RecurringFrequency;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Set;

@Data
@Builder
public class CreateTemplateRequest {
    
    @NotBlank(message = "Template name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @Size(max = 50, message = "Category cannot exceed 50 characters")
    private String category;
    
    private String defaultRecipientId;
    
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 4, message = "Invalid amount format")
    private BigDecimal defaultAmount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    private String currency;
    
    @NotNull(message = "Frequency is required")
    private RecurringFrequency frequency;
    
    @Min(value = 1, message = "Day of month must be between 1 and 31")
    @Max(value = 31, message = "Day of month must be between 1 and 31")
    private Integer dayOfMonth;
    
    private DayOfWeek dayOfWeek;
    
    private MonthlyPattern monthlyPattern;
    
    private boolean reminderEnabled = false;
    
    private Set<@Min(0) @Max(30) Integer> reminderDays;
    
    private boolean autoRetry = true;
    
    @Min(value = 1, message = "Max retry attempts must be at least 1")
    @Max(value = 10, message = "Max retry attempts cannot exceed 10")
    private Integer maxRetryAttempts = 3;
    
    private String paymentMethod;
    
    private FailureAction failureAction = FailureAction.CONTINUE;
    
    private Set<@Size(max = 50) String> tags;
}