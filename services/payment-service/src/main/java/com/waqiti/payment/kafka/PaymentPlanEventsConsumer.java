package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentPlanEvent;
import com.waqiti.common.events.SubscriptionBillingEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentPlan;
import com.waqiti.payment.domain.PaymentPlanStatus;
import com.waqiti.payment.domain.BillingCycle;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentPlanRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.SubscriptionService;
import com.waqiti.payment.service.BillingService;
import com.waqiti.payment.service.ProrationService;
import com.waqiti.payment.service.DunningService;
import com.waqiti.payment.exception.PaymentPlanNotFoundException;
import com.waqiti.payment.metrics.SubscriptionMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentPlanEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentPlanRepository paymentPlanRepository;
    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final BillingService billingService;
    private final ProrationService prorationService;
    private final DunningService dunningService;
    private final SubscriptionMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_DUNNING_ATTEMPTS = 4;
    private static final int GRACE_PERIOD_DAYS = 7;
    private static final String[] BILLING_INTERVALS = {"DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"};
    
    @KafkaListener(
        topics = {"payment-plan-events", "subscription-billing-events", "recurring-charge-events"},
        groupId = "payment-plan-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentPlanEvent(
            @Payload PaymentPlanEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("plan-%s-p%d-o%d", 
            event.getPlanId(), partition, offset);
        
        log.info("Processing payment plan event: planId={}, eventType={}, correlation={}",
            event.getPlanId(), event.getEventType(), correlationId);
        
        try {
            securityContext.validateFinancialOperation(event.getPlanId(), "PAYMENT_PLAN_PROCESSING");
            validatePaymentPlanEvent(event);
            
            switch (event.getEventType()) {
                case PLAN_CREATED:
                    processPlanCreated(event, correlationId);
                    break;
                case PLAN_UPDATED:
                    processPlanUpdated(event, correlationId);
                    break;
                case BILLING_CYCLE_START:
                    processBillingCycleStart(event, correlationId);
                    break;
                case PAYMENT_DUE:
                    processPaymentDue(event, correlationId);
                    break;
                case PAYMENT_FAILED:
                    processPaymentFailed(event, correlationId);
                    break;
                case PLAN_CANCELLED:
                    processPlanCancelled(event, correlationId);
                    break;
                case PRORATION_CALCULATED:
                    processProrationCalculated(event, correlationId);
                    break;
                case TRIAL_PERIOD_ENDING:
                    processTrialPeriodEnding(event, correlationId);
                    break;
                case PLAN_UPGRADED:
                    processPlanUpgraded(event, correlationId);
                    break;
                case PLAN_DOWNGRADED:
                    processPlanDowngraded(event, correlationId);
                    break;
                default:
                    log.warn("Unknown payment plan event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logFinancialEvent(
                "PAYMENT_PLAN_EVENT_PROCESSED",
                event.getPlanId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process payment plan event: planId={}, error={}",
                event.getPlanId(), e.getMessage(), e);
            
            handlePaymentPlanEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processPlanCreated(PaymentPlanEvent event, String correlationId) {
        log.info("Processing plan created: planId={}, userId={}, amount={}", 
            event.getPlanId(), event.getUserId(), event.getAmount());
        
        PaymentPlan plan = PaymentPlan.builder()
            .id(event.getPlanId())
            .userId(event.getUserId())
            .planName(event.getPlanName())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .billingInterval(event.getBillingInterval())
            .billingCycleDay(event.getBillingCycleDay())
            .status(PaymentPlanStatus.ACTIVE)
            .trialPeriodDays(event.getTrialPeriodDays())
            .startDate(event.getStartDate())
            .nextBillingDate(calculateNextBillingDate(event))
            .paymentMethodId(event.getPaymentMethodId())
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        paymentPlanRepository.save(plan);
        
        if (event.getTrialPeriodDays() != null && event.getTrialPeriodDays() > 0) {
            subscriptionService.scheduleTrialEndNotification(
                event.getPlanId(), 
                event.getTrialPeriodDays()
            );
        }
        
        scheduleNextBilling(plan);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Subscription Created",
            String.format("Your %s subscription has been created. Next billing: %s",
                event.getPlanName(), plan.getNextBillingDate()),
            event.getPlanId()
        );
        
        metricsService.recordPlanCreated(event.getBillingInterval(), event.getAmount());
        
        log.info("Payment plan created: planId={}, nextBilling={}", 
            plan.getId(), plan.getNextBillingDate());
    }
    
    private void processPlanUpdated(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.info("Processing plan update: planId={}, changes={}", 
            plan.getId(), event.getUpdateFields());
        
        boolean amountChanged = false;
        boolean intervalChanged = false;
        
        if (event.getAmount() != null && !event.getAmount().equals(plan.getAmount())) {
            plan.setPreviousAmount(plan.getAmount());
            plan.setAmount(event.getAmount());
            amountChanged = true;
        }
        
        if (event.getBillingInterval() != null && 
            !event.getBillingInterval().equals(plan.getBillingInterval())) {
            plan.setBillingInterval(event.getBillingInterval());
            intervalChanged = true;
        }
        
        if (event.getPaymentMethodId() != null) {
            plan.setPaymentMethodId(event.getPaymentMethodId());
        }
        
        plan.setUpdatedAt(LocalDateTime.now());
        paymentPlanRepository.save(plan);
        
        if (amountChanged || intervalChanged) {
            BigDecimal prorationAmount = prorationService.calculateProration(
                plan, 
                plan.getPreviousAmount(), 
                plan.getAmount()
            );
            
            if (prorationAmount.compareTo(BigDecimal.ZERO) != 0) {
                processProration(plan, prorationAmount, correlationId);
            }
            
            plan.setNextBillingDate(calculateNextBillingDate(plan));
            paymentPlanRepository.save(plan);
        }
        
        notificationService.sendNotification(
            plan.getUserId(),
            "Subscription Updated",
            String.format("Your %s subscription has been updated.", plan.getPlanName()),
            plan.getId()
        );
        
        metricsService.recordPlanUpdated(plan.getBillingInterval());
    }
    
    private void processBillingCycleStart(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.info("Processing billing cycle start: planId={}, cycleNumber={}", 
            plan.getId(), event.getCycleNumber());
        
        BillingCycle cycle = BillingCycle.builder()
            .id(UUID.randomUUID().toString())
            .planId(plan.getId())
            .cycleNumber(event.getCycleNumber())
            .startDate(LocalDateTime.now())
            .endDate(calculateCycleEndDate(plan))
            .amount(plan.getAmount())
            .currency(plan.getCurrency())
            .status("ACTIVE")
            .build();
        
        billingService.createBillingCycle(cycle);
        
        plan.setCurrentCycleId(cycle.getId());
        plan.setCurrentCycleNumber(event.getCycleNumber());
        paymentPlanRepository.save(plan);
        
        metricsService.recordBillingCycleStarted(plan.getBillingInterval());
    }
    
    private void processPaymentDue(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.info("Processing payment due: planId={}, amount={}", 
            plan.getId(), plan.getAmount());
        
        if (!plan.isActive()) {
            log.warn("Plan is not active, skipping payment: planId={}, status={}", 
                plan.getId(), plan.getStatus());
            return;
        }
        
        try {
            Payment payment = paymentService.createRecurringPayment(
                plan.getUserId(),
                plan.getAmount(),
                plan.getCurrency(),
                plan.getPaymentMethodId(),
                plan.getId(),
                correlationId
            );
            
            plan.setLastPaymentId(payment.getId());
            plan.setLastPaymentAttemptDate(LocalDateTime.now());
            plan.setNextBillingDate(calculateNextBillingDate(plan));
            paymentPlanRepository.save(plan);
            
            metricsService.recordSubscriptionPaymentInitiated(
                plan.getBillingInterval(), 
                plan.getAmount()
            );
            
        } catch (Exception e) {
            log.error("Failed to create recurring payment: planId={}, error={}", 
                plan.getId(), e.getMessage(), e);
            
            handlePaymentCreationFailure(plan, e, correlationId);
        }
    }
    
    private void processPaymentFailed(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.warn("Processing payment failure: planId={}, attempt={}, reason={}", 
            plan.getId(), plan.getFailedPaymentAttempts(), event.getFailureReason());
        
        plan.setFailedPaymentAttempts(plan.getFailedPaymentAttempts() + 1);
        plan.setLastPaymentFailureReason(event.getFailureReason());
        plan.setLastPaymentFailureDate(LocalDateTime.now());
        
        if (plan.getFailedPaymentAttempts() >= MAX_DUNNING_ATTEMPTS) {
            log.error("Max payment failures reached: planId={}, attempts={}", 
                plan.getId(), plan.getFailedPaymentAttempts());
            
            suspendPlan(plan, "Maximum payment failures exceeded", correlationId);
            
            notificationService.sendNotification(
                plan.getUserId(),
                "Subscription Suspended",
                String.format("Your %s subscription has been suspended due to payment failures. " +
                    "Please update your payment method.", plan.getPlanName()),
                plan.getId()
            );
            
        } else {
            plan.setStatus(PaymentPlanStatus.PAST_DUE);
            paymentPlanRepository.save(plan);
            
            dunningService.scheduleDunningAttempt(
                plan.getId(),
                plan.getFailedPaymentAttempts(),
                calculateDunningDelay(plan.getFailedPaymentAttempts())
            );
            
            notificationService.sendNotification(
                plan.getUserId(),
                "Payment Failed",
                String.format("Your payment for %s subscription failed. We'll retry in %d days.",
                    plan.getPlanName(), calculateDunningDelay(plan.getFailedPaymentAttempts())),
                plan.getId()
            );
        }
        
        metricsService.recordSubscriptionPaymentFailed(
            plan.getBillingInterval(), 
            plan.getFailedPaymentAttempts()
        );
    }
    
    private void processPlanCancelled(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.info("Processing plan cancellation: planId={}, reason={}", 
            plan.getId(), event.getCancellationReason());
        
        plan.setStatus(PaymentPlanStatus.CANCELLED);
        plan.setCancellationReason(event.getCancellationReason());
        plan.setCancelledAt(LocalDateTime.now());
        plan.setCancelledBy(event.getCancelledBy());
        
        if (event.isImmediateCancellation()) {
            plan.setEndDate(LocalDateTime.now());
        } else {
            plan.setEndDate(plan.getNextBillingDate());
        }
        
        paymentPlanRepository.save(plan);
        
        billingService.cancelPendingCharges(plan.getId());
        
        subscriptionService.processRefundIfApplicable(plan, event.isImmediateCancellation());
        
        notificationService.sendNotification(
            plan.getUserId(),
            "Subscription Cancelled",
            String.format("Your %s subscription has been cancelled. Access ends: %s",
                plan.getPlanName(), plan.getEndDate()),
            plan.getId()
        );
        
        metricsService.recordPlanCancelled(
            plan.getBillingInterval(), 
            event.getCancellationReason()
        );
    }
    
    private void processProrationCalculated(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.info("Processing proration: planId={}, amount={}", 
            plan.getId(), event.getProrationAmount());
        
        processProration(plan, event.getProrationAmount(), correlationId);
    }
    
    private void processTrialPeriodEnding(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.info("Processing trial period ending: planId={}, daysRemaining={}", 
            plan.getId(), event.getDaysRemaining());
        
        notificationService.sendNotification(
            plan.getUserId(),
            "Trial Period Ending Soon",
            String.format("Your trial for %s ends in %d days. Your first payment of %s %s will be charged on %s.",
                plan.getPlanName(), event.getDaysRemaining(), 
                plan.getAmount(), plan.getCurrency(), plan.getNextBillingDate()),
            plan.getId()
        );
        
        metricsService.recordTrialEnding(plan.getBillingInterval(), event.getDaysRemaining());
    }
    
    private void processPlanUpgraded(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.info("Processing plan upgrade: planId={}, from={} to={}", 
            plan.getId(), plan.getAmount(), event.getNewAmount());
        
        BigDecimal prorationAmount = prorationService.calculateUpgradeProration(
            plan, 
            event.getNewAmount(),
            event.getNewBillingInterval()
        );
        
        plan.setPreviousAmount(plan.getAmount());
        plan.setAmount(event.getNewAmount());
        plan.setPlanName(event.getNewPlanName());
        
        if (event.getNewBillingInterval() != null) {
            plan.setBillingInterval(event.getNewBillingInterval());
            plan.setNextBillingDate(calculateNextBillingDate(plan));
        }
        
        plan.setUpdatedAt(LocalDateTime.now());
        paymentPlanRepository.save(plan);
        
        if (prorationAmount.compareTo(BigDecimal.ZERO) > 0) {
            processProration(plan, prorationAmount, correlationId);
        }
        
        notificationService.sendNotification(
            plan.getUserId(),
            "Subscription Upgraded",
            String.format("Your subscription has been upgraded to %s. Prorated amount: %s %s",
                plan.getPlanName(), prorationAmount, plan.getCurrency()),
            plan.getId()
        );
        
        metricsService.recordPlanUpgraded(plan.getBillingInterval(), event.getNewAmount());
    }
    
    private void processPlanDowngraded(PaymentPlanEvent event, String correlationId) {
        PaymentPlan plan = getPaymentPlan(event.getPlanId());
        
        log.info("Processing plan downgrade: planId={}, from={} to={}", 
            plan.getId(), plan.getAmount(), event.getNewAmount());
        
        plan.setPreviousAmount(plan.getAmount());
        plan.setAmount(event.getNewAmount());
        plan.setPlanName(event.getNewPlanName());
        plan.setDowngradePending(true);
        plan.setDowngradeEffectiveDate(plan.getNextBillingDate());
        plan.setUpdatedAt(LocalDateTime.now());
        paymentPlanRepository.save(plan);
        
        notificationService.sendNotification(
            plan.getUserId(),
            "Subscription Downgrade Scheduled",
            String.format("Your subscription will be downgraded to %s on %s.",
                plan.getPlanName(), plan.getDowngradeEffectiveDate()),
            plan.getId()
        );
        
        metricsService.recordPlanDowngraded(plan.getBillingInterval(), event.getNewAmount());
    }
    
    private void processProration(PaymentPlan plan, BigDecimal prorationAmount, 
            String correlationId) {
        
        if (prorationAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        
        try {
            if (prorationAmount.compareTo(BigDecimal.ZERO) > 0) {
                Payment prorationPayment = paymentService.createProrationPayment(
                    plan.getUserId(),
                    prorationAmount,
                    plan.getCurrency(),
                    plan.getPaymentMethodId(),
                    plan.getId(),
                    "Plan change proration",
                    correlationId
                );
                
                log.info("Proration payment created: planId={}, paymentId={}, amount={}", 
                    plan.getId(), prorationPayment.getId(), prorationAmount);
                
            } else {
                subscriptionService.applyCreditToAccount(
                    plan.getUserId(),
                    prorationAmount.abs(),
                    plan.getCurrency(),
                    "Plan downgrade credit"
                );
                
                log.info("Credit applied for downgrade: planId={}, amount={}", 
                    plan.getId(), prorationAmount.abs());
            }
            
        } catch (Exception e) {
            log.error("Failed to process proration: planId={}, amount={}, error={}", 
                plan.getId(), prorationAmount, e.getMessage(), e);
        }
    }
    
    private void suspendPlan(PaymentPlan plan, String reason, String correlationId) {
        plan.setStatus(PaymentPlanStatus.SUSPENDED);
        plan.setSuspensionReason(reason);
        plan.setSuspendedAt(LocalDateTime.now());
        plan.setGracePeriodEndDate(LocalDateTime.now().plusDays(GRACE_PERIOD_DAYS));
        paymentPlanRepository.save(plan);
        
        subscriptionService.scheduleAutoCancellation(
            plan.getId(), 
            GRACE_PERIOD_DAYS
        );
        
        metricsService.recordPlanSuspended(plan.getBillingInterval(), reason);
    }
    
    private void handlePaymentCreationFailure(PaymentPlan plan, Exception error, 
            String correlationId) {
        
        plan.setFailedPaymentAttempts(plan.getFailedPaymentAttempts() + 1);
        plan.setLastPaymentFailureReason(error.getMessage());
        plan.setLastPaymentFailureDate(LocalDateTime.now());
        paymentPlanRepository.save(plan);
        
        dunningService.scheduleDunningAttempt(
            plan.getId(),
            plan.getFailedPaymentAttempts(),
            1
        );
    }
    
    private LocalDateTime calculateNextBillingDate(PaymentPlanEvent event) {
        LocalDateTime startDate = event.getStartDate();
        int trialDays = event.getTrialPeriodDays() != null ? event.getTrialPeriodDays() : 0;
        
        LocalDateTime billingStart = startDate.plusDays(trialDays);
        
        return addBillingInterval(billingStart, event.getBillingInterval());
    }
    
    private LocalDateTime calculateNextBillingDate(PaymentPlan plan) {
        return addBillingInterval(plan.getNextBillingDate(), plan.getBillingInterval());
    }
    
    private LocalDateTime calculateCycleEndDate(PaymentPlan plan) {
        return addBillingInterval(LocalDateTime.now(), plan.getBillingInterval());
    }
    
    private LocalDateTime addBillingInterval(LocalDateTime date, String interval) {
        switch (interval) {
            case "DAILY":
                return date.plusDays(1);
            case "WEEKLY":
                return date.plusWeeks(1);
            case "MONTHLY":
                return date.plusMonths(1);
            case "QUARTERLY":
                return date.plusMonths(3);
            case "YEARLY":
                return date.plusYears(1);
            default:
                return date.plusMonths(1);
        }
    }
    
    private int calculateDunningDelay(int attemptNumber) {
        return switch (attemptNumber) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            default -> 7;
        };
    }
    
    private void scheduleNextBilling(PaymentPlan plan) {
        billingService.scheduleRecurringPayment(
            plan.getId(),
            plan.getNextBillingDate()
        );
    }
    
    private void validatePaymentPlanEvent(PaymentPlanEvent event) {
        if (event.getPlanId() == null || event.getPlanId().trim().isEmpty()) {
            throw new IllegalArgumentException("Plan ID is required");
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private PaymentPlan getPaymentPlan(String planId) {
        return paymentPlanRepository.findById(planId)
            .orElseThrow(() -> new PaymentPlanNotFoundException(
                "Payment plan not found: " + planId));
    }
    
    private void handlePaymentPlanEventError(PaymentPlanEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-plan-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Payment Plan Event Processing Failed",
            String.format("Failed to process payment plan event for plan %s: %s",
                event.getPlanId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementPlanEventError(event.getEventType());
    }
}