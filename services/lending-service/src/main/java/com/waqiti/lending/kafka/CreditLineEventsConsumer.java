package com.waqiti.lending.kafka;

import com.waqiti.common.events.CreditLineEvent;
import com.waqiti.lending.domain.CreditLine;
import com.waqiti.lending.domain.CreditLineDraw;
import com.waqiti.lending.repository.CreditLineRepository;
import com.waqiti.lending.repository.CreditLineDrawRepository;
import com.waqiti.lending.service.CreditLineService;
import com.waqiti.lending.service.RevolvingCreditService;
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
public class CreditLineEventsConsumer {
    
    private final CreditLineRepository creditLineRepository;
    private final CreditLineDrawRepository drawRepository;
    private final CreditLineService creditLineService;
    private final RevolvingCreditService revolvingCreditService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MIN_DRAW_AMOUNT = new BigDecimal("100");
    private static final BigDecimal UTILIZATION_WARNING_THRESHOLD = new BigDecimal("0.75");
    private static final int REVIEW_FREQUENCY_MONTHS = 12;
    
    @KafkaListener(
        topics = {"credit-line-events", "line-of-credit-events", "revolving-credit-events"},
        groupId = "lending-credit-line-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 90)
    public void handleCreditLineEvent(
            @Payload CreditLineEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("credit-line-%s-p%d-o%d", 
            event.getCreditLineId() != null ? event.getCreditLineId() : event.getBorrowerId(), 
            partition, offset);
        
        log.info("Processing credit line event: creditLineId={}, type={}, amount={}", 
            event.getCreditLineId(), event.getEventType(), event.getAmount());
        
        try {
            switch (event.getEventType()) {
                case CREDIT_LINE_OPENED:
                    processCreditLineOpened(event, correlationId);
                    break;
                case FUNDS_DRAWN:
                    processFundsDrawn(event, correlationId);
                    break;
                case PAYMENT_MADE:
                    processPaymentMade(event, correlationId);
                    break;
                case CREDIT_LIMIT_INCREASED:
                    processCreditLimitIncreased(event, correlationId);
                    break;
                case CREDIT_LIMIT_DECREASED:
                    processCreditLimitDecreased(event, correlationId);
                    break;
                case UTILIZATION_HIGH:
                    processUtilizationHigh(event, correlationId);
                    break;
                case ANNUAL_REVIEW_COMPLETED:
                    processAnnualReviewCompleted(event, correlationId);
                    break;
                case INTEREST_RATE_ADJUSTED:
                    processInterestRateAdjusted(event, correlationId);
                    break;
                case CREDIT_LINE_FROZEN:
                    processCreditLineFrozen(event, correlationId);
                    break;
                case CREDIT_LINE_UNFROZEN:
                    processCreditLineUnfrozen(event, correlationId);
                    break;
                case CREDIT_LINE_CLOSED:
                    processCreditLineClosed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown credit line event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "CREDIT_LINE_EVENT_PROCESSED",
                event.getCreditLineId() != null ? event.getCreditLineId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "amount", event.getAmount() != null ? event.getAmount().toString() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process credit line event: {}", e.getMessage(), e);
            kafkaTemplate.send("credit-line-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processCreditLineOpened(CreditLineEvent event, String correlationId) {
        log.info("Credit line opened: borrowerId={}, creditLimit={}, interestRate={}, type={}", 
            event.getBorrowerId(), event.getCreditLimit(), event.getInterestRate(), event.getCreditLineType());
        
        CreditLine creditLine = CreditLine.builder()
            .id(UUID.randomUUID().toString())
            .borrowerId(event.getBorrowerId())
            .creditLimit(event.getCreditLimit())
            .availableCredit(event.getCreditLimit())
            .outstandingBalance(BigDecimal.ZERO)
            .interestRate(event.getInterestRate())
            .creditLineType(event.getCreditLineType())
            .openedAt(LocalDateTime.now())
            .status("ACTIVE")
            .utilizationRate(BigDecimal.ZERO)
            .nextReviewDate(LocalDateTime.now().plusMonths(REVIEW_FREQUENCY_MONTHS))
            .correlationId(correlationId)
            .build();
        
        creditLineRepository.save(creditLine);
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Credit Line Opened",
            String.format("Your %s line of credit has been opened! " +
                "Credit limit: %s, Interest rate: %s%% APR. " +
                "Draw funds anytime up to your limit. Interest charged only on amounts drawn.",
                event.getCreditLineType(), event.getCreditLimit(), event.getInterestRate()),
            correlationId
        );
        
        metricsService.recordCreditLineOpened(event.getCreditLineType(), event.getCreditLimit());
    }
    
    private void processFundsDrawn(CreditLineEvent event, String correlationId) {
        log.info("Funds drawn: creditLineId={}, drawAmount={}, purpose={}", 
            event.getCreditLineId(), event.getDrawAmount(), event.getDrawPurpose());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        if (event.getDrawAmount().compareTo(MIN_DRAW_AMOUNT) < 0) {
            log.error("Draw amount below minimum: drawAmount={}, minimum={}", 
                event.getDrawAmount(), MIN_DRAW_AMOUNT);
            return;
        }
        
        if (event.getDrawAmount().compareTo(creditLine.getAvailableCredit()) > 0) {
            log.error("Draw amount exceeds available credit: drawAmount={}, available={}", 
                event.getDrawAmount(), creditLine.getAvailableCredit());
            return;
        }
        
        CreditLineDraw draw = CreditLineDraw.builder()
            .id(UUID.randomUUID().toString())
            .creditLineId(event.getCreditLineId())
            .drawAmount(event.getDrawAmount())
            .drawPurpose(event.getDrawPurpose())
            .interestRate(creditLine.getInterestRate())
            .drawnAt(LocalDateTime.now())
            .outstandingBalance(event.getDrawAmount())
            .correlationId(correlationId)
            .build();
        
        drawRepository.save(draw);
        
        creditLine.setAvailableCredit(
            creditLine.getAvailableCredit().subtract(event.getDrawAmount()));
        creditLine.setOutstandingBalance(
            creditLine.getOutstandingBalance().add(event.getDrawAmount()));
        creditLine.setTotalDraws(creditLine.getTotalDraws() + 1);
        creditLine.setLastDrawDate(LocalDateTime.now());
        
        BigDecimal utilizationRate = creditLine.getOutstandingBalance()
            .divide(creditLine.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP);
        creditLine.setUtilizationRate(utilizationRate);
        
        creditLineRepository.save(creditLine);
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "Funds Drawn from Credit Line",
            String.format("You've drawn %s from your credit line. " +
                "Outstanding balance: %s, Available credit: %s, Utilization: %s%%",
                event.getDrawAmount(), creditLine.getOutstandingBalance(),
                creditLine.getAvailableCredit(), 
                utilizationRate.multiply(new BigDecimal("100"))),
            correlationId
        );
        
        if (utilizationRate.compareTo(UTILIZATION_WARNING_THRESHOLD) > 0) {
            creditLineService.sendUtilizationWarning(creditLine.getId());
        }
        
        metricsService.recordFundsDrawn(event.getDrawAmount(), utilizationRate);
    }
    
    private void processPaymentMade(CreditLineEvent event, String correlationId) {
        log.info("Payment made: creditLineId={}, paymentAmount={}, principalPaid={}, interestPaid={}", 
            event.getCreditLineId(), event.getPaymentAmount(), 
            event.getPrincipalPaid(), event.getInterestPaid());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        creditLine.setOutstandingBalance(
            creditLine.getOutstandingBalance().subtract(event.getPrincipalPaid()));
        creditLine.setAvailableCredit(
            creditLine.getAvailableCredit().add(event.getPrincipalPaid()));
        creditLine.setTotalPaid(
            creditLine.getTotalPaid().add(event.getPaymentAmount()));
        creditLine.setTotalInterestPaid(
            creditLine.getTotalInterestPaid().add(event.getInterestPaid()));
        creditLine.setLastPaymentDate(LocalDateTime.now());
        
        BigDecimal utilizationRate = creditLine.getOutstandingBalance()
            .divide(creditLine.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP);
        creditLine.setUtilizationRate(utilizationRate);
        
        creditLineRepository.save(creditLine);
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "Credit Line Payment Received",
            String.format("Your payment of %s has been applied. " +
                "Principal: %s, Interest: %s. " +
                "Outstanding balance: %s, Available credit: %s",
                event.getPaymentAmount(), event.getPrincipalPaid(), event.getInterestPaid(),
                creditLine.getOutstandingBalance(), creditLine.getAvailableCredit()),
            correlationId
        );
        
        metricsService.recordCreditLinePaymentMade(
            event.getPaymentAmount(), event.getPrincipalPaid(), event.getInterestPaid());
    }
    
    private void processCreditLimitIncreased(CreditLineEvent event, String correlationId) {
        log.info("Credit limit increased: creditLineId={}, oldLimit={}, newLimit={}", 
            event.getCreditLineId(), event.getOldCreditLimit(), event.getNewCreditLimit());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        BigDecimal increase = event.getNewCreditLimit().subtract(event.getOldCreditLimit());
        
        creditLine.setCreditLimit(event.getNewCreditLimit());
        creditLine.setAvailableCredit(
            creditLine.getAvailableCredit().add(increase));
        creditLine.setLastLimitChange(LocalDateTime.now());
        
        BigDecimal utilizationRate = creditLine.getOutstandingBalance()
            .divide(creditLine.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP);
        creditLine.setUtilizationRate(utilizationRate);
        
        creditLineRepository.save(creditLine);
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "Credit Limit Increased",
            String.format("Great news! Your credit limit has been increased from %s to %s. " +
                "Available credit: %s. This is based on your excellent payment history.",
                event.getOldCreditLimit(), event.getNewCreditLimit(), creditLine.getAvailableCredit()),
            correlationId
        );
        
        metricsService.recordCreditLimitIncreased(event.getOldCreditLimit(), event.getNewCreditLimit());
    }
    
    private void processCreditLimitDecreased(CreditLineEvent event, String correlationId) {
        log.info("Credit limit decreased: creditLineId={}, oldLimit={}, newLimit={}, reason={}", 
            event.getCreditLineId(), event.getOldCreditLimit(), 
            event.getNewCreditLimit(), event.getDecreaseReason());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        BigDecimal decrease = event.getOldCreditLimit().subtract(event.getNewCreditLimit());
        
        creditLine.setCreditLimit(event.getNewCreditLimit());
        creditLine.setAvailableCredit(
            creditLine.getAvailableCredit().subtract(decrease));
        creditLine.setLastLimitChange(LocalDateTime.now());
        
        BigDecimal utilizationRate = creditLine.getOutstandingBalance()
            .divide(creditLine.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP);
        creditLine.setUtilizationRate(utilizationRate);
        
        creditLineRepository.save(creditLine);
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "Credit Limit Decreased",
            String.format("Your credit limit has been decreased from %s to %s. Reason: %s. " +
                "Available credit: %s. Contact us to discuss.",
                event.getOldCreditLimit(), event.getNewCreditLimit(), 
                event.getDecreaseReason(), creditLine.getAvailableCredit()),
            correlationId
        );
        
        metricsService.recordCreditLimitDecreased(event.getOldCreditLimit(), event.getNewCreditLimit());
    }
    
    private void processUtilizationHigh(CreditLineEvent event, String correlationId) {
        log.warn("High utilization detected: creditLineId={}, utilization={}%", 
            event.getCreditLineId(), event.getUtilizationRate().multiply(new BigDecimal("100")));
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "High Credit Utilization Alert",
            String.format("Your credit line utilization is %s%% (Outstanding: %s of %s limit). " +
                "High utilization may impact your credit score. " +
                "Consider making a payment to reduce your balance.",
                event.getUtilizationRate().multiply(new BigDecimal("100")),
                creditLine.getOutstandingBalance(), creditLine.getCreditLimit()),
            correlationId
        );
        
        metricsService.recordUtilizationHigh(event.getUtilizationRate());
    }
    
    private void processAnnualReviewCompleted(CreditLineEvent event, String correlationId) {
        log.info("Annual review completed: creditLineId={}, reviewResult={}, newRate={}", 
            event.getCreditLineId(), event.getReviewResult(), event.getNewInterestRate());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        creditLine.setLastReviewDate(LocalDateTime.now());
        creditLine.setNextReviewDate(LocalDateTime.now().plusMonths(REVIEW_FREQUENCY_MONTHS));
        
        if (event.getNewInterestRate() != null && 
            !event.getNewInterestRate().equals(creditLine.getInterestRate())) {
            creditLine.setInterestRate(event.getNewInterestRate());
        }
        
        creditLineRepository.save(creditLine);
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "Credit Line Annual Review",
            String.format("Your annual credit line review is complete. " +
                "Status: %s. Interest rate: %s%% APR. Next review: %s",
                event.getReviewResult(), creditLine.getInterestRate(), 
                creditLine.getNextReviewDate().toLocalDate()),
            correlationId
        );
        
        metricsService.recordAnnualReviewCompleted(event.getReviewResult());
    }
    
    private void processInterestRateAdjusted(CreditLineEvent event, String correlationId) {
        log.info("Interest rate adjusted: creditLineId={}, oldRate={}, newRate={}, reason={}", 
            event.getCreditLineId(), event.getOldInterestRate(), 
            event.getNewInterestRate(), event.getRateChangeReason());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        creditLine.setInterestRate(event.getNewInterestRate());
        creditLine.setLastRateChange(LocalDateTime.now());
        creditLineRepository.save(creditLine);
        
        String changeDirection = event.getNewInterestRate().compareTo(event.getOldInterestRate()) > 0 
            ? "increased" : "decreased";
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "Interest Rate Adjustment",
            String.format("Your credit line interest rate has been %s from %s%% to %s%% APR. " +
                "Reason: %s. This affects future draws and outstanding balance.",
                changeDirection, event.getOldInterestRate(), 
                event.getNewInterestRate(), event.getRateChangeReason()),
            correlationId
        );
        
        metricsService.recordInterestRateAdjusted(event.getOldInterestRate(), event.getNewInterestRate());
    }
    
    private void processCreditLineFrozen(CreditLineEvent event, String correlationId) {
        log.warn("Credit line frozen: creditLineId={}, reason={}", 
            event.getCreditLineId(), event.getFreezeReason());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        creditLine.setStatus("FROZEN");
        creditLine.setFrozenAt(LocalDateTime.now());
        creditLine.setFreezeReason(event.getFreezeReason());
        creditLineRepository.save(creditLine);
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "Credit Line Frozen",
            String.format("Your credit line has been frozen. Reason: %s. " +
                "You cannot draw additional funds. Outstanding balance: %s. " +
                "Contact us immediately to resolve this issue.",
                event.getFreezeReason(), creditLine.getOutstandingBalance()),
            correlationId
        );
        
        metricsService.recordCreditLineFrozen(event.getFreezeReason());
    }
    
    private void processCreditLineUnfrozen(CreditLineEvent event, String correlationId) {
        log.info("Credit line unfrozen: creditLineId={}", event.getCreditLineId());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        creditLine.setStatus("ACTIVE");
        creditLine.setUnfrozenAt(LocalDateTime.now());
        creditLineRepository.save(creditLine);
        
        notificationService.sendNotification(
            creditLine.getBorrowerId(),
            "Credit Line Reactivated",
            String.format("Your credit line has been reactivated. " +
                "Available credit: %s. You can now draw funds again.",
                creditLine.getAvailableCredit()),
            correlationId
        );
        
        metricsService.recordCreditLineUnfrozen();
    }
    
    private void processCreditLineClosed(CreditLineEvent event, String correlationId) {
        log.info("Credit line closed: creditLineId={}, closedBy={}, finalBalance={}", 
            event.getCreditLineId(), event.getClosedBy(), event.getFinalBalance());
        
        CreditLine creditLine = creditLineRepository.findById(event.getCreditLineId())
            .orElseThrow();
        
        creditLine.setStatus("CLOSED");
        creditLine.setClosedAt(LocalDateTime.now());
        creditLine.setClosedBy(event.getClosedBy());
        creditLine.setAvailableCredit(BigDecimal.ZERO);
        creditLineRepository.save(creditLine);
        
        if (event.getFinalBalance().compareTo(BigDecimal.ZERO) > 0) {
            notificationService.sendNotification(
                creditLine.getBorrowerId(),
                "Credit Line Closed",
                String.format("Your credit line has been closed. " +
                    "Final outstanding balance: %s must be paid. " +
                    "Monthly payments continue until balance is $0.",
                    event.getFinalBalance()),
                correlationId
            );
        } else {
            notificationService.sendNotification(
                creditLine.getBorrowerId(),
                "Credit Line Closed",
                "Your credit line has been closed with a zero balance. " +
                    "Thank you for your business!",
                correlationId
            );
        }
        
        metricsService.recordCreditLineClosed(event.getClosedBy(), event.getFinalBalance());
    }
}