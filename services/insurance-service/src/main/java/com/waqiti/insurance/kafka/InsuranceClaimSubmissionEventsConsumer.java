package com.waqiti.insurance.kafka;

import com.waqiti.common.events.InsuranceClaimSubmissionEvent;
import com.waqiti.insurance.domain.ClaimSubmission;
import com.waqiti.insurance.repository.ClaimSubmissionRepository;
import com.waqiti.insurance.repository.PolicyRepository;
import com.waqiti.insurance.service.ClaimSubmissionService;
import com.waqiti.insurance.service.DocumentValidationService;
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
public class InsuranceClaimSubmissionEventsConsumer {

    private final ClaimSubmissionRepository claimSubmissionRepository;
    private final PolicyRepository policyRepository;
    private final ClaimSubmissionService claimSubmissionService;
    private final DocumentValidationService documentValidationService;
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
        successCounter = Counter.builder("insurance_claim_submission_processed_total")
            .description("Total number of successfully processed insurance claim submission events")
            .register(meterRegistry);
        errorCounter = Counter.builder("insurance_claim_submission_errors_total")
            .description("Total number of insurance claim submission processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("insurance_claim_submission_processing_duration")
            .description("Time taken to process insurance claim submission events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"insurance-claim-submission-events", "claim-intake-workflow", "claim-registration-requests"},
        groupId = "insurance-claim-submission-service-group",
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
    @CircuitBreaker(name = "insurance-claim-submission", fallbackMethod = "handleInsuranceClaimSubmissionEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInsuranceClaimSubmissionEvent(
            @Payload InsuranceClaimSubmissionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("claim-submission-%s-p%d-o%d", event.getClaimId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getClaimId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing claim submission event: claimId={}, policyId={}, type={}",
                event.getClaimId(), event.getPolicyId(), event.getClaimType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CLAIM_SUBMITTED:
                    processClaimSubmission(event, correlationId);
                    break;

                case INITIAL_VALIDATION:
                    performInitialValidation(event, correlationId);
                    break;

                case DOCUMENT_VERIFICATION:
                    verifyDocuments(event, correlationId);
                    break;

                case POLICY_VERIFICATION:
                    verifyPolicy(event, correlationId);
                    break;

                case ELIGIBILITY_CHECK:
                    checkEligibility(event, correlationId);
                    break;

                case CLAIM_REGISTERED:
                    registerClaim(event, correlationId);
                    break;

                case FIRST_NOTICE_OF_LOSS:
                    processFNOL(event, correlationId);
                    break;

                case CLAIM_ASSIGNED:
                    assignClaim(event, correlationId);
                    break;

                case SUBMISSION_REJECTED:
                    rejectSubmission(event, correlationId);
                    break;

                default:
                    log.warn("Unknown claim submission event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("INSURANCE_CLAIM_SUBMISSION_EVENT_PROCESSED", event.getClaimId(),
                Map.of("eventType", event.getEventType(), "claimType", event.getClaimType(),
                    "policyId", event.getPolicyId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process claim submission event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("insurance-claim-submission-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInsuranceClaimSubmissionEventFallback(
            InsuranceClaimSubmissionEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("claim-submission-fallback-%s-p%d-o%d", event.getClaimId(), partition, offset);

        log.error("Circuit breaker fallback triggered for claim submission: claimId={}, error={}",
            event.getClaimId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("insurance-claim-submission-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Insurance Claim Submission Circuit Breaker Triggered",
                String.format("Claim %s submission failed: %s", event.getClaimId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInsuranceClaimSubmissionEvent(
            @Payload InsuranceClaimSubmissionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-claim-submission-%s-%d", event.getClaimId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Claim submission permanently failed: claimId={}, topic={}, error={}",
            event.getClaimId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("INSURANCE_CLAIM_SUBMISSION_DLT_EVENT", event.getClaimId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Insurance Claim Submission Dead Letter Event",
                String.format("Claim %s submission sent to DLT: %s", event.getClaimId(), exceptionMessage),
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

    private void processClaimSubmission(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = ClaimSubmission.builder()
            .claimId(event.getClaimId())
            .policyId(event.getPolicyId())
            .claimantId(event.getClaimantId())
            .claimType(event.getClaimType())
            .incidentDate(event.getIncidentDate())
            .submissionDate(LocalDateTime.now())
            .status("SUBMITTED")
            .claimAmount(event.getClaimAmount())
            .description(event.getDescription())
            .correlationId(correlationId)
            .build();
        claimSubmissionRepository.save(submission);

        claimSubmissionService.generateClaimNumber(event.getClaimId());
        claimSubmissionService.assignPriority(event.getClaimId(), event.getClaimType(), event.getClaimAmount());

        notificationService.sendNotification(event.getClaimantId(), "Claim Submitted Successfully",
            "We have received your insurance claim and will begin processing it shortly.",
            correlationId);

        kafkaTemplate.send("claim-intake-workflow", Map.of(
            "claimId", event.getClaimId(),
            "eventType", "INITIAL_VALIDATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClaimSubmitted(event.getClaimType());

        log.info("Claim submitted: claimId={}, type={}, amount={}",
            event.getClaimId(), event.getClaimType(), event.getClaimAmount());
    }

    private void performInitialValidation(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = claimSubmissionRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim submission not found"));

        submission.setStatus("INITIAL_VALIDATION");
        submission.setValidationStartedAt(LocalDateTime.now());
        claimSubmissionRepository.save(submission);

        boolean validationPassed = claimSubmissionService.performInitialValidation(event.getClaimId());

        if (validationPassed) {
            kafkaTemplate.send("claim-intake-workflow", Map.of(
                "claimId", event.getClaimId(),
                "eventType", "DOCUMENT_VERIFICATION",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("claim-intake-workflow", Map.of(
                "claimId", event.getClaimId(),
                "eventType", "SUBMISSION_REJECTED",
                "rejectionReason", "INITIAL_VALIDATION_FAILED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInitialValidation(validationPassed);

        log.info("Initial validation completed: claimId={}, passed={}",
            event.getClaimId(), validationPassed);
    }

    private void verifyDocuments(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = claimSubmissionRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim submission not found"));

        submission.setStatus("DOCUMENT_VERIFICATION");
        submission.setDocumentVerificationStartedAt(LocalDateTime.now());
        claimSubmissionRepository.save(submission);

        List<String> missingDocuments = documentValidationService.validateRequiredDocuments(
            event.getClaimId(), event.getClaimType());

        if (missingDocuments.isEmpty()) {
            kafkaTemplate.send("claim-intake-workflow", Map.of(
                "claimId", event.getClaimId(),
                "eventType", "POLICY_VERIFICATION",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            claimSubmissionService.requestMissingDocuments(event.getClaimId(), missingDocuments);

            notificationService.sendNotification(event.getClaimantId(), "Additional Documents Required",
                "Please provide the missing documents to proceed with your claim.",
                correlationId);
        }

        metricsService.recordDocumentVerification(missingDocuments.isEmpty());

        log.info("Document verification completed: claimId={}, missingDocs={}",
            event.getClaimId(), missingDocuments.size());
    }

    private void verifyPolicy(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = claimSubmissionRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim submission not found"));

        submission.setStatus("POLICY_VERIFICATION");
        submission.setPolicyVerificationStartedAt(LocalDateTime.now());
        claimSubmissionRepository.save(submission);

        boolean policyValid = claimSubmissionService.verifyPolicyStatus(event.getPolicyId(), event.getIncidentDate());

        if (policyValid) {
            kafkaTemplate.send("claim-intake-workflow", Map.of(
                "claimId", event.getClaimId(),
                "eventType", "ELIGIBILITY_CHECK",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("claim-intake-workflow", Map.of(
                "claimId", event.getClaimId(),
                "eventType", "SUBMISSION_REJECTED",
                "rejectionReason", "POLICY_INVALID",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordPolicyVerification(policyValid);

        log.info("Policy verification completed: claimId={}, valid={}",
            event.getClaimId(), policyValid);
    }

    private void checkEligibility(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = claimSubmissionRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim submission not found"));

        submission.setStatus("ELIGIBILITY_CHECK");
        submission.setEligibilityCheckStartedAt(LocalDateTime.now());
        claimSubmissionRepository.save(submission);

        boolean eligible = claimSubmissionService.checkCoverageEligibility(event.getClaimId(),
            event.getClaimType(), event.getIncidentDate());

        if (eligible) {
            kafkaTemplate.send("claim-intake-workflow", Map.of(
                "claimId", event.getClaimId(),
                "eventType", "CLAIM_REGISTERED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("claim-intake-workflow", Map.of(
                "claimId", event.getClaimId(),
                "eventType", "SUBMISSION_REJECTED",
                "rejectionReason", "NOT_ELIGIBLE",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordEligibilityCheck(eligible);

        log.info("Eligibility check completed: claimId={}, eligible={}",
            event.getClaimId(), eligible);
    }

    private void registerClaim(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = claimSubmissionRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim submission not found"));

        submission.setStatus("REGISTERED");
        submission.setRegisteredAt(LocalDateTime.now());
        claimSubmissionRepository.save(submission);

        claimSubmissionService.registerClaimInSystem(event.getClaimId());
        claimSubmissionService.createClaimFile(event.getClaimId());

        kafkaTemplate.send("claim-intake-workflow", Map.of(
            "claimId", event.getClaimId(),
            "eventType", "FIRST_NOTICE_OF_LOSS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification(event.getClaimantId(), "Claim Registered",
            "Your claim has been registered and assigned a claim number for tracking.",
            correlationId);

        metricsService.recordClaimRegistered();

        log.info("Claim registered: claimId={}", event.getClaimId());
    }

    private void processFNOL(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = claimSubmissionRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim submission not found"));

        submission.setStatus("FNOL_PROCESSED");
        submission.setFnolProcessedAt(LocalDateTime.now());
        claimSubmissionRepository.save(submission);

        claimSubmissionService.processFirstNoticeOfLoss(event.getClaimId());
        claimSubmissionService.notifyRelevantParties(event.getClaimId());

        kafkaTemplate.send("claim-intake-workflow", Map.of(
            "claimId", event.getClaimId(),
            "eventType", "CLAIM_ASSIGNED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordFNOLProcessed();

        log.info("FNOL processed: claimId={}", event.getClaimId());
    }

    private void assignClaim(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = claimSubmissionRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim submission not found"));

        String adjusterId = claimSubmissionService.assignClaimToAdjuster(event.getClaimId(), event.getClaimType());

        submission.setStatus("ASSIGNED");
        submission.setAssignedAt(LocalDateTime.now());
        submission.setAssignedAdjusterId(adjusterId);
        claimSubmissionRepository.save(submission);

        notificationService.sendNotification(event.getClaimantId(), "Claim Assigned",
            "Your claim has been assigned to an adjuster who will contact you shortly.",
            correlationId);

        kafkaTemplate.send("insurance-claim-processing-events", Map.of(
            "claimId", event.getClaimId(),
            "eventType", "REVIEW_INITIATED",
            "adjusterId", adjusterId,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClaimAssigned();

        log.info("Claim assigned: claimId={}, adjuster={}",
            event.getClaimId(), adjusterId);
    }

    private void rejectSubmission(InsuranceClaimSubmissionEvent event, String correlationId) {
        ClaimSubmission submission = claimSubmissionRepository.findByClaimId(event.getClaimId())
            .orElseThrow(() -> new RuntimeException("Claim submission not found"));

        submission.setStatus("REJECTED");
        submission.setRejectedAt(LocalDateTime.now());
        submission.setRejectionReason(event.getRejectionReason());
        claimSubmissionRepository.save(submission);

        notificationService.sendNotification(event.getClaimantId(), "Claim Submission Rejected",
            String.format("Your claim submission was rejected. Reason: %s", event.getRejectionReason()),
            correlationId);

        metricsService.recordSubmissionRejected(event.getRejectionReason());

        log.warn("Claim submission rejected: claimId={}, reason={}",
            event.getClaimId(), event.getRejectionReason());
    }
}