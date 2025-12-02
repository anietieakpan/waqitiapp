package com.waqiti.lending.kafka;

import com.waqiti.common.events.LoanApprovedEvent;
import com.waqiti.lending.service.LoanService;
import com.waqiti.lending.service.LoanDisbursementService;
import com.waqiti.lending.service.LoanMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoanApprovedConsumer {

    private final LoanService loanService;
    private final LoanDisbursementService disbursementService;
    private final LoanMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("loan_approved_processed_total")
            .description("Total number of successfully processed loan approved events")
            .register(meterRegistry);
        errorCounter = Counter.builder("loan_approved_errors_total")
            .description("Total number of loan approved processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("loan_approved_processing_duration")
            .description("Time taken to process loan approved events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"loan-approved", "loan-approval-granted", "credit-approved"},
        groupId = "loan-approved-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "loan-approved", fallbackMethod = "handleLoanApprovedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleLoanApprovedEvent(
            @Payload LoanApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("loan-approved-%s-p%d-o%d", event.getLoanId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getLoanId(), event.getApprovedBy(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing loan approved: loanId={}, customerId={}, amount={}, approvedBy={}",
                event.getLoanId(), event.getCustomerId(), event.getApprovedAmount(), event.getApprovedBy());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Update loan status
            updateLoanStatus(event, correlationId);

            // Create loan agreement
            createLoanAgreement(event, correlationId);

            // Setup loan account
            setupLoanAccount(event, correlationId);

            // Schedule disbursement
            scheduleDisbursement(event, correlationId);

            // Send approval notifications
            sendApprovalNotifications(event, correlationId);

            // Update credit limits if applicable
            updateCreditLimits(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logLoanEvent("LOAN_APPROVED_PROCESSED", event.getLoanId(),
                Map.of("customerId", event.getCustomerId(), "approvedAmount", event.getApprovedAmount(),
                    "approvedBy", event.getApprovedBy(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process loan approved event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("loan-approved-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleLoanApprovedEventFallback(
            LoanApprovedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("loan-approved-fallback-%s-p%d-o%d", event.getLoanId(), partition, offset);

        log.error("Circuit breaker fallback triggered for loan approved: loanId={}, error={}",
            event.getLoanId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("loan-approved-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification
        try {
            notificationService.sendCriticalAlert(
                "Loan Approved Circuit Breaker Triggered",
                String.format("Loan %s approval processing failed: %s", event.getLoanId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltLoanApprovedEvent(
            @Payload LoanApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-loan-approved-%s-%d", event.getLoanId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Loan approved permanently failed: loanId={}, topic={}, error={}",
            event.getLoanId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logLoanEvent("LOAN_APPROVED_DLT_EVENT", event.getLoanId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "customerId", event.getCustomerId(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Loan Approved Dead Letter Event",
                String.format("Loan %s approval sent to DLT: %s", event.getLoanId(), exceptionMessage),
                Map.of("loanId", event.getLoanId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void updateLoanStatus(LoanApprovedEvent event, String correlationId) {
        // Update loan status to approved
        loanService.updateLoanStatus(
            event.getLoanId(),
            "APPROVED",
            event.getApprovedBy(),
            event.getApprovalNotes(),
            event.getApprovedAt(),
            correlationId
        );

        // Set approved loan terms
        loanService.setApprovedTerms(
            event.getLoanId(),
            event.getApprovedAmount(),
            event.getApprovedInterestRate(),
            event.getApprovedTermMonths(),
            event.getApprovedPaymentAmount(),
            correlationId
        );

        // Update metrics
        metricsService.incrementLoansApproved(event.getLoanType());
        metricsService.recordLoanAmount(event.getApprovedAmount(), event.getCurrency());
        metricsService.recordApprovalTime(
            event.getApprovedAt().toEpochMilli() - event.getApplicationSubmittedAt().toEpochMilli()
        );

        log.info("Loan status updated to approved: loanId={}, amount={}",
            event.getLoanId(), event.getApprovedAmount());
    }

    private void createLoanAgreement(LoanApprovedEvent event, String correlationId) {
        // Generate loan agreement document
        var agreementId = loanService.generateLoanAgreement(
            event.getLoanId(),
            event.getCustomerId(),
            event.getApprovedAmount(),
            event.getApprovedInterestRate(),
            event.getApprovedTermMonths(),
            event.getApprovedPaymentAmount(),
            event.getLoanTerms(),
            correlationId
        );

        // Send agreement for electronic signature
        kafkaTemplate.send("document-signature-requests", Map.of(
            "documentId", agreementId,
            "documentType", "LOAN_AGREEMENT",
            "loanId", event.getLoanId(),
            "customerId", event.getCustomerId(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send agreement to customer
        notificationService.sendNotification(
            event.getCustomerId(),
            "Loan Agreement Ready for Signature",
            String.format("Your loan for %s %s has been approved. Please review and sign your loan agreement to proceed with disbursement.",
                event.getApprovedAmount(), event.getCurrency()),
            correlationId
        );

        log.info("Loan agreement created and sent for signature: loanId={}, agreementId={}",
            event.getLoanId(), agreementId);
    }

    private void setupLoanAccount(LoanApprovedEvent event, String correlationId) {
        // Create loan account
        var loanAccountId = loanService.createLoanAccount(
            event.getLoanId(),
            event.getCustomerId(),
            event.getApprovedAmount(),
            event.getCurrency(),
            event.getApprovedInterestRate(),
            event.getApprovedTermMonths(),
            correlationId
        );

        // Setup automatic payment if opted in
        if (event.getAutoPayEnabled()) {
            kafkaTemplate.send("automatic-payment-setup", Map.of(
                "loanId", event.getLoanId(),
                "customerId", event.getCustomerId(),
                "accountId", loanAccountId,
                "paymentAmount", event.getApprovedPaymentAmount(),
                "paymentFrequency", event.getPaymentFrequency(),
                "paymentMethodToken", event.getPaymentMethodToken(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Send account creation event
        kafkaTemplate.send("account-creation-events", Map.of(
            "accountId", loanAccountId,
            "accountType", "LOAN",
            "customerId", event.getCustomerId(),
            "loanId", event.getLoanId(),
            "initialBalance", event.getApprovedAmount(),
            "currency", event.getCurrency(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Loan account created: loanId={}, accountId={}", event.getLoanId(), loanAccountId);
    }

    private void scheduleDisbursement(LoanApprovedEvent event, String correlationId) {
        // Schedule loan disbursement after agreement signing
        disbursementService.scheduleDisbursement(
            event.getLoanId(),
            event.getCustomerId(),
            event.getApprovedAmount(),
            event.getCurrency(),
            event.getDisbursementMethod(),
            event.getDisbursementAccountId(),
            correlationId
        );

        // Send disbursement scheduling event
        kafkaTemplate.send("loan-disbursement-events", Map.of(
            "eventType", "DISBURSEMENT_SCHEDULED",
            "loanId", event.getLoanId(),
            "customerId", event.getCustomerId(),
            "amount", event.getApprovedAmount(),
            "currency", event.getCurrency(),
            "disbursementMethod", event.getDisbursementMethod(),
            "disbursementAccountId", event.getDisbursementAccountId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send customer notification about disbursement timeline
        notificationService.sendNotification(
            event.getCustomerId(),
            "Loan Disbursement Scheduled",
            String.format("Your loan funds of %s %s will be disbursed after you sign the loan agreement. " +
                    "Funds typically arrive within 1-2 business days.",
                event.getApprovedAmount(), event.getCurrency()),
            correlationId
        );

        log.info("Loan disbursement scheduled: loanId={}, amount={}",
            event.getLoanId(), event.getApprovedAmount());
    }

    private void sendApprovalNotifications(LoanApprovedEvent event, String correlationId) {
        // Send approval notification to customer
        notificationService.sendNotification(
            event.getCustomerId(),
            "Loan Approved!",
            String.format("Great news! Your loan application for %s %s has been approved with an interest rate of %.2f%%. " +
                    "Your monthly payment will be %s %s.",
                event.getApprovedAmount(), event.getCurrency(),
                event.getApprovedInterestRate() * 100,
                event.getApprovedPaymentAmount(), event.getCurrency()),
            correlationId
        );

        // Send email confirmation
        kafkaTemplate.send("email-notifications", Map.of(
            "type", "LOAN_APPROVAL",
            "customerId", event.getCustomerId(),
            "loanId", event.getLoanId(),
            "approvedAmount", event.getApprovedAmount(),
            "currency", event.getCurrency(),
            "interestRate", event.getApprovedInterestRate(),
            "termMonths", event.getApprovedTermMonths(),
            "monthlyPayment", event.getApprovedPaymentAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send SMS if opted in
        if (event.getSmsNotificationEnabled()) {
            kafkaTemplate.send("sms-notifications", Map.of(
                "type", "LOAN_APPROVED",
                "customerId", event.getCustomerId(),
                "message", String.format("Your loan for %s %s has been approved! Check your email for next steps.",
                    event.getApprovedAmount(), event.getCurrency()),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Approval notifications sent: loanId={}, customerId={}",
            event.getLoanId(), event.getCustomerId());
    }

    private void updateCreditLimits(LoanApprovedEvent event, String correlationId) {
        // Update customer's total credit exposure
        kafkaTemplate.send("credit-exposure-updates", Map.of(
            "customerId", event.getCustomerId(),
            "loanId", event.getLoanId(),
            "additionalExposure", event.getApprovedAmount(),
            "loanType", event.getLoanType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check if customer has reached credit limits
        kafkaTemplate.send("credit-limit-checks", Map.of(
            "customerId", event.getCustomerId(),
            "newLoanAmount", event.getApprovedAmount(),
            "loanType", event.getLoanType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update credit bureau if applicable
        if (event.getReportToCreditBureau()) {
            kafkaTemplate.send("credit-bureau-reporting", Map.of(
                "type", "LOAN_APPROVED",
                "customerId", event.getCustomerId(),
                "loanId", event.getLoanId(),
                "amount", event.getApprovedAmount(),
                "loanType", event.getLoanType(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Credit limits updated: customerId={}, loanAmount={}",
            event.getCustomerId(), event.getApprovedAmount());
    }
}