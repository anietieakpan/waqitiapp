package com.waqiti.lending.kafka;

import com.waqiti.common.events.BalloonPaymentEvent;
import com.waqiti.lending.domain.BalloonLoan;
import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.repository.BalloonLoanRepository;
import com.waqiti.lending.repository.LoanRepository;
import com.waqiti.lending.service.BalloonPaymentService;
import com.waqiti.lending.service.RefinancingService;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class BalloonPaymentEventsConsumer {
    
    private final BalloonLoanRepository balloonLoanRepository;
    private final LoanRepository loanRepository;
    private final BalloonPaymentService balloonPaymentService;
    private final RefinancingService refinancingService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int BALLOON_WARNING_MONTHS = 6;
    
    @KafkaListener(
        topics = {"balloon-payment-events", "balloon-loan-events", "lump-sum-payment-events"},
        groupId = "lending-balloon-payment-service-group",
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
    public void handleBalloonPaymentEvent(
            @Payload BalloonPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("balloon-%s-p%d-o%d", 
            event.getLoanId(), partition, offset);
        
        log.info("Processing balloon payment event: loanId={}, type={}, balloonAmount={}", 
            event.getLoanId(), event.getEventType(), event.getBalloonPaymentAmount());
        
        try {
            switch (event.getEventType()) {
                case BALLOON_LOAN_ORIGINATED:
                    processBalloonLoanOriginated(event, correlationId);
                    break;
                case BALLOON_PAYMENT_SCHEDULED:
                    processBalloonPaymentScheduled(event, correlationId);
                    break;
                case BALLOON_PAYMENT_WARNING_6_MONTHS:
                    processBalloonPaymentWarning6Months(event, correlationId);
                    break;
                case BALLOON_PAYMENT_WARNING_3_MONTHS:
                    processBalloonPaymentWarning3Months(event, correlationId);
                    break;
                case BALLOON_PAYMENT_WARNING_30_DAYS:
                    processBalloonPaymentWarning30Days(event, correlationId);
                    break;
                case REFINANCING_OPTIONS_PRESENTED:
                    processRefinancingOptionsPresented(event, correlationId);
                    break;
                case REFINANCING_APPLICATION_SUBMITTED:
                    processRefinancingApplicationSubmitted(event, correlationId);
                    break;
                case BALLOON_PAYMENT_MADE:
                    processBalloonPaymentMade(event, correlationId);
                    break;
                case BALLOON_PAYMENT_MISSED:
                    processBalloonPaymentMissed(event, correlationId);
                    break;
                case BALLOON_LOAN_REFINANCED:
                    processBalloonLoanRefinanced(event, correlationId);
                    break;
                case PARTIAL_BALLOON_PAYMENT_MADE:
                    processPartialBalloonPaymentMade(event, correlationId);
                    break;
                case BALLOON_PAYMENT_EXTENSION_GRANTED:
                    processBalloonPaymentExtensionGranted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown balloon payment event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "BALLOON_PAYMENT_EVENT_PROCESSED",
                event.getLoanId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "balloonAmount", event.getBalloonPaymentAmount() != null ? event.getBalloonPaymentAmount().toString() : "N/A",
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process balloon payment event: {}", e.getMessage(), e);
            kafkaTemplate.send("balloon-payment-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processBalloonLoanOriginated(BalloonPaymentEvent event, String correlationId) {
        log.info("Balloon loan originated: loanId={}, loanAmount={}, balloonAmount={}, dueDate={}", 
            event.getLoanId(), event.getLoanAmount(), event.getBalloonPaymentAmount(), event.getBalloonDueDate());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        BalloonLoan balloonLoan = BalloonLoan.builder()
            .id(UUID.randomUUID().toString())
            .loanId(event.getLoanId())
            .borrowerId(event.getBorrowerId())
            .loanAmount(event.getLoanAmount())
            .regularMonthlyPayment(event.getRegularMonthlyPayment())
            .balloonPaymentAmount(event.getBalloonPaymentAmount())
            .balloonDueDate(event.getBalloonDueDate())
            .interestRate(loan.getInterestRate())
            .originatedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .build();
        
        balloonLoanRepository.save(balloonLoan);
        
        loan.setHasBalloonPayment(true);
        loan.setBalloonPaymentAmount(event.getBalloonPaymentAmount());
        loan.setBalloonDueDate(event.getBalloonDueDate());
        loanRepository.save(loan);
        
        BigDecimal balloonPercent = event.getBalloonPaymentAmount()
            .divide(event.getLoanAmount(), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Balloon Loan Originated",
            String.format("Your balloon loan of %s has been originated. " +
                "Regular monthly payment: %s. Large balloon payment of %s (%s%% of loan) due on %s. " +
                "Start planning for this payment - we'll offer refinancing options as the date approaches.",
                event.getLoanAmount(), event.getRegularMonthlyPayment(), 
                event.getBalloonPaymentAmount(), balloonPercent, event.getBalloonDueDate().toLocalDate()),
            correlationId
        );
        
        metricsService.recordBalloonLoanOriginated(event.getBalloonPaymentAmount());
    }
    
    private void processBalloonPaymentScheduled(BalloonPaymentEvent event, String correlationId) {
        log.info("Balloon payment scheduled: loanId={}, amount={}, dueDate={}", 
            event.getLoanId(), event.getBalloonPaymentAmount(), event.getBalloonDueDate());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setBalloonScheduled(true);
        balloonLoan.setBalloonScheduledAt(LocalDateTime.now());
        balloonLoanRepository.save(balloonLoan);
        
        balloonPaymentService.scheduleReminders(balloonLoan.getId());
        
        metricsService.recordBalloonPaymentScheduled(event.getBalloonDueDate());
    }
    
    private void processBalloonPaymentWarning6Months(BalloonPaymentEvent event, String correlationId) {
        log.info("Balloon payment 6-month warning: loanId={}, balloonAmount={}, dueDate={}", 
            event.getLoanId(), event.getBalloonPaymentAmount(), event.getBalloonDueDate());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setSixMonthWarningIssued(true);
        balloonLoan.setSixMonthWarningAt(LocalDateTime.now());
        balloonLoanRepository.save(balloonLoan);
        
        refinancingService.generateRefinancingOptions(balloonLoan.getId());
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "Balloon Payment Due in 6 Months",
            String.format("Important: Your balloon payment of %s is due in 6 months on %s. " +
                "Options available:\n" +
                "1. Pay the balloon amount in full\n" +
                "2. Refinance into a new loan\n" +
                "3. Sell the asset (if applicable)\n" +
                "Contact us to discuss refinancing options with competitive rates.",
                event.getBalloonPaymentAmount(), event.getBalloonDueDate().toLocalDate()),
            correlationId
        );
        
        metricsService.recordBalloonPaymentWarning(6);
    }
    
    private void processBalloonPaymentWarning3Months(BalloonPaymentEvent event, String correlationId) {
        log.info("Balloon payment 3-month warning: loanId={}, balloonAmount={}, dueDate={}", 
            event.getLoanId(), event.getBalloonPaymentAmount(), event.getBalloonDueDate());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setThreeMonthWarningIssued(true);
        balloonLoan.setThreeMonthWarningAt(LocalDateTime.now());
        balloonLoanRepository.save(balloonLoan);
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "Urgent: Balloon Payment Due in 3 Months",
            String.format("Urgent reminder: Your balloon payment of %s is due in 3 months on %s. " +
                "If you haven't already, NOW is the time to:\n" +
                "1. Secure financing for the balloon payment\n" +
                "2. Apply for refinancing (typically takes 30-45 days)\n" +
                "3. Arrange to sell the asset if needed\n" +
                "Contact us immediately if you need assistance.",
                event.getBalloonPaymentAmount(), event.getBalloonDueDate().toLocalDate()),
            correlationId
        );
        
        metricsService.recordBalloonPaymentWarning(3);
    }
    
    private void processBalloonPaymentWarning30Days(BalloonPaymentEvent event, String correlationId) {
        log.info("Balloon payment 30-day warning: loanId={}, balloonAmount={}, dueDate={}", 
            event.getLoanId(), event.getBalloonPaymentAmount(), event.getBalloonDueDate());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setThirtyDayWarningIssued(true);
        balloonLoan.setThirtyDayWarningAt(LocalDateTime.now());
        balloonLoanRepository.save(balloonLoan);
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "CRITICAL: Balloon Payment Due in 30 Days",
            String.format("CRITICAL: Your balloon payment of %s is due in 30 days on %s. " +
                "Payment methods:\n" +
                "- Bank transfer (recommended)\n" +
                "- Cashier's check\n" +
                "- Wire transfer\n" +
                "If you cannot make this payment, contact us IMMEDIATELY to discuss emergency options. " +
                "Failure to pay will result in default and potential asset seizure.",
                event.getBalloonPaymentAmount(), event.getBalloonDueDate().toLocalDate()),
            correlationId
        );
        
        metricsService.recordBalloonPaymentWarning(1);
    }
    
    private void processRefinancingOptionsPresented(BalloonPaymentEvent event, String correlationId) {
        log.info("Refinancing options presented: loanId={}, optionCount={}", 
            event.getLoanId(), event.getRefinancingOptions().size());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setRefinancingOptionsPresented(true);
        balloonLoan.setRefinancingOptionsPresentedAt(LocalDateTime.now());
        balloonLoanRepository.save(balloonLoan);
        
        StringBuilder optionsText = new StringBuilder("Refinancing options for your balloon payment:\n\n");
        for (Map<String, Object> option : event.getRefinancingOptions()) {
            optionsText.append(String.format("Option %s: %s%% APR, %s/month for %s months\n",
                option.get("name"), option.get("rate"), 
                option.get("monthlyPayment"), option.get("termMonths")));
        }
        optionsText.append("\nApply now to avoid balloon payment stress!");
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "Refinancing Options Available",
            optionsText.toString(),
            correlationId
        );
        
        metricsService.recordRefinancingOptionsPresented(event.getRefinancingOptions().size());
    }
    
    private void processRefinancingApplicationSubmitted(BalloonPaymentEvent event, String correlationId) {
        log.info("Refinancing application submitted: loanId={}, refinanceAmount={}, selectedRate={}", 
            event.getLoanId(), event.getRefinanceAmount(), event.getRefinanceRate());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setRefinancingApplicationSubmitted(true);
        balloonLoan.setRefinanceApplicationSubmittedAt(LocalDateTime.now());
        balloonLoan.setRefinanceAmount(event.getRefinanceAmount());
        balloonLoanRepository.save(balloonLoan);
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "Refinancing Application Submitted",
            String.format("Your refinancing application for %s at %s%% APR has been submitted. " +
                "We'll review and respond within 5 business days. " +
                "If approved before your balloon due date, the refinance will pay off your balloon payment automatically.",
                event.getRefinanceAmount(), event.getRefinanceRate()),
            correlationId
        );
        
        metricsService.recordRefinancingApplicationSubmitted(event.getRefinanceAmount());
    }
    
    private void processBalloonPaymentMade(BalloonPaymentEvent event, String correlationId) {
        log.info("Balloon payment made: loanId={}, paymentAmount={}, paymentMethod={}", 
            event.getLoanId(), event.getBalloonPaymentAmount(), event.getPaymentMethod());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setBalloonPaid(true);
        balloonLoan.setBalloonPaidAt(LocalDateTime.now());
        balloonLoan.setPaymentMethod(event.getPaymentMethod());
        balloonLoan.setStatus("PAID");
        balloonLoanRepository.save(balloonLoan);
        
        loan.setStatus("PAID_OFF");
        loan.setRemainingBalance(BigDecimal.ZERO);
        loan.setPaidOffAt(LocalDateTime.now());
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "Congratulations - Balloon Payment Complete!",
            String.format("Your balloon payment of %s has been received and processed. " +
                "Your loan is now fully paid off! You own the asset free and clear. " +
                "Thank you for your business - we'll send your final payoff letter within 7 days.",
                event.getBalloonPaymentAmount()),
            correlationId
        );
        
        metricsService.recordBalloonPaymentMade(event.getBalloonPaymentAmount());
    }
    
    private void processBalloonPaymentMissed(BalloonPaymentEvent event, String correlationId) {
        log.error("Balloon payment missed: loanId={}, balloonAmount={}, dueDate={}, daysPastDue={}", 
            event.getLoanId(), event.getBalloonPaymentAmount(), 
            event.getBalloonDueDate(), event.getDaysPastDue());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setBalloonMissed(true);
        balloonLoan.setDaysPastDue(event.getDaysPastDue());
        balloonLoan.setStatus("DEFAULTED");
        balloonLoanRepository.save(balloonLoan);
        
        loan.setStatus("DEFAULTED");
        loan.setDaysPastDue(event.getDaysPastDue());
        loan.setDefaultedAt(LocalDateTime.now());
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "URGENT: Balloon Payment Missed - Default",
            String.format("Your balloon payment of %s was due on %s and has not been received (%d days past due). " +
                "Your loan is now in DEFAULT. Immediate consequences:\n" +
                "- Late fees accruing daily\n" +
                "- Negative credit reporting\n" +
                "- Asset repossession proceedings initiated\n" +
                "Contact us IMMEDIATELY to arrange payment or discuss emergency options.",
                event.getBalloonPaymentAmount(), event.getBalloonDueDate().toLocalDate(), event.getDaysPastDue()),
            correlationId
        );
        
        metricsService.recordBalloonPaymentMissed(event.getBalloonPaymentAmount(), event.getDaysPastDue());
    }
    
    private void processBalloonLoanRefinanced(BalloonPaymentEvent event, String correlationId) {
        log.info("Balloon loan refinanced: oldLoanId={}, newLoanId={}, refinanceAmount={}, newRate={}", 
            event.getLoanId(), event.getNewLoanId(), event.getRefinanceAmount(), event.getRefinanceRate());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        Loan oldLoan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setRefinanced(true);
        balloonLoan.setRefinancedAt(LocalDateTime.now());
        balloonLoan.setNewLoanId(event.getNewLoanId());
        balloonLoan.setStatus("REFINANCED");
        balloonLoanRepository.save(balloonLoan);
        
        oldLoan.setStatus("REFINANCED");
        oldLoan.setRefinancedToLoanId(event.getNewLoanId());
        loanRepository.save(oldLoan);
        
        Loan newLoan = Loan.builder()
            .id(event.getNewLoanId())
            .borrowerId(balloonLoan.getBorrowerId())
            .loanAmount(event.getRefinanceAmount())
            .remainingBalance(event.getRefinanceAmount())
            .monthlyPayment(event.getNewMonthlyPayment())
            .interestRate(event.getRefinanceRate())
            .remainingTermMonths(event.getNewTermMonths())
            .status("ACTIVE")
            .refinancedFromLoanId(event.getLoanId())
            .hasBalloonPayment(false)
            .originatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        loanRepository.save(newLoan);
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "Balloon Loan Refinanced Successfully",
            String.format("Your balloon loan has been refinanced! " +
                "Old balloon payment of %s has been eliminated. " +
                "New loan: %s at %s%% APR, %s/month for %d months. " +
                "No more balloon payment stress!",
                balloonLoan.getBalloonPaymentAmount(), event.getRefinanceAmount(), 
                event.getRefinanceRate(), event.getNewMonthlyPayment(), event.getNewTermMonths()),
            correlationId
        );
        
        metricsService.recordBalloonLoanRefinanced(event.getRefinanceAmount());
    }
    
    private void processPartialBalloonPaymentMade(BalloonPaymentEvent event, String correlationId) {
        log.info("Partial balloon payment made: loanId={}, fullAmount={}, paidAmount={}, remainingAmount={}", 
            event.getLoanId(), event.getBalloonPaymentAmount(), 
            event.getPartialPaymentAmount(), event.getRemainingBalloonAmount());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setPartialPaymentReceived(true);
        balloonLoan.setPartialPaymentAmount(event.getPartialPaymentAmount());
        balloonLoan.setRemainingBalloonAmount(event.getRemainingBalloonAmount());
        balloonLoan.setPartialPaymentReceivedAt(LocalDateTime.now());
        balloonLoanRepository.save(balloonLoan);
        
        BigDecimal paidPercent = event.getPartialPaymentAmount()
            .divide(event.getBalloonPaymentAmount(), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "Partial Balloon Payment Received",
            String.format("We've received your partial balloon payment of %s (%s%% of total). " +
                "Remaining balance: %s. Full amount of %s still due on %s. " +
                "Contact us to arrange payment of the remaining balance to avoid default.",
                event.getPartialPaymentAmount(), paidPercent, 
                event.getRemainingBalloonAmount(), event.getBalloonPaymentAmount(),
                balloonLoan.getBalloonDueDate().toLocalDate()),
            correlationId
        );
        
        metricsService.recordPartialBalloonPaymentMade(event.getPartialPaymentAmount());
    }
    
    private void processBalloonPaymentExtensionGranted(BalloonPaymentEvent event, String correlationId) {
        log.info("Balloon payment extension granted: loanId={}, originalDue={}, newDue={}, extensionDays={}, extensionFee={}", 
            event.getLoanId(), event.getOriginalDueDate(), event.getNewDueDate(), 
            event.getExtensionDays(), event.getExtensionFee());
        
        BalloonLoan balloonLoan = balloonLoanRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        balloonLoan.setExtensionGranted(true);
        balloonLoan.setOriginalDueDate(event.getOriginalDueDate());
        balloonLoan.setBalloonDueDate(event.getNewDueDate());
        balloonLoan.setExtensionDays(event.getExtensionDays());
        balloonLoan.setExtensionFee(event.getExtensionFee());
        balloonLoan.setExtensionGrantedAt(LocalDateTime.now());
        balloonLoanRepository.save(balloonLoan);
        
        loan.setBalloonDueDate(event.getNewDueDate());
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            balloonLoan.getBorrowerId(),
            "Balloon Payment Extension Granted",
            String.format("Your balloon payment has been extended by %d days. " +
                "Original due date: %s, New due date: %s. Extension fee: %s. " +
                "This is a one-time courtesy extension. The full balloon payment of %s " +
                "MUST be received by the new due date - no further extensions available.",
                event.getExtensionDays(), event.getOriginalDueDate().toLocalDate(), 
                event.getNewDueDate().toLocalDate(), event.getExtensionFee(),
                balloonLoan.getBalloonPaymentAmount()),
            correlationId
        );
        
        metricsService.recordBalloonPaymentExtensionGranted(event.getExtensionDays());
    }
}