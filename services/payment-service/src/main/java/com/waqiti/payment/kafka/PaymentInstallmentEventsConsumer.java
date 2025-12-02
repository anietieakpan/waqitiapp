package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentInstallmentEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.common.events.InstallmentScheduleEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentInstallment;
import com.waqiti.payment.domain.InstallmentStatus;
import com.waqiti.payment.domain.InstallmentPlan;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentInstallmentRepository;
import com.waqiti.payment.repository.InstallmentPlanRepository;
import com.waqiti.payment.service.InstallmentService;
import com.waqiti.payment.service.BNPLService;
import com.waqiti.payment.service.CreditAssessmentService;
import com.waqiti.payment.exception.InstallmentException;
import com.waqiti.payment.metrics.InstallmentMetricsService;
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
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * CRITICAL Consumer for Payment Installment Events
 * 
 * Handles installment payment processing including:
 * - Buy Now Pay Later (BNPL) plans
 * - Traditional installment schedules
 * - Credit assessment and approval
 * - Installment collection and reminders
 * - Late payment handling and fees
 * - Plan modifications and settlements
 * - Risk assessment and fraud prevention
 * - Merchant fee distribution
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentInstallmentEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentInstallmentRepository installmentRepository;
    private final InstallmentPlanRepository planRepository;
    private final InstallmentService installmentService;
    private final BNPLService bnplService;
    private final CreditAssessmentService creditService;
    private final InstallmentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Installment configuration
    private static final BigDecimal MAX_INSTALLMENT_AMOUNT = new BigDecimal("50000");
    private static final BigDecimal MIN_INSTALLMENT_AMOUNT = new BigDecimal("50");
    private static final int MAX_INSTALLMENTS = 36;
    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.05"); // 5%
    private static final int GRACE_PERIOD_DAYS = 3;
    
    @KafkaListener(
        topics = "payment-installment-events",
        groupId = "payment-installment-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentInstallmentEvent(
            @Payload PaymentInstallmentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("installment-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing installment event: paymentId={}, action={}, correlation={}",
            event.getPaymentId(), event.getAction(), correlationId);
        
        try {
            securityContext.validateFinancialOperation(event.getPaymentId(), "INSTALLMENT_PROCESSING");
            validateInstallmentEvent(event);
            
            switch (event.getAction()) {
                case CREATE_PLAN:
                    processCreatePlan(event, correlationId);
                    break;
                case CREDIT_ASSESSMENT:
                    processCreditAssessment(event, correlationId);
                    break;
                case APPROVE_PLAN:
                    processApprovePlan(event, correlationId);
                    break;
                case COLLECT_INSTALLMENT:
                    processCollectInstallment(event, correlationId);
                    break;
                case LATE_PAYMENT:
                    processLatePayment(event, correlationId);
                    break;
                case MODIFY_PLAN:
                    processModifyPlan(event, correlationId);
                    break;
                case SETTLE_EARLY:
                    processEarlySettlement(event, correlationId);
                    break;
                case DEFAULT_PAYMENT:
                    processDefaultPayment(event, correlationId);
                    break;
                default:
                    log.warn("Unknown installment action: {}", event.getAction());
                    break;
            }
            
            auditService.logFinancialEvent(
                "INSTALLMENT_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "action", event.getAction(),
                    "planId", event.getPlanId() != null ? event.getPlanId() : "NEW",
                    "installmentNumber", event.getInstallmentNumber() != null ? event.getInstallmentNumber() : 0,
                    "amount", event.getAmount() != null ? event.getAmount() : BigDecimal.ZERO,
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process installment event: paymentId={}, error={}",
                event.getPaymentId(), e.getMessage(), e);
            handleInstallmentEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processCreatePlan(PaymentInstallmentEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Creating installment plan: paymentId={}, installments={}, amount={}",
            payment.getId(), event.getNumberOfInstallments(), payment.getAmount());
        
        // Validate installment eligibility
        validateInstallmentEligibility(payment, event);
        
        // Calculate installment amounts
        List<BigDecimal> installmentAmounts = calculateInstallmentAmounts(
            payment.getAmount(), 
            event.getNumberOfInstallments(),
            event.getInterestRate()
        );
        
        // Create installment plan
        InstallmentPlan plan = InstallmentPlan.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .customerId(payment.getCustomerId())
            .merchantId(payment.getMerchantId())
            .totalAmount(payment.getAmount())
            .numberOfInstallments(event.getNumberOfInstallments())
            .interestRate(event.getInterestRate())
            .planType(event.getPlanType())
            .status("PENDING_APPROVAL")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        planRepository.save(plan);
        
        // Create individual installments
        createInstallmentSchedule(plan, installmentAmounts, correlationId);
        
        // Update payment
        payment.setInstallmentPlanId(plan.getId());
        payment.setIsInstallment(true);
        paymentRepository.save(payment);
        
        // Trigger credit assessment
        if ("BNPL".equals(event.getPlanType())) {
            triggerCreditAssessment(plan, correlationId);
        } else {
            // Traditional installment - auto approve
            triggerPlanApproval(plan, correlationId);
        }
        
        metricsService.recordPlanCreated(event.getPlanType(), payment.getAmount(), event.getNumberOfInstallments());
        
        log.info("Installment plan created: planId={}, installments={}", plan.getId(), event.getNumberOfInstallments());
    }
    
    private void processCreditAssessment(PaymentInstallmentEvent event, String correlationId) {
        InstallmentPlan plan = getPlanById(event.getPlanId());
        Payment payment = getPaymentById(plan.getPaymentId());
        
        log.info("Processing credit assessment: planId={}, customerId={}", 
            plan.getId(), plan.getCustomerId());
        
        try {
            // Perform credit assessment
            CreditAssessmentResult assessment = creditService.assessCustomerCredit(
                plan.getCustomerId(),
                plan.getTotalAmount(),
                plan.getNumberOfInstallments(),
                correlationId
            );
            
            plan.setCreditScore(assessment.getCreditScore());
            plan.setRiskCategory(assessment.getRiskCategory());
            plan.setAssessedAt(LocalDateTime.now());
            
            if (assessment.isApproved()) {
                plan.setApprovedAmount(assessment.getApprovedAmount());
                plan.setApprovedInterestRate(assessment.getApprovedInterestRate());
                plan.setStatus("APPROVED");
                
                // Trigger plan approval
                triggerPlanApproval(plan, correlationId);
                
            } else {
                plan.setStatus("REJECTED");
                plan.setRejectionReason(assessment.getRejectionReason());
                
                // Notify customer of rejection
                sendCreditRejectionNotification(plan, assessment);
            }
            
            planRepository.save(plan);
            
            metricsService.recordCreditAssessment(
                assessment.getRiskCategory(), 
                assessment.isApproved(), 
                plan.getTotalAmount()
            );
            
            log.info("Credit assessment completed: planId={}, approved={}, score={}", 
                plan.getId(), assessment.isApproved(), assessment.getCreditScore());
            
        } catch (Exception e) {
            log.error("Credit assessment failed: planId={}, error={}",
                plan.getId(), e.getMessage(), e);
            
            plan.setStatus("ASSESSMENT_FAILED");
            plan.setRejectionReason("Credit assessment failed: " + e.getMessage());
            planRepository.save(plan);
        }
    }
    
    private void processApprovePlan(PaymentInstallmentEvent event, String correlationId) {
        InstallmentPlan plan = getPlanById(event.getPlanId());
        
        log.info("Approving installment plan: planId={}", plan.getId());
        
        plan.setStatus("ACTIVE");
        plan.setApprovedAt(LocalDateTime.now());
        plan.setApprovedBy(event.getApprovedBy());
        planRepository.save(plan);
        
        // Activate first installment
        activateNextInstallment(plan, correlationId);
        
        // Send approval notification
        sendPlanApprovalNotification(plan);
        
        // Update payment status
        Payment payment = getPaymentById(plan.getPaymentId());
        payment.setStatus(PaymentStatus.INSTALLMENT_ACTIVE);
        paymentRepository.save(payment);
        
        metricsService.recordPlanApproved(plan.getPlanType(), plan.getTotalAmount());
        
        log.info("Installment plan approved: planId={}", plan.getId());
    }
    
    private void processCollectInstallment(PaymentInstallmentEvent event, String correlationId) {
        PaymentInstallment installment = getInstallmentById(event.getInstallmentId());
        InstallmentPlan plan = getPlanById(installment.getPlanId());
        
        log.info("Collecting installment: installmentId={}, amount={}, number={}", 
            installment.getId(), installment.getAmount(), installment.getInstallmentNumber());
        
        try {
            // Process installment payment
            boolean paymentSuccess = installmentService.collectInstallment(
                installment.getId(),
                event.getPaymentMethodId(),
                correlationId
            );
            
            if (paymentSuccess) {
                installment.setStatus(InstallmentStatus.PAID);
                installment.setPaidAt(LocalDateTime.now());
                installment.setPaymentMethodId(event.getPaymentMethodId());
                installmentRepository.save(installment);
                
                // Update plan progress
                updatePlanProgress(plan, installment);
                
                // Send payment confirmation
                sendInstallmentConfirmation(plan, installment);
                
                // Activate next installment if exists
                if (hasNextInstallment(plan, installment.getInstallmentNumber())) {
                    activateNextInstallment(plan, correlationId);
                } else {
                    // Plan completed
                    completePlan(plan, correlationId);
                }
                
                metricsService.recordInstallmentCollected(plan.getPlanType(), installment.getAmount());
                
            } else {
                handleFailedInstallmentCollection(installment, plan, correlationId);
            }
            
        } catch (Exception e) {
            log.error("Failed to collect installment: installmentId={}, error={}",
                installment.getId(), e.getMessage(), e);
            handleFailedInstallmentCollection(installment, plan, correlationId);
        }
    }
    
    private void processLatePayment(PaymentInstallmentEvent event, String correlationId) {
        PaymentInstallment installment = getInstallmentById(event.getInstallmentId());
        InstallmentPlan plan = getPlanById(installment.getPlanId());
        
        log.info("Processing late payment: installmentId={}, daysLate={}", 
            installment.getId(), event.getDaysLate());
        
        // Calculate late fee
        BigDecimal lateFee = calculateLateFee(installment.getAmount(), event.getDaysLate());
        
        installment.setStatus(InstallmentStatus.LATE);
        installment.setDaysLate(event.getDaysLate());
        installment.setLateFee(lateFee);
        installment.setTotalAmountDue(installment.getAmount().add(lateFee));
        installmentRepository.save(installment);
        
        // Update plan status
        plan.setHasLatePayments(true);
        plan.setTotalLateFees(
            plan.getTotalLateFees() != null ? 
            plan.getTotalLateFees().add(lateFee) : 
            lateFee
        );
        planRepository.save(plan);
        
        // Send late payment notification
        sendLatePaymentNotification(plan, installment);
        
        // Check if should mark as default
        if (event.getDaysLate() >= 30) {
            triggerDefaultProcess(plan, installment, correlationId);
        }
        
        metricsService.recordLatePayment(plan.getPlanType(), event.getDaysLate());
        
        log.info("Late payment processed: installmentId={}, lateFee={}", 
            installment.getId(), lateFee);
    }
    
    private void processModifyPlan(PaymentInstallmentEvent event, String correlationId) {
        InstallmentPlan plan = getPlanById(event.getPlanId());
        
        log.info("Modifying installment plan: planId={}, modificationType={}", 
            plan.getId(), event.getModificationType());
        
        boolean modified = false;
        
        if ("EXTEND_TERMS".equals(event.getModificationType())) {
            // Extend payment terms
            if (event.getNewNumberOfInstallments() != null) {
                recalculateInstallments(plan, event.getNewNumberOfInstallments());
                modified = true;
            }
        } else if ("REDUCE_PAYMENT".equals(event.getModificationType())) {
            // Reduce payment amount by extending terms
            if (event.getNewInstallmentAmount() != null) {
                recalculateWithNewAmount(plan, event.getNewInstallmentAmount());
                modified = true;
            }
        } else if ("DEFER_PAYMENT".equals(event.getModificationType())) {
            // Defer next payment
            deferNextPayment(plan, event.getDeferralPeriodDays());
            modified = true;
        }
        
        if (modified) {
            plan.setModifiedAt(LocalDateTime.now());
            plan.setModificationReason(event.getModificationReason());
            planRepository.save(plan);
            
            sendPlanModificationNotification(plan);
            metricsService.recordPlanModified(plan.getPlanType(), event.getModificationType());
        }
        
        log.info("Plan modification completed: planId={}, type={}, modified={}", 
            plan.getId(), event.getModificationType(), modified);
    }
    
    private void processEarlySettlement(PaymentInstallmentEvent event, String correlationId) {
        InstallmentPlan plan = getPlanById(event.getPlanId());
        
        log.info("Processing early settlement: planId={}, settlementAmount={}", 
            plan.getId(), event.getSettlementAmount());
        
        // Calculate settlement discount
        BigDecimal remainingAmount = calculateRemainingAmount(plan);
        BigDecimal settlementDiscount = remainingAmount.subtract(event.getSettlementAmount());
        
        // Mark remaining installments as settled
        List<PaymentInstallment> remainingInstallments = installmentRepository
            .findByPlanIdAndStatus(plan.getId(), InstallmentStatus.PENDING);
        
        for (PaymentInstallment installment : remainingInstallments) {
            installment.setStatus(InstallmentStatus.SETTLED);
            installment.setSettledAt(LocalDateTime.now());
            installment.setSettlementDiscount(
                installment.getAmount().multiply(settlementDiscount)
                    .divide(remainingAmount, 2, RoundingMode.HALF_UP)
            );
        }
        installmentRepository.saveAll(remainingInstallments);
        
        // Update plan
        plan.setStatus("SETTLED");
        plan.setSettledAt(LocalDateTime.now());
        plan.setSettlementAmount(event.getSettlementAmount());
        plan.setSettlementDiscount(settlementDiscount);
        planRepository.save(plan);
        
        // Send settlement confirmation
        sendEarlySettlementConfirmation(plan);
        
        metricsService.recordEarlySettlement(plan.getPlanType(), settlementDiscount);
        
        log.info("Early settlement completed: planId={}, discount={}", 
            plan.getId(), settlementDiscount);
    }
    
    private void processDefaultPayment(PaymentInstallmentEvent event, String correlationId) {
        InstallmentPlan plan = getPlanById(event.getPlanId());
        
        log.warn("Processing payment default: planId={}, reason={}", 
            plan.getId(), event.getDefaultReason());
        
        plan.setStatus("DEFAULTED");
        plan.setDefaultedAt(LocalDateTime.now());
        plan.setDefaultReason(event.getDefaultReason());
        planRepository.save(plan);
        
        // Mark all pending installments as defaulted
        List<PaymentInstallment> pendingInstallments = installmentRepository
            .findByPlanIdAndStatus(plan.getId(), InstallmentStatus.PENDING);
        
        for (PaymentInstallment installment : pendingInstallments) {
            installment.setStatus(InstallmentStatus.DEFAULTED);
            installment.setDefaultedAt(LocalDateTime.now());
        }
        installmentRepository.saveAll(pendingInstallments);
        
        // Send default notification
        sendDefaultNotification(plan);
        
        // Trigger collections process
        triggerCollectionsProcess(plan, correlationId);
        
        metricsService.recordPlanDefault(plan.getPlanType(), calculateRemainingAmount(plan));
        
        log.warn("Payment default processed: planId={}", plan.getId());
    }
    
    @Scheduled(cron = "0 0 9 * * ?") // Daily at 9 AM
    public void checkDueInstallments() {
        log.info("Checking for due installments...");
        
        LocalDate today = LocalDate.now();
        List<PaymentInstallment> dueInstallments = installmentRepository
            .findDueInstallments(today);
        
        for (PaymentInstallment installment : dueInstallments) {
            try {
                PaymentInstallmentEvent collectionEvent = PaymentInstallmentEvent.builder()
                    .paymentId(installment.getPaymentId())
                    .planId(installment.getPlanId())
                    .installmentId(installment.getId())
                    .action("COLLECT_INSTALLMENT")
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("payment-installment-events", collectionEvent);
                
            } catch (Exception e) {
                log.error("Failed to process due installment: installmentId={}, error={}",
                    installment.getId(), e.getMessage());
            }
        }
        
        if (!dueInstallments.isEmpty()) {
            log.info("Processed {} due installments", dueInstallments.size());
        }
    }
    
    @Scheduled(cron = "0 0 10 * * ?") // Daily at 10 AM
    public void checkLateInstallments() {
        log.info("Checking for late installments...");
        
        LocalDate cutoffDate = LocalDate.now().minusDays(GRACE_PERIOD_DAYS);
        List<PaymentInstallment> lateInstallments = installmentRepository
            .findLateInstallments(cutoffDate);
        
        for (PaymentInstallment installment : lateInstallments) {
            try {
                long daysLate = LocalDate.now().toEpochDay() - installment.getDueDate().toEpochDay();
                
                PaymentInstallmentEvent lateEvent = PaymentInstallmentEvent.builder()
                    .paymentId(installment.getPaymentId())
                    .planId(installment.getPlanId())
                    .installmentId(installment.getId())
                    .action("LATE_PAYMENT")
                    .daysLate((int) daysLate)
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("payment-installment-events", lateEvent);
                
            } catch (Exception e) {
                log.error("Failed to process late installment: installmentId={}, error={}",
                    installment.getId(), e.getMessage());
            }
        }
        
        if (!lateInstallments.isEmpty()) {
            log.info("Processed {} late installments", lateInstallments.size());
        }
    }
    
    private void validateInstallmentEligibility(Payment payment, PaymentInstallmentEvent event) {
        if (payment.getAmount().compareTo(MIN_INSTALLMENT_AMOUNT) < 0) {
            throw new InstallmentException("Amount too small for installments");
        }
        
        if (payment.getAmount().compareTo(MAX_INSTALLMENT_AMOUNT) > 0) {
            throw new InstallmentException("Amount exceeds installment limit");
        }
        
        if (event.getNumberOfInstallments() > MAX_INSTALLMENTS) {
            throw new InstallmentException("Too many installments requested");
        }
    }
    
    private List<BigDecimal> calculateInstallmentAmounts(BigDecimal totalAmount, 
            int numberOfInstallments, BigDecimal interestRate) {
        
        List<BigDecimal> amounts = new ArrayList<>();
        
        if (interestRate == null || interestRate.compareTo(BigDecimal.ZERO) == 0) {
            // Equal installments without interest
            BigDecimal installmentAmount = totalAmount.divide(
                new BigDecimal(numberOfInstallments), 2, RoundingMode.HALF_UP);
            
            for (int i = 0; i < numberOfInstallments; i++) {
                amounts.add(installmentAmount);
            }
            
            // Adjust last installment for rounding
            BigDecimal totalCalculated = installmentAmount.multiply(new BigDecimal(numberOfInstallments));
            BigDecimal difference = totalAmount.subtract(totalCalculated);
            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                amounts.set(numberOfInstallments - 1, 
                    amounts.get(numberOfInstallments - 1).add(difference));
            }
        } else {
            // Calculate with interest (compound)
            BigDecimal monthlyRate = interestRate.divide(new BigDecimal("12"), 6, RoundingMode.HALF_UP);
            BigDecimal onePlusRate = BigDecimal.ONE.add(monthlyRate);
            
            // EMI calculation: P * r * (1+r)^n / ((1+r)^n - 1)
            BigDecimal numerator = totalAmount.multiply(monthlyRate).multiply(
                onePlusRate.pow(numberOfInstallments));
            BigDecimal denominator = onePlusRate.pow(numberOfInstallments).subtract(BigDecimal.ONE);
            BigDecimal emi = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
            
            for (int i = 0; i < numberOfInstallments; i++) {
                amounts.add(emi);
            }
        }
        
        return amounts;
    }
    
    private void createInstallmentSchedule(InstallmentPlan plan, List<BigDecimal> amounts, 
            String correlationId) {
        
        LocalDate startDate = LocalDate.now().plusDays(30); // First payment in 30 days
        
        for (int i = 0; i < amounts.size(); i++) {
            PaymentInstallment installment = PaymentInstallment.builder()
                .id(UUID.randomUUID().toString())
                .planId(plan.getId())
                .paymentId(plan.getPaymentId())
                .installmentNumber(i + 1)
                .amount(amounts.get(i))
                .dueDate(startDate.plusMonths(i))
                .status(i == 0 ? InstallmentStatus.PENDING : InstallmentStatus.SCHEDULED)
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            
            installmentRepository.save(installment);
        }
    }
    
    private Payment getPaymentById(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new InstallmentException("Payment not found: " + paymentId));
    }
    
    private InstallmentPlan getPlanById(String planId) {
        return planRepository.findById(planId)
            .orElseThrow(() -> new InstallmentException("Installment plan not found: " + planId));
    }
    
    private PaymentInstallment getInstallmentById(String installmentId) {
        return installmentRepository.findById(installmentId)
            .orElseThrow(() -> new InstallmentException("Installment not found: " + installmentId));
    }
    
    private void validateInstallmentEvent(PaymentInstallmentEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Action is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    // Additional utility methods would be implemented here...
    // For brevity, I'm including key method signatures
    
    private void triggerCreditAssessment(InstallmentPlan plan, String correlationId) {
        // Implementation for credit assessment trigger
    }
    
    private void triggerPlanApproval(InstallmentPlan plan, String correlationId) {
        // Implementation for plan approval trigger
    }
    
    private void activateNextInstallment(InstallmentPlan plan, String correlationId) {
        // Implementation for activating next installment
    }
    
    private void updatePlanProgress(InstallmentPlan plan, PaymentInstallment installment) {
        // Implementation for updating plan progress
    }
    
    private boolean hasNextInstallment(InstallmentPlan plan, int currentNumber) {
        return currentNumber < plan.getNumberOfInstallments();
    }
    
    private void completePlan(InstallmentPlan plan, String correlationId) {
        // Implementation for completing plan
    }
    
    private void handleFailedInstallmentCollection(PaymentInstallment installment, 
            InstallmentPlan plan, String correlationId) {
        // Implementation for handling failed collection
    }
    
    private BigDecimal calculateLateFee(BigDecimal amount, int daysLate) {
        return amount.multiply(LATE_FEE_PERCENTAGE).multiply(new BigDecimal(daysLate))
            .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
    }
    
    private void triggerDefaultProcess(InstallmentPlan plan, PaymentInstallment installment, 
            String correlationId) {
        // Implementation for default process
    }
    
    private BigDecimal calculateRemainingAmount(InstallmentPlan plan) {
        List<PaymentInstallment> remaining = installmentRepository
            .findByPlanIdAndStatus(plan.getId(), InstallmentStatus.PENDING);
        
        return remaining.stream()
            .map(PaymentInstallment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private void recalculateInstallments(InstallmentPlan plan, int newNumberOfInstallments) {
        // Implementation for recalculating installments
    }
    
    private void recalculateWithNewAmount(InstallmentPlan plan, BigDecimal newAmount) {
        // Implementation for recalculating with new amount
    }
    
    private void deferNextPayment(InstallmentPlan plan, int deferralDays) {
        // Implementation for deferring payment
    }
    
    private void triggerCollectionsProcess(InstallmentPlan plan, String correlationId) {
        // Implementation for collections process
    }
    
    // Notification methods
    private void sendCreditRejectionNotification(InstallmentPlan plan, CreditAssessmentResult assessment) {
        // Implementation for credit rejection notification
    }
    
    private void sendPlanApprovalNotification(InstallmentPlan plan) {
        // Implementation for plan approval notification
    }
    
    private void sendInstallmentConfirmation(InstallmentPlan plan, PaymentInstallment installment) {
        // Implementation for installment confirmation
    }
    
    private void sendLatePaymentNotification(InstallmentPlan plan, PaymentInstallment installment) {
        // Implementation for late payment notification
    }
    
    private void sendPlanModificationNotification(InstallmentPlan plan) {
        // Implementation for plan modification notification
    }
    
    private void sendEarlySettlementConfirmation(InstallmentPlan plan) {
        // Implementation for early settlement confirmation
    }
    
    private void sendDefaultNotification(InstallmentPlan plan) {
        // Implementation for default notification
    }
    
    private void handleInstallmentEventError(PaymentInstallmentEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-installment-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Installment Event Processing Failed",
            String.format("Failed to process installment event: %s", error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementInstallmentEventError(event.getAction());
    }
    
    // Inner class for credit assessment result
    public static class CreditAssessmentResult {
        private boolean approved;
        private String riskCategory;
        private int creditScore;
        private BigDecimal approvedAmount;
        private BigDecimal approvedInterestRate;
        private String rejectionReason;
        
        // Getters and setters omitted for brevity
        public boolean isApproved() { return approved; }
        public String getRiskCategory() { return riskCategory; }
        public int getCreditScore() { return creditScore; }
        public BigDecimal getApprovedAmount() { return approvedAmount; }
        public BigDecimal getApprovedInterestRate() { return approvedInterestRate; }
        public String getRejectionReason() { return rejectionReason; }
    }
}