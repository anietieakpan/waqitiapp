package com.waqiti.billingorchestrator.dto.response;

import com.waqiti.billingorchestrator.entity.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Subscription
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Subscription response with complete details")
public class SubscriptionResponse {

    @Schema(description = "Subscription UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Customer UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc")
    private UUID customerId;

    @Schema(description = "Account UUID", example = "456e7890-e89b-12d3-a456-426614174222")
    private UUID accountId;

    @Schema(description = "Plan UUID", example = "789e0123-e89b-12d3-a456-426614174444")
    private UUID planId;

    @Schema(description = "Plan name", example = "Premium Plan")
    private String planName;

    @Schema(description = "Subscription status", example = "ACTIVE")
    private Subscription.SubscriptionStatus status;

    @Schema(description = "Price", example = "29.99")
    private BigDecimal price;

    @Schema(description = "Currency", example = "USD")
    private String currency;

    @Schema(description = "Billing interval", example = "MONTHLY")
    private Subscription.BillingInterval billingInterval;

    @Schema(description = "Billing interval count", example = "1")
    private Integer billingIntervalCount;

    @Schema(description = "Start date", example = "2025-01-01")
    private LocalDate startDate;

    @Schema(description = "Current period start", example = "2025-02-01")
    private LocalDate currentPeriodStart;

    @Schema(description = "Current period end", example = "2025-02-28")
    private LocalDate currentPeriodEnd;

    @Schema(description = "Next billing date", example = "2025-03-01")
    private LocalDate nextBillingDate;

    @Schema(description = "End date (if set)", example = "2026-01-01")
    private LocalDate endDate;

    @Schema(description = "Cancelled timestamp", example = null)
    private LocalDateTime cancelledAt;

    @Schema(description = "Paused timestamp", example = null)
    private LocalDateTime pausedAt;

    @Schema(description = "Trial start date", example = "2025-01-01")
    private LocalDate trialStart;

    @Schema(description = "Trial end date", example = "2025-01-15")
    private LocalDate trialEnd;

    @Schema(description = "Trial days", example = "14")
    private Integer trialDays;

    @Schema(description = "Discount percentage", example = "10.00")
    private BigDecimal discountPercentage;

    @Schema(description = "Discount amount", example = "5.00")
    private BigDecimal discountAmount;

    @Schema(description = "Discount end date", example = "2025-03-31")
    private LocalDate discountEndDate;

    @Schema(description = "Auto-renew enabled", example = "true")
    private Boolean autoRenew;

    @Schema(description = "Cancellation reason", example = null)
    private String cancellationReason;

    @Schema(description = "Payment method UUID", example = "321e9876-e89b-12d3-a456-426614174666")
    private UUID paymentMethodId;

    @Schema(description = "Days until next billing", example = "15")
    private Integer daysUntilNextBilling;

    @Schema(description = "Is in trial period", example = "false")
    private Boolean isInTrial;

    @Schema(description = "Trial days remaining", example = "0")
    private Integer trialDaysRemaining;

    @Schema(description = "Effective price (after discounts)", example = "26.99")
    private BigDecimal effectivePrice;

    @Schema(description = "Created timestamp", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Updated timestamp", example = "2025-02-01T00:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "Management URL", example = "https://billing.example.com/subscriptions/sub_123")
    private String managementUrl;
}
