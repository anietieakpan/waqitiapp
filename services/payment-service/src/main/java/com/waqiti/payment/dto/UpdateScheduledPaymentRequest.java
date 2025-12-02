/**
 * Update Scheduled Payment Request DTO
 */
package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScheduledPaymentRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
    
    @Size(max = 500, message = "Description too long")
    private String description;
    
    @Future(message = "End date must be in the future")
    private LocalDate endDate;
    
    private LocalTime preferredTime;
    
    private Boolean sendReminder;
    
    @Min(value = 0, message = "Reminder days must be non-negative")
    @Max(value = 30, message = "Reminder days too far in advance")
    private Integer reminderDaysBefore;
    
    private Boolean notifyOnSuccess;
    
    private Boolean notifyOnFailure;
}