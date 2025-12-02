package com.waqiti.recurringpayment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class RecurringPaymentSummaryDto {
    private String id;
    private String description;
    private BigDecimal amount;
    private String currency;
    private String recipientName;
    private String frequency;
    private String status;
    private Instant nextExecutionDate;
    private Integer totalExecutions;
    private Integer successfulExecutions;
    private BigDecimal totalAmountPaid;
    private double successRate;
    private Instant createdAt;
}