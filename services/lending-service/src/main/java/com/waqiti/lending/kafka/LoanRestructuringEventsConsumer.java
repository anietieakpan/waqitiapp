package com.waqiti.lending.kafka;

import com.waqiti.common.events.LoanRestructuringEvent;
import com.waqiti.lending.domain.LoanRestructuring;
import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.repository.LoanRestructuringRepository;
import com.waqiti.lending.repository.LoanRepository;
import com.waqiti.lending.service.LoanRestructuringService;
import com.waqiti.lending.service.DebtWorkoutService;
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
public class LoanRestructuringEventsConsumer {
    
    private final LoanRestructuringRepository loanRestructuringRepository;
    private final LoanRepository loanRepository;
    private final LoanRestructuringService loanRestructuringService;
    private final DebtWorkoutService debtWorkoutService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MAX_PRINCIPAL_REDUCTION_PERCENT = new BigDecimal("0.40");
    
    @KafkaListener(
        topics = {"loan-restructuring-events", "debt-workout-events", "loan-settlement-events"},
        groupId = "lending-loan-restructuring-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 180)
    public void handleLoanRestructuringEvent(
            @Payload LoanRestructuringEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("restructure-%s-p%d-o%d", 
            event.getLoanId(), partition, offset);
        
        log.info("Processing loan restructuring event: loanId={}, type={}, restructureType={}", 
            event.getLoanId(), event.getEventType(), event.getRestructuringType());
        
        try {
            switch (event.getEventType()) {
                case RESTRUCTURING_REQUESTED:
                    processRestructuringRequested(event, correlationId);
                    break;
                case FINANCIAL_ANALYSIS_COMPLETED:
                    processFinancialAnalysisCompleted(event, correlationId);
                    break;
                case RESTRUCTURING_PLAN_PROPOSED:
                    processRestructuringPlanProposed(event, correlationId);
                    break;
                case PRINCIPAL_REDUCTION_APPROVED:
                    processPrincipalReductionApproved(event, correlationId);
                    break;
                case PAYMENT_PLAN_RESTRUCTURED:
                    processPaymentPlanRestructured(event, correlationId);
                    break;
                case DEBT_CONSOLIDATED:
                    processDebtConsolidated(event, correlationId);
                    break;
                case SETTLEMENT_OFFER_MADE:
                    processSettlementOfferMade(event, correlationId);
                    break;
                case SETTLEMENT_ACCEPTED:
                    processSettlementAccepted(event, correlationId);
                    break;
                case RESTRUCTURING_AGREEMENT_SIGNED:
                    processRestructuringAgreementSigned(event, correlationId);
                    break;
                case RESTRUCTURING_COMPLETED:
                    processRestructuringCompleted(event, correlationId);
                    break;
                case RESTRUCTURED_LOAN_DEFAULTED:
                    processRestructuredLoanDefaulted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown loan restructuring event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "LOAN_RESTRUCTURING_EVENT_PROCESSED",
                event.getLoanId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "restructuringType", event.getRestructuringType() != null ? event.getRestructuringType() : "N/A",
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process loan restructuring event: {}", e.getMessage(), e);
            kafkaTemplate.send("loan-restructuring-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processRestructuringRequested(LoanRestructuringEvent event, String correlationId) {
        log.info("Loan restructuring requested: loanId={}, type={}, reason={}, delinquentDays={}", 
            event.getLoanId(), event.getRestructuringType(), event.getReason(), event.getDelinquentDays());
        
        Loan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        LoanRestructuring restructuring = LoanRestructuring.builder()
            .id(UUID.randomUUID().toString())
            .loanId(event.getLoanId())
            .borrowerId(event.getBorrowerId())
            .restructuringType(event.getRestructuringType())
            .reason(event.getReason())
            .requestedAt(LocalDateTime.now())
            .originalBalance(loan.getRemainingBalance())
            .originalMonthlyPayment(loan.getMonthlyPayment())
            .originalInterestRate(loan.getInterestRate())
            .delinquentDays(event.getDelinquentDays())
            .status("REQUESTED")
            .correlationId(correlationId)
            .build();
        
        loanRestructuringRepository.save(restructuring);
        
        debtWorkoutService.initiateFinancialAnalysis(restructuring.getId(), event.getBorrowerId());
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Loan Restructuring Request Received",
            String.format("Your loan restructuring request has been received. Type: %s. " +
                "We'll analyze your financial situation and propose a sustainable repayment plan. " +
                "This process typically takes 10-14 business days.",
                event.getRestructuringType()),
            correlationId
        );
        
        metricsService.recordLoanRestructuringRequested(event.getRestructuringType());
    }
    
    private void processFinancialAnalysisCompleted(LoanRestructuringEvent event, String correlationId) {
        log.info("Financial analysis completed: restructuringId={}, monthlyIncome={}, monthlyExpenses={}, disposableIncome={}", 
            event.getRestructuringId(), event.getMonthlyIncome(), 
            event.getMonthlyExpenses(), event.getDisposableIncome());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        restructuring.setFinancialAnalysisCompleted(true);
        restructuring.setMonthlyIncome(event.getMonthlyIncome());
        restructuring.setMonthlyExpenses(event.getMonthlyExpenses());
        restructuring.setDisposableIncome(event.getDisposableIncome());
        restructuring.setAnalysisCompletedAt(LocalDateTime.now());
        loanRestructuringRepository.save(restructuring);
        
        BigDecimal affordablePayment = event.getDisposableIncome()
            .multiply(new BigDecimal("0.40"));
        
        log.info("Borrower can afford approximately {} based on 40% of disposable income", affordablePayment);
        
        loanRestructuringService.proposePlan(restructuring.getId(), affordablePayment);
        
        metricsService.recordFinancialAnalysisCompleted(event.getDisposableIncome());
    }
    
    private void processRestructuringPlanProposed(LoanRestructuringEvent event, String correlationId) {
        log.info("Restructuring plan proposed: restructuringId={}, newPayment={}, newRate={}, newTerm={}", 
            event.getRestructuringId(), event.getProposedMonthlyPayment(), 
            event.getProposedInterestRate(), event.getProposedTermMonths());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        restructuring.setPlanProposed(true);
        restructuring.setProposedMonthlyPayment(event.getProposedMonthlyPayment());
        restructuring.setProposedInterestRate(event.getProposedInterestRate());
        restructuring.setProposedTermMonths(event.getProposedTermMonths());
        restructuring.setPrincipalReduction(event.getPrincipalReduction());
        restructuring.setProposedAt(LocalDateTime.now());
        loanRestructuringRepository.save(restructuring);
        
        BigDecimal oldPayment = restructuring.getOriginalMonthlyPayment();
        BigDecimal newPayment = event.getProposedMonthlyPayment();
        BigDecimal reduction = oldPayment.subtract(newPayment);
        BigDecimal reductionPercent = reduction.divide(oldPayment, 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        notificationService.sendNotification(
            restructuring.getBorrowerId(),
            "Restructuring Plan Proposed",
            String.format("We've prepared a restructuring plan for you:\n" +
                "- New monthly payment: %s (was %s, saving %s or %s%%)\n" +
                "- New interest rate: %s%% APR (was %s%%)\n" +
                "- New term: %d months\n" +
                "- Principal reduction: %s\n" +
                "Review and accept within 14 days.",
                newPayment, oldPayment, reduction, reductionPercent,
                event.getProposedInterestRate(), restructuring.getOriginalInterestRate(),
                event.getProposedTermMonths(), event.getPrincipalReduction()),
            correlationId
        );
        
        metricsService.recordRestructuringPlanProposed(event.getProposedMonthlyPayment());
    }
    
    private void processPrincipalReductionApproved(LoanRestructuringEvent event, String correlationId) {
        log.info("Principal reduction approved: restructuringId={}, originalPrincipal={}, reducedAmount={}, newPrincipal={}", 
            event.getRestructuringId(), event.getOriginalPrincipal(), 
            event.getPrincipalReduction(), event.getNewPrincipal());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        BigDecimal reductionPercent = event.getPrincipalReduction()
            .divide(event.getOriginalPrincipal(), 4, java.math.RoundingMode.HALF_UP);
        
        if (reductionPercent.compareTo(MAX_PRINCIPAL_REDUCTION_PERCENT) > 0) {
            log.warn("Principal reduction exceeds typical maximum: {}% > {}%", 
                reductionPercent.multiply(new BigDecimal("100")), 
                MAX_PRINCIPAL_REDUCTION_PERCENT.multiply(new BigDecimal("100")));
        }
        
        restructuring.setPrincipalReductionApproved(true);
        restructuring.setPrincipalReduction(event.getPrincipalReduction());
        restructuring.setNewPrincipal(event.getNewPrincipal());
        loanRestructuringRepository.save(restructuring);
        
        notificationService.sendNotification(
            restructuring.getBorrowerId(),
            "Principal Reduction Granted",
            String.format("Your loan principal has been reduced by %s (from %s to %s). " +
                "This is a %s%% reduction. This forgiven amount may have tax implications - consult a tax advisor.",
                event.getPrincipalReduction(), event.getOriginalPrincipal(), event.getNewPrincipal(),
                reductionPercent.multiply(new BigDecimal("100"))),
            correlationId
        );
        
        metricsService.recordPrincipalReductionApproved(event.getPrincipalReduction());
    }
    
    private void processPaymentPlanRestructured(LoanRestructuringEvent event, String correlationId) {
        log.info("Payment plan restructured: restructuringId={}, newSchedule={}, graduatedPayments={}", 
            event.getRestructuringId(), event.getNewPaymentSchedule(), event.isGraduatedPayments());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        restructuring.setPaymentPlanRestructured(true);
        restructuring.setNewPaymentSchedule(event.getNewPaymentSchedule());
        restructuring.setGraduatedPayments(event.isGraduatedPayments());
        loanRestructuringRepository.save(restructuring);
        
        String scheduleDescription = event.isGraduatedPayments() 
            ? "Payments start low and increase gradually as your income recovers"
            : "Fixed equal payments throughout the loan term";
        
        notificationService.sendNotification(
            restructuring.getBorrowerId(),
            "Payment Plan Restructured",
            String.format("Your payment plan has been restructured. Schedule: %s. %s",
                event.getNewPaymentSchedule(), scheduleDescription),
            correlationId
        );
        
        metricsService.recordPaymentPlanRestructured(event.getNewPaymentSchedule());
    }
    
    private void processDebtConsolidated(LoanRestructuringEvent event, String correlationId) {
        log.info("Debt consolidated: restructuringId={}, loansConsolidated={}, totalOldDebt={}, newLoanAmount={}", 
            event.getRestructuringId(), event.getLoanIds().size(), 
            event.getTotalOldDebt(), event.getNewLoanAmount());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        restructuring.setDebtConsolidated(true);
        restructuring.setConsolidatedLoanIds(event.getLoanIds());
        restructuring.setTotalOldDebt(event.getTotalOldDebt());
        restructuring.setNewLoanAmount(event.getNewLoanAmount());
        loanRestructuringRepository.save(restructuring);
        
        for (String loanId : event.getLoanIds()) {
            Loan oldLoan = loanRepository.findById(loanId).orElseThrow();
            oldLoan.setStatus("CONSOLIDATED");
            oldLoan.setConsolidatedIntoLoanId(event.getNewLoanId());
            loanRepository.save(oldLoan);
        }
        
        notificationService.sendNotification(
            restructuring.getBorrowerId(),
            "Loans Consolidated",
            String.format("Your %d loans have been consolidated into one loan. " +
                "Old total debt: %s, New loan: %s. One payment, one rate, simplified management.",
                event.getLoanIds().size(), event.getTotalOldDebt(), event.getNewLoanAmount()),
            correlationId
        );
        
        metricsService.recordDebtConsolidated(event.getLoanIds().size(), event.getNewLoanAmount());
    }
    
    private void processSettlementOfferMade(LoanRestructuringEvent event, String correlationId) {
        log.info("Settlement offer made: restructuringId={}, originalDebt={}, settlementAmount={}, discount={}", 
            event.getRestructuringId(), event.getOriginalDebt(), 
            event.getSettlementAmount(), event.getSettlementDiscount());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        restructuring.setSettlementOffered(true);
        restructuring.setSettlementAmount(event.getSettlementAmount());
        restructuring.setSettlementDiscount(event.getSettlementDiscount());
        restructuring.setSettlementOfferedAt(LocalDateTime.now());
        loanRestructuringRepository.save(restructuring);
        
        BigDecimal discountPercent = event.getSettlementDiscount()
            .divide(event.getOriginalDebt(), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        notificationService.sendNotification(
            restructuring.getBorrowerId(),
            "Settlement Offer",
            String.format("We're offering to settle your debt of %s for a one-time payment of %s " +
                "(saving you %s or %s%%). This offer expires in 30 days. " +
                "Settlement may impact your credit score and have tax implications.",
                event.getOriginalDebt(), event.getSettlementAmount(), 
                event.getSettlementDiscount(), discountPercent),
            correlationId
        );
        
        metricsService.recordSettlementOfferMade(event.getSettlementAmount(), event.getSettlementDiscount());
    }
    
    private void processSettlementAccepted(LoanRestructuringEvent event, String correlationId) {
        log.info("Settlement accepted: restructuringId={}, settlementAmount={}, paymentReceived={}", 
            event.getRestructuringId(), event.getSettlementAmount(), event.getSettlementPaymentReceived());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        Loan loan = loanRepository.findById(restructuring.getLoanId())
            .orElseThrow();
        
        restructuring.setSettlementAccepted(true);
        restructuring.setSettlementPaymentReceived(event.getSettlementPaymentReceived());
        restructuring.setSettlementAcceptedAt(LocalDateTime.now());
        restructuring.setStatus("SETTLED");
        loanRestructuringRepository.save(restructuring);
        
        loan.setStatus("SETTLED");
        loan.setRemainingBalance(BigDecimal.ZERO);
        loan.setSettledAt(LocalDateTime.now());
        loan.setSettlementAmount(event.getSettlementAmount());
        loanRepository.save(loan);
        
        BigDecimal forgivenAmount = restructuring.getOriginalBalance().subtract(event.getSettlementAmount());
        
        notificationService.sendNotification(
            restructuring.getBorrowerId(),
            "Loan Settled",
            String.format("Your loan has been settled for %s (forgiven: %s). " +
                "The loan is now closed. You'll receive a settlement letter for your records. " +
                "This may be reported to credit bureaus as 'Settled for less than owed'.",
                event.getSettlementAmount(), forgivenAmount),
            correlationId
        );
        
        metricsService.recordSettlementAccepted(event.getSettlementAmount());
    }
    
    private void processRestructuringAgreementSigned(LoanRestructuringEvent event, String correlationId) {
        log.info("Restructuring agreement signed: restructuringId={}, signedAt={}, method={}", 
            event.getRestructuringId(), event.getSignedAt(), event.getSignatureMethod());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        restructuring.setAgreementSigned(true);
        restructuring.setSignedAt(event.getSignedAt());
        restructuring.setSignatureMethod(event.getSignatureMethod());
        restructuring.setStatus("AGREEMENT_SIGNED");
        loanRestructuringRepository.save(restructuring);
        
        loanRestructuringService.implementRestructuring(restructuring.getId());
        
        metricsService.recordRestructuringAgreementSigned();
    }
    
    private void processRestructuringCompleted(LoanRestructuringEvent event, String correlationId) {
        log.info("Restructuring completed: restructuringId={}, loanId={}, newLoanId={}", 
            event.getRestructuringId(), event.getLoanId(), event.getNewLoanId());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        Loan oldLoan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        Loan newLoan = Loan.builder()
            .id(event.getNewLoanId())
            .borrowerId(restructuring.getBorrowerId())
            .loanAmount(restructuring.getNewPrincipal())
            .remainingBalance(restructuring.getNewPrincipal())
            .monthlyPayment(restructuring.getProposedMonthlyPayment())
            .interestRate(restructuring.getProposedInterestRate())
            .remainingTermMonths(restructuring.getProposedTermMonths())
            .status("ACTIVE")
            .restructuredFromLoanId(event.getLoanId())
            .isRestructured(true)
            .originatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        loanRepository.save(newLoan);
        
        oldLoan.setStatus("RESTRUCTURED");
        oldLoan.setRestructuredToLoanId(event.getNewLoanId());
        loanRepository.save(oldLoan);
        
        restructuring.setCompleted(true);
        restructuring.setNewLoanId(event.getNewLoanId());
        restructuring.setCompletedAt(LocalDateTime.now());
        restructuring.setStatus("COMPLETED");
        loanRestructuringRepository.save(restructuring);
        
        notificationService.sendNotification(
            restructuring.getBorrowerId(),
            "Loan Restructuring Complete",
            String.format("Your loan restructuring is complete! New loan details:\n" +
                "- Monthly payment: %s\n" +
                "- Interest rate: %s%% APR\n" +
                "- Term: %d months\n" +
                "- Total amount: %s\n" +
                "First payment due: %s. This is your fresh start!",
                newLoan.getMonthlyPayment(), newLoan.getInterestRate(), 
                newLoan.getRemainingTermMonths(), newLoan.getRemainingBalance(),
                newLoan.getNextPaymentDue()),
            correlationId
        );
        
        metricsService.recordRestructuringCompleted(restructuring.getRestructuringType());
    }
    
    private void processRestructuredLoanDefaulted(LoanRestructuringEvent event, String correlationId) {
        log.error("Restructured loan defaulted: restructuringId={}, newLoanId={}, daysPastDue={}, missedPayments={}", 
            event.getRestructuringId(), event.getNewLoanId(), 
            event.getDaysPastDue(), event.getMissedPayments());
        
        LoanRestructuring restructuring = loanRestructuringRepository.findById(event.getRestructuringId())
            .orElseThrow();
        
        Loan loan = loanRepository.findById(event.getNewLoanId())
            .orElseThrow();
        
        loan.setStatus("DEFAULTED");
        loan.setDaysPastDue(event.getDaysPastDue());
        loan.setDefaultedAt(LocalDateTime.now());
        loanRepository.save(loan);
        
        restructuring.setRestructuredLoanDefaulted(true);
        restructuring.setDefaultedAt(LocalDateTime.now());
        loanRestructuringRepository.save(restructuring);
        
        notificationService.sendNotification(
            restructuring.getBorrowerId(),
            "Restructured Loan in Default",
            String.format("Your restructured loan is now in default (%d days past due, %d missed payments). " +
                "This is a serious situation. Further collection actions may be taken including legal action. " +
                "Contact us immediately to discuss options.",
                event.getDaysPastDue(), event.getMissedPayments()),
            correlationId
        );
        
        metricsService.recordRestructuredLoanDefaulted(event.getDaysPastDue());
    }
}