package com.waqiti.dispute.kafka;

import com.waqiti.common.events.DisputeRejectionEvent;
import com.waqiti.dispute.domain.DisputeRejection;
import com.waqiti.dispute.repository.DisputeRejectionRepository;
import com.waqiti.dispute.service.DisputeRejectionService;
import com.waqiti.dispute.service.DisputeStatusService;
import com.waqiti.dispute.service.CustomerCommunicationService;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class DisputeRejectionsConsumer {

    private final DisputeRejectionRepository rejectionRepository;
    private final DisputeRejectionService rejectionService;
    private final DisputeStatusService disputeStatusService;
    private final CustomerCommunicationService customerCommunicationService;
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
        successCounter = Counter.builder("dispute_rejections_processed_total")
            .description("Total number of successfully processed dispute rejection events")
            .register(meterRegistry);
        errorCounter = Counter.builder("dispute_rejections_errors_total")
            .description("Total number of dispute rejection processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("dispute_rejections_processing_duration")
            .description("Time taken to process dispute rejection events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"dispute-rejections"},
        groupId = "dispute-rejections-service-group",
        containerFactory = "disputeKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dispute-rejections", fallbackMethod = "handleRejectionEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
    public void handleRejectionEvent(
            @Payload DisputeRejectionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("rejection-%s-p%d-o%d", event.getDisputeId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDisputeId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing dispute rejection: disputeId={}, eventType={}, rejectionReason={}",
                event.getDisputeId(), event.getEventType(), event.getRejectionReason());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case DISPUTE_REJECTED:
                    processDisputeRejected(event, correlationId);
                    break;

                case REJECTION_APPEALED:
                    processRejectionAppealed(event, correlationId);
                    break;

                case APPEAL_REJECTED:
                    processAppealRejected(event, correlationId);
                    break;

                case APPEAL_APPROVED:
                    processAppealApproved(event, correlationId);
                    break;

                case REJECTION_FINAL:
                    processRejectionFinal(event, correlationId);
                    break;

                case REJECTION_REVIEW_REQUESTED:
                    processRejectionReviewRequested(event, correlationId);
                    break;

                case REJECTION_DOCUMENTATION_REQUIRED:
                    processRejectionDocumentationRequired(event, correlationId);
                    break;

                case REJECTION_ESCALATED:
                    processRejectionEscalated(event, correlationId);
                    break;

                default:
                    log.warn("Unknown dispute rejection event type: {}", event.getEventType());
                    processUnknownRejectionEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("DISPUTE_REJECTION_EVENT_PROCESSED", event.getDisputeId(),
                Map.of("eventType", event.getEventType(), "rejectionReason", event.getRejectionReason(),
                    "rejectionId", event.getRejectionId(), "customerId", event.getCustomerId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process dispute rejection event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("dispute-rejections-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleRejectionEventFallback(
            DisputeRejectionEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("rejection-fallback-%s-p%d-o%d", event.getDisputeId(), partition, offset);

        log.error("Circuit breaker fallback triggered for dispute rejection: disputeId={}, error={}",
            event.getDisputeId(), ex.getMessage());

        // Create incident for circuit breaker
        rejectionService.createRejectionIncident(
            "DISPUTE_REJECTION_CIRCUIT_BREAKER",
            String.format("Dispute rejection circuit breaker triggered for dispute %s", event.getDisputeId()),
            "HIGH",
            Map.of("disputeId", event.getDisputeId(), "eventType", event.getEventType(),
                "rejectionId", event.getRejectionId(), "rejectionReason", event.getRejectionReason(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("dispute-rejections-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send alert
        try {
            notificationService.sendOperationalAlert(
                "Dispute Rejection Circuit Breaker",
                String.format("Dispute rejection processing failed for dispute %s: %s",
                    event.getDisputeId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltRejectionEvent(
            @Payload DisputeRejectionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-rejection-%s-%d", event.getDisputeId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Dispute rejection permanently failed: disputeId={}, topic={}, error={}",
            event.getDisputeId(), topic, exceptionMessage);

        // Create critical incident
        rejectionService.createRejectionIncident(
            "DISPUTE_REJECTION_DLT_EVENT",
            String.format("Dispute rejection sent to DLT for dispute %s", event.getDisputeId()),
            "CRITICAL",
            Map.of("disputeId", event.getDisputeId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "rejectionId", event.getRejectionId(),
                "correlationId", correlationId, "requiresManualIntervention", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("DISPUTE_REJECTION_DLT_EVENT", event.getDisputeId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Dispute Rejection Dead Letter Event",
                String.format("Dispute rejection for dispute %s sent to DLT: %s",
                    event.getDisputeId(), exceptionMessage),
                Map.of("disputeId", event.getDisputeId(), "topic", topic, "correlationId", correlationId)
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

    private void processDisputeRejected(DisputeRejectionEvent event, String correlationId) {
        // Create rejection record
        DisputeRejection rejection = DisputeRejection.builder()
            .rejectionId(event.getRejectionId())
            .disputeId(event.getDisputeId())
            .customerId(event.getCustomerId())
            .rejectionReason(event.getRejectionReason())
            .rejectionCategory(event.getRejectionCategory())
            .rejectedBy(event.getRejectedBy())
            .rejectedAt(LocalDateTime.now())
            .status("REJECTED")
            .appealDeadline(event.getAppealDeadline())
            .rejectionDetails(event.getRejectionDetails())
            .correlationId(correlationId)
            .build();

        rejectionRepository.save(rejection);

        // Update dispute status
        disputeStatusService.updateDisputeStatus(event.getDisputeId(), "REJECTED",
            event.getRejectionReason());

        // Reverse any provisional credits if applicable
        if (event.getProvisionalCreditId() != null) {
            kafkaTemplate.send("dispute.provisional.credit.issued", Map.of(
                "disputeId", event.getDisputeId(),
                "creditId", event.getProvisionalCreditId(),
                "eventType", "PROVISIONAL_CREDIT_REVERSED",
                "reversalReason", "DISPUTE_REJECTED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Send customer notification
        customerCommunicationService.sendRejectionNotification(event.getCustomerId(),
            event.getDisputeId(), event.getRejectionReason(), event.getAppealDeadline(),
            correlationId);

        // Generate rejection letter
        String rejectionLetterId = customerCommunicationService.generateRejectionLetter(
            event.getDisputeId(), event.getRejectionReason(), event.getRejectionDetails(),
            event.getAppealDeadline());

        // Check if customer can appeal
        if (rejectionService.canCustomerAppeal(event.getDisputeId(), event.getRejectionCategory())) {
            customerCommunicationService.sendAppealInstructions(event.getCustomerId(),
                event.getDisputeId(), event.getAppealDeadline(), correlationId);
        }

        metricsService.recordDisputeRejected(event.getRejectionCategory(), event.getRejectionReason());

        log.info("Dispute rejected: disputeId={}, rejectionId={}, reason={}, appealDeadline={}",
            event.getDisputeId(), event.getRejectionId(), event.getRejectionReason(),
            event.getAppealDeadline());
    }

    private void processRejectionAppealed(DisputeRejectionEvent event, String correlationId) {
        // Update rejection status
        DisputeRejection rejection = rejectionRepository.findByRejectionId(event.getRejectionId())
            .orElseThrow(() -> new RuntimeException("Rejection record not found"));

        rejection.setStatus("APPEALED");
        rejection.setAppealedAt(LocalDateTime.now());
        rejection.setAppealReason(event.getAppealReason());
        rejection.setAppealDocuments(event.getAppealDocuments());
        rejectionRepository.save(rejection);

        // Update dispute status
        disputeStatusService.updateDisputeStatus(event.getDisputeId(), "APPEAL_SUBMITTED",
            "Customer has appealed the rejection");

        // Create appeal review task
        String appealReviewId = rejectionService.createAppealReview(event.getDisputeId(),
            event.getRejectionId(), event.getAppealReason(), event.getAppealDocuments());

        // Send appeal acknowledgment to customer
        customerCommunicationService.sendAppealAcknowledgment(event.getCustomerId(),
            event.getDisputeId(), appealReviewId, correlationId);

        // Assign appeal to reviewer
        rejectionService.assignAppealReviewer(appealReviewId, event.getDisputeId(),
            event.getRejectionCategory());

        metricsService.recordDisputeRejectionAppealed(event.getRejectionCategory());

        log.info("Dispute rejection appealed: disputeId={}, rejectionId={}, appealReviewId={}",
            event.getDisputeId(), event.getRejectionId(), appealReviewId);
    }

    private void processAppealRejected(DisputeRejectionEvent event, String correlationId) {
        // Update rejection status
        DisputeRejection rejection = rejectionRepository.findByRejectionId(event.getRejectionId())
            .orElseThrow(() -> new RuntimeException("Rejection record not found"));

        rejection.setStatus("APPEAL_REJECTED");
        rejection.setAppealRejectedAt(LocalDateTime.now());
        rejection.setAppealRejectedBy(event.getAppealRejectedBy());
        rejection.setAppealRejectionReason(event.getAppealRejectionReason());
        rejectionRepository.save(rejection);

        // Update dispute status to final rejection
        disputeStatusService.updateDisputeStatus(event.getDisputeId(), "FINAL_REJECTION",
            event.getAppealRejectionReason());

        // Send final rejection notification
        customerCommunicationService.sendFinalRejectionNotification(event.getCustomerId(),
            event.getDisputeId(), event.getAppealRejectionReason(), correlationId);

        // Close dispute
        kafkaTemplate.send("dispute-status-updates", Map.of(
            "disputeId", event.getDisputeId(),
            "status", "CLOSED_REJECTED",
            "finalReason", event.getAppealRejectionReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordDisputeAppealRejected(event.getRejectionCategory());

        log.info("Dispute appeal rejected: disputeId={}, rejectionId={}, finalReason={}",
            event.getDisputeId(), event.getRejectionId(), event.getAppealRejectionReason());
    }

    private void processAppealApproved(DisputeRejectionEvent event, String correlationId) {
        // Update rejection status
        DisputeRejection rejection = rejectionRepository.findByRejectionId(event.getRejectionId())
            .orElseThrow(() -> new RuntimeException("Rejection record not found"));

        rejection.setStatus("APPEAL_APPROVED");
        rejection.setAppealApprovedAt(LocalDateTime.now());
        rejection.setAppealApprovedBy(event.getAppealApprovedBy());
        rejection.setAppealApprovalReason(event.getAppealApprovalReason());
        rejectionRepository.save(rejection);

        // Reopen dispute
        disputeStatusService.updateDisputeStatus(event.getDisputeId(), "APPEAL_APPROVED",
            event.getAppealApprovalReason());

        // Send approval notification to customer
        customerCommunicationService.sendAppealApprovalNotification(event.getCustomerId(),
            event.getDisputeId(), event.getAppealApprovalReason(), correlationId);

        // Restart dispute processing
        kafkaTemplate.send("dispute-status-updates", Map.of(
            "disputeId", event.getDisputeId(),
            "status", "REOPENED_FROM_APPEAL",
            "reason", event.getAppealApprovalReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Re-issue provisional credit if applicable
        if (event.shouldReissueProvisionalCredit()) {
            kafkaTemplate.send("dispute.provisional.credit.issued", Map.of(
                "disputeId", event.getDisputeId(),
                "eventType", "PROVISIONAL_CREDIT_ISSUED",
                "reason", "APPEAL_APPROVED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordDisputeAppealApproved(event.getRejectionCategory());

        log.info("Dispute appeal approved: disputeId={}, rejectionId={}, approvalReason={}",
            event.getDisputeId(), event.getRejectionId(), event.getAppealApprovalReason());
    }

    private void processRejectionFinal(DisputeRejectionEvent event, String correlationId) {
        // Update rejection status
        DisputeRejection rejection = rejectionRepository.findByRejectionId(event.getRejectionId())
            .orElseThrow(() -> new RuntimeException("Rejection record not found"));

        rejection.setStatus("FINAL");
        rejection.setFinalizedAt(LocalDateTime.now());
        rejection.setFinalizedBy(event.getFinalizedBy());
        rejection.setFinalizationNotes(event.getFinalizationNotes());
        rejectionRepository.save(rejection);

        // Update dispute status to closed
        disputeStatusService.updateDisputeStatus(event.getDisputeId(), "CLOSED_FINAL_REJECTION",
            "No further appeals available");

        // Send final closure notification
        customerCommunicationService.sendFinalClosureNotification(event.getCustomerId(),
            event.getDisputeId(), event.getFinalizationNotes(), correlationId);

        // Archive dispute records
        rejectionService.archiveDisputeRecords(event.getDisputeId());

        metricsService.recordDisputeRejectionFinalized(event.getRejectionCategory());

        log.info("Dispute rejection finalized: disputeId={}, rejectionId={}",
            event.getDisputeId(), event.getRejectionId());
    }

    private void processRejectionReviewRequested(DisputeRejectionEvent event, String correlationId) {
        // Create review request
        String reviewRequestId = rejectionService.createRejectionReview(event.getDisputeId(),
            event.getRejectionId(), event.getReviewRequestReason(), event.getRequestedBy());

        // Log review request
        auditService.logDisputeEvent("REJECTION_REVIEW_REQUESTED", event.getDisputeId(),
            Map.of("rejectionId", event.getRejectionId(), "reviewRequestId", reviewRequestId,
                "reviewReason", event.getReviewRequestReason(), "requestedBy", event.getRequestedBy(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        // Assign review to senior analyst
        rejectionService.assignRejectionReviewer(reviewRequestId, event.getDisputeId(),
            "SENIOR_ANALYST");

        // Send review request notification
        notificationService.sendDisputeNotification(
            "senior-dispute-team",
            "Rejection Review Requested",
            String.format("Rejection review requested for dispute %s: %s",
                event.getDisputeId(), event.getReviewRequestReason()),
            correlationId
        );

        metricsService.recordRejectionReviewRequested(event.getRejectionCategory());

        log.info("Rejection review requested: disputeId={}, rejectionId={}, reviewRequestId={}",
            event.getDisputeId(), event.getRejectionId(), reviewRequestId);
    }

    private void processRejectionDocumentationRequired(DisputeRejectionEvent event, String correlationId) {
        // Update rejection status
        DisputeRejection rejection = rejectionRepository.findByRejectionId(event.getRejectionId())
            .orElseThrow(() -> new RuntimeException("Rejection record not found"));

        rejection.setStatus("DOCUMENTATION_REQUIRED");
        rejection.setDocumentationRequestedAt(LocalDateTime.now());
        rejection.setRequiredDocuments(event.getRequiredDocuments());
        rejection.setDocumentDeadline(event.getDocumentDeadline());
        rejectionRepository.save(rejection);

        // Send documentation request to customer
        customerCommunicationService.sendDocumentationRequest(event.getCustomerId(),
            event.getDisputeId(), event.getRequiredDocuments(), event.getDocumentDeadline(),
            correlationId);

        // Set up document collection tracking
        rejectionService.setupDocumentTracking(event.getDisputeId(), event.getRejectionId(),
            event.getRequiredDocuments(), event.getDocumentDeadline());

        metricsService.recordRejectionDocumentationRequired(event.getRejectionCategory());

        log.info("Rejection documentation required: disputeId={}, rejectionId={}, deadline={}",
            event.getDisputeId(), event.getRejectionId(), event.getDocumentDeadline());
    }

    private void processRejectionEscalated(DisputeRejectionEvent event, String correlationId) {
        // Update rejection status
        DisputeRejection rejection = rejectionRepository.findByRejectionId(event.getRejectionId())
            .orElseThrow(() -> new RuntimeException("Rejection record not found"));

        rejection.setStatus("ESCALATED");
        rejection.setEscalatedAt(LocalDateTime.now());
        rejection.setEscalatedBy(event.getEscalatedBy());
        rejection.setEscalationReason(event.getEscalationReason());
        rejection.setEscalatedTo(event.getEscalatedTo());
        rejectionRepository.save(rejection);

        // Escalate to higher authority
        rejectionService.escalateRejection(event.getDisputeId(), event.getRejectionId(),
            event.getEscalatedTo(), event.getEscalationReason());

        // Send escalation notification
        notificationService.sendEscalationAlert(
            "Dispute Rejection Escalated",
            String.format("Dispute rejection %s has been escalated: %s",
                event.getRejectionId(), event.getEscalationReason()),
            "HIGH"
        );

        // Update dispute status
        disputeStatusService.updateDisputeStatus(event.getDisputeId(), "REJECTION_ESCALATED",
            event.getEscalationReason());

        metricsService.recordRejectionEscalated(event.getRejectionCategory());

        log.warn("Dispute rejection escalated: disputeId={}, rejectionId={}, reason={}, escalatedTo={}",
            event.getDisputeId(), event.getRejectionId(), event.getEscalationReason(),
            event.getEscalatedTo());
    }

    private void processUnknownRejectionEvent(DisputeRejectionEvent event, String correlationId) {
        // Create incident for unknown event type
        rejectionService.createRejectionIncident(
            "UNKNOWN_DISPUTE_REJECTION_EVENT",
            String.format("Unknown dispute rejection event type %s for dispute %s",
                event.getEventType(), event.getDisputeId()),
            "MEDIUM",
            Map.of("disputeId", event.getDisputeId(), "rejectionId", event.getRejectionId(),
                "unknownEventType", event.getEventType(), "correlationId", correlationId)
        );

        log.warn("Unknown dispute rejection event: disputeId={}, rejectionId={}, eventType={}",
            event.getDisputeId(), event.getRejectionId(), event.getEventType());
    }
}