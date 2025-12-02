package com.waqiti.lending.kafka;

import com.waqiti.common.alerting.PagerDutyAlertService;
import com.waqiti.common.alerting.SlackNotificationService;
import com.waqiti.common.events.LoanRepaymentEvent;
import com.waqiti.common.kafka.KafkaDlqHandler;
import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.LoanPayment;
import com.waqiti.lending.domain.enums.LoanStatus;
import com.waqiti.lending.repository.LoanRepository;
import com.waqiti.lending.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * INDUSTRIAL-GRADE Loan Repayment Events Consumer
 *
 * Handles loan repayment/payment lifecycle events with enterprise-scale reliability:
 *
 * EVENT TYPES SUPPORTED:
 * - PAYMENT_RECEIVED - Payment successfully received
 * - PAYMENT_APPLIED - Payment applied to loan balance
 * - PAYMENT_FAILED - Payment processing failed
 * - PAYMENT_REVERSED - Payment was reversed/cancelled
 * - PAYMENT_SCHEDULED - Future payment scheduled
 * - EARLY_PAYOFF - Full loan payoff before term
 * - LATE_PAYMENT - Payment received after due date
 * - PARTIAL_PAYMENT - Less than full payment received
 *
 * PRODUCTION-GRADE PATTERNS:
 * ✓ Database-backed idempotency (survives service restart)
 * ✓ Distributed locking (prevents concurrent processing)
 * ✓ SERIALIZABLE transaction isolation (financial safety)
 * ✓ Circuit breaker (prevents cascading failures)
 * ✓ Automatic retry with exponential backoff
 * ✓ Dead Letter Topic (DLT) handling
 * ✓ Prometheus metrics (payments/failures/amounts)
 * ✓ PagerDuty alerting for critical failures
 * ✓ Slack notifications for visibility
 * ✓ Comprehensive audit trail
 * ✓ Correlation ID propagation
 * ✓ Manual intervention workflow
 * ✓ Balance recalculation and validation
 *
 * FINANCIAL OPERATIONS:
 * - Principal/interest allocation
 * - Outstanding balance updates
 * - Delinquency status management
 * - Payment schedule tracking
 * - Early payoff calculation
 * - Late fee assessment
 *
 * @author Lending Service Team
 * @version 3.0 - Industrial-Grade Production Implementation
 * @since 2025-11-08
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoanRepaymentEventsConsumer {

    // Core Services
    private final IdempotencyService idempotencyService;
    private final LoanService loanService;
    private final LoanPaymentService loanPaymentService;
    private final LoanScheduleService loanScheduleService;
    private final LoanAccountService loanAccountService;
    private final NotificationService notificationService;
    private final ManualInterventionService manualInterventionService;
    private final LoanRepository loanRepository;

    // Industrial-Grade Components from Common Module
    private final KafkaDlqHandler dlqHandler;
    private final PagerDutyAlertService pagerDutyAlertService;
    private final SlackNotificationService slackNotificationService;
    private final MeterRegistry meterRegistry;

    // Prometheus Metrics
    private Counter successCounter;
    private Counter failureCounter;
    private Counter criticalFailureCounter;
    private Timer processingTimer;
    private Counter paymentsReceivedCounter;
    private Counter paymentsFailedCounter;
    private Counter earlyPayoffsCounter;
    private Counter latePaymentsCounter;

    @PostConstruct
    public void initializeMetrics() {
        successCounter = Counter.builder("loan.repayment.events.processed.total")
                .description("Total successful repayment event processing")
                .tag("service", "lending-service")
                .register(meterRegistry);

        failureCounter = Counter.builder("loan.repayment.events.failed.total")
                .description("Total failed repayment event processing")
                .tag("service", "lending-service")
                .register(meterRegistry);

        criticalFailureCounter = Counter.builder("loan.repayment.events.critical_failures.total")
                .description("Total critical repayment failures")
                .tag("service", "lending-service")
                .register(meterRegistry);

        processingTimer = Timer.builder("loan.repayment.events.processing.duration")
                .description("Duration of repayment event processing")
                .tag("service", "lending-service")
                .register(meterRegistry);

        paymentsReceivedCounter = Counter.builder("loan.payments.received.total")
                .description("Total payments received")
                .tag("service", "lending-service")
                .register(meterRegistry);

        paymentsFailedCounter = Counter.builder("loan.payments.failed.total")
                .description("Total failed payments")
                .tag("service", "lending-service")
                .register(meterRegistry);

        earlyPayoffsCounter = Counter.builder("loan.early_payoffs.total")
                .description("Total early loan payoffs")
                .tag("service", "lending-service")
                .register(meterRegistry);

        latePaymentsCounter = Counter.builder("loan.late_payments.total")
                .description("Total late payments")
                .tag("service", "lending-service")
                .register(meterRegistry);

        log.info("Prometheus metrics initialized for LoanRepaymentEventsConsumer");
    }

    /**
     * Main Kafka listener with full enterprise resilience patterns
     */
    @KafkaListener(
            topics = "${kafka.topics.loan-repayment-events:loan-repayment-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 16000),
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            include = {Exception.class}
    )
    @CircuitBreaker(name = "loan-repayment-events", fallbackMethod = "handleRepaymentEventFallback")
    @Retry(name = "loan-repayment-events")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Timed(value = "loan.repayment.events.processing", description = "Time to process repayment events")
    @Counted(value = "loan.repayment.events.invocations", description = "Number of repayment event invocations")
    public void handleLoanRepaymentEvent(
            @Payload LoanRepaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = event.getEventId();
        String loanId = event.getLoanId();
        String paymentId = event.getPaymentId();
        String eventType = event.getEventType();
        String correlationId = String.format("repayment-%s-%s-%d", loanId, paymentId, System.currentTimeMillis());

        log.info("╔═══════════════════════════════════════════════════════════════════════════╗");
        log.info("║  REPAYMENT EVENT PROCESSING STARTED                                       ║");
        log.info("╠═══════════════════════════════════════════════════════════════════════════╣");
        log.info("║  EventID:       {}", String.format("%-59s", eventId) + "║");
        log.info("║  LoanID:        {}", String.format("%-59s", loanId) + "║");
        log.info("║  PaymentID:     {}", String.format("%-59s", paymentId) + "║");
        log.info("║  EventType:     {}", String.format("%-59s", eventType) + "║");
        log.info("║  Amount:        {}", String.format("%-59s", event.getPaymentAmount()) + "║");
        log.info("║  CorrelationID: {}", String.format("%-59s", correlationId) + "║");
        log.info("╚═══════════════════════════════════════════════════════════════════════════╝");

        try {
            // STEP 1: Idempotency Check
            if (idempotencyService.isEventProcessed(eventId)) {
                log.warn("⚠ Event already processed (idempotent), skipping: {}", eventId);
                acknowledgment.acknowledge();
                sample.stop(processingTimer);
                return;
            }

            // STEP 2: Acquire Distributed Lock
            if (!idempotencyService.tryAcquire("repayment:" + loanId, Duration.ofMinutes(5))) {
                log.warn("⚠ Could not acquire distributed lock for loan: {}, will retry", loanId);
                throw new RuntimeException("Lock acquisition failed for loan: " + loanId);
            }

            try {
                // STEP 3: Load Loan
                Loan loan = loanRepository.findByLoanId(loanId)
                        .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));

                log.info("→ Processing repayment for Loan: {} (Borrower: {}, Outstanding: {})",
                        loanId, loan.getBorrowerId(), loan.getOutstandingBalance());

                // STEP 4: Route to Event Handler
                processEventByType(event, loan, correlationId);

                // STEP 5: Mark Event as Processed
                idempotencyService.markEventAsProcessed(eventId, "LoanRepaymentEvent", loanId);

                // STEP 6: Send Success Notification
                sendSlackNotification(event, loan, "SUCCESS", correlationId);

                // STEP 7: Acknowledge Kafka Message
                acknowledgment.acknowledge();

                // STEP 8: Record Success Metrics
                successCounter.increment();
                sample.stop(processingTimer);

                log.info("╔═══════════════════════════════════════════════════════════════════════════╗");
                log.info("║  ✓ REPAYMENT EVENT PROCESSING COMPLETED SUCCESSFULLY                      ║");
                log.info("╚═══════════════════════════════════════════════════════════════════════════╝");

            } finally {
                idempotencyService.release("repayment:" + loanId);
            }

        } catch (Exception e) {
            failureCounter.increment();
            sample.stop(processingTimer);

            log.error("╔═══════════════════════════════════════════════════════════════════════════╗");
            log.error("║  ✗ REPAYMENT EVENT PROCESSING FAILED                                      ║");
            log.error("╠═══════════════════════════════════════════════════════════════════════════╣");
            log.error("║  EventID:   {}", String.format("%-63s", eventId) + "║");
            log.error("║  LoanID:    {}", String.format("%-63s", loanId) + "║");
            log.error("║  PaymentID: {}", String.format("%-63s", paymentId) + "║");
            log.error("║  Error:     {}", String.format("%-63s", e.getMessage()) + "║");
            log.error("╚═══════════════════════════════════════════════════════════════════════════╝", e);

            handleProcessingFailure(event, e, correlationId);
            throw new RuntimeException("Repayment event processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Route event to appropriate handler
     */
    private void processEventByType(LoanRepaymentEvent event, Loan loan, String correlationId) {
        switch (event.getEventType()) {
            case "PAYMENT_RECEIVED":
                handlePaymentReceived(event, loan, correlationId);
                break;
            case "PAYMENT_APPLIED":
                handlePaymentApplied(event, loan, correlationId);
                break;
            case "PAYMENT_FAILED":
                handlePaymentFailed(event, loan, correlationId);
                break;
            case "PAYMENT_REVERSED":
                handlePaymentReversed(event, loan, correlationId);
                break;
            case "EARLY_PAYOFF":
                handleEarlyPayoff(event, loan, correlationId);
                break;
            case "LATE_PAYMENT":
                handleLatePayment(event, loan, correlationId);
                break;
            case "PARTIAL_PAYMENT":
                handlePartialPayment(event, loan, correlationId);
                break;
            default:
                log.warn("⚠ Unknown repayment event type: {}", event.getEventType());
                break;
        }
    }

    /**
     * Circuit Breaker Fallback
     */
    public void handleRepaymentEventFallback(
            LoanRepaymentEvent event,
            String key,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception exception) {

        log.error("⚡ CIRCUIT BREAKER TRIGGERED - Repayment event processing degraded");
        log.error("LoanID: {}, PaymentID: {}, Error: {}", event.getLoanId(), event.getPaymentId(), exception.getMessage());

        try {
            pagerDutyAlertService.sendCriticalAlert(
                    "Loan Repayment Circuit Breaker Triggered",
                    String.format("Circuit breaker opened for loan %s payment %s - System degraded. Error: %s",
                            event.getLoanId(), event.getPaymentId(), exception.getMessage()),
                    Map.of("loanId", event.getLoanId(), "paymentId", event.getPaymentId(), "error", exception.getMessage())
            );
        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert", e);
        }

        acknowledgment.acknowledge();
    }

    /**
     * Dead Letter Topic Handler
     */
    @DltHandler
    public void handleDltLoanRepaymentEvent(
            @Payload LoanRepaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {

        String correlationId = String.format("dlt-%s-%d", event.getLoanId(), System.currentTimeMillis());
        criticalFailureCounter.increment();
        paymentsFailedCounter.increment();

        log.error("╔═══════════════════════════════════════════════════════════════════════════╗");
        log.error("║  ☠ DEAD LETTER TOPIC - REPAYMENT PERMANENT FAILURE                        ║");
        log.error("╠═══════════════════════════════════════════════════════════════════════════╣");
        log.error("║  LoanID:    {}", String.format("%-63s", event.getLoanId()) + "║");
        log.error("║  PaymentID: {}", String.format("%-63s", event.getPaymentId()) + "║");
        log.error("║  Amount:    {}", String.format("%-63s", event.getPaymentAmount()) + "║");
        log.error("║  Error:     {}", String.format("%-63s", exceptionMessage) + "║");
        log.error("╚═══════════════════════════════════════════════════════════════════════════╝");

        loanAccountService.createAuditEntry(
                event.getLoanId(),
                "REPAYMENT_DLT_EVENT",
                event,
                String.format("CRITICAL: Repayment event sent to DLT - Payment: %s, Amount: %s, Error: %s",
                        event.getPaymentId(), event.getPaymentAmount(), exceptionMessage)
        );

        try {
            pagerDutyAlertService.sendCriticalAlert(
                    "🚨 CRITICAL: Loan Repayment Permanent Failure",
                    String.format("Payment %s for loan %s failed permanently. Amount: %s. IMMEDIATE ACTION REQUIRED.",
                            event.getPaymentId(), event.getLoanId(), event.getPaymentAmount()),
                    Map.of(
                            "loanId", event.getLoanId(),
                            "paymentId", event.getPaymentId(),
                            "amount", event.getPaymentAmount().toString(),
                            "error", exceptionMessage != null ? exceptionMessage : "Unknown",
                            "correlationId", correlationId,
                            "severity", "CRITICAL"
                    )
            );
        } catch (Exception e) {
            log.error("Failed to send DLT PagerDuty alert", e);
        }

        try {
            manualInterventionService.createPaymentFailureTask(
                    event.getLoanId(),
                    event.getPaymentId(),
                    event.getPaymentAmount(),
                    String.format("DLT PERMANENT FAILURE: %s", exceptionMessage)
            );
        } catch (Exception e) {
            log.error("Failed to create DLT manual intervention task", e);
        }
    }

    // ==================== EVENT HANDLERS ====================

    private void handlePaymentReceived(LoanRepaymentEvent event, Loan loan, String correlationId) {
        log.info("✓ PAYMENT_RECEIVED - Amount: {}", event.getPaymentAmount());

        LoanPayment payment = loanPaymentService.processPayment(
                loan.getLoanId(),
                loan.getBorrowerId(),
                event.getPaymentAmount(),
                event.getPaymentMethod(),
                event.isAutopay()
        );

        paymentsReceivedCounter.increment();

        notificationService.sendPaymentReceivedNotification(
                loan.getBorrowerId(),
                loan.getLoanId(),
                event.getPaymentAmount(),
                loan.getOutstandingBalance()
        );

        loanAccountService.createAuditEntry(loan.getLoanId(), "PAYMENT_RECEIVED", event,
                String.format("Payment received - PaymentID: %s, Amount: %s, CorrelationID: %s",
                        payment.getPaymentId(), event.getPaymentAmount(), correlationId));
    }

    private void handlePaymentApplied(LoanRepaymentEvent event, Loan loan, String correlationId) {
        log.info("✓ PAYMENT_APPLIED - Amount: {}, NewBalance: {}", event.getPaymentAmount(), event.getNewBalance());

        BigDecimal previousBalance = loan.getOutstandingBalance();
        loan.setOutstandingBalance(event.getNewBalance());
        loan.setLastPaymentDate(Instant.now());

        // Check if loan is now paid off
        if (event.getNewBalance().compareTo(BigDecimal.ZERO) == 0) {
            loan.setLoanStatus(LoanStatus.PAID_OFF);
            loan.setPaidOffAt(Instant.now());
            log.info("🎉 LOAN PAID OFF - LoanID: {}", loan.getLoanId());

            notificationService.sendLoanPaidOffNotification(
                    loan.getBorrowerId(),
                    loan.getLoanId(),
                    loan.getPrincipalAmount()
            );
        } else if (loan.getLoanStatus() == LoanStatus.DELINQUENT && event.getNewBalance().compareTo(previousBalance) < 0) {
            // Payment made on delinquent loan - update status to current
            loan.setLoanStatus(LoanStatus.CURRENT);
        }

        loanRepository.save(loan);

        loanAccountService.createAuditEntry(loan.getLoanId(), "PAYMENT_APPLIED", event,
                String.format("Payment applied - Amount: %s, PreviousBalance: %s, NewBalance: %s, CorrelationID: %s",
                        event.getPaymentAmount(), previousBalance, event.getNewBalance(), correlationId));
    }

    private void handlePaymentFailed(LoanRepaymentEvent event, Loan loan, String correlationId) {
        log.error("✗ PAYMENT_FAILED - Amount: {}, Reason: {}", event.getPaymentAmount(), event.getFailureReason());

        paymentsFailedCounter.increment();

        loanPaymentService.markPaymentAsFailed(event.getPaymentId(), event.getFailureReason());

        String taskId = manualInterventionService.createPaymentFailureTask(
                loan.getLoanId(),
                event.getPaymentId(),
                event.getPaymentAmount(),
                event.getFailureReason()
        );

        notificationService.sendPaymentFailedNotification(
                loan.getBorrowerId(),
                loan.getLoanId(),
                event.getPaymentAmount(),
                event.getFailureReason()
        );

        loanAccountService.createAuditEntry(loan.getLoanId(), "PAYMENT_FAILED", event,
                String.format("CRITICAL: Payment failed - PaymentID: %s, Amount: %s, Reason: %s, TaskID: %s, CorrelationID: %s",
                        event.getPaymentId(), event.getPaymentAmount(), event.getFailureReason(), taskId, correlationId));

        try {
            pagerDutyAlertService.sendErrorAlert(
                    "Loan Payment Failed",
                    String.format("Payment failed for loan %s - Amount: %s, Reason: %s",
                            loan.getLoanId(), event.getPaymentAmount(), event.getFailureReason()),
                    Map.of("loanId", loan.getLoanId(), "amount", event.getPaymentAmount().toString(),
                            "reason", event.getFailureReason(), "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert", e);
        }
    }

    private void handlePaymentReversed(LoanRepaymentEvent event, Loan loan, String correlationId) {
        log.warn("⚠ PAYMENT_REVERSED - PaymentID: {}, Amount: {}", event.getPaymentId(), event.getPaymentAmount());

        BigDecimal previousBalance = loan.getOutstandingBalance();
        loan.setOutstandingBalance(previousBalance.add(event.getPaymentAmount()));
        loanRepository.save(loan);

        notificationService.sendPaymentReversedNotification(
                loan.getBorrowerId(),
                loan.getLoanId(),
                event.getPaymentAmount(),
                event.getReversalReason()
        );

        loanAccountService.createAuditEntry(loan.getLoanId(), "PAYMENT_REVERSED", event,
                String.format("Payment reversed - PaymentID: %s, Amount: %s, Reason: %s, NewBalance: %s, CorrelationID: %s",
                        event.getPaymentId(), event.getPaymentAmount(), event.getReversalReason(),
                        loan.getOutstandingBalance(), correlationId));
    }

    private void handleEarlyPayoff(LoanRepaymentEvent event, Loan loan, String correlationId) {
        log.info("🎉 EARLY_PAYOFF - LoanID: {}, Amount: {}", loan.getLoanId(), event.getPaymentAmount());

        earlyPayoffsCounter.increment();

        LoanPayment payoffPayment = loanPaymentService.processEarlyPayoff(
                loan.getLoanId(),
                loan.getBorrowerId(),
                event.getPaymentMethod()
        );

        loan.setLoanStatus(LoanStatus.PAID_OFF);
        loan.setPaidOffAt(Instant.now());
        loan.setOutstandingBalance(BigDecimal.ZERO);
        loanRepository.save(loan);

        notificationService.sendLoanPaidOffNotification(
                loan.getBorrowerId(),
                loan.getLoanId(),
                loan.getPrincipalAmount()
        );

        loanAccountService.createAuditEntry(loan.getLoanId(), "EARLY_PAYOFF", event,
                String.format("Early payoff completed - PaymentID: %s, Amount: %s, CorrelationID: %s",
                        payoffPayment.getPaymentId(), event.getPaymentAmount(), correlationId));
    }

    private void handleLatePayment(LoanRepaymentEvent event, Loan loan, String correlationId) {
        log.warn("⚠ LATE_PAYMENT - LoanID: {}, DaysLate: {}", loan.getLoanId(), event.getDaysLate());

        latePaymentsCounter.increment();

        LoanPayment payment = loanPaymentService.processPayment(
                loan.getLoanId(),
                loan.getBorrowerId(),
                event.getPaymentAmount(),
                event.getPaymentMethod(),
                false
        );

        // Apply late fee if applicable
        BigDecimal lateFee = event.getLateFee();
        if (lateFee != null && lateFee.compareTo(BigDecimal.ZERO) > 0) {
            loan.setOutstandingBalance(loan.getOutstandingBalance().add(lateFee));
        }

        // Update delinquency status if needed
        if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0) {
            loan.setLoanStatus(LoanStatus.DELINQUENT);
        }

        loanRepository.save(loan);

        notificationService.sendLatePaymentNotification(
                loan.getBorrowerId(),
                loan.getLoanId(),
                event.getPaymentAmount(),
                event.getDaysLate(),
                lateFee
        );

        loanAccountService.createAuditEntry(loan.getLoanId(), "LATE_PAYMENT", event,
                String.format("Late payment received - PaymentID: %s, Amount: %s, DaysLate: %d, LateFee: %s, CorrelationID: %s",
                        payment.getPaymentId(), event.getPaymentAmount(), event.getDaysLate(), lateFee, correlationId));
    }

    private void handlePartialPayment(LoanRepaymentEvent event, Loan loan, String correlationId) {
        log.warn("⚠ PARTIAL_PAYMENT - Expected: {}, Received: {}",
                event.getExpectedAmount(), event.getPaymentAmount());

        LoanPayment payment = loanPaymentService.processPayment(
                loan.getLoanId(),
                loan.getBorrowerId(),
                event.getPaymentAmount(),
                event.getPaymentMethod(),
                false
        );

        BigDecimal shortfall = event.getExpectedAmount().subtract(event.getPaymentAmount());

        notificationService.sendPartialPaymentNotification(
                loan.getBorrowerId(),
                loan.getLoanId(),
                event.getPaymentAmount(),
                event.getExpectedAmount(),
                shortfall
        );

        loanAccountService.createAuditEntry(loan.getLoanId(), "PARTIAL_PAYMENT", event,
                String.format("Partial payment received - PaymentID: %s, Received: %s, Expected: %s, Shortfall: %s, CorrelationID: %s",
                        payment.getPaymentId(), event.getPaymentAmount(), event.getExpectedAmount(), shortfall, correlationId));
    }

    // ==================== HELPER METHODS ====================

    private void handleProcessingFailure(LoanRepaymentEvent event, Exception exception, String correlationId) {
        try {
            manualInterventionService.createPaymentFailureTask(
                    event.getLoanId(),
                    event.getPaymentId(),
                    event.getPaymentAmount(),
                    "Processing failed: " + exception.getMessage() + " - CorrelationID: " + correlationId
            );
        } catch (Exception e) {
            log.error("Failed to create manual intervention task", e);
        }
    }

    private void sendSlackNotification(LoanRepaymentEvent event, Loan loan, String status, String correlationId) {
        try {
            slackNotificationService.sendNotification(
                    "#loan-payments",
                    String.format("Payment %s: Loan %s - %s - Amount: %s - CorrelationID: %s",
                            status, loan.getLoanId(), event.getEventType(), event.getPaymentAmount(), correlationId)
            );
        } catch (Exception e) {
            log.warn("Failed to send Slack notification", e);
        }
    }
}
