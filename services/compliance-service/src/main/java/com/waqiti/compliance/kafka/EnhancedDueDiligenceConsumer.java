package com.waqiti.compliance.kafka;

import com.waqiti.common.events.EnhancedDueDiligenceEvent;
import com.waqiti.compliance.domain.EDDCase;
import com.waqiti.compliance.repository.EDDCaseRepository;
import com.waqiti.compliance.service.EnhancedDueDiligenceService;
import com.waqiti.compliance.service.RiskAssessmentService;
import com.waqiti.compliance.service.ComplianceService;
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
public class EnhancedDueDiligenceConsumer {

    private final EDDCaseRepository eddCaseRepository;
    private final EnhancedDueDiligenceService eddService;
    private final RiskAssessmentService riskAssessmentService;
    private final ComplianceService complianceService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter highRiskCasesCounter;
    private Counter eddTriggeredCounter;
    private Counter eddCompletedCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("enhanced_due_diligence_processed_total")
            .description("Total number of successfully processed EDD events")
            .register(meterRegistry);
        errorCounter = Counter.builder("enhanced_due_diligence_errors_total")
            .description("Total number of EDD processing errors")
            .register(meterRegistry);
        highRiskCasesCounter = Counter.builder("edd_high_risk_cases_total")
            .description("Total number of high-risk EDD cases")
            .register(meterRegistry);
        eddTriggeredCounter = Counter.builder("edd_triggered_total")
            .description("Total number of EDD procedures triggered")
            .register(meterRegistry);
        eddCompletedCounter = Counter.builder("edd_completed_total")
            .description("Total number of completed EDD procedures")
            .register(meterRegistry);
        processingTimer = Timer.builder("enhanced_due_diligence_processing_duration")
            .description("Time taken to process EDD events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"enhanced-due-diligence", "edd-triggers", "high-risk-customer-events", "pep-events", "beneficial-owner-events"},
        groupId = "compliance-edd-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 1.5, maxDelay = 16000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "enhanced-due-diligence", fallbackMethod = "handleEDDFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 1.5, maxDelay = 16000))
    public void handleEnhancedDueDiligenceEvent(
            @Payload EnhancedDueDiligenceEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("edd-%s-p%d-o%d", event.getCustomerId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCustomerId(), event.getTriggerType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing enhanced due diligence event: customerId={}, triggerType={}, riskLevel={}, urgency={}",
                event.getCustomerId(), event.getTriggerType(), event.getRiskLevel(), event.getUrgencyLevel());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case EDD_REQUIRED:
                    processEDDRequired(event, correlationId);
                    break;

                case EDD_INITIATED:
                    processEDDInitiated(event, correlationId);
                    break;

                case RISK_PROFILE_UPDATED:
                    processRiskProfileUpdated(event, correlationId);
                    break;

                case PEP_IDENTIFIED:
                    processPEPIdentified(event, correlationId);
                    break;

                case BENEFICIAL_OWNER_CHANGE:
                    processBeneficialOwnerChange(event, correlationId);
                    break;

                case HIGH_VALUE_TRANSACTION:
                    processHighValueTransaction(event, correlationId);
                    break;

                case GEOGRAPHIC_RISK_CHANGE:
                    processGeographicRiskChange(event, correlationId);
                    break;

                case EDD_REVIEW_REQUIRED:
                    processEDDReviewRequired(event, correlationId);
                    break;

                case EDD_COMPLETED:
                    processEDDCompleted(event, correlationId);
                    break;

                case EDD_ESCALATED:
                    processEDDEscalated(event, correlationId);
                    break;

                default:
                    log.warn("Unknown EDD event type: {}", event.getEventType());
                    break;
            }

            // Check for immediate regulatory notifications
            if (requiresImmediateRegulatoryNotification(event)) {
                notifyRegulatoryBodies(event, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("EDD_EVENT_PROCESSED", event.getCustomerId(),
                Map.of("triggerType", event.getTriggerType(), "riskLevel", event.getRiskLevel(),
                    "urgencyLevel", event.getUrgencyLevel(), "correlationId", correlationId,
                    "eventType", event.getEventType(), "timestamp", Instant.now()));

            successCounter.increment();
            if ("HIGH".equals(event.getRiskLevel()) || "CRITICAL".equals(event.getRiskLevel())) {
                highRiskCasesCounter.increment();
            }
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process EDD event: {}", e.getMessage(), e);

            // Escalate high-risk EDD failures immediately
            if ("CRITICAL".equals(event.getRiskLevel()) || "URGENT".equals(event.getUrgencyLevel())) {
                escalateEDDFailure(event, correlationId, e);
            }

            // Send fallback event
            kafkaTemplate.send("edd-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "requiresEscalation", "CRITICAL".equals(event.getRiskLevel())));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleEDDFallback(
            EnhancedDueDiligenceEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("edd-fallback-%s-p%d-o%d", event.getCustomerId(), partition, offset);

        log.error("Circuit breaker fallback triggered for EDD: customerId={}, error={}",
            event.getCustomerId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("edd-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for EDD failures
        try {
            notificationService.sendCriticalAlert(
                "Critical EDD System Failure",
                String.format("EDD processing failed for customer %s (%s): %s",
                    event.getCustomerId(), event.getTriggerType(), ex.getMessage()),
                "CRITICAL"
            );

            // Mandatory escalation for EDD failures
            escalateEDDFailure(event, correlationId, ex);

        } catch (Exception notificationEx) {
            log.error("Failed to send critical EDD alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltEDDEvent(
            @Payload EnhancedDueDiligenceEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-edd-%s-%d", event.getCustomerId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - EDD permanently failed: customerId={}, topic={}, error={}",
            event.getCustomerId(), topic, exceptionMessage);

        // Save to dead letter store for compliance investigation
        auditService.logComplianceEvent("EDD_DLT_EVENT", event.getCustomerId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "triggerType", event.getTriggerType(), "correlationId", correlationId,
                "requiresRegulatoryNotification", true, "priority", "EMERGENCY", "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Emergency: EDD in DLT",
                String.format("CRITICAL: EDD processing sent to DLT - Customer: %s, Trigger: %s, Error: %s",
                    event.getCustomerId(), event.getTriggerType(), exceptionMessage),
                Map.of("customerId", event.getCustomerId(), "triggerType", event.getTriggerType(),
                    "topic", topic, "correlationId", correlationId,
                    "severity", "EMERGENCY", "requiresImmediateAction", true)
            );

            // Mandatory executive and regulatory escalation for DLT events
            escalateEDDFailure(event, correlationId, new Exception(exceptionMessage));
            notifyRegulatoryBodies(event, correlationId);

        } catch (Exception ex) {
            log.error("Failed to send emergency EDD DLT alert: {}", ex.getMessage());
        }
    }

    private void processEDDRequired(EnhancedDueDiligenceEvent event, String correlationId) {
        EDDCase eddCase = EDDCase.builder()
            .caseId(UUID.randomUUID().toString())
            .customerId(event.getCustomerId())
            .triggerType(event.getTriggerType())
            .riskLevel(event.getRiskLevel())
            .urgencyLevel(event.getUrgencyLevel())
            .status("REQUIRED")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .triggerDetails(event.getTriggerDetails())
            .build();

        eddCaseRepository.save(eddCase);

        eddTriggeredCounter.increment();

        // Auto-assign based on risk level and urgency
        String assignedAnalyst = eddService.assignEDDAnalyst(event.getRiskLevel(), event.getUrgencyLevel());
        eddCase.setAssignedAnalyst(assignedAnalyst);
        eddCaseRepository.save(eddCase);

        // Set priority deadlines based on risk level
        LocalDateTime deadline = calculateEDDDeadline(event.getRiskLevel(), event.getUrgencyLevel());
        eddCase.setDeadline(deadline);
        eddCaseRepository.save(eddCase);

        // Send notification to assigned analyst
        notificationService.sendAssignmentNotification(assignedAnalyst,
            "EDD Case Assigned",
            String.format("New EDD case assigned: Customer %s, Risk: %s, Deadline: %s",
                event.getCustomerId(), event.getRiskLevel(), deadline),
            correlationId);

        // Initiate automatic EDD procedures
        kafkaTemplate.send("edd-procedures-initiate", Map.of(
            "caseId", eddCase.getCaseId(),
            "customerId", event.getCustomerId(),
            "triggerType", event.getTriggerType(),
            "riskLevel", event.getRiskLevel(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("EDD required and case created: caseId={}, customerId={}, analyst={}",
            eddCase.getCaseId(), event.getCustomerId(), assignedAnalyst);
    }

    private void processEDDInitiated(EnhancedDueDiligenceEvent event, String correlationId) {
        EDDCase eddCase = eddCaseRepository.findByCustomerId(event.getCustomerId())
            .orElseThrow(() -> new RuntimeException("EDD case not found"));

        eddCase.setStatus("IN_PROGRESS");
        eddCase.setInitiatedAt(LocalDateTime.now());
        eddCase.setInitiatedBy(event.getInitiatedBy());
        eddCaseRepository.save(eddCase);

        // Start comprehensive customer review
        eddService.initiateComprehensiveReview(event.getCustomerId(), correlationId);

        // Check for immediate red flags
        if (eddService.hasImmediateRedFlags(event.getCustomerId())) {
            eddCase.setUrgencyLevel("CRITICAL");
            eddCaseRepository.save(eddCase);

            notificationService.sendCriticalAlert(
                "Critical Red Flags in EDD",
                String.format("Critical red flags detected during EDD initiation for customer %s",
                    event.getCustomerId()),
                "HIGH"
            );
        }

        log.info("EDD initiated: caseId={}, customerId={}, initiatedBy={}",
            eddCase.getCaseId(), event.getCustomerId(), event.getInitiatedBy());
    }

    private void processRiskProfileUpdated(EnhancedDueDiligenceEvent event, String correlationId) {
        // Update risk assessment
        riskAssessmentService.updateCustomerRiskProfile(event.getCustomerId(),
            event.getRiskLevel(), event.getRiskFactors(), correlationId);

        // Check if risk escalation requires additional EDD
        if (isRiskEscalationSignificant(event.getPreviousRiskLevel(), event.getRiskLevel())) {
            kafkaTemplate.send("edd-triggers", Map.of(
                "customerId", event.getCustomerId(),
                "triggerType", "RISK_ESCALATION",
                "riskLevel", event.getRiskLevel(),
                "previousRiskLevel", event.getPreviousRiskLevel(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Risk profile updated: customerId={}, newRisk={}, previousRisk={}",
            event.getCustomerId(), event.getRiskLevel(), event.getPreviousRiskLevel());
    }

    private void processPEPIdentified(EnhancedDueDiligenceEvent event, String correlationId) {
        // Immediate enhanced screening for PEP
        eddService.initiatePEPEnhancedScreening(event.getCustomerId(), event.getPepDetails(), correlationId);

        // Create high-priority EDD case
        EDDCase eddCase = EDDCase.builder()
            .caseId(UUID.randomUUID().toString())
            .customerId(event.getCustomerId())
            .triggerType("PEP_IDENTIFIED")
            .riskLevel("HIGH")
            .urgencyLevel("URGENT")
            .status("REQUIRED")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .pepDetails(event.getPepDetails())
            .build();

        eddCaseRepository.save(eddCase);

        // Immediate notification to senior compliance
        notificationService.sendExecutiveAlert(
            "PEP Identified",
            String.format("Politically Exposed Person identified: Customer %s, Position: %s",
                event.getCustomerId(), event.getPepDetails().getPosition()),
            "HIGH"
        );

        // Enhanced monitoring
        complianceService.enableEnhancedMonitoring(event.getCustomerId(), "PEP_IDENTIFIED", correlationId);

        log.warn("PEP identified: customerId={}, position={}",
            event.getCustomerId(), event.getPepDetails().getPosition());
    }

    private void processBeneficialOwnerChange(EnhancedDueDiligenceEvent event, String correlationId) {
        // Verify beneficial ownership changes
        eddService.verifyBeneficialOwnershipChange(event.getCustomerId(),
            event.getBeneficialOwnerDetails(), correlationId);

        // Screen new beneficial owners
        for (String newOwner : event.getBeneficialOwnerDetails().getNewOwners()) {
            kafkaTemplate.send("sanctions-screening-required", Map.of(
                "entityId", newOwner,
                "entityType", "BENEFICIAL_OWNER",
                "customerId", event.getCustomerId(),
                "screeningType", "EDD_BENEFICIAL_OWNER",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Beneficial owner change processed: customerId={}, newOwners={}",
            event.getCustomerId(), event.getBeneficialOwnerDetails().getNewOwners().size());
    }

    private void processHighValueTransaction(EnhancedDueDiligenceEvent event, String correlationId) {
        // Enhanced transaction monitoring
        eddService.analyzeHighValueTransaction(event.getCustomerId(),
            event.getTransactionDetails(), correlationId);

        // Check for suspicious patterns
        if (eddService.detectsSuspiciousPatterns(event.getCustomerId(), event.getTransactionDetails())) {
            complianceService.flagForSuspiciousActivity(event.getCustomerId(),
                "HIGH_VALUE_TRANSACTION_PATTERN", correlationId);
        }

        log.info("High-value transaction analyzed: customerId={}, amount={}",
            event.getCustomerId(), event.getTransactionDetails().getAmount());
    }

    private void processGeographicRiskChange(EnhancedDueDiligenceEvent event, String correlationId) {
        // Update geographic risk assessment
        riskAssessmentService.updateGeographicRisk(event.getCustomerId(),
            event.getGeographicDetails(), correlationId);

        // Check if customer moved to high-risk jurisdiction
        if (event.getGeographicDetails().isHighRiskJurisdiction()) {
            kafkaTemplate.send("edd-triggers", Map.of(
                "customerId", event.getCustomerId(),
                "triggerType", "HIGH_RISK_JURISDICTION",
                "riskLevel", "HIGH",
                "jurisdictionDetails", event.getGeographicDetails(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Geographic risk change processed: customerId={}, newJurisdiction={}",
            event.getCustomerId(), event.getGeographicDetails().getNewJurisdiction());
    }

    private void processEDDReviewRequired(EnhancedDueDiligenceEvent event, String correlationId) {
        EDDCase eddCase = eddCaseRepository.findByCustomerId(event.getCustomerId())
            .orElseThrow(() -> new RuntimeException("EDD case not found"));

        eddCase.setStatus("REVIEW_REQUIRED");
        eddCase.setReviewRequestedAt(LocalDateTime.now());
        eddCase.setReviewReason(event.getReviewReason());
        eddCaseRepository.save(eddCase);

        // Assign to senior analyst for review
        String seniorAnalyst = eddService.assignSeniorAnalyst(event.getRiskLevel());
        eddCase.setReviewedBy(seniorAnalyst);
        eddCaseRepository.save(eddCase);

        notificationService.sendAssignmentNotification(seniorAnalyst,
            "EDD Review Required",
            String.format("EDD case requires senior review: Customer %s, Reason: %s",
                event.getCustomerId(), event.getReviewReason()),
            correlationId);

        log.info("EDD review required: caseId={}, customerId={}, reviewedBy={}",
            eddCase.getCaseId(), event.getCustomerId(), seniorAnalyst);
    }

    private void processEDDCompleted(EnhancedDueDiligenceEvent event, String correlationId) {
        EDDCase eddCase = eddCaseRepository.findByCustomerId(event.getCustomerId())
            .orElseThrow(() -> new RuntimeException("EDD case not found"));

        eddCase.setStatus("COMPLETED");
        eddCase.setCompletedAt(LocalDateTime.now());
        eddCase.setCompletedBy(event.getCompletedBy());
        eddCase.setFinalRiskLevel(event.getFinalRiskLevel());
        eddCase.setConclusion(event.getConclusion());
        eddCaseRepository.save(eddCase);

        eddCompletedCounter.increment();

        // Update customer risk profile based on EDD conclusion
        riskAssessmentService.updateCustomerRiskProfile(event.getCustomerId(),
            event.getFinalRiskLevel(), event.getRiskFactors(), correlationId);

        // Set ongoing monitoring requirements
        complianceService.setOngoingMonitoring(event.getCustomerId(),
            event.getFinalRiskLevel(), correlationId);

        // Schedule next EDD review if required
        if (requiresPeriodicReview(event.getFinalRiskLevel())) {
            LocalDateTime nextReviewDate = calculateNextReviewDate(event.getFinalRiskLevel());
            eddService.schedulePeriodicReview(event.getCustomerId(), nextReviewDate, correlationId);
        }

        log.info("EDD completed: caseId={}, customerId={}, finalRisk={}, conclusion={}",
            eddCase.getCaseId(), event.getCustomerId(), event.getFinalRiskLevel(), event.getConclusion());
    }

    private void processEDDEscalated(EnhancedDueDiligenceEvent event, String correlationId) {
        EDDCase eddCase = eddCaseRepository.findByCustomerId(event.getCustomerId())
            .orElseThrow(() -> new RuntimeException("EDD case not found"));

        eddCase.setStatus("ESCALATED");
        eddCase.setEscalatedAt(LocalDateTime.now());
        eddCase.setEscalatedBy(event.getEscalatedBy());
        eddCase.setEscalationReason(event.getEscalationReason());
        eddCaseRepository.save(eddCase);

        // Escalate to executive team
        escalateEDDCase(event, correlationId);

        // Create regulatory incident if required
        if (requiresRegulatoryNotification(event.getEscalationReason())) {
            complianceService.createRegulatoryIncident("EDD_ESCALATION",
                event.getEscalationReason(), "HIGH", correlationId);
        }

        log.error("EDD escalated: caseId={}, customerId={}, reason={}",
            eddCase.getCaseId(), event.getCustomerId(), event.getEscalationReason());
    }

    // Helper methods

    private LocalDateTime calculateEDDDeadline(String riskLevel, String urgencyLevel) {
        switch (urgencyLevel) {
            case "CRITICAL":
                return LocalDateTime.now().plusHours(24);
            case "URGENT":
                return LocalDateTime.now().plusDays(3);
            case "HIGH":
                return LocalDateTime.now().plusDays(7);
            case "MEDIUM":
                return LocalDateTime.now().plusDays(14);
            default:
                return LocalDateTime.now().plusDays(30);
        }
    }

    private boolean isRiskEscalationSignificant(String previousRisk, String newRisk) {
        Map<String, Integer> riskLevels = Map.of(
            "LOW", 1, "MEDIUM", 2, "HIGH", 3, "CRITICAL", 4
        );
        return riskLevels.getOrDefault(newRisk, 0) > riskLevels.getOrDefault(previousRisk, 0);
    }

    private boolean requiresImmediateRegulatoryNotification(EnhancedDueDiligenceEvent event) {
        return "CRITICAL".equals(event.getRiskLevel()) ||
               "PEP_IDENTIFIED".equals(event.getTriggerType()) ||
               "SANCTIONS_HIT".equals(event.getTriggerType());
    }

    private boolean requiresPeriodicReview(String riskLevel) {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    private LocalDateTime calculateNextReviewDate(String riskLevel) {
        switch (riskLevel) {
            case "CRITICAL":
                return LocalDateTime.now().plusMonths(6);
            case "HIGH":
                return LocalDateTime.now().plusMonths(12);
            default:
                return LocalDateTime.now().plusMonths(24);
        }
    }

    private boolean requiresRegulatoryNotification(String escalationReason) {
        return escalationReason.contains("SANCTIONS") ||
               escalationReason.contains("TERRORISM") ||
               escalationReason.contains("MONEY_LAUNDERING");
    }

    private void escalateEDDFailure(EnhancedDueDiligenceEvent event, String correlationId, Exception error) {
        try {
            notificationService.sendExecutiveEscalation(
                "Critical EDD Processing Failure",
                String.format("URGENT: EDD processing failure requiring executive attention.\n" +
                    "Customer ID: %s\n" +
                    "Trigger Type: %s\n" +
                    "Risk Level: %s\n" +
                    "Urgency: %s\n" +
                    "Error: %s\n" +
                    "Correlation ID: %s\n" +
                    "Time: %s",
                    event.getCustomerId(), event.getTriggerType(), event.getRiskLevel(),
                    event.getUrgencyLevel(), error.getMessage(), correlationId, Instant.now()),
                Map.of(
                    "priority", "EMERGENCY",
                    "category", "EDD_COMPLIANCE",
                    "customerId", event.getCustomerId(),
                    "correlationId", correlationId,
                    "requiresImmediateAction", true
                )
            );
        } catch (Exception ex) {
            log.error("Failed to escalate EDD failure to executive team: {}", ex.getMessage());
        }
    }

    private void escalateEDDCase(EnhancedDueDiligenceEvent event, String correlationId) {
        try {
            notificationService.sendExecutiveEscalation(
                "EDD Case Escalation",
                String.format("EDD case escalated to executive level.\n" +
                    "Customer ID: %s\n" +
                    "Escalation Reason: %s\n" +
                    "Escalated By: %s\n" +
                    "Correlation ID: %s\n" +
                    "Time: %s",
                    event.getCustomerId(), event.getEscalationReason(),
                    event.getEscalatedBy(), correlationId, Instant.now()),
                Map.of(
                    "priority", "HIGH",
                    "category", "EDD_ESCALATION",
                    "customerId", event.getCustomerId(),
                    "correlationId", correlationId,
                    "requiresAction", true
                )
            );
        } catch (Exception ex) {
            log.error("Failed to escalate EDD case to executive team: {}", ex.getMessage());
        }
    }

    private void notifyRegulatoryBodies(EnhancedDueDiligenceEvent event, String correlationId) {
        try {
            eddService.notifyRegulatoryBodyOfEDDEvent(
                event.getCustomerId(),
                event.getTriggerType(),
                event.getRiskLevel(),
                correlationId
            );
        } catch (Exception ex) {
            log.error("Failed to notify regulatory body of EDD event: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}