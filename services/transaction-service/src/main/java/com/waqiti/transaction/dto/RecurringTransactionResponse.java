package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransactionResponse {
    
    private UUID id;
    private String reference;
    private String fromWalletId;
    private String toWalletId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private RecurrencePattern pattern;
    private Integer intervalValue;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextExecutionDate;
    private Integer executedCount;
    private Integer maxExecutions;
    private Boolean isActive;
    private RecurringStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum RecurrencePattern {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }
    
    public enum RecurringStatus {
        ACTIVE,
        PAUSED,
        COMPLETED,
        CANCELLED,
        ERROR
    }
}