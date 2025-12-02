package com.waqiti.lending.kafka;

import com.waqiti.common.events.P2PLendingEvent;
import com.waqiti.lending.domain.P2PLoan;
import com.waqiti.lending.domain.LendingOffer;
import com.waqiti.lending.repository.P2PLoanRepository;
import com.waqiti.lending.repository.LendingOfferRepository;
import com.waqiti.lending.service.P2PLendingService;
import com.waqiti.lending.service.CreditMatchingService;
import com.waqiti.lending.service.EscrowService;
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
public class P2PLendingEventsConsumer {
    
    private final P2PLoanRepository loanRepository;
    private final LendingOfferRepository offerRepository;
    private final P2PLendingService p2pLendingService;
    private final CreditMatchingService matchingService;
    private final EscrowService escrowService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("100");
    private static final BigDecimal MAX_LOAN_AMOUNT = new BigDecimal("50000");
    private static final BigDecimal PLATFORM_FEE_PERCENTAGE = new BigDecimal("0.01");
    
    @KafkaListener(
        topics = {"p2p-lending-events", "peer-to-peer-loan-events", "marketplace-lending-events"},
        groupId = "lending-p2p-service-group",
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
    public void handleP2PLendingEvent(
            @Payload P2PLendingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("p2p-loan-%s-p%d-o%d", 
            event.getLoanId() != null ? event.getLoanId() : event.getBorrowerId(), 
            partition, offset);
        
        log.info("Processing P2P lending event: loanId={}, type={}, amount={}", 
            event.getLoanId(), event.getEventType(), event.getLoanAmount());
        
        try {
            switch (event.getEventType()) {
                case BORROWING_REQUEST_CREATED:
                    processBorrowingRequestCreated(event, correlationId);
                    break;
                case LENDING_OFFER_CREATED:
                    processLendingOfferCreated(event, correlationId);
                    break;
                case LOAN_MATCHED:
                    processLoanMatched(event, correlationId);
                    break;
                case LOAN_FUNDED:
                    processLoanFunded(event, correlationId);
                    break;
                case FUNDS_ESCROWED:
                    processFundsEscrowed(event, correlationId);
                    break;
                case FUNDS_DISBURSED:
                    processFundsDisbursed(event, correlationId);
                    break;
                case REPAYMENT_RECEIVED:
                    processRepaymentReceived(event, correlationId);
                    break;
                case INTEREST_DISTRIBUTED:
                    processInterestDistributed(event, correlationId);
                    break;
                case LOAN_FULLY_REPAID:
                    processLoanFullyRepaid(event, correlationId);
                    break;
                case LOAN_DEFAULTED:
                    processLoanDefaulted(event, correlationId);
                    break;
                case LATE_PAYMENT_FEE_CHARGED:
                    processLatePaymentFeeCharged(event, correlationId);
                    break;
                default:
                    log.warn("Unknown P2P lending event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "P2P_LENDING_EVENT_PROCESSED",
                event.getLoanId() != null ? event.getLoanId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "lenderId", event.getLenderId() != null ? event.getLenderId() : "N/A",
                    "loanAmount", event.getLoanAmount() != null ? event.getLoanAmount().toString() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process P2P lending event: {}", e.getMessage(), e);
            kafkaTemplate.send("p2p-lending-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processBorrowingRequestCreated(P2PLendingEvent event, String correlationId) {
        log.info("Borrowing request created: borrowerId={}, amount={}, purpose={}, rate={}", 
            event.getBorrowerId(), event.getLoanAmount(), event.getLoanPurpose(), event.getInterestRate());
        
        if (event.getLoanAmount().compareTo(MIN_LOAN_AMOUNT) < 0 || 
            event.getLoanAmount().compareTo(MAX_LOAN_AMOUNT) > 0) {
            log.error("Loan amount out of range: amount={}, min={}, max={}", 
                event.getLoanAmount(), MIN_LOAN_AMOUNT, MAX_LOAN_AMOUNT);
            return;
        }
        
        P2PLoan loan = P2PLoan.builder()
            .id(UUID.randomUUID().toString())
            .borrowerId(event.getBorrowerId())
            .loanAmount(event.getLoanAmount())
            .interestRate(event.getInterestRate())
            .termMonths(event.getTermMonths())
            .loanPurpose(event.getLoanPurpose())
            .creditScore(event.getBorrowerCreditScore())
            .requestedAt(LocalDateTime.now())
            .status("REQUESTED")
            .platformFee(event.getLoanAmount().multiply(PLATFORM_FEE_PERCENTAGE))
            .correlationId(correlationId)
            .build();
        
        loanRepository.save(loan);
        
        matchingService.findPotentialLenders(loan.getId());
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Borrowing Request Submitted",
            String.format("Your request to borrow %s at %s%% APR for %d months has been submitted. " +
                "We're matching you with potential lenders.",
                event.getLoanAmount(), event.getInterestRate(), event.getTermMonths()),
            correlationId
        );
        
        metricsService.recordBorrowingRequestCreated(event.getLoanPurpose(), event.getLoanAmount());
    }
    
    private void processLendingOfferCreated(P2PLendingEvent event, String correlationId) {
        log.info("Lending offer created: lenderId={}, loanId={}, offeredAmount={}", 
            event.getLenderId(), event.getLoanId(), event.getOfferedAmount());
        
        LendingOffer offer = LendingOffer.builder()
            .id(UUID.randomUUID().toString())
            .loanId(event.getLoanId())
            .lenderId(event.getLenderId())
            .offeredAmount(event.getOfferedAmount())
            .offeredRate(event.getOfferedRate())
            .offerValidUntil(LocalDateTime.now().plusDays(7))
            .createdAt(LocalDateTime.now())
            .status("PENDING")
            .correlationId(correlationId)
            .build();
        
        offerRepository.save(offer);
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        notificationService.sendNotification(
            loan.getBorrowerId(),
            "New Lending Offer",
            String.format("A lender is offering %s at %s%% APR for your loan request. " +
                "Review and accept in the app within 7 days.",
                event.getOfferedAmount(), event.getOfferedRate()),
            correlationId
        );
        
        metricsService.recordLendingOfferCreated(event.getOfferedAmount());
    }
    
    private void processLoanMatched(P2PLendingEvent event, String correlationId) {
        log.info("Loan matched: loanId={}, borrowerId={}, lenderId={}, amount={}", 
            event.getLoanId(), event.getBorrowerId(), event.getLenderId(), event.getLoanAmount());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setLenderId(event.getLenderId());
        loan.setMatchedAt(LocalDateTime.now());
        loan.setStatus("MATCHED");
        loanRepository.save(loan);
        
        LendingOffer acceptedOffer = offerRepository
            .findByLoanIdAndLenderId(event.getLoanId(), event.getLenderId())
            .orElseThrow();
        
        acceptedOffer.setStatus("ACCEPTED");
        acceptedOffer.setAcceptedAt(LocalDateTime.now());
        offerRepository.save(acceptedOffer);
        
        notificationService.sendNotification(
            event.getLenderId(),
            "Loan Matched",
            String.format("Your lending offer of %s has been accepted. " +
                "Please fund the loan within 48 hours.",
                event.getLoanAmount()),
            correlationId
        );
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Loan Matched",
            String.format("Your loan request of %s has been matched with a lender. " +
                "Funds will be disbursed once the lender transfers the amount.",
                event.getLoanAmount()),
            correlationId
        );
        
        metricsService.recordLoanMatched(event.getLoanAmount());
    }
    
    private void processLoanFunded(P2PLendingEvent event, String correlationId) {
        log.info("Loan funded: loanId={}, lenderId={}, fundedAmount={}", 
            event.getLoanId(), event.getLenderId(), event.getFundedAmount());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setFundedAmount(event.getFundedAmount());
        loan.setFundedAt(LocalDateTime.now());
        loan.setStatus("FUNDED");
        loanRepository.save(loan);
        
        escrowService.transferToEscrow(loan.getId(), event.getFundedAmount());
        
        metricsService.recordLoanFunded(event.getFundedAmount());
    }
    
    private void processFundsEscrowed(P2PLendingEvent event, String correlationId) {
        log.info("Funds escrowed: loanId={}, escrowAmount={}, escrowAccount={}", 
            event.getLoanId(), event.getEscrowAmount(), event.getEscrowAccount());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setEscrowAccount(event.getEscrowAccount());
        loan.setEscrowedAt(LocalDateTime.now());
        loan.setStatus("ESCROWED");
        loanRepository.save(loan);
        
        p2pLendingService.initiateDisbursement(loan.getId());
        
        metricsService.recordFundsEscrowed(event.getEscrowAmount());
    }
    
    private void processFundsDisbursed(P2PLendingEvent event, String correlationId) {
        log.info("Funds disbursed: loanId={}, borrowerId={}, disbursedAmount={}", 
            event.getLoanId(), event.getBorrowerId(), event.getDisbursedAmount());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        BigDecimal netDisbursement = event.getDisbursedAmount().subtract(loan.getPlatformFee());
        
        loan.setDisbursedAmount(event.getDisbursedAmount());
        loan.setDisbursedAt(LocalDateTime.now());
        loan.setNetDisbursement(netDisbursement);
        loan.setStatus("ACTIVE");
        loan.setFirstPaymentDue(LocalDateTime.now().plusMonths(1));
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Loan Disbursed",
            String.format("Your loan of %s has been disbursed (Platform fee: %s, Net: %s). " +
                "First payment of %s is due on %s.",
                event.getDisbursedAmount(), loan.getPlatformFee(), netDisbursement,
                event.getMonthlyPayment(), loan.getFirstPaymentDue().toLocalDate()),
            correlationId
        );
        
        notificationService.sendNotification(
            event.getLenderId(),
            "Loan Active",
            String.format("Your loan of %s is now active. You'll receive monthly payments of %s " +
                "starting on %s.",
                event.getDisbursedAmount(), event.getMonthlyPayment(), 
                loan.getFirstPaymentDue().toLocalDate()),
            correlationId
        );
        
        metricsService.recordFundsDisbursed(netDisbursement);
    }
    
    private void processRepaymentReceived(P2PLendingEvent event, String correlationId) {
        log.info("Repayment received: loanId={}, paymentAmount={}, principalPaid={}, interestPaid={}", 
            event.getLoanId(), event.getPaymentAmount(), event.getPrincipalPaid(), event.getInterestPaid());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setTotalPrincipalPaid(
            loan.getTotalPrincipalPaid().add(event.getPrincipalPaid()));
        loan.setTotalInterestPaid(
            loan.getTotalInterestPaid().add(event.getInterestPaid()));
        loan.setPaymentsMade(loan.getPaymentsMade() + 1);
        loan.setLastPaymentDate(LocalDateTime.now());
        loan.setRemainingBalance(
            loan.getLoanAmount().subtract(loan.getTotalPrincipalPaid()));
        loanRepository.save(loan);
        
        p2pLendingService.distributePaymentToLender(loan.getId(), event.getPaymentAmount());
        
        metricsService.recordRepaymentReceived(event.getPaymentAmount(), event.getPrincipalPaid(), event.getInterestPaid());
    }
    
    private void processInterestDistributed(P2PLendingEvent event, String correlationId) {
        log.info("Interest distributed: loanId={}, lenderId={}, interestAmount={}", 
            event.getLoanId(), event.getLenderId(), event.getInterestAmount());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setTotalInterestDistributed(
            loan.getTotalInterestDistributed().add(event.getInterestAmount()));
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            event.getLenderId(),
            "Interest Payment Received",
            String.format("You've received an interest payment of %s from loan %s. " +
                "Total interest earned: %s",
                event.getInterestAmount(), event.getLoanId(), 
                loan.getTotalInterestDistributed()),
            correlationId
        );
        
        metricsService.recordInterestDistributed(event.getInterestAmount());
    }
    
    private void processLoanFullyRepaid(P2PLendingEvent event, String correlationId) {
        log.info("Loan fully repaid: loanId={}, totalRepaid={}, completedAt={}", 
            event.getLoanId(), event.getTotalRepaid(), event.getCompletedAt());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setStatus("COMPLETED");
        loan.setCompletedAt(event.getCompletedAt());
        loan.setRemainingBalance(BigDecimal.ZERO);
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            loan.getBorrowerId(),
            "Loan Paid Off",
            String.format("Congratulations! You've successfully paid off your loan of %s. " +
                "Total paid: %s (Principal: %s, Interest: %s)",
                loan.getLoanAmount(), event.getTotalRepaid(),
                loan.getTotalPrincipalPaid(), loan.getTotalInterestPaid()),
            correlationId
        );
        
        notificationService.sendNotification(
            loan.getLenderId(),
            "Loan Completed",
            String.format("The loan of %s has been fully repaid. " +
                "Total return: %s (Principal: %s, Interest: %s)",
                loan.getLoanAmount(), event.getTotalRepaid(),
                loan.getTotalPrincipalPaid(), loan.getTotalInterestPaid()),
            correlationId
        );
        
        metricsService.recordLoanFullyRepaid(event.getTotalRepaid(), loan.getTermMonths());
    }
    
    private void processLoanDefaulted(P2PLendingEvent event, String correlationId) {
        log.error("Loan defaulted: loanId={}, borrowerId={}, outstandingBalance={}, daysPastDue={}", 
            event.getLoanId(), event.getBorrowerId(), event.getOutstandingBalance(), event.getDaysPastDue());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setStatus("DEFAULTED");
        loan.setDefaultedAt(LocalDateTime.now());
        loan.setDefaultAmount(event.getOutstandingBalance());
        loan.setDaysPastDue(event.getDaysPastDue());
        loanRepository.save(loan);
        
        p2pLendingService.initiateCollections(loan.getId());
        
        notificationService.sendNotification(
            loan.getLenderId(),
            "Loan Default Notice",
            String.format("Unfortunately, loan %s has defaulted. Outstanding balance: %s. " +
                "We're initiating collection proceedings. You may be eligible for default protection.",
                event.getLoanId(), event.getOutstandingBalance()),
            correlationId
        );
        
        metricsService.recordLoanDefaulted(event.getOutstandingBalance(), event.getDaysPastDue());
    }
    
    private void processLatePaymentFeeCharged(P2PLendingEvent event, String correlationId) {
        log.warn("Late payment fee charged: loanId={}, feeAmount={}, daysPastDue={}", 
            event.getLoanId(), event.getLateFee(), event.getDaysPastDue());
        
        P2PLoan loan = loanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setTotalLateFees(loan.getTotalLateFees().add(event.getLateFee()));
        loan.setDaysPastDue(event.getDaysPastDue());
        loanRepository.save(loan);
        
        notificationService.sendNotification(
            loan.getBorrowerId(),
            "Late Payment Fee",
            String.format("A late fee of %s has been charged to your loan. " +
                "Your payment is %d days overdue. Please make a payment to avoid further fees.",
                event.getLateFee(), event.getDaysPastDue()),
            correlationId
        );
        
        metricsService.recordLatePaymentFeeCharged(event.getLateFee(), event.getDaysPastDue());
    }
}