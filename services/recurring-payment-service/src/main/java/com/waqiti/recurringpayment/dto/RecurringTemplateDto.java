package com.waqiti.recurringpayment.dto;

import com.waqiti.recurringpayment.domain.FailureAction;
import com.waqiti.recurringpayment.domain.MonthlyPattern;
import com.waqiti.recurringpayment.domain.RecurringFrequency;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

@Data
@Builder
public class RecurringTemplateDto {
    private String id;
    private String name;
    private String description;
    private String category;
    private String defaultRecipientId;
    private BigDecimal defaultAmount;
    private String currency;
    private RecurringFrequency frequency;
    private Integer dayOfMonth;
    private DayOfWeek dayOfWeek;
    private MonthlyPattern monthlyPattern;
    private boolean reminderEnabled;
    private Set<Integer> reminderDays;
    private boolean autoRetry;
    private Integer maxRetryAttempts;
    private String paymentMethod;
    private FailureAction failureAction;
    private Set<String> tags;
    private Integer usageCount;
    private Instant lastUsedAt;
    private boolean favorite;
    private Instant createdAt;
    private Instant updatedAt;
}