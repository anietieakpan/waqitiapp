package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Subscription Entity
 * Manages recurring billing subscriptions
 */
@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Subscription extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    // Pricing
    @Column(name = "price", precision = 19, scale = 4, nullable = false)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false)
    private BillingInterval billingInterval;

    @Column(name = "billing_interval_count")
    private Integer billingIntervalCount = 1;

    // Dates
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "current_period_start")
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDate currentPeriodEnd;

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "resumed_at")
    private LocalDateTime resumedAt;

    // Trial
    @Column(name = "trial_start")
    private LocalDate trialStart;

    @Column(name = "trial_end")
    private LocalDate trialEnd;

    @Column(name = "trial_days")
    private Integer trialDays;

    // Discount
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discount_amount", precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "discount_end_date")
    private LocalDate discountEndDate;

    @Column(name = "promo_code")
    private String promoCode;

    // Usage limits
    @Column(name = "usage_limit", columnDefinition = "JSON")
    private String usageLimit;

    @Column(name = "current_usage", columnDefinition = "JSON")
    private String currentUsage;

    // Payment
    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @Column(name = "auto_renew")
    private Boolean autoRenew = true;

    @Column(name = "payment_retry_count")
    private Integer paymentRetryCount = 0;

    @Column(name = "last_payment_attempt")
    private LocalDateTime lastPaymentAttempt;

    // Features
    @ElementCollection
    @CollectionTable(name = "subscription_features", joinColumns = @JoinColumn(name = "subscription_id"))
    @MapKeyColumn(name = "feature_name")
    @Column(name = "feature_value")
    private Map<String, String> features = new HashMap<>();

    // Metadata
    @ElementCollection
    @CollectionTable(name = "subscription_metadata", joinColumns = @JoinColumn(name = "subscription_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata = new HashMap<>();

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancellation_feedback", columnDefinition = "TEXT")
    private String cancellationFeedback;

    @Column(name = "referral_source")
    private String referralSource;

    @Column(name = "acquisition_channel")
    private String acquisitionChannel;

    /**
     * Subscription status
     */
    public enum SubscriptionStatus {
        PENDING,               // Not yet started
        TRIALING,              // In trial period
        ACTIVE,                // Active and billing
        PAST_DUE,              // Payment overdue
        PAUSED,                // Temporarily paused
        CANCELLED,             // Cancelled but active until end of period
        EXPIRED,               // Subscription ended
        SUSPENDED              // Suspended due to non-payment
    }

    /**
     * Billing intervals
     */
    public enum BillingInterval {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        SEMI_ANNUAL,
        ANNUAL
    }

    // Business methods
    
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE || 
               status == SubscriptionStatus.TRIALING;
    }
    
    public boolean isInTrial() {
        return status == SubscriptionStatus.TRIALING && 
               trialEnd != null && 
               LocalDate.now().isBefore(trialEnd);
    }
    
    public boolean canCharge() {
        return status == SubscriptionStatus.ACTIVE && 
               autoRenew && 
               paymentMethodId != null;
    }
    
    public BigDecimal getEffectivePrice() {
        if (isInTrial()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal effectivePrice = price;
        
        // Apply discount if active
        if (discountEndDate == null || LocalDate.now().isBefore(discountEndDate)) {
            if (discountAmount != null) {
                effectivePrice = effectivePrice.subtract(discountAmount);
            } else if (discountPercentage != null) {
                BigDecimal discountValue = effectivePrice.multiply(discountPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                effectivePrice = effectivePrice.subtract(discountValue);
            }
        }
        
        return effectivePrice.max(BigDecimal.ZERO);
    }
    
    public void advanceBillingPeriod() {
        if (currentPeriodEnd != null) {
            currentPeriodStart = currentPeriodEnd.plusDays(1);
            currentPeriodEnd = calculateNextPeriodEnd(currentPeriodStart);
            nextBillingDate = currentPeriodEnd.plusDays(1);
        }
    }
    
    private LocalDate calculateNextPeriodEnd(LocalDate startDate) {
        int intervalCount = billingIntervalCount != null ? billingIntervalCount : 1;
        
        return switch (billingInterval) {
            case DAILY -> startDate.plusDays(intervalCount - 1);
            case WEEKLY -> startDate.plusWeeks(intervalCount).minusDays(1);
            case MONTHLY -> startDate.plusMonths(intervalCount).minusDays(1);
            case QUARTERLY -> startDate.plusMonths(3 * intervalCount).minusDays(1);
            case SEMI_ANNUAL -> startDate.plusMonths(6 * intervalCount).minusDays(1);
            case ANNUAL -> startDate.plusYears(intervalCount).minusDays(1);
        };
    }
}