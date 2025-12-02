package com.waqiti.lending.kafka;

import com.waqiti.common.events.LoanServicingEvent;
import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.LoanPayment;
import com.waqiti.lending.domain.LoanDelinquency;
import com.waqiti.lending.repository.LoanRepository;
import com.waqiti.lending.repository.LoanPaymentRepository;
import com.waqiti.lending.repository.LoanDelinquencyRepository;
import com.waqiti.lending.service.LoanPaymentService;
import com.waqiti.lending.service.DelinquencyManagementService;
import com.waqiti.lending.service.LoanModificationService;
import com.waqiti.lending.metrics.LoanMetricsService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoanServicingEventsConsumer {
    
    private final LoanRepository loanRepository;
    private final LoanPaymentRepository paymentRepository;
    private final LoanDelinquencyRepository delinquencyRepository;
    private final LoanPaymentService paymentService;
    private final DelinquencyManagementService delinquencyService;
    private final LoanModificationService modificationService;
    private final LoanMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"loan-servicing-events", "loan-payment-events", "loan-delinquency-events"},
        groupId = "loan-servicing-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleLoanServicingEvent(
            @Payload LoanServicingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("loan-%s-p%d-o%d", 
            event.getLoanId(), partition, offset);
        
        log.info("Processing loan servicing event: loanId={}, type={}", 
            event.getLoanId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case PAYMENT_DUE:
                    processPaymentDue(event, correlationId);
                    break;
                case PAYMENT_RECEIVED:
                    processPaymentReceived(event, correlationId);
                    break;
                case PAYMENT_APPLIED:
                    processPaymentApplied(event, correlationId);
                    break;
                case PAYMENT_MISSED:
                    processPaymentMissed(event, correlationId);
                    break;
                case PAYMENT_LATE:
                    processPaymentLate(event, correlationId);
                    break;
                case LATE_FEE_ASSESSED:
                    processLateFeeAssessed(event, correlationId);
                    break;
                case DELINQUENCY_DETECTED:
                    processDelinquencyDetected(event, correlationId);
                    break;
                case DELINQUENCY_ESCALATED:
                    processDelinquencyEscalated(event, correlationId);
                    break;
                case LOAN_CURRENT:
                    processLoanCurrent(event, correlationId);
                    break;
                case LOAN_PAID_OFF:
                    processLoanPaidOff(event, correlationId);
                    break;
                case LOAN_MODIFICATION_REQUESTED:
                    processLoanModificationRequested(event, correlationId);
                    break;
                case LOAN_MODIFICATION_APPROVED:
                    processLoanModificationApproved(event, correlationId);
                    break;
                case INTEREST_ACCRUED:
                    processInterestAccrued(event, correlationId);
                    break;
                case PRINCIPAL_ADJUSTMENT:
                    processPrincipalAdjustment(event, correlationId);
                    break;
                default:
                    log.warn("Unknown loan servicing event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLoanEvent(
                "LOAN_SERVICING_EVENT_PROCESSED",
                event.getLoanId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process loan servicing event: {}", e.getMessage(), e);
            kafkaTemplate.send("loan-servicing-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processPaymentDue(LoanServicingEvent event, String correlationId) {
        log.info("Loan payment due: loanId={}, dueDate={}, amount={}", 
            event.getLoanId(), event.getDueDate(), event.getPaymentAmount());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setNextPaymentDueDate(event.getDueDate());
        loan.setNextPaymentAmount(event.getPaymentAmount());
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Loan Payment Due",
            String.format("Your loan payment of %.2f is due on %s", 
                event.getPaymentAmount(), event.getDueDate()),
            correlationId
        );
        
        metricsService.recordPaymentDue(event.getPaymentAmount());
    }
    
    private void processPaymentReceived(LoanServicingEvent event, String correlationId) {
        log.info("Loan payment received: loanId={}, paymentId={}, amount={}", 
            event.getLoanId(), event.getPaymentId(), event.getPaymentAmount());
        
        LoanPayment payment = LoanPayment.builder()
            .id(event.getPaymentId())
            .loanId(event.getLoanId())
            .userId(event.getUserId())
            .amount(event.getPaymentAmount())
            .receivedAt(LocalDateTime.now())
            .status("RECEIVED")
            .paymentMethod(event.getPaymentMethod())
            .correlationId(correlationId)
            .build();
        
        paymentRepository.save(payment);
        paymentService.processPayment(event.getPaymentId());
        
        metricsService.recordPaymentReceived(event.getPaymentAmount(), event.getPaymentMethod());
    }
    
    private void processPaymentApplied(LoanServicingEvent event, String correlationId) {
        log.info("Loan payment applied: loanId={}, paymentId={}, principal={}, interest={}", 
            event.getLoanId(), event.getPaymentId(), 
            event.getPrincipalAmount(), event.getInterestAmount());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(event.getPrincipalAmount()));
        loan.setAccruedInterest(loan.getAccruedInterest().subtract(event.getInterestAmount()));
        loan.setLastPaymentDate(LocalDateTime.now());
        loan.setLastPaymentAmount(event.getPaymentAmount());
        loan.setPaymentsMade(loan.getPaymentsMade() + 1);
        loanRepository.save(loan);
        
        LoanPayment payment = paymentRepository.findById(event.getPaymentId())
            .orElseThrow();
        payment.setStatus("APPLIED");
        payment.setAppliedAt(LocalDateTime.now());
        payment.setPrincipalAmount(event.getPrincipalAmount());
        payment.setInterestAmount(event.getInterestAmount());
        payment.setFeesAmount(event.getFeesAmount());
        paymentRepository.save(payment);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Payment Applied",
            String.format("Your payment of %.2f has been applied to your loan.", event.getPaymentAmount()),
            correlationId
        );
        
        metricsService.recordPaymentApplied(event.getPrincipalAmount(), event.getInterestAmount());
    }
    
    private void processPaymentMissed(LoanServicingEvent event, String correlationId) {
        log.warn("Loan payment missed: loanId={}, dueDate={}, amount={}", 
            event.getLoanId(), event.getDueDate(), event.getPaymentAmount());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setPaymentsMissed(loan.getPaymentsMissed() + 1);
        loan.setLastMissedPaymentDate(event.getDueDate());
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Missed Payment",
            String.format("You missed a loan payment of %.2f that was due on %s. Please make a payment to avoid late fees.", 
                event.getPaymentAmount(), event.getDueDate()),
            correlationId
        );
        
        metricsService.recordPaymentMissed();
    }
    
    private void processPaymentLate(LoanServicingEvent event, String correlationId) {
        log.warn("Loan payment late: loanId={}, daysLate={}", 
            event.getLoanId(), event.getDaysLate());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setDaysDelinquent(event.getDaysLate());
        loanRepository.save(loan);
        
        paymentService.assessLateFee(event.getLoanId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Late Payment",
            String.format("Your loan payment is %d days late. A late fee has been assessed.", event.getDaysLate()),
            correlationId
        );
        
        metricsService.recordPaymentLate(event.getDaysLate());
    }
    
    private void processLateFeeAssessed(LoanServicingEvent event, String correlationId) {
        log.info("Late fee assessed: loanId={}, feeAmount={}", 
            event.getLoanId(), event.getFeeAmount());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setLateFees(loan.getLateFees().add(event.getFeeAmount()));
        loan.setTotalAmountDue(loan.getTotalAmountDue().add(event.getFeeAmount()));
        loanRepository.save(loan);
        
        metricsService.recordLateFeeAssessed(event.getFeeAmount());
    }
    
    private void processDelinquencyDetected(LoanServicingEvent event, String correlationId) {
        log.error("Loan delinquency detected: loanId={}, daysDelinquent={}, severity={}", 
            event.getLoanId(), event.getDaysDelinquent(), event.getDelinquencySeverity());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setStatus("DELINQUENT");
        loan.setDelinquentSince(LocalDateTime.now());
        loan.setDaysDelinquent(event.getDaysDelinquent());
        loan.setDelinquencySeverity(event.getDelinquencySeverity());
        loanRepository.save(loan);
        
        LoanDelinquency delinquency = LoanDelinquency.builder()
            .id(UUID.randomUUID().toString())
            .loanId(event.getLoanId())
            .userId(event.getUserId())
            .severity(event.getDelinquencySeverity())
            .daysDelinquent(event.getDaysDelinquent())
            .amountPastDue(event.getAmountPastDue())
            .detectedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .build();
        
        delinquencyRepository.save(delinquency);
        delinquencyService.initiateCollections(delinquency.getId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Loan Delinquency",
            String.format("Your loan is %d days delinquent. Please contact us immediately to resolve this.", 
                event.getDaysDelinquent()),
            correlationId
        );
        
        metricsService.recordDelinquencyDetected(event.getDelinquencySeverity(), event.getDaysDelinquent());
    }
    
    private void processDelinquencyEscalated(LoanServicingEvent event, String correlationId) {
        log.error("Delinquency escalated: loanId={}, from={}, to={}", 
            event.getLoanId(), event.getPreviousSeverity(), event.getDelinquencySeverity());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setDelinquencySeverity(event.getDelinquencySeverity());
        loanRepository.save(loan);
        
        LoanDelinquency delinquency = delinquencyRepository.findActivByLoanId(event.getLoanId())
            .orElseThrow();
        delinquency.setSeverity(event.getDelinquencySeverity());
        delinquency.setEscalatedAt(LocalDateTime.now());
        delinquencyRepository.save(delinquency);
        
        if ("CHARGE_OFF".equals(event.getDelinquencySeverity())) {
            delinquencyService.processChargeOff(event.getLoanId());
        }
        
        metricsService.recordDelinquencyEscalated(
            event.getPreviousSeverity(), 
            event.getDelinquencySeverity()
        );
    }
    
    private void processLoanCurrent(LoanServicingEvent event, String correlationId) {
        log.info("Loan current: loanId={}", event.getLoanId());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setStatus("CURRENT");
        loan.setDaysDelinquent(0);
        loan.setDelinquencySeverity(null);
        loan.setDelinquentSince(null);
        loanRepository.save(loan);
        
        delinquencyRepository.findActivByLoanId(event.getLoanId())
            .ifPresent(delinquency -> {
                delinquency.setStatus("RESOLVED");
                delinquency.setResolvedAt(LocalDateTime.now());
                delinquencyRepository.save(delinquency);
            });
        
        notificationService.sendNotification(
            event.getUserId(),
            "Loan Current",
            "Great news! Your loan is now current. Thank you for your payment.",
            correlationId
        );
        
        metricsService.recordLoanCurrent();
    }
    
    private void processLoanPaidOff(LoanServicingEvent event, String correlationId) {
        log.info("Loan paid off: loanId={}, finalPayment={}", 
            event.getLoanId(), event.getFinalPaymentAmount());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setStatus("PAID_OFF");
        loan.setPaidOffAt(LocalDateTime.now());
        loan.setOutstandingPrincipal(BigDecimal.ZERO);
        loan.setAccruedInterest(BigDecimal.ZERO);
        loan.setTotalAmountDue(BigDecimal.ZERO);
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Congratulations!",
            "Your loan has been paid off! Thank you for your business.",
            correlationId
        );
        
        metricsService.recordLoanPaidOff(loan.getLoanType(), loan.getOriginalAmount());
    }
    
    private void processLoanModificationRequested(LoanServicingEvent event, String correlationId) {
        log.info("Loan modification requested: loanId={}, modificationType={}", 
            event.getLoanId(), event.getModificationType());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setModificationRequested(true);
        loan.setModificationRequestedAt(LocalDateTime.now());
        loan.setModificationType(event.getModificationType());
        loanRepository.save(loan);
        
        modificationService.processModificationRequest(event.getLoanId(), event.getModificationType());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Loan Modification Request",
            "We've received your loan modification request and will review it shortly.",
            correlationId
        );
        
        metricsService.recordModificationRequested(event.getModificationType());
    }
    
    private void processLoanModificationApproved(LoanServicingEvent event, String correlationId) {
        log.info("Loan modification approved: loanId={}, modificationType={}", 
            event.getLoanId(), event.getModificationType());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setModificationApproved(true);
        loan.setModificationApprovedAt(LocalDateTime.now());
        
        if (event.getNewInterestRate() != null) {
            loan.setInterestRate(event.getNewInterestRate());
        }
        if (event.getNewTermMonths() != null) {
            loan.setTermMonths(event.getNewTermMonths());
        }
        if (event.getNewMonthlyPayment() != null) {
            loan.setMonthlyPayment(event.getNewMonthlyPayment());
        }
        
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Loan Modification Approved",
            "Your loan modification has been approved. Check your updated terms.",
            correlationId
        );
        
        metricsService.recordModificationApproved(event.getModificationType());
    }
    
    private void processInterestAccrued(LoanServicingEvent event, String correlationId) {
        log.debug("Interest accrued: loanId={}, amount={}", 
            event.getLoanId(), event.getInterestAmount());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setAccruedInterest(loan.getAccruedInterest().add(event.getInterestAmount()));
        loan.setTotalAmountDue(loan.getTotalAmountDue().add(event.getInterestAmount()));
        loanRepository.save(loan);
        
        metricsService.recordInterestAccrued(event.getInterestAmount());
    }
    
    private void processPrincipalAdjustment(LoanServicingEvent event, String correlationId) {
        log.info("Principal adjustment: loanId={}, adjustment={}, reason={}", 
            event.getLoanId(), event.getAdjustmentAmount(), event.getAdjustmentReason());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().add(event.getAdjustmentAmount()));
        loanRepository.save(loan);
        
        metricsService.recordPrincipalAdjustment(event.getAdjustmentAmount(), event.getAdjustmentReason());
    }
}