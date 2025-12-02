package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.dto.request.CreateSubscriptionRequest;
import com.waqiti.billingorchestrator.dto.request.UpdateSubscriptionRequest;
import com.waqiti.billingorchestrator.entity.Subscription;
import com.waqiti.billingorchestrator.exception.SubscriptionNotFoundException;
import com.waqiti.billingorchestrator.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing subscriptions
 *
 * Handles subscription lifecycle: creation, updates, cancellation, trial management
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingEventService billingEventService;

    /**
     * Create a new subscription
     */
    @Transactional
    public Subscription createSubscription(CreateSubscriptionRequest request) {
        log.info("Creating subscription for customer: {}, plan: {}", request.getCustomerId(), request.getPlanId());

        // Calculate period dates
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        LocalDate currentPeriodStart = startDate;
        LocalDate currentPeriodEnd = calculatePeriodEnd(startDate, request.getBillingInterval(), request.getBillingIntervalCount());
        LocalDate nextBillingDate = currentPeriodEnd.plusDays(1);

        // Handle trial period
        Subscription.SubscriptionStatus initialStatus = Subscription.SubscriptionStatus.PENDING;
        LocalDate trialStart = null;
        LocalDate trialEnd = null;

        if (request.getTrialDays() != null && request.getTrialDays() > 0) {
            initialStatus = Subscription.SubscriptionStatus.TRIALING;
            trialStart = startDate;
            trialEnd = startDate.plusDays(request.getTrialDays());
            nextBillingDate = trialEnd.plusDays(1);
        } else if (startDate.equals(LocalDate.now()) || startDate.isBefore(LocalDate.now())) {
            initialStatus = Subscription.SubscriptionStatus.ACTIVE;
        }

        Subscription subscription = Subscription.builder()
                .customerId(request.getCustomerId())
                .accountId(request.getAccountId())
                .planId(request.getPlanId())
                .planName(request.getPlanName())
                .status(initialStatus)
                .price(request.getPrice())
                .currency(request.getCurrency())
                .billingInterval(request.getBillingInterval())
                .billingIntervalCount(request.getBillingIntervalCount() != null ? request.getBillingIntervalCount() : 1)
                .startDate(startDate)
                .currentPeriodStart(currentPeriodStart)
                .currentPeriodEnd(currentPeriodEnd)
                .nextBillingDate(nextBillingDate)
                .trialStart(trialStart)
                .trialEnd(trialEnd)
                .trialDays(request.getTrialDays())
                .discountPercentage(request.getDiscountPercentage())
                .discountAmount(request.getDiscountAmount())
                .discountEndDate(request.getDiscountEndDate())
                .promoCode(request.getPromoCode())
                .paymentMethodId(request.getPaymentMethodId())
                .autoRenew(request.getAutoRenew() != null ? request.getAutoRenew() : true)
                .referralSource(request.getReferralSource())
                .acquisitionChannel(request.getAcquisitionChannel())
                .build();

        if (request.getFeatures() != null) {
            subscription.setFeatures(request.getFeatures());
        }

        if (request.getMetadata() != null) {
            subscription.setMetadata(request.getMetadata());
        }

        subscription = subscriptionRepository.save(subscription);

        log.info("Subscription created successfully: {}, status: {}", subscription.getId(), subscription.getStatus());

        return subscription;
    }

    /**
     * Get subscription by ID
     */
    @Transactional(readOnly = true)
    public Subscription getSubscription(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found: " + subscriptionId));
    }

    /**
     * Get subscriptions by customer ID
     */
    @Transactional(readOnly = true)
    public List<Subscription> getSubscriptionsByCustomer(UUID customerId) {
        return subscriptionRepository.findByCustomerId(customerId);
    }

    /**
     * Get subscriptions by customer ID with pagination
     */
    @Transactional(readOnly = true)
    public Page<Subscription> getSubscriptionsByCustomer(UUID customerId, Pageable pageable) {
        return subscriptionRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * Get active subscriptions for customer
     */
    @Transactional(readOnly = true)
    public List<Subscription> getActiveSubscriptionsByCustomer(UUID customerId) {
        return subscriptionRepository.findActiveSubscriptionsByCustomer(customerId);
    }

    /**
     * Get subscriptions due for billing
     */
    @Transactional(readOnly = true)
    public List<Subscription> getSubscriptionsDueForBilling(LocalDate date) {
        return subscriptionRepository.findSubscriptionsDueForBilling(date);
    }

    /**
     * Update subscription
     */
    @Transactional
    public Subscription updateSubscription(UUID subscriptionId, UpdateSubscriptionRequest request) {
        log.info("Updating subscription: {}", subscriptionId);

        Subscription subscription = getSubscription(subscriptionId);

        if (request.getPlanId() != null && !request.getPlanId().equals(subscription.getPlanId())) {
            subscription.setPlanId(request.getPlanId());
            subscription.setPlanName(request.getPlanName());
            log.info("Subscription plan changed: {} -> {}", subscription.getId(), request.getPlanId());
        }

        if (request.getPrice() != null) {
            subscription.setPrice(request.getPrice());
        }

        if (request.getPaymentMethodId() != null) {
            subscription.setPaymentMethodId(request.getPaymentMethodId());
        }

        if (request.getAutoRenew() != null) {
            subscription.setAutoRenew(request.getAutoRenew());
        }

        if (request.getDiscountPercentage() != null) {
            subscription.setDiscountPercentage(request.getDiscountPercentage());
        }

        if (request.getDiscountAmount() != null) {
            subscription.setDiscountAmount(request.getDiscountAmount());
        }

        if (request.getDiscountEndDate() != null) {
            subscription.setDiscountEndDate(request.getDiscountEndDate());
        }

        if (request.getPromoCode() != null) {
            subscription.setPromoCode(request.getPromoCode());
        }

        if (request.getFeatures() != null) {
            subscription.getFeatures().putAll(request.getFeatures());
        }

        if (request.getMetadata() != null) {
            subscription.getMetadata().putAll(request.getMetadata());
        }

        subscription = subscriptionRepository.save(subscription);

        log.info("Subscription updated successfully: {}", subscriptionId);

        return subscription;
    }

    /**
     * Cancel subscription
     */
    @Transactional
    public Subscription cancelSubscription(UUID subscriptionId, String reason, String feedback, boolean immediate) {
        log.info("Cancelling subscription: {}, immediate: {}", subscriptionId, immediate);

        Subscription subscription = getSubscription(subscriptionId);

        if (subscription.getStatus() == Subscription.SubscriptionStatus.CANCELLED ||
                subscription.getStatus() == Subscription.SubscriptionStatus.EXPIRED) {
            log.warn("Subscription already cancelled/expired: {}", subscriptionId);
            return subscription;
        }

        subscription.setCancelledAt(LocalDateTime.now());
        subscription.setCancellationReason(reason);
        subscription.setCancellationFeedback(feedback);

        if (immediate) {
            subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscription.setEndDate(LocalDate.now());
            subscription.setAutoRenew(false);
            log.info("Subscription cancelled immediately: {}", subscriptionId);
        } else {
            // Cancel at end of period
            subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscription.setEndDate(subscription.getCurrentPeriodEnd());
            subscription.setAutoRenew(false);
            log.info("Subscription will be cancelled at period end: {}, end date: {}",
                    subscriptionId, subscription.getEndDate());
        }

        subscription = subscriptionRepository.save(subscription);

        return subscription;
    }

    /**
     * Pause subscription
     */
    @Transactional
    public Subscription pauseSubscription(UUID subscriptionId) {
        log.info("Pausing subscription: {}", subscriptionId);

        Subscription subscription = getSubscription(subscriptionId);

        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Can only pause ACTIVE subscriptions");
        }

        subscription.setStatus(Subscription.SubscriptionStatus.PAUSED);
        subscription.setPausedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        log.info("Subscription paused successfully: {}", subscriptionId);

        return subscription;
    }

    /**
     * Resume subscription
     */
    @Transactional
    public Subscription resumeSubscription(UUID subscriptionId) {
        log.info("Resuming subscription: {}", subscriptionId);

        Subscription subscription = getSubscription(subscriptionId);

        if (subscription.getStatus() != Subscription.SubscriptionStatus.PAUSED) {
            throw new IllegalStateException("Can only resume PAUSED subscriptions");
        }

        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setResumedAt(LocalDateTime.now());

        // Recalculate next billing date
        LocalDate now = LocalDate.now();
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(calculatePeriodEnd(now, subscription.getBillingInterval(), subscription.getBillingIntervalCount()));
        subscription.setNextBillingDate(subscription.getCurrentPeriodEnd().plusDays(1));

        subscription = subscriptionRepository.save(subscription);

        log.info("Subscription resumed successfully: {}", subscriptionId);

        return subscription;
    }

    /**
     * Advance subscription to next billing period
     */
    @Transactional
    public Subscription advanceBillingPeriod(UUID subscriptionId) {
        log.info("Advancing billing period for subscription: {}", subscriptionId);

        Subscription subscription = getSubscription(subscriptionId);

        subscription.advanceBillingPeriod();

        subscription = subscriptionRepository.save(subscription);

        log.info("Billing period advanced: {}, new period: {} to {}",
                subscriptionId, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());

        return subscription;
    }

    /**
     * Mark subscription as past due
     */
    @Transactional
    public Subscription markPastDue(UUID subscriptionId) {
        log.info("Marking subscription as past due: {}", subscriptionId);

        Subscription subscription = getSubscription(subscriptionId);

        if (subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE) {
            subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
            subscription = subscriptionRepository.save(subscription);
            log.info("Subscription marked as past due: {}", subscriptionId);
        }

        return subscription;
    }

    /**
     * Reactivate subscription after payment
     */
    @Transactional
    public Subscription reactivateSubscription(UUID subscriptionId) {
        log.info("Reactivating subscription: {}", subscriptionId);

        Subscription subscription = getSubscription(subscriptionId);

        if (subscription.getStatus() == Subscription.SubscriptionStatus.PAST_DUE ||
                subscription.getStatus() == Subscription.SubscriptionStatus.SUSPENDED) {
            subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            subscription.setPaymentRetryCount(0);
            subscription = subscriptionRepository.save(subscription);
            log.info("Subscription reactivated: {}", subscriptionId);
        }

        return subscription;
    }

    /**
     * Increment payment retry count
     */
    @Transactional
    public Subscription incrementPaymentRetry(UUID subscriptionId) {
        Subscription subscription = getSubscription(subscriptionId);

        int retryCount = subscription.getPaymentRetryCount() != null ? subscription.getPaymentRetryCount() : 0;
        subscription.setPaymentRetryCount(retryCount + 1);
        subscription.setLastPaymentAttempt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        log.info("Payment retry count incremented: {}, count: {}", subscriptionId, subscription.getPaymentRetryCount());

        return subscription;
    }

    /**
     * Check and convert trial subscriptions to active
     */
    @Transactional
    public List<Subscription> convertTrialSubscriptions() {
        LocalDate today = LocalDate.now();
        List<Subscription> trialSubscriptions = subscriptionRepository.findSubscriptionsInTrial(today);

        List<Subscription> converted = trialSubscriptions.stream()
                .filter(sub -> sub.getTrialEnd() != null && today.isAfter(sub.getTrialEnd()))
                .map(sub -> {
                    sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                    log.info("Converting trial subscription to active: {}", sub.getId());
                    return subscriptionRepository.save(sub);
                })
                .toList();

        log.info("Converted {} trial subscriptions to active", converted.size());

        return converted;
    }

    /**
     * Calculate MRR (Monthly Recurring Revenue)
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateMonthlyRecurringRevenue() {
        return subscriptionRepository.calculateMonthlyRecurringRevenue()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Helper: Calculate period end date
     */
    private LocalDate calculatePeriodEnd(LocalDate startDate, Subscription.BillingInterval interval, Integer intervalCount) {
        int count = intervalCount != null ? intervalCount : 1;

        return switch (interval) {
            case DAILY -> startDate.plusDays(count - 1);
            case WEEKLY -> startDate.plusWeeks(count).minusDays(1);
            case MONTHLY -> startDate.plusMonths(count).minusDays(1);
            case QUARTERLY -> startDate.plusMonths(3 * count).minusDays(1);
            case SEMI_ANNUAL -> startDate.plusMonths(6 * count).minusDays(1);
            case ANNUAL -> startDate.plusYears(count).minusDays(1);
        };
    }
}
