package com.waqiti.payment.core.model;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Map;
import java.util.UUID;

/**
 * Recurring payment data model for subscription and scheduled payments
 * Industrial-grade implementation for production recurring payment processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"metadata"})
public class RecurringPaymentData {
    
    @NotNull
    private UUID recurringId;
    
    @NotNull
    private RecurrenceType recurrenceType;
    
    @NotNull
    private RecurrenceInterval interval;
    
    @Min(1)
    private int intervalCount;
    
    @NotNull
    private LocalDateTime startDate;
    
    private LocalDateTime endDate;
    
    @Min(0)
    private Integer maxOccurrences;
    
    @Min(0)
    @Builder.Default
    private int currentOccurrence = 0;
    
    @NotNull
    private BigDecimal amount;
    
    @NotNull
    private String currency;
    
    private BigDecimal trialAmount;
    
    private Period trialPeriod;
    
    @Builder.Default
    private boolean autoRenew = true;
    
    @Builder.Default
    private RetryStrategy retryStrategy = RetryStrategy.STANDARD;
    
    private int maxRetryAttempts;
    
    private Period gracePeriod;
    
    @Builder.Default
    private RecurringStatus status = RecurringStatus.ACTIVE;
    
    private LocalDateTime nextChargeDate;
    
    private LocalDateTime lastChargeDate;
    
    private BigDecimal totalChargedAmount;
    
    @Min(0)
    @Builder.Default
    private int failedAttempts = 0;
    
    private String cancellationReason;
    
    private Map<String, Object> metadata;
    
    public enum RecurrenceType {
        SUBSCRIPTION,       // Regular subscription
        INSTALLMENT,       // Fixed installment plan
        MEMBERSHIP,        // Membership fees
        UTILITY,          // Utility bills
        LOAN_PAYMENT,     // Loan repayments
        RENT,            // Rent payments
        INSURANCE,       // Insurance premiums
        CUSTOM          // Custom recurrence
    }
    
    public enum RecurrenceInterval {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        BIMONTHLY,
        QUARTERLY,
        SEMIANNUALLY,
        ANNUALLY,
        CUSTOM
    }
    
    public enum RetryStrategy {
        IMMEDIATE,       // Retry immediately
        STANDARD,       // Standard retry with backoff
        AGGRESSIVE,     // More frequent retries
        CONSERVATIVE,   // Less frequent retries
        CUSTOM         // Custom retry logic
    }
    
    public enum RecurringStatus {
        PENDING,        // Not yet started
        ACTIVE,         // Currently active
        TRIAL,          // In trial period
        PAST_DUE,       // Payment past due
        SUSPENDED,      // Temporarily suspended
        CANCELLED,      // Cancelled by user
        EXPIRED,        // Expired naturally
        COMPLETED,      // All installments completed
        FAILED          // Failed permanently
    }
    
    // Business logic methods
    public boolean isActive() {
        return status == RecurringStatus.ACTIVE || 
               status == RecurringStatus.TRIAL;
    }
    
    public boolean shouldCharge() {
        return isActive() && 
               (nextChargeDate == null || LocalDateTime.now().isAfter(nextChargeDate));
    }
    
    public boolean hasReachedMaxOccurrences() {
        return maxOccurrences != null && currentOccurrence >= maxOccurrences;
    }
    
    public boolean hasExpired() {
        return endDate != null && LocalDateTime.now().isAfter(endDate);
    }
    
    public LocalDateTime calculateNextChargeDate() {
        if (lastChargeDate == null) {
            return startDate;
        }
        
        return switch (interval) {
            case DAILY -> lastChargeDate.plusDays(intervalCount);
            case WEEKLY -> lastChargeDate.plusWeeks(intervalCount);
            case BIWEEKLY -> lastChargeDate.plusWeeks(2 * intervalCount);
            case MONTHLY -> lastChargeDate.plusMonths(intervalCount);
            case BIMONTHLY -> lastChargeDate.plusMonths(2 * intervalCount);
            case QUARTERLY -> lastChargeDate.plusMonths(3 * intervalCount);
            case SEMIANNUALLY -> lastChargeDate.plusMonths(6 * intervalCount);
            case ANNUALLY -> lastChargeDate.plusYears(intervalCount);
            default -> lastChargeDate.plusDays(intervalCount);
        };
    }
    
    public BigDecimal getChargeAmount() {
        if (status == RecurringStatus.TRIAL && trialAmount != null) {
            return trialAmount;
        }
        return amount;
    }
}