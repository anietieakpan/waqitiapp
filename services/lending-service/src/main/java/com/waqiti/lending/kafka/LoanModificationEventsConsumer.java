package com.waqiti.lending.kafka;

import com.waqiti.common.events.LoanModificationEvent;
import com.waqiti.lending.domain.LoanModification;
import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.repository.LoanModificationRepository;
import com.waqiti.lending.repository.LoanRepository;
import com.waqiti.lending.service.LoanModificationService;
import com.waqiti.lending.service.UnderwritingService;
import com.waqiti.lending.metrics.LendingMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoanModificationEventsConsumer {
    
    private final LoanModificationRepository loanModificationRepository;
    private final LoanRepository loanRepository;
    private final LoanModificationService loanModificationService;
    private final UnderwritingService underwritingService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MAX_RATE_REDUCTION = new BigDecimal("5.0");
    private static final int MAX_TERM_EXTENSION_MONTHS = 120;
    
    @KafkaListener(
        topics = {"loan-modification-events", "loan-adjustment-events", "loan-change-events"},
        groupId = "lending-loan-modification-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleLoanModificationEvent(
            @Payload LoanModificationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("loan-mod-%s-p%d-o%d", 
            event.getLoanId(), partition, offset);
        
        log.info("Processing loan modification event: loanId={}, type={}, modificationType={}", 
            event.getLoanId(), event.getEventType(), event.getModificationType());
        
        try {
            switch (event.getEventType()) {
                case MODIFICATION_REQUESTED:
                    processModificationRequested(event, correlationId);
                    break;
                case HARDSHIP_VERIFIED:
                    processHardshipVerified(event, correlationId);
                    break;
                case PAYMENT_REDUCTION_APPROVED:
                    processPaymentReductionApproved(event, correlationId);
                    break;
                case TERM_EXTENSION_APPROVED:
                    processTermExtensionApproved(event, correlationId);
                    break;
                case INTEREST_RATE_REDUCTION_APPROVED:
                    processInterestRateReductionApproved(event, correlationId);
                    break;
                case PRINCIPAL_DEFERMENT_APPROVED:
                    processPrincipalDefermentApproved(event, correlationId);
                    break;
                case MODIFICATION_AGREEMENT_SIGNED:
                    processModificationAgreementSigned(event, correlationId);
                    break;
                case MODIFICATION_ACTIVATED:
                    processModificationActivated(event, correlationId);
                    break;
                case TRIAL_PERIOD_STARTED:
                    processTrialPeriodStarted(event, correlationId);
                    break;
                case TRIAL_PERIOD_COMPLETED:
                    processTrialPeriodCompleted(event, correlationId);
                    break;
                case MODIFICATION_PERMANENT:
                    processModificationPermanent(event, correlationId);
                    break;
                case MODIFICATION_DENIED:
                    processModificationDenied(event, correlationId);
                    break;
                default:
                    log.warn("Unknown loan modification event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "LOAN_MODIFICATION_EVENT_PROCESSED",
                event.getLoanId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "modificationType", event.getModificationType() != null ? event.getModificationType() : "N/A",
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process loan modification event: {}", e.getMessage(), e);
            kafkaTemplate.send("loan-modification-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processModificationRequested(LoanModificationEvent event, String correlationId) {
        log.info("Loan modification requested: loanId={}, modificationType={}, reason={}", 
            event.getLoanId(), event.getModificationType(), event.getModificationReason());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        LoanModification modification = LoanModification.builder()
            .id(UUID.randomUUID().toString())
            .loanId(event.getLoanId())
            .borrowerId(event.getBorrowerId())
            .modificationType(event.getModificationType())
            .modificationReason(event.getModificationReason())
            .requestedAt(LocalDateTime.now())
            .currentMonthlyPayment(loan.getMonthlyPayment())
            .currentInterestRate(loan.getInterestRate())
            .currentRemainingTerm(loan.getRemainingTermMonths())
            .status("REQUESTED")
            .correlationId(correlationId)
            .build();
        
        loanModificationRepository.save(modification);
        
        underwritingService.verifyHardship(modification.getId(), event.getBorrowerId());
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Loan Modification Request Received",
            String.format("Your request for loan modification has been received. Type: %s. " +
                "We're reviewing your hardship documentation and will respond within 5 business days.",
                event.getModificationType()),
            correlationId
        );
        
        metricsService.recordLoanModificationRequested(event.getModificationType());
    }
    
    private void processHardshipVerified(LoanModificationEvent event, String correlationId) {
        log.info("Hardship verified: modificationId={}, hardshipType={}, severity={}", 
            event.getModificationId(), event.getHardshipType(), event.getHardshipSeverity());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        modification.setHardshipVerified(true);
        modification.setHardshipType(event.getHardshipType());
        modification.setHardshipSeverity(event.getHardshipSeverity());
        modification.setHardshipVerifiedAt(LocalDateTime.now());
        modification.setIncomeReduction(event.getIncomeReduction());
        loanModificationRepository.save(modification);
        
        loanModificationService.evaluateModificationOptions(modification.getId());
        
        metricsService.recordHardshipVerified(event.getHardshipType());
    }
    
    private void processPaymentReductionApproved(LoanModificationEvent event, String correlationId) {
        log.info("Payment reduction approved: modificationId={}, oldPayment={}, newPayment={}, reduction={}", 
            event.getModificationId(), event.getOldMonthlyPayment(), 
            event.getNewMonthlyPayment(), event.getPaymentReduction());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        modification.setPaymentReductionApproved(true);
        modification.setNewMonthlyPayment(event.getNewMonthlyPayment());
        modification.setPaymentReduction(event.getPaymentReduction());
        loanModificationRepository.save(modification);
        
        BigDecimal reductionPercent = event.getPaymentReduction()
            .divide(event.getOldMonthlyPayment(), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        notificationService.sendNotification(
            modification.getBorrowerId(),
            "Payment Reduction Approved",
            String.format("Your monthly payment will be reduced from %s to %s (saving %s or %s%%). " +
                "This reduction is temporary and will be reviewed in 6 months.",
                event.getOldMonthlyPayment(), event.getNewMonthlyPayment(), 
                event.getPaymentReduction(), reductionPercent),
            correlationId
        );
        
        metricsService.recordPaymentReductionApproved(event.getPaymentReduction());
    }
    
    private void processTermExtensionApproved(LoanModificationEvent event, String correlationId) {
        log.info("Term extension approved: modificationId={}, oldTerm={}, newTerm={}, extension={} months", 
            event.getModificationId(), event.getOldTermMonths(), 
            event.getNewTermMonths(), event.getTermExtensionMonths());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        if (event.getTermExtensionMonths() > MAX_TERM_EXTENSION_MONTHS) {
            log.error("Term extension exceeds maximum: extension={}, max={}", 
                event.getTermExtensionMonths(), MAX_TERM_EXTENSION_MONTHS);
            return;
        }
        
        modification.setTermExtensionApproved(true);
        modification.setNewTermMonths(event.getNewTermMonths());
        modification.setTermExtensionMonths(event.getTermExtensionMonths());
        loanModificationRepository.save(modification);
        
        notificationService.sendNotification(
            modification.getBorrowerId(),
            "Loan Term Extended",
            String.format("Your loan term has been extended from %d to %d months (additional %d months). " +
                "This reduces your monthly payment but increases total interest paid over the life of the loan.",
                event.getOldTermMonths(), event.getNewTermMonths(), event.getTermExtensionMonths()),
            correlationId
        );
        
        metricsService.recordTermExtensionApproved(event.getTermExtensionMonths());
    }
    
    private void processInterestRateReductionApproved(LoanModificationEvent event, String correlationId) {
        log.info("Interest rate reduction approved: modificationId={}, oldRate={}, newRate={}, reduction={}", 
            event.getModificationId(), event.getOldInterestRate(), 
            event.getNewInterestRate(), event.getRateReduction());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        if (event.getRateReduction().compareTo(MAX_RATE_REDUCTION) > 0) {
            log.warn("Rate reduction exceeds typical maximum: reduction={}%, max={}%", 
                event.getRateReduction(), MAX_RATE_REDUCTION);
        }
        
        modification.setRateReductionApproved(true);
        modification.setNewInterestRate(event.getNewInterestRate());
        modification.setRateReduction(event.getRateReduction());
        loanModificationRepository.save(modification);
        
        notificationService.sendNotification(
            modification.getBorrowerId(),
            "Interest Rate Reduced",
            String.format("Your interest rate has been reduced from %s%% to %s%% APR (reduction of %s%%). " +
                "This will significantly lower your monthly payment and total interest paid.",
                event.getOldInterestRate(), event.getNewInterestRate(), event.getRateReduction()),
            correlationId
        );
        
        metricsService.recordInterestRateReductionApproved(event.getRateReduction());
    }
    
    private void processPrincipalDefermentApproved(LoanModificationEvent event, String correlationId) {
        log.info("Principal deferment approved: modificationId={}, defermentMonths={}, deferredAmount={}", 
            event.getModificationId(), event.getDefermentMonths(), event.getDeferredPrincipal());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        modification.setPrincipalDefermentApproved(true);
        modification.setDefermentMonths(event.getDefermentMonths());
        modification.setDeferredPrincipal(event.getDeferredPrincipal());
        loanModificationRepository.save(modification);
        
        notificationService.sendNotification(
            modification.getBorrowerId(),
            "Principal Payments Deferred",
            String.format("Your principal payments have been deferred for %d months. " +
                "You'll only pay interest during this period (interest-only payments). " +
                "Principal payments resume after the deferment period.",
                event.getDefermentMonths()),
            correlationId
        );
        
        metricsService.recordPrincipalDefermentApproved(event.getDefermentMonths());
    }
    
    private void processModificationAgreementSigned(LoanModificationEvent event, String correlationId) {
        log.info("Modification agreement signed: modificationId={}, signedAt={}, method={}", 
            event.getModificationId(), event.getSignedAt(), event.getSignatureMethod());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        modification.setAgreementSigned(true);
        modification.setSignedAt(event.getSignedAt());
        modification.setSignatureMethod(event.getSignatureMethod());
        modification.setStatus("AGREEMENT_SIGNED");
        loanModificationRepository.save(modification);
        
        loanModificationService.initiateTrialPeriod(modification.getId());
        
        metricsService.recordModificationAgreementSigned();
    }
    
    private void processModificationActivated(LoanModificationEvent event, String correlationId) {
        log.info("Modification activated: modificationId={}, loanId={}, effectiveDate={}", 
            event.getModificationId(), event.getLoanId(), event.getEffectiveDate());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        if (modification.getNewMonthlyPayment() != null) {
            loan.setMonthlyPayment(modification.getNewMonthlyPayment());
        }
        
        if (modification.getNewInterestRate() != null) {
            loan.setInterestRate(modification.getNewInterestRate());
        }
        
        if (modification.getNewTermMonths() != null) {
            loan.setRemainingTermMonths(modification.getNewTermMonths());
        }
        
        loan.setModifiedAt(LocalDateTime.now());
        loan.setModificationId(modification.getId());
        loanRepository.save(loan);
        
        modification.setActivatedAt(LocalDateTime.now());
        modification.setStatus("ACTIVE");
        loanModificationRepository.save(modification);
        
        notificationService.sendNotification(
            modification.getBorrowerId(),
            "Loan Modification Active",
            String.format("Your loan modification is now active. New monthly payment: %s. " +
                "First modified payment due: %s. Continue making on-time payments.",
                loan.getMonthlyPayment(), loan.getNextPaymentDue()),
            correlationId
        );
        
        metricsService.recordModificationActivated(modification.getModificationType());
    }
    
    private void processTrialPeriodStarted(LoanModificationEvent event, String correlationId) {
        log.info("Trial period started: modificationId={}, trialMonths={}, trialPayment={}", 
            event.getModificationId(), event.getTrialPeriodMonths(), event.getTrialPayment());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        modification.setTrialPeriodActive(true);
        modification.setTrialPeriodMonths(event.getTrialPeriodMonths());
        modification.setTrialPayment(event.getTrialPayment());
        modification.setTrialStartDate(LocalDateTime.now());
        modification.setTrialEndDate(LocalDateTime.now().plusMonths(event.getTrialPeriodMonths()));
        modification.setStatus("TRIAL_PERIOD");
        loanModificationRepository.save(modification);
        
        notificationService.sendNotification(
            modification.getBorrowerId(),
            "Trial Modification Period Started",
            String.format("Your %d-month trial modification period has started. " +
                "Make %d consecutive on-time payments of %s to qualify for permanent modification. " +
                "Missing any payment will disqualify you.",
                event.getTrialPeriodMonths(), event.getTrialPeriodMonths(), event.getTrialPayment()),
            correlationId
        );
        
        metricsService.recordTrialPeriodStarted(event.getTrialPeriodMonths());
    }
    
    private void processTrialPeriodCompleted(LoanModificationEvent event, String correlationId) {
        log.info("Trial period completed: modificationId={}, paymentsComplete={}, allOnTime={}", 
            event.getModificationId(), event.getTrialPaymentsCompleted(), event.isAllPaymentsOnTime());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        modification.setTrialPeriodActive(false);
        modification.setTrialPaymentsCompleted(event.getTrialPaymentsCompleted());
        modification.setAllTrialPaymentsOnTime(event.isAllPaymentsOnTime());
        modification.setTrialCompletedAt(LocalDateTime.now());
        loanModificationRepository.save(modification);
        
        if (event.isAllPaymentsOnTime()) {
            loanModificationService.makeModificationPermanent(modification.getId());
        } else {
            loanModificationService.revertModification(modification.getId(), "TRIAL_FAILED");
        }
        
        metricsService.recordTrialPeriodCompleted(event.isAllPaymentsOnTime());
    }
    
    private void processModificationPermanent(LoanModificationEvent event, String correlationId) {
        log.info("Modification made permanent: modificationId={}, loanId={}", 
            event.getModificationId(), event.getLoanId());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        modification.setPermanent(true);
        modification.setPermanentAt(LocalDateTime.now());
        modification.setStatus("PERMANENT");
        loanModificationRepository.save(modification);
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setPermanentlyModified(true);
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            modification.getBorrowerId(),
            "Loan Modification Now Permanent",
            String.format("Congratulations! You've successfully completed your trial period. " +
                "Your loan modification is now permanent. Monthly payment: %s. " +
                "Continue making on-time payments to maintain good standing.",
                loan.getMonthlyPayment()),
            correlationId
        );
        
        metricsService.recordModificationPermanent();
    }
    
    private void processModificationDenied(LoanModificationEvent event, String correlationId) {
        log.info("Modification denied: modificationId={}, denialReason={}", 
            event.getModificationId(), event.getDenialReason());
        
        LoanModification modification = loanModificationRepository.findById(event.getModificationId())
            .orElseThrow();
        
        modification.setDenied(true);
        modification.setDenialReason(event.getDenialReason());
        modification.setDeniedAt(LocalDateTime.now());
        modification.setStatus("DENIED");
        loanModificationRepository.save(modification);
        
        notificationService.sendNotification(
            modification.getBorrowerId(),
            "Loan Modification Request Denied",
            String.format("Unfortunately, your loan modification request has been denied. Reason: %s. " +
                "You may appeal this decision or explore other options like forbearance. Contact us to discuss.",
                event.getDenialReason()),
            correlationId
        );
        
        metricsService.recordModificationDenied(event.getDenialReason());
    }
}