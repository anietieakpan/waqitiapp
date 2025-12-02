/**
 * Create Scheduled Payment Request DTO
 */
package com.waqiti.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.waqiti.payment.entity.ScheduledPayment.ScheduleType;
import com.waqiti.payment.entity.ScheduledPayment.RecurrencePattern;
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
public class CreateScheduledPaymentRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Recipient ID is required")
    private UUID recipientId;
    
    @NotBlank(message = "Recipient type is required")
    @Pattern(regexp = "USER|MERCHANT|BILL_PAYEE", message = "Invalid recipient type")
    private String recipientType;
    
    @NotBlank(message = "Recipient name is required")
    @Size(max = 255)
    private String recipientName;
    
    @Size(max = 100)
    private String recipientAccount;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO code")
    private String currency;
    
    @Size(max = 500, message = "Description too long")
    private String description;
    
    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "WALLET|BANK_ACCOUNT|CARD", message = "Invalid payment method")
    private String paymentMethod;
    
    private String paymentMethodId;
    
    @NotNull(message = "Schedule type is required")
    private ScheduleType scheduleType;
    
    @NotNull(message = "Recurrence pattern is required")
    private RecurrencePattern recurrencePattern;
    
    @Min(value = 1, message = "Recurrence interval must be positive")
    private Integer recurrenceInterval;
    
    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date cannot be in the past")
    private LocalDate startDate;
    
    @Future(message = "End date must be in the future")
    private LocalDate endDate;
    
    private LocalTime preferredTime;
    
    @Min(value = 1, message = "Total payments must be positive")
    private Integer totalPayments;
    
    private Boolean sendReminder;
    
    @Min(value = 0, message = "Reminder days must be non-negative")
    @Max(value = 30, message = "Reminder days too far in advance")
    private Integer reminderDaysBefore;
    
    private Boolean notifyOnSuccess;
    
    private Boolean notifyOnFailure;
    
    private JsonNode metadata;
    
    @AssertTrue(message = "Recurring payments must have either end date or total payments")
    private boolean isValidRecurringPayment() {
        if (scheduleType == ScheduleType.RECURRING) {
            return endDate != null || totalPayments != null;
        }
        return true;
    }
    
    @AssertTrue(message = "End date must be after start date")
    private boolean isEndDateValid() {
        if (endDate != null && startDate != null) {
            return endDate.isAfter(startDate);
        }
        return true;
    }
}