package com.waqiti.insurance.kafka;

import com.waqiti.common.events.InsuranceClaimProcessingEvent;
import com.waqiti.insurance.domain.ClaimProcessing;
import com.waqiti.insurance.repository.ClaimProcessingRepository;
import com.waqiti.insurance.repository.ClaimRepository;
import com.waqiti.insurance.service.ClaimProcessingService;
import com.waqiti.insurance.service.ClaimAdjudicationService;
import com.waqiti.insurance.metrics.InsuranceMetricsService;
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
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class InsuranceClaimProcessingEventsConsumer {

    private final ClaimProcessingRepository claimProcessingRepository;
    private final ClaimRepository claimRepository;
    private final ClaimProcessingService claimProcessingService;
    private final ClaimAdjudicationService claimAdjudicationService;
    private final InsuranceMetricsService metricsService;
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
        successCounter = Counter.builder("insurance_claim_processing_processed_total")
            .description("Total number of successfully processed insurance claim processing events")
            .register(meterRegistry);
        errorCounter = Counter.builder("insurance_claim_processing_errors_total")
            .description("Total number of insurance claim processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("insurance_claim_processing_duration")
            .description("Time taken to process insurance claim processing events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"insurance-claim-processing-events", "claim-adjudication-workflow", "claim-settlement-requests"},
        groupId = "insurance-claim-processing-service-group",
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
    @CircuitBreaker(name = "insurance-claim-processing", fallbackMethod = "handleInsuranceClaimProcessingEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInsuranceClaimProcessingEvent(
            @Payload InsuranceClaimProcessingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("claim-processing-%s-p%d-o%d", event.getClaimId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getClaimId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing claim processing event: claimId={}, status={}, adjuster={}",
                event.getClaimId(), event.getProcessingStatus(), event.getAdjusterId());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case REVIEW_INITIATED:
                    initiateClaimReview(event, correlationId);
                    break;

                case DOCUMENTATION_REQUESTED:
                    requestDocumentation(event, correlationId);
                    break;

                case INVESTIGATION_STARTED:
                    startInvestigation(event, correlationId);
                    break;

                case EXPERT_ASSESSMENT_REQUIRED:
                    requestExpertAssessment(event, correlationId);
                    break;

                case ADJUDICATION_COMPLETED:
                    completeAdjudication(event, correlationId);
                    break;

                case SETTLEMENT_APPROVED:
                    approveSettlement(event, correlationId);
                    break;

                case PAYMENT_PROCESSING:
                    processPayment(event, correlationId);
                    break;

                case CLAIM_CLOSED:
                    closeClaim(event, correlationId);
                    break;

                case CLAIM_REOPENED:
                    reopenClaim(event, correlationId);
                    break;

                default:
                    log.warn("Unknown claim processing event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("INSURANCE_CLAIM_PROCESSING_EVENT_PROCESSED", event.getClaimId(),
                Map.of("eventType", event.getEventType(), "processingStatus", event.getProcessingStatus(),
                    "adjusterId", event.getAdjusterId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process claim processing event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("insurance-claim-processing-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInsuranceClaimProcessingEventFallback(
            InsuranceClaimProcessingEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("claim-processing-fallback-%s-p%d-o%d", event.getClaimId(), partition, offset);

        log.error("Circuit breaker fallback triggered for claim processing: claimId={}, error={}",
            event.getClaimId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("insurance-claim-processing-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Insurance Claim Processing Circuit Breaker Triggered",
                String.format("Claim %s processing failed: %s", event.getClaimId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInsuranceClaimProcessingEvent(
            @Payload InsuranceClaimProcessingEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-claim-processing-%s-%d", event.getClaimId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Claim processing permanently failed: claimId={}, topic={}, error={}",
            event.getClaimId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("INSURANCE_CLAIM_PROCESSING_DLT_EVENT", event.getClaimId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Insurance Claim Processing Dead Letter Event",
                String.format("Claim %s processing sent to DLT: %s", event.getClaimId(), exceptionMessage),
                Map.of("claimId", event.getClaimId(), "topic", topic, "correlationId", correlationId)
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

    private void initiateClaimReview(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = ClaimProcessing.builder()
            .claimId(event.getClaimId())
            .adjusterId(event.getAdjusterId())
            .status("REVIEW_IN_PROGRESS")
            .reviewStartedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        claimProcessingRepository.save(processing);

        claimProcessingService.validateClaimEligibility(event.getClaimId());
        claimProcessingService.performInitialAssessment(event.getClaimId());

        notificationService.sendNotification(event.getClaimantId(), "Claim Review Started",
            "We have started reviewing your insurance claim and will keep you updated on progress.",
            correlationId);

        kafkaTemplate.send("claim-adjudication-workflow", Map.of(
            "claimId", event.getClaimId(),
            "eventType", "INITIAL_REVIEW_COMPLETED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClaimReviewInitiated(event.getClaimType());

        log.info("Claim review initiated: claimId={}, adjuster={}",
            event.getClaimId(), event.getAdjusterId());
    }

    private void requestDocumentation(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = claimProcessingRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim processing not found"));

        processing.setStatus("PENDING_DOCUMENTATION");
        processing.setDocumentationRequestedAt(LocalDateTime.now());
        processing.setRequiredDocuments(event.getRequiredDocuments());
        claimProcessingRepository.save(processing);

        claimProcessingService.sendDocumentationRequest(event.getClaimId(), event.getRequiredDocuments());

        notificationService.sendNotification(event.getClaimantId(), "Additional Documentation Required",
            "We need additional documentation to process your claim. Please check your account for details.",
            correlationId);

        metricsService.recordDocumentationRequested();

        log.info("Documentation requested: claimId={}, documents={}",
            event.getClaimId(), event.getRequiredDocuments());
    }

    private void startInvestigation(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = claimProcessingRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim processing not found"));

        processing.setStatus("UNDER_INVESTIGATION");
        processing.setInvestigationStartedAt(LocalDateTime.now());
        processing.setInvestigatorId(event.getInvestigatorId());
        claimProcessingRepository.save(processing);

        claimProcessingService.initiateInvestigation(event.getClaimId(), event.getInvestigatorId());
        claimProcessingService.scheduleFieldInspection(event.getClaimId());

        notificationService.sendNotification(event.getClaimantId(), "Claim Investigation Started",
            "An investigation has been initiated for your claim to gather additional information.",
            correlationId);

        metricsService.recordInvestigationStarted();

        log.info("Investigation started: claimId={}, investigator={}",
            event.getClaimId(), event.getInvestigatorId());
    }

    private void requestExpertAssessment(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = claimProcessingRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim processing not found"));

        processing.setStatus("EXPERT_ASSESSMENT_PENDING");
        processing.setExpertAssessmentRequestedAt(LocalDateTime.now());
        processing.setExpertType(event.getExpertType());
        claimProcessingRepository.save(processing);

        claimProcessingService.requestExpertEvaluation(event.getClaimId(), event.getExpertType());

        metricsService.recordExpertAssessmentRequested(event.getExpertType());

        log.info("Expert assessment requested: claimId={}, expertType={}",
            event.getClaimId(), event.getExpertType());
    }

    private void completeAdjudication(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = claimProcessingRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim processing not found"));

        processing.setStatus("ADJUDICATION_COMPLETED");
        processing.setAdjudicationCompletedAt(LocalDateTime.now());
        processing.setAdjudicationDecision(event.getAdjudicationDecision());
        processing.setSettlementAmount(event.getSettlementAmount());
        claimProcessingRepository.save(processing);

        claimAdjudicationService.finalizeAdjudication(event.getClaimId(), event.getAdjudicationDecision(),
            event.getSettlementAmount());

        if ("APPROVED".equals(event.getAdjudicationDecision())) {
            kafkaTemplate.send("claim-adjudication-workflow", Map.of(
                "claimId", event.getClaimId(),
                "eventType", "SETTLEMENT_APPROVED",
                "settlementAmount", event.getSettlementAmount(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendNotification(event.getClaimantId(), "Claim Decision Made",
            String.format("A decision has been made on your claim. Decision: %s", event.getAdjudicationDecision()),
            correlationId);

        metricsService.recordAdjudicationCompleted(event.getAdjudicationDecision());

        log.info("Adjudication completed: claimId={}, decision={}, amount={}",
            event.getClaimId(), event.getAdjudicationDecision(), event.getSettlementAmount());
    }

    private void approveSettlement(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = claimProcessingRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim processing not found"));

        processing.setStatus("SETTLEMENT_APPROVED");
        processing.setSettlementApprovedAt(LocalDateTime.now());
        claimProcessingRepository.save(processing);

        claimProcessingService.approveSettlement(event.getClaimId(), event.getSettlementAmount());
        claimProcessingService.reserveSettlementFunds(event.getClaimId(), event.getSettlementAmount());

        kafkaTemplate.send("claim-adjudication-workflow", Map.of(
            "claimId", event.getClaimId(),
            "eventType", "PAYMENT_PROCESSING",
            "settlementAmount", event.getSettlementAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordSettlementApproved();

        log.info("Settlement approved: claimId={}, amount={}",
            event.getClaimId(), event.getSettlementAmount());
    }

    private void processPayment(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = claimProcessingRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim processing not found"));

        processing.setStatus("PAYMENT_PROCESSING");
        processing.setPaymentInitiatedAt(LocalDateTime.now());
        claimProcessingRepository.save(processing);

        String paymentId = claimProcessingService.initiatePayment(event.getClaimId(),
            event.getSettlementAmount(), event.getPaymentMethod());

        notificationService.sendNotification(event.getClaimantId(), "Payment Processing",
            "Your claim payment is being processed and will be sent to your designated account.",
            correlationId);

        kafkaTemplate.send("payment-processing-events", Map.of(
            "paymentId", paymentId,
            "claimId", event.getClaimId(),
            "amount", event.getSettlementAmount(),
            "paymentMethod", event.getPaymentMethod(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPaymentProcessing();

        log.info("Payment processing initiated: claimId={}, paymentId={}, amount={}",
            event.getClaimId(), paymentId, event.getSettlementAmount());
    }

    private void closeClaim(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = claimProcessingRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim processing not found"));

        processing.setStatus("CLOSED");
        processing.setClosedAt(LocalDateTime.now());
        processing.setClosureReason(event.getClosureReason());
        claimProcessingRepository.save(processing);

        var claim = claimRepository.findById(event.getClaimId()).orElseThrow();
        claim.setStatus("CLOSED");
        claim.setClosedAt(LocalDateTime.now());
        claimRepository.save(claim);

        claimProcessingService.archiveClaimData(event.getClaimId());

        notificationService.sendNotification(event.getClaimantId(), "Claim Closed",
            "Your insurance claim has been closed. Thank you for choosing our services.",
            correlationId);

        kafkaTemplate.send("insurance-lifecycle-events", Map.of(
            "claimId", event.getClaimId(),
            "claimantId", event.getClaimantId(),
            "eventType", "CLAIM_CLOSED",
            "closureReason", event.getClosureReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClaimClosed(event.getClosureReason());

        log.info("Claim closed: claimId={}, reason={}",
            event.getClaimId(), event.getClosureReason());
    }

    private void reopenClaim(InsuranceClaimProcessingEvent event, String correlationId) {
        ClaimProcessing processing = claimProcessingRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim processing not found"));

        processing.setStatus("REOPENED");
        processing.setReopenedAt(LocalDateTime.now());
        processing.setReopenReason(event.getReopenReason());
        claimProcessingRepository.save(processing);

        var claim = claimRepository.findById(event.getClaimId()).orElseThrow();
        claim.setStatus("REOPENED");
        claimRepository.save(claim);

        notificationService.sendNotification(event.getClaimantId(), "Claim Reopened",
            "Your insurance claim has been reopened for further review.",
            correlationId);

        metricsService.recordClaimReopened();

        log.info("Claim reopened: claimId={}, reason={}",
            event.getClaimId(), event.getReopenReason());
    }
}