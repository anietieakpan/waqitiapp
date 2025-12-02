package com.waqiti.lending.kafka;

import com.waqiti.common.alerting.PagerDutyAlertService;
import com.waqiti.common.alerting.SlackNotificationService;
import com.waqiti.common.events.LoanDisbursementEvent;
import com.waqiti.common.kafka.KafkaDlqHandler;
import com.waqiti.lending.domain.Loan;
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
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import java.util.HashMap;
import java.util.Map;

/**
 * INDUSTRIAL-GRADE Loan Disbursement Events Consumer
 *
 * Handles loan disbursement lifecycle events with enterprise-scale reliability:
 *
 * EVENT TYPES SUPPORTED:
 * - DISBURSEMENT_INITIATED - Process started
 * - DISBURSEMENT_PENDING - Awaiting approval/validation
 * - DISBURSEMENT_APPROVED - Approved for processing
 * - DISBURSEMENT_IN_PROGRESS - Funds being transferred
 * - DISBURSEMENT_COMPLETED - Successfully disbursed ✓
 * - DISBURSEMENT_FAILED - Failed, requires intervention ✗
 * - DISBURSEMENT_CANCELLED - Cancelled
 * - PARTIAL_DISBURSEMENT - Partial funds disbursed
 *
 * PRODUCTION-GRADE PATTERNS:
 * ✓ Database-backed idempotency (survives service restart)
 * ✓ Distributed locking (prevents concurrent processing)
 * ✓ SERIALIZABLE transaction isolation (financial safety)
 * ✓ Circuit breaker (prevents cascading failures)
 * ✓ Automatic retry with exponential backoff
 * ✓ Dead Letter Topic (DLT) handling
 * ✓ Prometheus metrics (success/failure/duration)
 * ✓ PagerDuty alerting for critical failures
 * ✓ Slack notifications for visibility
 * ✓ Comprehensive audit trail
 * ✓ Correlation ID propagation
 * ✓ Manual intervention workflow
 *
 * RESILIENCE STRATEGY:
 * - 5 automatic retries with exponential backoff (1s, 2s, 4s, 8s, 16s)
 * - Circuit breaker opens after 50% failure rate
 * - DLT for permanent failures requiring manual recovery
 * - PagerDuty alert for CRITICAL disbursement failures
 *
 * OBSERVABILITY:
 * - Metrics: loan.disbursement.events.{processed|failed|duration}
 * - Alerts: PagerDuty (CRITICAL), Slack (all events)
 * - Audit: Full event trail in database
 *
 * @author Lending Service Team
 * @version 3.0 - Industrial-Grade Production Implementation
 * @since 2025-11-08
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoanDisbursementEventsConsumer {

    // Core Services
    private final IdempotencyService idempotencyService;
    private final LoanService loanService;
    private final LoanDisbursementService disbursementService;
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
    private Counter disbursementCompletedCounter;
    private Counter disbursementFailedCounter;

    @PostConstruct
    public void initializeMetrics() {
        successCounter = Counter.builder("loan.disbursement.events.processed.total")
                .description("Total successful disbursement event processing")
                .tag("service", "lending-service")
                .tag("event_type", "loan_disbursement")
                .register(meterRegistry);

        failureCounter = Counter.builder("loan.disbursement.events.failed.total")
                .description("Total failed disbursement event processing")
                .tag("service", "lending-service")
                .tag("event_type", "loan_disbursement")
                .register(meterRegistry);

        criticalFailureCounter = Counter.builder("loan.disbursement.events.critical_failures.total")
                .description("Total critical disbursement failures requiring immediate attention")
                .tag("service", "lending-service")
                .tag("severity", "critical")
                .register(meterRegistry);

        processingTimer = Timer.builder("loan.disbursement.events.processing.duration")
                .description("Duration of disbursement event processing")
                .tag("service", "lending-service")
                .register(meterRegistry);

        disbursementCompletedCounter = Counter.builder("loan.disbursement.completed.total")
                .description("Total completed loan disbursements")
                .tag("service", "lending-service")
                .register(meterRegistry);

        disbursementFailedCounter = Counter.builder("loan.disbursement.failed.total")
                .description("Total failed loan disbursements")
                .tag("service", "lending-service")
                .register(meterRegistry);

        log.info("Prometheus metrics initialized for LoanDisbursementEventsConsumer");
    }

    /**
     * Main Kafka listener with full enterprise resilience patterns
     */
    @KafkaListener(
            topics = "${kafka.topics.loan-disbursement-events:loan-disbursement-events}",
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
    @CircuitBreaker(name = "loan-disbursement-events", fallbackMethod = "handleDisbursementEventFallback")
    @Retry(name = "loan-disbursement-events")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Timed(value = "loan.disbursement.events.processing", description = "Time to process disbursement events")
    @Counted(value = "loan.disbursement.events.invocations", description = "Number of disbursement event invocations")
    public void handleLoanDisbursementEvent(
            @Payload LoanDisbursementEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = event.getEventId();
        String loanId = event.getLoanId();
        String eventType = event.getEventType();
        String correlationId = String.format("disbursement-%s-%s-%d", loanId, eventType, System.currentTimeMillis());

        log.info("╔═══════════════════════════════════════════════════════════════════════════╗");
        log.info("║  DISBURSEMENT EVENT PROCESSING STARTED                                    ║");
        log.info("╠═══════════════════════════════════════════════════════════════════════════╣");
        log.info("║  EventID:       {}", String.format("%-59s", eventId) + "║");
        log.info("║  LoanID:        {}", String.format("%-59s", loanId) + "║");
        log.info("║  EventType:     {}", String.format("%-59s", eventType) + "║");
        log.info("║  CorrelationID: {}", String.format("%-59s", correlationId) + "║");
        log.info("║  Partition:     {}", String.format("%-59s", partition) + "║");
        log.info("║  Offset:        {}", String.format("%-59s", offset) + "║");
        log.info("╚═══════════════════════════════════════════════════════════════════════════╝");

        try {
            // STEP 1: Idempotency Check (Database-backed)
            if (idempotencyService.isEventProcessed(eventId)) {
                log.warn("⚠ Event already processed (idempotent), skipping: {}", eventId);
                acknowledgment.acknowledge();
                sample.stop(processingTimer);
                return;
            }

            // STEP 2: Acquire Distributed Lock (Redis-backed)
            Duration lockTimeout = Duration.ofMinutes(5);
            if (!idempotencyService.tryAcquire("disbursement:" + loanId, lockTimeout)) {
                log.warn("⚠ Could not acquire distributed lock for loan: {}, will retry", loanId);
                throw new RuntimeException("Lock acquisition failed for loan: " + loanId);
            }

            try {
                // STEP 3: Load Loan from Database
                Loan loan = loanRepository.findByLoanId(loanId)
                        .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));

                log.info("→ Processing disbursement for Loan: {} (Borrower: {}, Amount: {})",
                        loanId, loan.getBorrowerId(), loan.getPrincipalAmount());

                // STEP 4: Route to Event Handler
                processEventByType(event, loan, correlationId);

                // STEP 5: Mark Event as Processed (Idempotency)
                idempotencyService.markEventAsProcessed(eventId, "LoanDisbursementEvent", loanId);

                // STEP 6: Send Success Notification to Slack
                sendSlackNotification(event, loan, "SUCCESS", correlationId);

                // STEP 7: Acknowledge Kafka Message
                acknowledgment.acknowledge();

                // STEP 8: Record Success Metrics
                successCounter.increment();
                sample.stop(processingTimer);

                log.info("╔═══════════════════════════════════════════════════════════════════════════╗");
                log.info("║  ✓ DISBURSEMENT EVENT PROCESSING COMPLETED SUCCESSFULLY                   ║");
                log.info("╠═══════════════════════════════════════════════════════════════════════════╣");
                log.info("║  EventID:       {}", String.format("%-59s", eventId) + "║");
                log.info("║  LoanID:        {}", String.format("%-59s", loanId) + "║");
                log.info("║  EventType:     {}", String.format("%-59s", eventType) + "║");
                log.info("╚═══════════════════════════════════════════════════════════════════════════╝");

            } finally {
                // Release Distributed Lock
                idempotencyService.release("disbursement:" + loanId);
            }

        } catch (Exception e) {
            failureCounter.increment();
            sample.stop(processingTimer);

            log.error("╔═══════════════════════════════════════════════════════════════════════════╗");
            log.error("║  ✗ DISBURSEMENT EVENT PROCESSING FAILED                                   ║");
            log.error("╠═══════════════════════════════════════════════════════════════════════════╣");
            log.error("║  EventID:       {}", String.format("%-59s", eventId) + "║");
            log.error("║  LoanID:        {}", String.format("%-59s", loanId) + "║");
            log.error("║  EventType:     {}", String.format("%-59s", eventType) + "║");
            log.error("║  Error:         {}", String.format("%-59s", e.getMessage()) + "║");
            log.error("╚═══════════════════════════════════════════════════════════════════════════╝", e);

            // Create Critical Manual Intervention Task
            handleProcessingFailure(event, e, correlationId);

            // Re-throw for retry mechanism and DLT
            throw new RuntimeException("Disbursement event processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Route event to appropriate handler based on type
     */
    private void processEventByType(LoanDisbursementEvent event, Loan loan, String correlationId) {
        switch (event.getEventType()) {
            case "DISBURSEMENT_INITIATED":
                handleDisbursementInitiated(event, loan, correlationId);
                break;
            case "DISBURSEMENT_PENDING":
                handleDisbursementPending(event, loan, correlationId);
                break;
            case "DISBURSEMENT_APPROVED":
                handleDisbursementApproved(event, loan, correlationId);
                break;
            case "DISBURSEMENT_IN_PROGRESS":
                handleDisbursementInProgress(event, loan, correlationId);
                break;
            case "DISBURSEMENT_COMPLETED":
                handleDisbursementCompleted(event, loan, correlationId);
                break;
            case "DISBURSEMENT_FAILED":
                handleDisbursementFailed(event, loan, correlationId);
                break;
            case "DISBURSEMENT_CANCELLED":
                handleDisbursementCancelled(event, loan, correlationId);
                break;
            case "PARTIAL_DISBURSEMENT":
                handlePartialDisbursement(event, loan, correlationId);
                break;
            default:
                log.warn("⚠ Unknown disbursement event type: {}", event.getEventType());
                break;
        }
    }

    /**
     * Circuit Breaker Fallback Handler
     */
    public void handleDisbursementEventFallback(
            LoanDisbursementEvent event,
            String key,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception exception) {

        String correlationId = String.format("fallback-%s-%d", event.getLoanId(), System.currentTimeMillis());

        log.error("⚡ CIRCUIT BREAKER TRIGGERED - Disbursement event processing degraded");
        log.error("LoanID: {}, Error: {}", event.getLoanId(), exception.getMessage());

        // Send CRITICAL PagerDuty Alert
        try {
            pagerDutyAlertService.sendCriticalAlert(
                    "Loan Disbursement Circuit Breaker Triggered",
                    String.format("Circuit breaker opened for loan %s - System is degraded. Error: %s",
                            event.getLoanId(), exception.getMessage()),
                    Map.of(
                            "loanId", event.getLoanId(),
                            "eventType", event.getEventType(),
                            "error", exception.getMessage(),
                            "correlationId", correlationId
                    )
            );
        } catch (Exception alertException) {
            log.error("Failed to send PagerDuty alert", alertException);
        }

        // Acknowledge to prevent infinite retry
        acknowledgment.acknowledge();
    }

    /**
     * Dead Letter Topic Handler
     * Handles messages that failed all retry attempts
     */
    @DltHandler
    public void handleDltLoanDisbursementEvent(
            @Payload LoanDisbursementEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {

        String correlationId = String.format("dlt-%s-%d", event.getLoanId(), System.currentTimeMillis());

        criticalFailureCounter.increment();
        disbursementFailedCounter.increment();

        log.error("╔═══════════════════════════════════════════════════════════════════════════╗");
        log.error("║  ☠ DEAD LETTER TOPIC - PERMANENT FAILURE                                  ║");
        log.error("╠═══════════════════════════════════════════════════════════════════════════╣");
        log.error("║  LoanID:        {}", String.format("%-59s", event.getLoanId()) + "║");
        log.error("║  EventType:     {}", String.format("%-59s", event.getEventType()) + "║");
        log.error("║  OriginalTopic: {}", String.format("%-59s", topic) + "║");
        log.error("║  Error:         {}", String.format("%-59s", exceptionMessage) + "║");
        log.error("║  CorrelationID: {}", String.format("%-59s", correlationId) + "║");
        log.error("╚═══════════════════════════════════════════════════════════════════════════╝");

        // Create CRITICAL audit entry
        loanAccountService.createAuditEntry(
                event.getLoanId(),
                "DISBURSEMENT_DLT_EVENT",
                event,
                String.format("CRITICAL: Disbursement event sent to DLT - Requires immediate manual intervention. Error: %s, CorrelationID: %s",
                        exceptionMessage, correlationId)
        );

        // Send CRITICAL PagerDuty Alert (on-call will be paged)
        try {
            pagerDutyAlertService.sendCriticalAlert(
                    "🚨 CRITICAL: Loan Disbursement Permanent Failure",
                    String.format("Loan %s disbursement failed permanently after all retries. IMMEDIATE ACTION REQUIRED. Error: %s",
                            event.getLoanId(), exceptionMessage),
                    Map.of(
                            "loanId", event.getLoanId(),
                            "eventType", event.getEventType(),
                            "amount", event.getAmount().toString(),
                            "customerId", event.getCustomerId().toString(),
                            "originalTopic", topic,
                            "error", exceptionMessage != null ? exceptionMessage : "Unknown",
                            "correlationId", correlationId,
                            "severity", "CRITICAL",
                            "requiresImmediateAction", "true"
                    )
            );
        } catch (Exception alertException) {
            log.error("Failed to send DLT PagerDuty alert", alertException);
        }

        // Create HIGH-PRIORITY manual intervention task
        try {
            manualInterventionService.createDisbursementFailureTask(
                    event.getLoanId(),
                    event.getCustomerId(),
                    event.getAmount(),
                    event.getDisbursementMethod(),
                    String.format("DLT PERMANENT FAILURE: %s - CorrelationID: %s", exceptionMessage, correlationId)
            );
        } catch (Exception taskException) {
            log.error("Failed to create DLT manual intervention task", taskException);
        }
    }

    // ==================== EVENT HANDLERS ====================

    private void handleDisbursementInitiated(LoanDisbursementEvent event, Loan loan, String correlationId) {
        log.info("→ DISBURSEMENT_INITIATED");
        loan.setLoanStatus(LoanStatus.PENDING_DISBURSEMENT);
        loanRepository.save(loan);
        loanAccountService.createAuditEntry(loan.getLoanId(), "DISBURSEMENT_INITIATED", event,
                "Disbursement process initiated - CorrelationID: " + correlationId);
    }

    private void handleDisbursementPending(LoanDisbursementEvent event, Loan loan, String correlationId) {
        log.info("→ DISBURSEMENT_PENDING: {}", event.getPendingReason());
        loan.setLoanStatus(LoanStatus.PENDING_DISBURSEMENT);
        loanRepository.save(loan);
        notificationService.sendLoanDisbursementPendingNotification(
                loan.getBorrowerId(), loan.getLoanId(), event.getAmount(), event.getPendingReason());
        loanAccountService.createAuditEntry(loan.getLoanId(), "DISBURSEMENT_PENDING", event,
                "Disbursement pending: " + event.getPendingReason() + " - CorrelationID: " + correlationId);
    }

    private void handleDisbursementApproved(LoanDisbursementEvent event, Loan loan, String correlationId) {
        log.info("→ DISBURSEMENT_APPROVED");
        loan.setLoanStatus(LoanStatus.APPROVED);
        loanRepository.save(loan);
        loanAccountService.createAuditEntry(loan.getLoanId(), "DISBURSEMENT_APPROVED", event,
                "Disbursement approved - CorrelationID: " + correlationId);
    }

    private void handleDisbursementInProgress(LoanDisbursementEvent event, Loan loan, String correlationId) {
        log.info("→ DISBURSEMENT_IN_PROGRESS");
        loan.setLoanStatus(LoanStatus.PENDING_DISBURSEMENT);
        loanRepository.save(loan);
        notificationService.sendLoanDisbursementInProgressNotification(
                loan.getBorrowerId(), loan.getLoanId(), event.getAmount());
        loanAccountService.createAuditEntry(loan.getLoanId(), "DISBURSEMENT_IN_PROGRESS", event,
                "Disbursement in progress - CorrelationID: " + correlationId);
    }

    private void handleDisbursementCompleted(LoanDisbursementEvent event, Loan loan, String correlationId) {
        log.info("✓ DISBURSEMENT_COMPLETED - Amount: {}", event.getAmount());

        loan.setDisbursementId(event.getDisbursementId());
        loan.setDisbursedAt(Instant.now());
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loanRepository.save(loan);

        disbursementCompletedCounter.increment();

        notificationService.sendLoanDisbursementNotification(
                loan.getBorrowerId(), loan.getLoanId(), event.getAmount());

        if (loan.getFirstPaymentDate() != null) {
            notificationService.sendFirstPaymentReminder(
                    loan.getBorrowerId(), loan.getLoanId(), loan.getMonthlyPayment(), loan.getFirstPaymentDate());
        }

        loanAccountService.createAuditEntry(loan.getLoanId(), "DISBURSEMENT_COMPLETED", event,
                String.format("Disbursement completed - DisbursementID: %s, Amount: %s, CorrelationID: %s",
                        event.getDisbursementId(), event.getAmount(), correlationId));
    }

    private void handleDisbursementFailed(LoanDisbursementEvent event, Loan loan, String correlationId) {
        log.error("✗ DISBURSEMENT_FAILED - Reason: {}", event.getFailureReason());

        loan.setLoanStatus(LoanStatus.DISBURSEMENT_FAILED);
        loanRepository.save(loan);

        disbursementFailedCounter.increment();

        String taskId = manualInterventionService.createDisbursementFailureTask(
                loan.getLoanId(), loan.getBorrowerId(), event.getAmount(),
                event.getDisbursementMethod(), event.getFailureReason());

        notificationService.sendLoanDisbursementFailedNotification(
                loan.getBorrowerId(), loan.getLoanId(), event.getFailureReason());

        loanAccountService.createAuditEntry(loan.getLoanId(), "DISBURSEMENT_FAILED", event,
                String.format("CRITICAL: Disbursement failed - Reason: %s, TaskID: %s, CorrelationID: %s",
                        event.getFailureReason(), taskId, correlationId));

        // Send PagerDuty alert for failed disbursements
        try {
            pagerDutyAlertService.sendErrorAlert(
                    "Loan Disbursement Failed",
                    String.format("Disbursement failed for loan %s - Reason: %s", loan.getLoanId(), event.getFailureReason()),
                    Map.of("loanId", loan.getLoanId(), "reason", event.getFailureReason(), "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert for disbursement failure", e);
        }
    }

    private void handleDisbursementCancelled(LoanDisbursementEvent event, Loan loan, String correlationId) {
        log.info("→ DISBURSEMENT_CANCELLED - Reason: {}", event.getCancellationReason());
        loan.setLoanStatus(LoanStatus.CANCELLED);
        loanRepository.save(loan);
        notificationService.sendLoanDisbursementCancelledNotification(
                loan.getBorrowerId(), loan.getLoanId(), event.getCancellationReason());
        loanAccountService.createAuditEntry(loan.getLoanId(), "DISBURSEMENT_CANCELLED", event,
                "Disbursement cancelled: " + event.getCancellationReason() + " - CorrelationID: " + correlationId);
    }

    private void handlePartialDisbursement(LoanDisbursementEvent event, Loan loan, String correlationId) {
        BigDecimal remaining = event.getAmount().subtract(event.getDisbursedAmount());
        log.info("→ PARTIAL_DISBURSEMENT - Disbursed: {}, Remaining: {}", event.getDisbursedAmount(), remaining);

        loan.setDisbursementId(event.getDisbursementId());
        loan.setLoanStatus(LoanStatus.PARTIALLY_DISBURSED);
        loanRepository.save(loan);

        notificationService.sendPartialDisbursementNotification(
                loan.getBorrowerId(), loan.getLoanId(), event.getDisbursedAmount(), remaining);

        String taskId = manualInterventionService.createTask("PARTIAL_DISBURSEMENT_TRACKING",
                String.format("Partial disbursement - Disbursed: %s, Remaining: %s", event.getDisbursedAmount(), remaining),
                loan.getLoanId());

        loanAccountService.createAuditEntry(loan.getLoanId(), "PARTIAL_DISBURSEMENT", event,
                String.format("Partial disbursement - Disbursed: %s, Remaining: %s, TaskID: %s, CorrelationID: %s",
                        event.getDisbursedAmount(), remaining, taskId, correlationId));
    }

    // ==================== HELPER METHODS ====================

    private void handleProcessingFailure(LoanDisbursementEvent event, Exception exception, String correlationId) {
        try {
            manualInterventionService.createDisbursementFailureTask(
                    event.getLoanId(),
                    event.getCustomerId(),
                    event.getAmount(),
                    event.getDisbursementMethod(),
                    "Processing failed: " + exception.getMessage() + " - CorrelationID: " + correlationId
            );
        } catch (Exception taskException) {
            log.error("Failed to create manual intervention task", taskException);
        }
    }

    private void sendSlackNotification(LoanDisbursementEvent event, Loan loan, String status, String correlationId) {
        try {
            slackNotificationService.sendNotification(
                    "#loan-disbursements",
                    String.format("Disbursement %s: Loan %s - %s - Amount: %s - CorrelationID: %s",
                            status, loan.getLoanId(), event.getEventType(), event.getAmount(), correlationId)
            );
        } catch (Exception e) {
            log.warn("Failed to send Slack notification", e);
        }
    }
}
