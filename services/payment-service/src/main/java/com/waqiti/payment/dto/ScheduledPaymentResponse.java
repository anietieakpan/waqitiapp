/**
 * Scheduled Payment Response DTO
 */
package com.waqiti.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.waqiti.payment.entity.ScheduledPayment.ScheduleType;
import com.waqiti.payment.entity.ScheduledPayment.RecurrencePattern;
import com.waqiti.payment.entity.ScheduledPayment.ScheduledPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledPaymentResponse {
    
    private UUID id;
    private UUID userId;
    private UUID recipientId;
    private String recipientType;
    private String recipientName;
    private String recipientAccount;
    
    // Payment Details
    private BigDecimal amount;
    private String currency;
    private String description;
    private String paymentMethod;
    private String paymentMethodId;
    
    // Schedule Configuration
    private ScheduleType scheduleType;
    private RecurrencePattern recurrencePattern;
    private Integer recurrenceInterval;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextPaymentDate;
    private LocalTime preferredTime;
    
    // Execution Details
    private Integer totalPayments;
    private Integer completedPayments;
    private Integer failedPayments;
    private LocalDateTime lastPaymentDate;
    private String lastPaymentStatus;
    private UUID lastPaymentId;
    
    // Notification Settings
    private Boolean sendReminder;
    private Integer reminderDaysBefore;
    private Boolean notifyOnSuccess;
    private Boolean notifyOnFailure;
    
    // Status
    private ScheduledPaymentStatus status;
    private String pauseReason;
    private String cancellationReason;
    
    // Metadata
    private JsonNode metadata;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime pausedAt;
    private LocalDateTime cancelledAt;
    
    // Computed fields
    private Boolean isActive;
    private Boolean isDue;
    private Integer remainingPayments;
    private LocalDate nextReminderDate;
}