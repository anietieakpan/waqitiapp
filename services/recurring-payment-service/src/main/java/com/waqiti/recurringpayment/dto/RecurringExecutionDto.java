package com.waqiti.recurringpayment.dto;

import com.waqiti.recurringpayment.domain.ExecutionStatus;
import com.waqiti.recurringpayment.domain.ExecutionTrigger;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class RecurringExecutionDto {
    private String id;
    private String recurringPaymentId;
    private Instant scheduledDate;
    private Instant executedAt;
    private Instant completedAt;
    private Instant failedAt;
    private BigDecimal amount;
    private String currency;
    private ExecutionStatus status;
    private ExecutionTrigger trigger;
    private String paymentId;
    private String failureReason;
    private String failureCode;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Instant retryAt;
    private Integer nextRetryDelayMinutes;
    private Long processingTimeMs;
    private Integer retryCount;
    private Instant createdAt;
}