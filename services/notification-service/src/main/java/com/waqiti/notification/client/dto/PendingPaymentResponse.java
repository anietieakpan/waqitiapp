package com.waqiti.notification.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingPaymentResponse {
    private UUID userId;
    private UUID paymentId;
    private String paymentType;
    private BigDecimal amount;
    private String currency;
    private String recipientName;
    private String recipientAccount;
    private LocalDateTime dueDate;
    private LocalDateTime scheduledDate;
    private String status;
    private boolean isRecurring;
    private String recurringFrequency;
    private Integer recurringCount;
    private String description;
    private boolean reminderSent;
    private LocalDateTime lastReminderDate;
}