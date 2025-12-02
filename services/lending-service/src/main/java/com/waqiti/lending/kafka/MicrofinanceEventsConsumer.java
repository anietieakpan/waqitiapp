package com.waqiti.lending.kafka;

import com.waqiti.common.events.MicrofinanceEvent;
import com.waqiti.lending.domain.MicroloanLoan;
import com.waqiti.lending.domain.SavingsGroup;
import com.waqiti.lending.repository.MicroloanRepository;
import com.waqiti.lending.repository.SavingsGroupRepository;
import com.waqiti.lending.service.MicrofinanceService;
import com.waqiti.lending.service.GroupLendingService;
import com.waqiti.lending.service.FinancialInclusionService;
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
public class MicrofinanceEventsConsumer {
    
    private final MicroloanRepository microloanRepository;
    private final SavingsGroupRepository savingsGroupRepository;
    private final MicrofinanceService microfinanceService;
    private final GroupLendingService groupLendingService;
    private final FinancialInclusionService financialInclusionService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MIN_MICROLOAN_AMOUNT = new BigDecimal("50");
    private static final BigDecimal MAX_MICROLOAN_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal TYPICAL_MICROFINANCE_RATE = new BigDecimal("12.0");
    
    @KafkaListener(
        topics = {"microfinance-events", "microloan-events", "group-lending-events"},
        groupId = "lending-microfinance-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleMicrofinanceEvent(
            @Payload MicrofinanceEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("microloan-%s-p%d-o%d", 
            event.getLoanId() != null ? event.getLoanId() : event.getBorrowerId(), 
            partition, offset);
        
        log.info("Processing microfinance event: loanId={}, type={}, amount={}", 
            event.getLoanId(), event.getEventType(), event.getLoanAmount());
        
        try {
            switch (event.getEventType()) {
                case MICROLOAN_APPLICATION_SUBMITTED:
                    processMicroloanApplicationSubmitted(event, correlationId);
                    break;
                case GROUP_FORMED:
                    processGroupFormed(event, correlationId);
                    break;
                case GROUP_MEMBER_ADDED:
                    processGroupMemberAdded(event, correlationId);
                    break;
                case SOCIAL_COLLATERAL_VERIFIED:
                    processSocialCollateralVerified(event, correlationId);
                    break;
                case MICROLOAN_APPROVED:
                    processMicroloanApproved(event, correlationId);
                    break;
                case MICROLOAN_DISBURSED:
                    processMicroloanDisbursed(event, correlationId);
                    break;
                case WEEKLY_REPAYMENT_MADE:
                    processWeeklyRepaymentMade(event, correlationId);
                    break;
                case GROUP_MEETING_HELD:
                    processGroupMeetingHeld(event, correlationId);
                    break;
                case SAVINGS_DEPOSITED:
                    processSavingsDeposited(event, correlationId);
                    break;
                case MICROLOAN_GRADUATED:
                    processMicroloanGraduated(event, correlationId);
                    break;
                case FINANCIAL_LITERACY_COMPLETED:
                    processFinancialLiteracyCompleted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown microfinance event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "MICROFINANCE_EVENT_PROCESSED",
                event.getLoanId() != null ? event.getLoanId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "groupId", event.getGroupId() != null ? event.getGroupId() : "N/A",
                    "loanAmount", event.getLoanAmount() != null ? event.getLoanAmount().toString() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process microfinance event: {}", e.getMessage(), e);
            kafkaTemplate.send("microfinance-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processMicroloanApplicationSubmitted(MicrofinanceEvent event, String correlationId) {
        log.info("Microloan application submitted: borrowerId={}, amount={}, purpose={}", 
            event.getBorrowerId(), event.getLoanAmount(), event.getLoanPurpose());
        
        if (event.getLoanAmount().compareTo(MIN_MICROLOAN_AMOUNT) < 0 || 
            event.getLoanAmount().compareTo(MAX_MICROLOAN_AMOUNT) > 0) {
            log.error("Microloan amount out of range: amount={}", event.getLoanAmount());
            return;
        }
        
        MicroloanLoan microloan = MicroloanLoan.builder()
            .id(UUID.randomUUID().toString())
            .borrowerId(event.getBorrowerId())
            .loanAmount(event.getLoanAmount())
            .loanPurpose(event.getLoanPurpose())
            .businessType(event.getBusinessType())
            .termWeeks(event.getTermWeeks())
            .interestRate(TYPICAL_MICROFINANCE_RATE)
            .repaymentFrequency("WEEKLY")
            .groupId(event.getGroupId())
            .applicationDate(LocalDateTime.now())
            .status("SUBMITTED")
            .requiresFinancialLiteracy(true)
            .correlationId(correlationId)
            .build();
        
        microloanRepository.save(microloan);
        
        if (event.getGroupId() != null) {
            groupLendingService.notifyGroupOfNewApplication(event.getGroupId(), microloan.getId());
        }
        
        microfinanceService.assessMicroloanApplication(microloan.getId());
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Microloan Application Received",
            String.format("Your microloan application for %s has been received. " +
                "Purpose: %s. We'll review and respond within 48 hours.",
                event.getLoanAmount(), event.getLoanPurpose()),
            correlationId
        );
        
        metricsService.recordMicroloanApplicationSubmitted(event.getLoanPurpose(), event.getLoanAmount());
    }
    
    private void processGroupFormed(MicrofinanceEvent event, String correlationId) {
        log.info("Savings/lending group formed: groupId={}, groupName={}, memberCount={}", 
            event.getGroupId(), event.getGroupName(), event.getInitialMemberCount());
        
        SavingsGroup group = SavingsGroup.builder()
            .id(event.getGroupId())
            .groupName(event.getGroupName())
            .leaderUserId(event.getGroupLeader())
            .memberCount(event.getInitialMemberCount())
            .totalSavings(BigDecimal.ZERO)
            .activeLoans(0)
            .formedAt(LocalDateTime.now())
            .meetingFrequency("WEEKLY")
            .status("ACTIVE")
            .correlationId(correlationId)
            .build();
        
        savingsGroupRepository.save(group);
        
        notificationService.sendNotification(
            event.getGroupLeader(),
            "Group Formed Successfully",
            String.format("Your savings group '%s' has been formed with %d members. " +
                "Schedule your first group meeting to start collective lending and savings.",
                event.getGroupName(), event.getInitialMemberCount()),
            correlationId
        );
        
        metricsService.recordGroupFormed(event.getInitialMemberCount());
    }
    
    private void processGroupMemberAdded(MicrofinanceEvent event, String correlationId) {
        log.info("Member added to group: groupId={}, newMemberId={}", 
            event.getGroupId(), event.getNewMemberId());
        
        SavingsGroup group = savingsGroupRepository.findById(event.getGroupId())
            .orElseThrow();
        
        group.setMemberCount(group.getMemberCount() + 1);
        group.getMembers().add(event.getNewMemberId());
        savingsGroupRepository.save(group);
        
        notificationService.sendNotification(
            event.getNewMemberId(),
            "Welcome to Savings Group",
            String.format("You've joined the savings group '%s'. " +
                "Attend weekly meetings to participate in group lending and savings.",
                group.getGroupName()),
            correlationId
        );
        
        metricsService.recordGroupMemberAdded(event.getGroupId());
    }
    
    private void processSocialCollateralVerified(MicrofinanceEvent event, String correlationId) {
        log.info("Social collateral verified: loanId={}, guarantors={}, groupApproval={}", 
            event.getLoanId(), event.getGuarantorCount(), event.isGroupApproved());
        
        MicroloanLoan microloan = microloanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        microloan.setSocialCollateralVerified(true);
        microloan.setGuarantorCount(event.getGuarantorCount());
        microloan.setGroupApproved(event.isGroupApproved());
        microloan.setSocialCollateralVerifiedAt(LocalDateTime.now());
        microloanRepository.save(microloan);
        
        log.info("Social collateral provides peer pressure for repayment - Grameen Bank model");
        
        metricsService.recordSocialCollateralVerified(event.getGuarantorCount());
    }
    
    private void processMicroloanApproved(MicrofinanceEvent event, String correlationId) {
        log.info("Microloan approved: loanId={}, approvedAmount={}, weeklyPayment={}", 
            event.getLoanId(), event.getApprovedAmount(), event.getWeeklyPayment());
        
        MicroloanLoan microloan = microloanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        microloan.setApprovedAmount(event.getApprovedAmount());
        microloan.setWeeklyPayment(event.getWeeklyPayment());
        microloan.setApprovedAt(LocalDateTime.now());
        microloan.setStatus("APPROVED");
        microloanRepository.save(microloan);
        
        if (microloan.isRequiresFinancialLiteracy()) {
            financialInclusionService.enrollInFinancialLiteracy(
                microloan.getBorrowerId(), microloan.getId());
        } else {
            microfinanceService.disburseMicroloan(microloan.getId());
        }
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Microloan Approved",
            String.format("Congratulations! Your microloan of %s has been approved. " +
                "Weekly payment: %s for %d weeks. Funds will be disbursed within 24 hours.",
                event.getApprovedAmount(), event.getWeeklyPayment(), microloan.getTermWeeks()),
            correlationId
        );
        
        metricsService.recordMicroloanApproved(event.getApprovedAmount());
    }
    
    private void processMicroloanDisbursed(MicrofinanceEvent event, String correlationId) {
        log.info("Microloan disbursed: loanId={}, disbursedAmount={}, disbursementMethod={}", 
            event.getLoanId(), event.getDisbursedAmount(), event.getDisbursementMethod());
        
        MicroloanLoan microloan = microloanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        microloan.setDisbursedAmount(event.getDisbursedAmount());
        microloan.setDisbursedAt(LocalDateTime.now());
        microloan.setDisbursementMethod(event.getDisbursementMethod());
        microloan.setStatus("ACTIVE");
        microloan.setFirstPaymentDue(LocalDateTime.now().plusWeeks(1));
        microloan.setRemainingBalance(event.getDisbursedAmount());
        microloanRepository.save(microloan);
        
        if (microloan.getGroupId() != null) {
            SavingsGroup group = savingsGroupRepository.findById(microloan.getGroupId())
                .orElseThrow();
            group.setActiveLoans(group.getActiveLoans() + 1);
            savingsGroupRepository.save(group);
        }
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Microloan Disbursed",
            String.format("Your microloan of %s has been disbursed via %s. " +
                "First weekly payment of %s is due on %s. " +
                "Attend your group meeting for support and accountability.",
                event.getDisbursedAmount(), event.getDisbursementMethod(),
                microloan.getWeeklyPayment(), microloan.getFirstPaymentDue().toLocalDate()),
            correlationId
        );
        
        metricsService.recordMicroloanDisbursed(event.getDisbursedAmount(), event.getDisbursementMethod());
    }
    
    private void processWeeklyRepaymentMade(MicrofinanceEvent event, String correlationId) {
        log.info("Weekly repayment made: loanId={}, paymentAmount={}, weekNumber={}", 
            event.getLoanId(), event.getPaymentAmount(), event.getWeekNumber());
        
        MicroloanLoan microloan = microloanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        microloan.setWeeksPaid(microloan.getWeeksPaid() + 1);
        microloan.setTotalPaid(microloan.getTotalPaid().add(event.getPaymentAmount()));
        microloan.setRemainingBalance(
            microloan.getRemainingBalance().subtract(event.getPrincipalPaid()));
        microloan.setLastPaymentDate(LocalDateTime.now());
        microloan.setOnTimePayments(microloan.getOnTimePayments() + 1);
        microloanRepository.save(microloan);
        
        if (microloan.getWeeksPaid().equals(microloan.getTermWeeks())) {
            microfinanceService.completeMicroloan(microloan.getId());
        }
        
        if (microloan.getGroupId() != null) {
            groupLendingService.recordGroupMemberPayment(
                microloan.getGroupId(), microloan.getBorrowerId(), event.getPaymentAmount());
        }
        
        metricsService.recordWeeklyRepaymentMade(event.getPaymentAmount(), event.getWeekNumber());
    }
    
    private void processGroupMeetingHeld(MicrofinanceEvent event, String correlationId) {
        log.info("Group meeting held: groupId={}, attendees={}, savingsCollected={}", 
            event.getGroupId(), event.getAttendeeCount(), event.getSavingsCollected());
        
        SavingsGroup group = savingsGroupRepository.findById(event.getGroupId())
            .orElseThrow();
        
        group.setLastMeetingDate(LocalDateTime.now());
        group.setMeetingsHeld(group.getMeetingsHeld() + 1);
        group.setTotalSavings(group.getTotalSavings().add(event.getSavingsCollected()));
        savingsGroupRepository.save(group);
        
        log.info("Group meetings provide: loan repayment collection, savings deposits, " +
            "financial education, peer support");
        
        metricsService.recordGroupMeetingHeld(event.getGroupId(), event.getAttendeeCount());
    }
    
    private void processSavingsDeposited(MicrofinanceEvent event, String correlationId) {
        log.info("Savings deposited: groupId={}, userId={}, depositAmount={}", 
            event.getGroupId(), event.getUserId(), event.getSavingsAmount());
        
        SavingsGroup group = savingsGroupRepository.findById(event.getGroupId())
            .orElseThrow();
        
        group.setTotalSavings(group.getTotalSavings().add(event.getSavingsAmount()));
        savingsGroupRepository.save(group);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Savings Deposit Confirmed",
            String.format("Your savings deposit of %s has been recorded. " +
                "Group total savings: %s. Keep building your financial resilience!",
                event.getSavingsAmount(), group.getTotalSavings()),
            correlationId
        );
        
        metricsService.recordSavingsDeposited(event.getSavingsAmount());
    }
    
    private void processMicroloanGraduated(MicrofinanceEvent event, String correlationId) {
        log.info("Microloan borrower graduated: borrowerId={}, newLoanType={}, newMaxAmount={}", 
            event.getBorrowerId(), event.getGraduationLoanType(), event.getGraduationMaxAmount());
        
        MicroloanLoan completedLoan = microloanRepository
            .findTopByBorrowerIdOrderByCompletedAtDesc(event.getBorrowerId())
            .orElseThrow();
        
        completedLoan.setGraduated(true);
        completedLoan.setGraduatedAt(LocalDateTime.now());
        completedLoan.setGraduationLoanType(event.getGraduationLoanType());
        microloanRepository.save(completedLoan);
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Congratulations on Your Graduation!",
            String.format("You've successfully completed your microloans and graduated to %s! " +
                "You're now eligible for loans up to %s. " +
                "This is a major milestone in your financial journey.",
                event.getGraduationLoanType(), event.getGraduationMaxAmount()),
            correlationId
        );
        
        metricsService.recordMicroloanGraduated(event.getGraduationLoanType());
    }
    
    private void processFinancialLiteracyCompleted(MicrofinanceEvent event, String correlationId) {
        log.info("Financial literacy training completed: userId={}, courseName={}, score={}", 
            event.getUserId(), event.getCourseName(), event.getCompletionScore());
        
        financialInclusionService.recordTrainingCompletion(
            event.getUserId(), event.getCourseName(), event.getCompletionScore());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Financial Literacy Certificate",
            String.format("Congratulations on completing '%s'! Score: %d%%. " +
                "You've gained valuable skills in budgeting, savings, and business management. " +
                "Your microloan can now be disbursed.",
                event.getCourseName(), event.getCompletionScore()),
            correlationId
        );
        
        metricsService.recordFinancialLiteracyCompleted(event.getCourseName(), event.getCompletionScore());
    }
}