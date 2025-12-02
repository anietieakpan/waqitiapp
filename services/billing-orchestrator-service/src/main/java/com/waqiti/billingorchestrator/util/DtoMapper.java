package com.waqiti.billingorchestrator.util;

import com.waqiti.billingorchestrator.dto.response.BillingCycleResponse;
import com.waqiti.billingorchestrator.dto.response.InvoiceResponse;
import com.waqiti.billingorchestrator.dto.response.SubscriptionResponse;
import com.waqiti.billingorchestrator.entity.BillingCycle;
import com.waqiti.billingorchestrator.entity.Subscription;
import lombok.experimental.UtilityClass;

/**
 * DTO Mapper Utility
 *
 * Provides static methods for mapping between entities and DTOs
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@UtilityClass
public class DtoMapper {

    /**
     * Map BillingCycle entity to BillingCycleResponse DTO
     */
    public BillingCycleResponse toBillingCycleResponse(BillingCycle cycle) {
        if (cycle == null) {
            return null;
        }

        return BillingCycleResponse.builder()
                .id(cycle.getId())
                .customerId(cycle.getCustomerId())
                .accountId(cycle.getAccountId())
                .status(cycle.getStatus().name())
                .cycleStartDate(cycle.getCycleStartDate())
                .cycleEndDate(cycle.getCycleEndDate())
                .dueDate(cycle.getDueDate())
                .gracePeriodEndDate(cycle.getGracePeriodEndDate())
                .totalAmount(cycle.getTotalAmount())
                .subscriptionCharges(cycle.getSubscriptionCharges())
                .usageCharges(cycle.getUsageCharges())
                .oneTimeCharges(cycle.getOneTimeCharges())
                .taxAmount(cycle.getTaxAmount())
                .discountAmount(cycle.getDiscountAmount())
                .adjustmentAmount(cycle.getAdjustmentAmount())
                .paidAmount(cycle.getPaidAmount())
                .balanceDue(cycle.getBalanceDue())
                .currency(cycle.getCurrency())
                .invoiceNumber(cycle.getInvoiceNumber())
                .invoiceGenerated(cycle.isInvoiceGenerated())
                .invoiceSent(cycle.isInvoiceSent())
                .billingFrequency(cycle.getBillingFrequency() != null ? cycle.getBillingFrequency().name() : null)
                .customerType(cycle.getCustomerType() != null ? cycle.getCustomerType().name() : null)
                .autoPayEnabled(cycle.isAutoPayEnabled())
                .dunningLevel(cycle.getDunningLevel())
                .createdAt(cycle.getCreatedAt())
                .updatedAt(cycle.getUpdatedAt())
                .build();
    }

    /**
     * Map BillingCycle entity to InvoiceResponse DTO
     */
    public InvoiceResponse toInvoiceResponse(BillingCycle cycle) {
        if (cycle == null) {
            return null;
        }

        return InvoiceResponse.builder()
                .invoiceId(cycle.getInvoiceId())
                .invoiceNumber(cycle.getInvoiceNumber())
                .customerId(cycle.getCustomerId())
                .accountId(cycle.getAccountId())
                .status(cycle.getStatus().name())
                .totalAmount(cycle.getTotalAmount())
                .paidAmount(cycle.getPaidAmount())
                .balanceDue(cycle.getBalanceDue())
                .currency(cycle.getCurrency())
                .dueDate(cycle.getDueDate())
                .invoiceGeneratedDate(cycle.getInvoiceGeneratedDate())
                .invoiceSentDate(cycle.getInvoiceSentDate())
                .paidDate(cycle.getPaidDate())
                .build();
    }

    /**
     * Map Subscription entity to SubscriptionResponse DTO
     */
    public SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        if (subscription == null) {
            return null;
        }

        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .customerId(subscription.getCustomerId())
                .accountId(subscription.getAccountId())
                .planId(subscription.getPlanId())
                .planName(subscription.getPlanName())
                .status(subscription.getStatus().name())
                .price(subscription.getPrice())
                .currency(subscription.getCurrency())
                .billingInterval(subscription.getBillingInterval().name())
                .billingIntervalCount(subscription.getBillingIntervalCount())
                .startDate(subscription.getStartDate())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .nextBillingDate(subscription.getNextBillingDate())
                .endDate(subscription.getEndDate())
                .trialStart(subscription.getTrialStart())
                .trialEnd(subscription.getTrialEnd())
                .trialDays(subscription.getTrialDays())
                .discountPercentage(subscription.getDiscountPercentage())
                .discountAmount(subscription.getDiscountAmount())
                .discountEndDate(subscription.getDiscountEndDate())
                .promoCode(subscription.getPromoCode())
                .paymentMethodId(subscription.getPaymentMethodId())
                .autoRenew(subscription.getAutoRenew())
                .cancelledAt(subscription.getCancelledAt())
                .pausedAt(subscription.getPausedAt())
                .resumedAt(subscription.getResumedAt())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    /**
     * Calculate days overdue for a billing cycle
     */
    public Integer calculateDaysOverdue(BillingCycle cycle) {
        if (cycle == null || cycle.getDueDate() == null) {
            return null;
        }

        if (cycle.getStatus() == BillingCycle.CycleStatus.PAID ||
                cycle.getStatus() == BillingCycle.CycleStatus.WRITTEN_OFF) {
            return 0;
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        if (today.isAfter(cycle.getDueDate())) {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(cycle.getDueDate(), today);
        }

        return 0;
    }

    /**
     * Check if billing cycle is past due
     */
    public Boolean isPastDue(BillingCycle cycle) {
        if (cycle == null || cycle.getDueDate() == null) {
            return false;
        }

        if (cycle.getStatus() == BillingCycle.CycleStatus.PAID ||
                cycle.getStatus() == BillingCycle.CycleStatus.WRITTEN_OFF) {
            return false;
        }

        return java.time.LocalDate.now().isAfter(cycle.getDueDate()) &&
                cycle.getBalanceDue().compareTo(java.math.BigDecimal.ZERO) > 0;
    }

    /**
     * Check if subscription is active
     */
    public Boolean isSubscriptionActive(Subscription subscription) {
        if (subscription == null) {
            return false;
        }

        return subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE ||
                subscription.getStatus() == Subscription.SubscriptionStatus.TRIALING;
    }

    /**
     * Get effective subscription price (after discounts)
     */
    public java.math.BigDecimal getEffectiveSubscriptionPrice(Subscription subscription) {
        if (subscription == null) {
            return java.math.BigDecimal.ZERO;
        }

        return subscription.getEffectivePrice();
    }
}
