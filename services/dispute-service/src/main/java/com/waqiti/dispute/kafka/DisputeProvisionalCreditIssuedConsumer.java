package com.waqiti.dispute.kafka;

import com.waqiti.common.events.DisputeProvisionalCreditEvent;
import com.waqiti.dispute.domain.DisputeProvisionalCredit;
import com.waqiti.dispute.repository.DisputeProvisionalCreditRepository;
import com.waqiti.dispute.service.ProvisionalCreditService;
import com.waqiti.dispute.service.DisputeAccountingService;
import com.waqiti.dispute.service.ComplianceService;
import com.waqiti.dispute.metrics.DisputeMetricsService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class DisputeProvisionalCreditIssuedConsumer {

    private final DisputeProvisionalCreditRepository provisionalCreditRepository;
    private final ProvisionalCreditService provisionalCreditService;
    private final DisputeAccountingService accountingService;
    private final ComplianceService complianceService;
    private final DisputeMetricsService metricsService;
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
        successCounter = Counter.builder("dispute_provisional_credit_processed_total")
            .description("Total number of successfully processed dispute provisional credit events")
            .register(meterRegistry);
        errorCounter = Counter.builder("dispute_provisional_credit_errors_total")
            .description("Total number of dispute provisional credit processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("dispute_provisional_credit_processing_duration")
            .description("Time taken to process dispute provisional credit events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"dispute.provisional.credit.issued"},
        groupId = "dispute-provisional-credit-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dispute-provisional-credit", fallbackMethod = "handleProvisionalCreditEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
    public void handleProvisionalCreditEvent(
            @Payload DisputeProvisionalCreditEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("provisional-credit-%s-p%d-o%d", event.getDisputeId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDisputeId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing dispute provisional credit: disputeId={}, eventType={}, amount={}",
                event.getDisputeId(), event.getEventType(), event.getCreditAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case PROVISIONAL_CREDIT_ISSUED:
                    processProvisionalCreditIssued(event, correlationId);
                    break;

                case PROVISIONAL_CREDIT_APPROVED:
                    processProvisionalCreditApproved(event, correlationId);
                    break;

                case PROVISIONAL_CREDIT_REJECTED:
                    processProvisionalCreditRejected(event, correlationId);
                    break;

                case PROVISIONAL_CREDIT_REVERSED:
                    processProvisionalCreditReversed(event, correlationId);
                    break;

                case PROVISIONAL_CREDIT_ADJUSTED:
                    processProvisionalCreditAdjusted(event, correlationId);
                    break;

                case PROVISIONAL_CREDIT_FINALIZED:
                    processProvisionalCreditFinalized(event, correlationId);
                    break;

                case PROVISIONAL_CREDIT_EXPIRED:
                    processProvisionalCreditExpired(event, correlationId);
                    break;

                case COMPLIANCE_REVIEW_REQUIRED:
                    processComplianceReviewRequired(event, correlationId);
                    break;

                default:
                    log.warn("Unknown dispute provisional credit event type: {}", event.getEventType());
                    processUnknownProvisionalCreditEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFinancialEvent("DISPUTE_PROVISIONAL_CREDIT_EVENT_PROCESSED", event.getDisputeId(),
                Map.of("eventType", event.getEventType(), "creditAmount", event.getCreditAmount(),
                    "creditId", event.getCreditId(), "customerId", event.getCustomerId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process dispute provisional credit event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("dispute-provisional-credit-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleProvisionalCreditEventFallback(
            DisputeProvisionalCreditEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("provisional-credit-fallback-%s-p%d-o%d", event.getDisputeId(), partition, offset);

        log.error("Circuit breaker fallback triggered for dispute provisional credit: disputeId={}, error={}",
            event.getDisputeId(), ex.getMessage());

        // Create financial incident
        provisionalCreditService.createFinancialIncident(
            "DISPUTE_PROVISIONAL_CREDIT_CIRCUIT_BREAKER",
            String.format("Dispute provisional credit circuit breaker triggered for dispute %s", event.getDisputeId()),
            "CRITICAL",
            Map.of("disputeId", event.getDisputeId(), "eventType", event.getEventType(),
                "creditAmount", event.getCreditAmount(), "creditId", event.getCreditId(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("dispute-provisional-credit-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical financial alert
        try {
            notificationService.sendCriticalFinancialAlert(
                "Dispute Provisional Credit Circuit Breaker",
                String.format("Provisional credit processing failed for dispute %s (Amount: %s): %s",
                    event.getDisputeId(), event.getCreditAmount(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical financial alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltProvisionalCreditEvent(
            @Payload DisputeProvisionalCreditEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-provisional-credit-%s-%d", event.getDisputeId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Dispute provisional credit permanently failed: disputeId={}, topic={}, error={}",
            event.getDisputeId(), topic, exceptionMessage);

        // Create critical financial incident
        provisionalCreditService.createFinancialIncident(
            "DISPUTE_PROVISIONAL_CREDIT_DLT_EVENT",
            String.format("Dispute provisional credit sent to DLT for dispute %s", event.getDisputeId()),
            "CRITICAL",
            Map.of("disputeId", event.getDisputeId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "creditAmount", event.getCreditAmount(),
                "creditId", event.getCreditId(), "correlationId", correlationId,
                "requiresImmediateFinancialReview", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logCriticalFinancialEvent("DISPUTE_PROVISIONAL_CREDIT_DLT_EVENT", event.getDisputeId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "creditAmount", event.getCreditAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send emergency financial alert
        try {
            notificationService.sendEmergencyFinancialAlert(
                "Dispute Provisional Credit Dead Letter Event",
                String.format("EMERGENCY: Provisional credit for dispute %s sent to DLT (Amount: %s): %s",
                    event.getDisputeId(), event.getCreditAmount(), exceptionMessage),
                Map.of("disputeId", event.getDisputeId(), "creditAmount", event.getCreditAmount(),
                    "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency financial DLT alert: {}", ex.getMessage());
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

    private void processProvisionalCreditIssued(DisputeProvisionalCreditEvent event, String correlationId) {
        // Create provisional credit record
        DisputeProvisionalCredit credit = DisputeProvisionalCredit.builder()
            .creditId(event.getCreditId())
            .disputeId(event.getDisputeId())
            .customerId(event.getCustomerId())
            .accountId(event.getAccountId())
            .creditAmount(event.getCreditAmount())
            .currency(event.getCurrency())
            .status("ISSUED")
            .issuedAt(LocalDateTime.now())
            .issuedBy(event.getIssuedBy())
            .expirationDate(event.getExpirationDate())
            .reason(event.getReason())
            .correlationId(correlationId)
            .build();

        provisionalCreditRepository.save(credit);

        // Process accounting entries
        accountingService.processProvisionalCreditAccounting(event.getCreditId(),
            event.getAccountId(), event.getCreditAmount(), event.getCurrency());

        // Update customer account balance
        provisionalCreditService.applyProvisionalCredit(event.getAccountId(),
            event.getCreditAmount(), event.getCurrency());

        // Send customer notification
        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Provisional Credit Issued",
            String.format("A provisional credit of %s %s has been issued to your account for dispute %s",
                event.getCreditAmount(), event.getCurrency(), event.getDisputeId()),
            correlationId
        );

        // Update dispute status
        kafkaTemplate.send("dispute-status-updates", Map.of(
            "disputeId", event.getDisputeId(),
            "status", "PROVISIONAL_CREDIT_ISSUED",
            "creditId", event.getCreditId(),
            "creditAmount", event.getCreditAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check if compliance review is required
        if (provisionalCreditService.requiresComplianceReview(event.getCreditAmount(), event.getCustomerId())) {
            kafkaTemplate.send("dispute.provisional.credit.issued", Map.of(
                "disputeId", event.getDisputeId(),
                "creditId", event.getCreditId(),
                "eventType", "COMPLIANCE_REVIEW_REQUIRED",
                "complianceReason", "HIGH_VALUE_CREDIT",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordProvisionalCreditIssued(event.getCreditAmount());

        log.info("Provisional credit issued: disputeId={}, creditId={}, amount={} {}",
            event.getDisputeId(), event.getCreditId(), event.getCreditAmount(), event.getCurrency());
    }

    private void processProvisionalCreditApproved(DisputeProvisionalCreditEvent event, String correlationId) {
        // Update credit status
        DisputeProvisionalCredit credit = provisionalCreditRepository.findByCreditId(event.getCreditId())
            .orElseThrow(() -> new RuntimeException("Provisional credit not found"));

        credit.setStatus("APPROVED");
        credit.setApprovedAt(LocalDateTime.now());
        credit.setApprovedBy(event.getApprovedBy());
        credit.setApprovalNotes(event.getApprovalNotes());
        provisionalCreditRepository.save(credit);

        // Confirm credit in accounting system
        accountingService.confirmProvisionalCredit(event.getCreditId());

        // Send customer notification
        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Provisional Credit Approved",
            String.format("Your provisional credit of %s %s for dispute %s has been approved",
                event.getCreditAmount(), event.getCurrency(), event.getDisputeId()),
            correlationId
        );

        metricsService.recordProvisionalCreditApproved(event.getCreditAmount());

        log.info("Provisional credit approved: creditId={}, approvedBy={}",
            event.getCreditId(), event.getApprovedBy());
    }

    private void processProvisionalCreditRejected(DisputeProvisionalCreditEvent event, String correlationId) {
        // Update credit status
        DisputeProvisionalCredit credit = provisionalCreditRepository.findByCreditId(event.getCreditId())
            .orElseThrow(() -> new RuntimeException("Provisional credit not found"));

        credit.setStatus("REJECTED");
        credit.setRejectedAt(LocalDateTime.now());
        credit.setRejectedBy(event.getRejectedBy());
        credit.setRejectionReason(event.getRejectionReason());
        provisionalCreditRepository.save(credit);

        // Reverse provisional credit
        provisionalCreditService.reverseProvisionalCredit(event.getAccountId(),
            event.getCreditAmount(), event.getCurrency());

        // Process reversal accounting
        accountingService.reverseProvisionalCreditAccounting(event.getCreditId(),
            event.getAccountId(), event.getCreditAmount(), event.getCurrency());

        // Send customer notification
        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Provisional Credit Rejected",
            String.format("Your provisional credit for dispute %s has been rejected: %s",
                event.getDisputeId(), event.getRejectionReason()),
            correlationId
        );

        metricsService.recordProvisionalCreditRejected(event.getCreditAmount(), event.getRejectionReason());

        log.warn("Provisional credit rejected: creditId={}, reason={}",
            event.getCreditId(), event.getRejectionReason());
    }

    private void processProvisionalCreditReversed(DisputeProvisionalCreditEvent event, String correlationId) {
        // Update credit status
        DisputeProvisionalCredit credit = provisionalCreditRepository.findByCreditId(event.getCreditId())
            .orElseThrow(() -> new RuntimeException("Provisional credit not found"));

        credit.setStatus("REVERSED");
        credit.setReversedAt(LocalDateTime.now());
        credit.setReversedBy(event.getReversedBy());
        credit.setReversalReason(event.getReversalReason());
        provisionalCreditRepository.save(credit);

        // Execute reversal
        provisionalCreditService.reverseProvisionalCredit(event.getAccountId(),
            event.getCreditAmount(), event.getCurrency());

        // Process reversal accounting
        accountingService.reverseProvisionalCreditAccounting(event.getCreditId(),
            event.getAccountId(), event.getCreditAmount(), event.getCurrency());

        // Send customer notification
        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Provisional Credit Reversed",
            String.format("Your provisional credit of %s %s for dispute %s has been reversed: %s",
                event.getCreditAmount(), event.getCurrency(), event.getDisputeId(), event.getReversalReason()),
            correlationId
        );

        metricsService.recordProvisionalCreditReversed(event.getCreditAmount(), event.getReversalReason());

        log.info("Provisional credit reversed: creditId={}, reason={}",
            event.getCreditId(), event.getReversalReason());
    }

    private void processProvisionalCreditAdjusted(DisputeProvisionalCreditEvent event, String correlationId) {
        // Update credit record
        DisputeProvisionalCredit credit = provisionalCreditRepository.findByCreditId(event.getCreditId())
            .orElseThrow(() -> new RuntimeException("Provisional credit not found"));

        BigDecimal originalAmount = credit.getCreditAmount();
        credit.setOriginalAmount(originalAmount);
        credit.setCreditAmount(event.getNewCreditAmount());
        credit.setStatus("ADJUSTED");
        credit.setAdjustedAt(LocalDateTime.now());
        credit.setAdjustedBy(event.getAdjustedBy());
        credit.setAdjustmentReason(event.getAdjustmentReason());
        provisionalCreditRepository.save(credit);

        // Process adjustment
        BigDecimal adjustmentAmount = event.getNewCreditAmount().subtract(originalAmount);
        provisionalCreditService.adjustProvisionalCredit(event.getAccountId(),
            adjustmentAmount, event.getCurrency());

        // Process adjustment accounting
        accountingService.adjustProvisionalCreditAccounting(event.getCreditId(),
            event.getAccountId(), adjustmentAmount, event.getCurrency());

        // Send customer notification
        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Provisional Credit Adjusted",
            String.format("Your provisional credit for dispute %s has been adjusted from %s to %s %s: %s",
                event.getDisputeId(), originalAmount, event.getNewCreditAmount(),
                event.getCurrency(), event.getAdjustmentReason()),
            correlationId
        );

        metricsService.recordProvisionalCreditAdjusted(originalAmount, event.getNewCreditAmount());

        log.info("Provisional credit adjusted: creditId={}, from={}, to={}, reason={}",
            event.getCreditId(), originalAmount, event.getNewCreditAmount(), event.getAdjustmentReason());
    }

    private void processProvisionalCreditFinalized(DisputeProvisionalCreditEvent event, String correlationId) {
        // Update credit status
        DisputeProvisionalCredit credit = provisionalCreditRepository.findByCreditId(event.getCreditId())
            .orElseThrow(() -> new RuntimeException("Provisional credit not found"));

        credit.setStatus("FINALIZED");
        credit.setFinalizedAt(LocalDateTime.now());
        credit.setFinalizedBy(event.getFinalizedBy());
        credit.setFinalizationNotes(event.getFinalizationNotes());
        provisionalCreditRepository.save(credit);

        // Convert provisional credit to permanent
        provisionalCreditService.finalizeProvisionalCredit(event.getCreditId(),
            event.getAccountId(), event.getCreditAmount(), event.getCurrency());

        // Process final accounting
        accountingService.finalizeProvisionalCreditAccounting(event.getCreditId(),
            event.getAccountId(), event.getCreditAmount(), event.getCurrency());

        // Send customer notification
        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Provisional Credit Finalized",
            String.format("Your provisional credit of %s %s for dispute %s has been finalized",
                event.getCreditAmount(), event.getCurrency(), event.getDisputeId()),
            correlationId
        );

        // Update dispute status
        kafkaTemplate.send("dispute-status-updates", Map.of(
            "disputeId", event.getDisputeId(),
            "status", "PROVISIONAL_CREDIT_FINALIZED",
            "creditId", event.getCreditId(),
            "finalAmount", event.getCreditAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordProvisionalCreditFinalized(event.getCreditAmount());

        log.info("Provisional credit finalized: creditId={}, amount={} {}",
            event.getCreditId(), event.getCreditAmount(), event.getCurrency());
    }

    private void processProvisionalCreditExpired(DisputeProvisionalCreditEvent event, String correlationId) {
        // Update credit status
        DisputeProvisionalCredit credit = provisionalCreditRepository.findByCreditId(event.getCreditId())
            .orElseThrow(() -> new RuntimeException("Provisional credit not found"));

        credit.setStatus("EXPIRED");
        credit.setExpiredAt(LocalDateTime.now());
        provisionalCreditRepository.save(credit);

        // Reverse expired credit
        provisionalCreditService.reverseProvisionalCredit(event.getAccountId(),
            event.getCreditAmount(), event.getCurrency());

        // Process expiration accounting
        accountingService.reverseProvisionalCreditAccounting(event.getCreditId(),
            event.getAccountId(), event.getCreditAmount(), event.getCurrency());

        // Send customer notification
        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Provisional Credit Expired",
            String.format("Your provisional credit of %s %s for dispute %s has expired and been reversed",
                event.getCreditAmount(), event.getCurrency(), event.getDisputeId()),
            correlationId
        );

        metricsService.recordProvisionalCreditExpired(event.getCreditAmount());

        log.info("Provisional credit expired: creditId={}, amount={} {}",
            event.getCreditId(), event.getCreditAmount(), event.getCurrency());
    }

    private void processComplianceReviewRequired(DisputeProvisionalCreditEvent event, String correlationId) {
        // Create compliance review request
        String reviewId = complianceService.createComplianceReview(event.getCreditId(),
            event.getDisputeId(), event.getComplianceReason());

        // Log compliance requirement
        auditService.logComplianceEvent("PROVISIONAL_CREDIT_COMPLIANCE_REVIEW_REQUIRED", event.getCreditId(),
            Map.of("disputeId", event.getDisputeId(), "reviewId", reviewId,
                "complianceReason", event.getComplianceReason(),
                "creditAmount", event.getCreditAmount(), "correlationId", correlationId,
                "timestamp", Instant.now()));

        // Send to compliance queue
        kafkaTemplate.send("compliance-review-queue", Map.of(
            "reviewId", reviewId,
            "creditId", event.getCreditId(),
            "disputeId", event.getDisputeId(),
            "reviewType", "PROVISIONAL_CREDIT_REVIEW",
            "amount", event.getCreditAmount(),
            "reason", event.getComplianceReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send notification to compliance team
        notificationService.sendComplianceAlert(
            "Provisional Credit Compliance Review Required",
            String.format("Compliance review required for provisional credit %s (Amount: %s %s): %s",
                event.getCreditId(), event.getCreditAmount(), event.getCurrency(),
                event.getComplianceReason()),
            Map.of("creditId", event.getCreditId(), "reviewId", reviewId,
                "correlationId", correlationId)
        );

        metricsService.recordProvisionalCreditComplianceReview(event.getCreditAmount());

        log.info("Compliance review required for provisional credit: creditId={}, reviewId={}, reason={}",
            event.getCreditId(), reviewId, event.getComplianceReason());
    }

    private void processUnknownProvisionalCreditEvent(DisputeProvisionalCreditEvent event, String correlationId) {
        // Create incident for unknown event type
        provisionalCreditService.createFinancialIncident(
            "UNKNOWN_DISPUTE_PROVISIONAL_CREDIT_EVENT",
            String.format("Unknown dispute provisional credit event type %s for credit %s",
                event.getEventType(), event.getCreditId()),
            "MEDIUM",
            Map.of("disputeId", event.getDisputeId(), "creditId", event.getCreditId(),
                "unknownEventType", event.getEventType(), "creditAmount", event.getCreditAmount(),
                "correlationId", correlationId)
        );

        log.warn("Unknown dispute provisional credit event: disputeId={}, creditId={}, eventType={}",
            event.getDisputeId(), event.getCreditId(), event.getEventType());
    }
}