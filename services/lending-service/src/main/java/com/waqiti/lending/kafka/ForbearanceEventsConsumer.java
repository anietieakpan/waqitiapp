package com.waqiti.lending.kafka;

import com.waqiti.common.events.ForbearanceEvent;
import com.waqiti.lending.domain.Forbearance;
import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.repository.ForbearanceRepository;
import com.waqiti.lending.repository.LoanRepository;
import com.waqiti.lending.service.ForbearanceService;
import com.waqiti.lending.service.HardshipVerificationService;
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
public class ForbearanceEventsConsumer {
    
    private final ForbearanceRepository forbearanceRepository;
    private final LoanRepository loanRepository;
    private final ForbearanceService forbearanceService;
    private final HardshipVerificationService hardshipVerificationService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_FORBEARANCE_MONTHS = 12;
    private static final int STANDARD_FORBEARANCE_MONTHS = 3;
    
    @KafkaListener(
        topics = {"forbearance-events", "payment-suspension-events", "hardship-relief-events"},
        groupId = "lending-forbearance-service-group",
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
    public void handleForbearanceEvent(
            @Payload ForbearanceEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("forbearance-%s-p%d-o%d", 
            event.getLoanId() != null ? event.getLoanId() : event.getBorrowerId(), 
            partition, offset);
        
        log.info("Processing forbearance event: loanId={}, type={}, reason={}", 
            event.getLoanId(), event.getEventType(), event.getForbearanceReason());
        
        try {
            switch (event.getEventType()) {
                case FORBEARANCE_REQUESTED:
                    processForbearanceRequested(event, correlationId);
                    break;
                case HARDSHIP_DOCUMENTED:
                    processHardshipDocumented(event, correlationId);
                    break;
                case FORBEARANCE_APPROVED:
                    processForbearanceApproved(event, correlationId);
                    break;
                case FORBEARANCE_STARTED:
                    processForbearanceStarted(event, correlationId);
                    break;
                case INTEREST_ACCRUED:
                    processInterestAccrued(event, correlationId);
                    break;
                case FORBEARANCE_EXTENDED:
                    processForbearanceExtended(event, correlationId);
                    break;
                case FORBEARANCE_ENDING_SOON:
                    processForbearanceEndingSoon(event, correlationId);
                    break;
                case FORBEARANCE_ENDED:
                    processForbearanceEnded(event, correlationId);
                    break;
                case REPAYMENT_OPTIONS_PRESENTED:
                    processRepaymentOptionsPresented(event, correlationId);
                    break;
                case CATCH_UP_PLAN_SELECTED:
                    processCatchUpPlanSelected(event, correlationId);
                    break;
                case FORBEARANCE_DENIED:
                    processForbearanceDenied(event, correlationId);
                    break;
                default:
                    log.warn("Unknown forbearance event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "FORBEARANCE_EVENT_PROCESSED",
                event.getLoanId() != null ? event.getLoanId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "forbearanceReason", event.getForbearanceReason() != null ? event.getForbearanceReason() : "N/A",
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process forbearance event: {}", e.getMessage(), e);
            kafkaTemplate.send("forbearance-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processForbearanceRequested(ForbearanceEvent event, String correlationId) {
        log.info("Forbearance requested: loanId={}, borrowerId={}, reason={}, requestedMonths={}", 
            event.getLoanId(), event.getBorrowerId(), event.getForbearanceReason(), event.getRequestedMonths());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        if (event.getRequestedMonths() > MAX_FORBEARANCE_MONTHS) {
            log.error("Requested forbearance exceeds maximum: requested={}, max={}", 
                event.getRequestedMonths(), MAX_FORBEARANCE_MONTHS);
            return;
        }
        
        Forbearance forbearance = Forbearance.builder()
            .id(UUID.randomUUID().toString())
            .loanId(event.getLoanId())
            .borrowerId(event.getBorrowerId())
            .forbearanceReason(event.getForbearanceReason())
            .requestedMonths(event.getRequestedMonths())
            .requestedAt(LocalDateTime.now())
            .currentOutstandingBalance(loan.getRemainingBalance())
            .status("REQUESTED")
            .correlationId(correlationId)
            .build();
        
        forbearanceRepository.save(forbearance);
        
        hardshipVerificationService.requestDocumentation(forbearance.getId(), event.getBorrowerId());
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Forbearance Request Received",
            String.format("Your forbearance request has been received. Reason: %s, Duration: %d months. " +
                "Please submit hardship documentation within 7 days. " +
                "Note: Interest continues to accrue during forbearance.",
                event.getForbearanceReason(), event.getRequestedMonths()),
            correlationId
        );
        
        metricsService.recordForbearanceRequested(event.getForbearanceReason());
    }
    
    private void processHardshipDocumented(ForbearanceEvent event, String correlationId) {
        log.info("Hardship documented: forbearanceId={}, hardshipType={}, documentsProvided={}", 
            event.getForbearanceId(), event.getHardshipType(), event.getDocumentsProvided());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        forbearance.setHardshipDocumented(true);
        forbearance.setHardshipType(event.getHardshipType());
        forbearance.setDocumentsProvided(event.getDocumentsProvided());
        forbearance.setDocumentedAt(LocalDateTime.now());
        forbearanceRepository.save(forbearance);
        
        forbearanceService.evaluateForbearanceRequest(forbearance.getId());
        
        metricsService.recordHardshipDocumented(event.getHardshipType());
    }
    
    private void processForbearanceApproved(ForbearanceEvent event, String correlationId) {
        log.info("Forbearance approved: forbearanceId={}, approvedMonths={}, startDate={}, endDate={}", 
            event.getForbearanceId(), event.getApprovedMonths(), 
            event.getForbearanceStartDate(), event.getForbearanceEndDate());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        forbearance.setApproved(true);
        forbearance.setApprovedMonths(event.getApprovedMonths());
        forbearance.setForbearanceStartDate(event.getForbearanceStartDate());
        forbearance.setForbearanceEndDate(event.getForbearanceEndDate());
        forbearance.setApprovedAt(LocalDateTime.now());
        forbearance.setStatus("APPROVED");
        forbearanceRepository.save(forbearance);
        
        Loan loan = loanRepository.findById(forbearance.getLoanId())
            .orElseThrow();
        
        loan.setInForbearance(true);
        loan.setForbearanceId(forbearance.getId());
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            forbearance.getBorrowerId(),
            "Forbearance Approved",
            String.format("Your forbearance has been approved for %d months (from %s to %s). " +
                "You don't need to make payments during this period, but interest will continue to accrue. " +
                "Your loan term will be extended to compensate.",
                event.getApprovedMonths(), event.getForbearanceStartDate().toLocalDate(), 
                event.getForbearanceEndDate().toLocalDate()),
            correlationId
        );
        
        metricsService.recordForbearanceApproved(event.getApprovedMonths());
    }
    
    private void processForbearanceStarted(ForbearanceEvent event, String correlationId) {
        log.info("Forbearance started: forbearanceId={}, loanId={}, suspendedPayment={}", 
            event.getForbearanceId(), event.getLoanId(), event.getSuspendedMonthlyPayment());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        forbearance.setActive(true);
        forbearance.setSuspendedMonthlyPayment(event.getSuspendedMonthlyPayment());
        forbearance.setStatus("ACTIVE");
        forbearanceRepository.save(forbearance);
        
        loan.setPaymentsSuspended(true);
        loan.setForbearanceStartDate(LocalDateTime.now());
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            forbearance.getBorrowerId(),
            "Forbearance Period Started",
            String.format("Your forbearance period has started. Your normal payment of %s is suspended. " +
                "Interest continues to accrue at %s%% APR and will be capitalized (added to principal) when forbearance ends.",
                event.getSuspendedMonthlyPayment(), loan.getInterestRate()),
            correlationId
        );
        
        metricsService.recordForbearanceStarted();
    }
    
    private void processInterestAccrued(ForbearanceEvent event, String correlationId) {
        log.info("Interest accrued during forbearance: forbearanceId={}, month={}, interestAccrued={}, totalAccrued={}", 
            event.getForbearanceId(), event.getForbearanceMonth(), 
            event.getMonthlyInterestAccrued(), event.getTotalInterestAccrued());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        forbearance.setTotalInterestAccrued(event.getTotalInterestAccrued());
        forbearance.setMonthsElapsed(event.getForbearanceMonth());
        forbearanceRepository.save(forbearance);
        
        if (event.getForbearanceMonth() % 3 == 0) {
            notificationService.sendNotification(
                forbearance.getBorrowerId(),
                "Forbearance Status Update",
                String.format("Month %d of %d in forbearance. Total interest accrued so far: %s. " +
                    "This will be added to your loan balance when forbearance ends.",
                    event.getForbearanceMonth(), forbearance.getApprovedMonths(), 
                    event.getTotalInterestAccrued()),
                correlationId
            );
        }
        
        metricsService.recordInterestAccrued(event.getMonthlyInterestAccrued());
    }
    
    private void processForbearanceExtended(ForbearanceEvent event, String correlationId) {
        log.info("Forbearance extended: forbearanceId={}, originalEnd={}, newEnd={}, additionalMonths={}", 
            event.getForbearanceId(), event.getOriginalEndDate(), 
            event.getNewEndDate(), event.getExtensionMonths());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        int totalMonths = forbearance.getApprovedMonths() + event.getExtensionMonths();
        
        if (totalMonths > MAX_FORBEARANCE_MONTHS) {
            log.error("Total forbearance exceeds maximum: total={}, max={}", 
                totalMonths, MAX_FORBEARANCE_MONTHS);
            return;
        }
        
        forbearance.setExtended(true);
        forbearance.setApprovedMonths(totalMonths);
        forbearance.setForbearanceEndDate(event.getNewEndDate());
        forbearance.setExtensionMonths(event.getExtensionMonths());
        forbearance.setExtendedAt(LocalDateTime.now());
        forbearanceRepository.save(forbearance);
        
        notificationService.sendNotification(
            forbearance.getBorrowerId(),
            "Forbearance Extended",
            String.format("Your forbearance has been extended by %d months. New end date: %s. " +
                "Total forbearance period: %d months. Continue monitoring your accrued interest.",
                event.getExtensionMonths(), event.getNewEndDate().toLocalDate(), totalMonths),
            correlationId
        );
        
        metricsService.recordForbearanceExtended(event.getExtensionMonths());
    }
    
    private void processForbearanceEndingSoon(ForbearanceEvent event, String correlationId) {
        log.info("Forbearance ending soon: forbearanceId={}, endDate={}, daysRemaining={}", 
            event.getForbearanceId(), event.getForbearanceEndDate(), event.getDaysUntilEnd());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        forbearanceService.generateRepaymentOptions(forbearance.getId());
        
        notificationService.sendNotification(
            forbearance.getBorrowerId(),
            "Forbearance Ending Soon",
            String.format("Your forbearance ends in %d days on %s. " +
                "Total interest accrued: %s. We've prepared repayment options for you: " +
                "1) Resume regular payments, 2) Extended repayment, 3) Loan modification. " +
                "Choose an option before your forbearance ends.",
                event.getDaysUntilEnd(), event.getForbearanceEndDate().toLocalDate(),
                forbearance.getTotalInterestAccrued()),
            correlationId
        );
        
        metricsService.recordForbearanceEndingNotification(event.getDaysUntilEnd());
    }
    
    private void processForbearanceEnded(ForbearanceEvent event, String correlationId) {
        log.info("Forbearance ended: forbearanceId={}, loanId={}, totalInterestCapitalized={}, newBalance={}", 
            event.getForbearanceId(), event.getLoanId(), 
            event.getTotalInterestCapitalized(), event.getNewLoanBalance());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        forbearance.setActive(false);
        forbearance.setEndedAt(LocalDateTime.now());
        forbearance.setTotalInterestCapitalized(event.getTotalInterestCapitalized());
        forbearance.setStatus("ENDED");
        forbearanceRepository.save(forbearance);
        
        loan.setInForbearance(false);
        loan.setPaymentsSuspended(false);
        loan.setRemainingBalance(event.getNewLoanBalance());
        loan.setForbearanceEndDate(LocalDateTime.now());
        loanRepository.save(loan);
        
        BigDecimal oldBalance = event.getNewLoanBalance().subtract(event.getTotalInterestCapitalized());
        
        notificationService.sendNotification(
            forbearance.getBorrowerId(),
            "Forbearance Period Ended",
            String.format("Your forbearance period has ended. Accrued interest of %s has been capitalized. " +
                "Previous balance: %s, New balance: %s. " +
                "Payments resume next month. Review your repayment options in your account.",
                event.getTotalInterestCapitalized(), oldBalance, event.getNewLoanBalance()),
            correlationId
        );
        
        metricsService.recordForbearanceEnded(event.getTotalInterestCapitalized());
    }
    
    private void processRepaymentOptionsPresented(ForbearanceEvent event, String correlationId) {
        log.info("Repayment options presented: forbearanceId={}, optionCount={}", 
            event.getForbearanceId(), event.getRepaymentOptions().size());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        forbearance.setRepaymentOptionsPresented(true);
        forbearance.setRepaymentOptionsPresentedAt(LocalDateTime.now());
        forbearanceRepository.save(forbearance);
        
        StringBuilder optionsText = new StringBuilder("Post-forbearance repayment options:\n");
        for (Map<String, Object> option : event.getRepaymentOptions()) {
            optionsText.append(String.format("- %s: %s/month\n", 
                option.get("name"), option.get("monthlyPayment")));
        }
        
        notificationService.sendNotification(
            forbearance.getBorrowerId(),
            "Choose Your Repayment Plan",
            optionsText.toString() + "Select an option within 14 days.",
            correlationId
        );
        
        metricsService.recordRepaymentOptionsPresented(event.getRepaymentOptions().size());
    }
    
    private void processCatchUpPlanSelected(ForbearanceEvent event, String correlationId) {
        log.info("Catch-up plan selected: forbearanceId={}, planType={}, catchUpMonths={}, catchUpPayment={}", 
            event.getForbearanceId(), event.getCatchUpPlan(), 
            event.getCatchUpMonths(), event.getCatchUpPayment());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        forbearance.setCatchUpPlanSelected(true);
        forbearance.setCatchUpPlan(event.getCatchUpPlan());
        forbearance.setCatchUpMonths(event.getCatchUpMonths());
        forbearance.setCatchUpPayment(event.getCatchUpPayment());
        forbearanceRepository.save(forbearance);
        
        Loan loan = loanRepository.findById(forbearance.getLoanId())
            .orElseThrow();
        
        loan.setMonthlyPayment(event.getCatchUpPayment());
        loan.setInCatchUpPlan(true);
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            forbearance.getBorrowerId(),
            "Catch-Up Plan Activated",
            String.format("Your %s catch-up plan is now active. Payment: %s/month for %d months. " +
                "After the catch-up period, your payment returns to the regular amount.",
                event.getCatchUpPlan(), event.getCatchUpPayment(), event.getCatchUpMonths()),
            correlationId
        );
        
        metricsService.recordCatchUpPlanSelected(event.getCatchUpPlan());
    }
    
    private void processForbearanceDenied(ForbearanceEvent event, String correlationId) {
        log.info("Forbearance denied: forbearanceId={}, denialReason={}", 
            event.getForbearanceId(), event.getDenialReason());
        
        Forbearance forbearance = forbearanceRepository.findById(event.getForbearanceId())
            .orElseThrow();
        
        forbearance.setDenied(true);
        forbearance.setDenialReason(event.getDenialReason());
        forbearance.setDeniedAt(LocalDateTime.now());
        forbearance.setStatus("DENIED");
        forbearanceRepository.save(forbearance);
        
        notificationService.sendNotification(
            forbearance.getBorrowerId(),
            "Forbearance Request Denied",
            String.format("Your forbearance request has been denied. Reason: %s. " +
                "You may explore other options like loan modification or repayment plans. " +
                "Contact us to discuss alternatives.",
                event.getDenialReason()),
            correlationId
        );
        
        metricsService.recordForbearanceDenied(event.getDenialReason());
    }
}