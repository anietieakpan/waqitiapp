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
public class PaymentReminderData {
    private UUID paymentId;
    private String paymentReference;
    private BigDecimal amount;
    private String currency;
    private String formattedAmount;
    private String recipientName;
    private LocalDateTime dueDate;
    private Integer daysUntilDue;
    private String urgencyLevel;
    private String category;
    private String description;
    private String actionUrl;
    private boolean autoPayEnabled;
    private String paymentMethod;
}