package com.waqiti.insurance.kafka;

import com.waqiti.common.events.InsurancePolicyCreationEvent;
import com.waqiti.insurance.domain.PolicyCreation;
import com.waqiti.insurance.repository.PolicyCreationRepository;
import com.waqiti.insurance.repository.PolicyRepository;
import com.waqiti.insurance.service.PolicyCreationService;
import com.waqiti.insurance.service.UnderwritingService;
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
public class InsurancePolicyCreationEventsConsumer {

    private final PolicyCreationRepository policyCreationRepository;
    private final PolicyRepository policyRepository;
    private final PolicyCreationService policyCreationService;
    private final UnderwritingService underwritingService;
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
        successCounter = Counter.builder("insurance_policy_creation_processed_total")
            .description("Total number of successfully processed insurance policy creation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("insurance_policy_creation_errors_total")
            .description("Total number of insurance policy creation processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("insurance_policy_creation_processing_duration")
            .description("Time taken to process insurance policy creation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"insurance-policy-creation-events", "policy-underwriting-workflow", "policy-issuance-requests"},
        groupId = "insurance-policy-creation-service-group",
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
    @CircuitBreaker(name = "insurance-policy-creation", fallbackMethod = "handleInsurancePolicyCreationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInsurancePolicyCreationEvent(
            @Payload InsurancePolicyCreationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("policy-creation-%s-p%d-o%d", event.getApplicationId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getApplicationId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing policy creation event: applicationId={}, policyType={}, applicantId={}",
                event.getApplicationId(), event.getPolicyType(), event.getApplicantId());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case APPLICATION_SUBMITTED:
                    processApplication(event, correlationId);
                    break;

                case ELIGIBILITY_VERIFICATION:
                    verifyEligibility(event, correlationId);
                    break;

                case UNDERWRITING_INITIATED:
                    initiateUnderwriting(event, correlationId);
                    break;

                case RISK_ASSESSMENT:
                    performRiskAssessment(event, correlationId);
                    break;

                case MEDICAL_REVIEW:
                    conductMedicalReview(event, correlationId);
                    break;

                case UNDERWRITING_APPROVED:
                    approveUnderwriting(event, correlationId);
                    break;

                case POLICY_ISSUED:
                    issuePolicy(event, correlationId);
                    break;

                case PREMIUM_CALCULATED:
                    calculatePremium(event, correlationId);
                    break;

                case APPLICATION_DECLINED:
                    declineApplication(event, correlationId);
                    break;

                case POLICY_ACTIVATED:
                    activatePolicy(event, correlationId);
                    break;

                default:
                    log.warn("Unknown policy creation event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("INSURANCE_POLICY_CREATION_EVENT_PROCESSED", event.getApplicationId(),
                Map.of("eventType", event.getEventType(), "policyType", event.getPolicyType(),
                    "applicantId", event.getApplicantId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process policy creation event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("insurance-policy-creation-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInsurancePolicyCreationEventFallback(
            InsurancePolicyCreationEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("policy-creation-fallback-%s-p%d-o%d", event.getApplicationId(), partition, offset);

        log.error("Circuit breaker fallback triggered for policy creation: applicationId={}, error={}",
            event.getApplicationId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("insurance-policy-creation-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Insurance Policy Creation Circuit Breaker Triggered",
                String.format("Policy creation %s failed: %s", event.getApplicationId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInsurancePolicyCreationEvent(
            @Payload InsurancePolicyCreationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-policy-creation-%s-%d", event.getApplicationId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Policy creation permanently failed: applicationId={}, topic={}, error={}",
            event.getApplicationId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("INSURANCE_POLICY_CREATION_DLT_EVENT", event.getApplicationId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Insurance Policy Creation Dead Letter Event",
                String.format("Policy creation %s sent to DLT: %s", event.getApplicationId(), exceptionMessage),
                Map.of("applicationId", event.getApplicationId(), "topic", topic, "correlationId", correlationId)
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

    private void processApplication(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = PolicyCreation.builder()
            .applicationId(event.getApplicationId())
            .applicantId(event.getApplicantId())
            .policyType(event.getPolicyType())
            .coverageAmount(event.getCoverageAmount())
            .status("APPLICATION_SUBMITTED")
            .applicationDate(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        policyCreationRepository.save(creation);

        policyCreationService.generateApplicationNumber(event.getApplicationId());
        policyCreationService.validateApplicationData(event.getApplicationId());

        notificationService.sendNotification(event.getApplicantId(), "Insurance Application Received",
            "We have received your insurance application and will begin processing it shortly.",
            correlationId);

        kafkaTemplate.send("policy-underwriting-workflow", Map.of(
            "applicationId", event.getApplicationId(),
            "eventType", "ELIGIBILITY_VERIFICATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordApplicationSubmitted(event.getPolicyType());

        log.info("Application processed: applicationId={}, type={}, coverage={}",
            event.getApplicationId(), event.getPolicyType(), event.getCoverageAmount());
    }

    private void verifyEligibility(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        creation.setStatus("ELIGIBILITY_VERIFICATION");
        creation.setEligibilityCheckStartedAt(LocalDateTime.now());
        policyCreationRepository.save(creation);

        boolean eligible = policyCreationService.verifyEligibility(event.getApplicationId(), event.getApplicantId());

        if (eligible) {
            creation.setEligible(true);
            policyCreationRepository.save(creation);

            kafkaTemplate.send("policy-underwriting-workflow", Map.of(
                "applicationId", event.getApplicationId(),
                "eventType", "UNDERWRITING_INITIATED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("policy-underwriting-workflow", Map.of(
                "applicationId", event.getApplicationId(),
                "eventType", "APPLICATION_DECLINED",
                "declineReason", "ELIGIBILITY_FAILED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordEligibilityVerification(eligible);

        log.info("Eligibility verification completed: applicationId={}, eligible={}",
            event.getApplicationId(), eligible);
    }

    private void initiateUnderwriting(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        creation.setStatus("UNDERWRITING_INITIATED");
        creation.setUnderwritingStartedAt(LocalDateTime.now());
        creation.setUnderwriterId(event.getUnderwriterId());
        policyCreationRepository.save(creation);

        underwritingService.assignUnderwriter(event.getApplicationId(), event.getPolicyType());
        underwritingService.gatherRequiredDocuments(event.getApplicationId());

        notificationService.sendNotification(event.getApplicantId(), "Underwriting Process Started",
            "Your application is now under review by our underwriting team.",
            correlationId);

        kafkaTemplate.send("policy-underwriting-workflow", Map.of(
            "applicationId", event.getApplicationId(),
            "eventType", "RISK_ASSESSMENT",
            "underwriterId", event.getUnderwriterId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordUnderwritingInitiated();

        log.info("Underwriting initiated: applicationId={}, underwriter={}",
            event.getApplicationId(), event.getUnderwriterId());
    }

    private void performRiskAssessment(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        creation.setStatus("RISK_ASSESSMENT");
        creation.setRiskAssessmentStartedAt(LocalDateTime.now());
        policyCreationRepository.save(creation);

        var riskRating = underwritingService.performRiskAssessment(event.getApplicationId(), event.getPolicyType());
        var requiresMedical = underwritingService.checkMedicalRequirements(event.getApplicationId(),
            event.getCoverageAmount());

        creation.setRiskRating(riskRating);
        creation.setRequiresMedicalReview(requiresMedical);
        policyCreationRepository.save(creation);

        if (requiresMedical) {
            kafkaTemplate.send("policy-underwriting-workflow", Map.of(
                "applicationId", event.getApplicationId(),
                "eventType", "MEDICAL_REVIEW",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("policy-underwriting-workflow", Map.of(
                "applicationId", event.getApplicationId(),
                "eventType", "PREMIUM_CALCULATED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRiskAssessment(riskRating);

        log.info("Risk assessment completed: applicationId={}, rating={}, medicalRequired={}",
            event.getApplicationId(), riskRating, requiresMedical);
    }

    private void conductMedicalReview(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        creation.setStatus("MEDICAL_REVIEW");
        creation.setMedicalReviewStartedAt(LocalDateTime.now());
        policyCreationRepository.save(creation);

        underwritingService.requestMedicalExamination(event.getApplicationId());
        underwritingService.orderMedicalRecords(event.getApplicationId());

        boolean medicalCleared = underwritingService.reviewMedicalInformation(event.getApplicationId());

        if (medicalCleared) {
            creation.setMedicalCleared(true);
            policyCreationRepository.save(creation);

            kafkaTemplate.send("policy-underwriting-workflow", Map.of(
                "applicationId", event.getApplicationId(),
                "eventType", "PREMIUM_CALCULATED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("policy-underwriting-workflow", Map.of(
                "applicationId", event.getApplicationId(),
                "eventType", "APPLICATION_DECLINED",
                "declineReason", "MEDICAL_REVIEW_FAILED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordMedicalReview(medicalCleared);

        log.info("Medical review completed: applicationId={}, cleared={}",
            event.getApplicationId(), medicalCleared);
    }

    private void calculatePremium(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        creation.setStatus("PREMIUM_CALCULATED");
        creation.setPremiumCalculatedAt(LocalDateTime.now());
        policyCreationRepository.save(creation);

        var premiumAmount = underwritingService.calculatePremium(event.getApplicationId(),
            creation.getRiskRating(), event.getCoverageAmount());

        creation.setPremiumAmount(premiumAmount);
        policyCreationRepository.save(creation);

        kafkaTemplate.send("policy-underwriting-workflow", Map.of(
            "applicationId", event.getApplicationId(),
            "eventType", "UNDERWRITING_APPROVED",
            "premiumAmount", premiumAmount,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPremiumCalculated();

        log.info("Premium calculated: applicationId={}, amount={}",
            event.getApplicationId(), premiumAmount);
    }

    private void approveUnderwriting(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        creation.setStatus("UNDERWRITING_APPROVED");
        creation.setUnderwritingApprovedAt(LocalDateTime.now());
        policyCreationRepository.save(creation);

        underwritingService.finalizeUnderwritingDecision(event.getApplicationId(), "APPROVED");

        notificationService.sendNotification(event.getApplicantId(), "Application Approved",
            "Congratulations! Your insurance application has been approved. Your policy will be issued shortly.",
            correlationId);

        kafkaTemplate.send("policy-underwriting-workflow", Map.of(
            "applicationId", event.getApplicationId(),
            "eventType", "POLICY_ISSUED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordUnderwritingApproved();

        log.info("Underwriting approved: applicationId={}", event.getApplicationId());
    }

    private void issuePolicy(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        String policyId = policyCreationService.generatePolicyNumber();

        creation.setStatus("POLICY_ISSUED");
        creation.setPolicyId(policyId);
        creation.setPolicyIssuedAt(LocalDateTime.now());
        policyCreationRepository.save(creation);

        var policy = policyCreationService.createPolicy(event.getApplicationId(), policyId,
            event.getApplicantId(), event.getPolicyType(), event.getCoverageAmount(), creation.getPremiumAmount());

        policyCreationService.generatePolicyDocuments(policyId);
        policyCreationService.setupPremiumSchedule(policyId, creation.getPremiumAmount());

        notificationService.sendNotification(event.getApplicantId(), "Policy Issued",
            "Your insurance policy has been issued. Policy documents are being prepared.",
            correlationId);

        kafkaTemplate.send("insurance-lifecycle-events", Map.of(
            "policyId", policyId,
            "applicationId", event.getApplicationId(),
            "policyHolderId", event.getApplicantId(),
            "eventType", "POLICY_CREATED",
            "policyType", event.getPolicyType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        kafkaTemplate.send("policy-underwriting-workflow", Map.of(
            "applicationId", event.getApplicationId(),
            "policyId", policyId,
            "eventType", "POLICY_ACTIVATED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPolicyIssued(event.getPolicyType());

        log.info("Policy issued: applicationId={}, policyId={}, type={}",
            event.getApplicationId(), policyId, event.getPolicyType());
    }

    private void activatePolicy(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        creation.setStatus("POLICY_ACTIVATED");
        creation.setPolicyActivatedAt(LocalDateTime.now());
        policyCreationRepository.save(creation);

        var policy = policyRepository.findById(creation.getPolicyId()).orElseThrow();
        policy.setStatus("ACTIVE");
        policy.setEffectiveDate(LocalDateTime.now().toLocalDate());
        policyRepository.save(policy);

        policyCreationService.activatePolicy(creation.getPolicyId());
        policyCreationService.schedulePremiumNotifications(creation.getPolicyId());

        notificationService.sendNotification(event.getApplicantId(), "Policy Activated",
            "Your insurance policy is now active and coverage is in effect.",
            correlationId);

        kafkaTemplate.send("insurance-premium-payment-events", Map.of(
            "policyId", creation.getPolicyId(),
            "eventType", "FIRST_PREMIUM_DUE",
            "premiumAmount", creation.getPremiumAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPolicyActivated(event.getPolicyType());

        log.info("Policy activated: applicationId={}, policyId={}",
            event.getApplicationId(), creation.getPolicyId());
    }

    private void declineApplication(InsurancePolicyCreationEvent event, String correlationId) {
        PolicyCreation creation = policyCreationRepository.findByApplicationId(event.getApplicationId())
            .orElseThrow(() -> new RuntimeException("Policy creation not found"));

        creation.setStatus("APPLICATION_DECLINED");
        creation.setDeclinedAt(LocalDateTime.now());
        creation.setDeclineReason(event.getDeclineReason());
        policyCreationRepository.save(creation);

        underwritingService.finalizeUnderwritingDecision(event.getApplicationId(), "DECLINED");

        notificationService.sendNotification(event.getApplicantId(), "Application Decision",
            String.format("We regret to inform you that your application was not approved. Reason: %s",
                event.getDeclineReason()),
            correlationId);

        metricsService.recordApplicationDeclined(event.getDeclineReason());

        log.warn("Application declined: applicationId={}, reason={}",
            event.getApplicationId(), event.getDeclineReason());
    }
}