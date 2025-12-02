package com.waqiti.recurringpayment.dto;

import com.waqiti.recurringpayment.domain.FailureAction;
import com.waqiti.recurringpayment.domain.MonthlyPattern;
import com.waqiti.recurringpayment.domain.RecurringFrequency;
import com.waqiti.recurringpayment.domain.RecurringStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

@Data
@Builder
public class RecurringPaymentDto {
    private String id;
    private String recipientId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private RecurringFrequency frequency;
    private Instant startDate;
    private Instant endDate;
    private Instant nextExecutionDate;
    private Integer maxOccurrences;
    private Integer dayOfMonth;
    private DayOfWeek dayOfWeek;
    private MonthlyPattern monthlyPattern;
    private RecurringStatus status;
    private Integer totalExecutions;
    private Integer successfulExecutions;
    private Integer failedExecutions;
    private BigDecimal totalAmountPaid;
    private Integer consecutiveFailures;
    private boolean reminderEnabled;
    private Set<Integer> reminderDays;
    private boolean autoRetry;
    private Integer maxRetryAttempts;
    private String paymentMethod;
    private FailureAction failureAction;
    private Set<String> tags;
    private Instant lastExecutionDate;
    private Instant lastFailureDate;
    private String lastFailureReason;
    private Instant pausedAt;
    private Instant cancelledAt;
    private Instant completedAt;
    private String cancellationReason;
    private Instant createdAt;
    private Instant updatedAt;
    private double successRate;
}