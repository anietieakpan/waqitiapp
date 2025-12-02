package com.waqiti.dispute.kafka;

import com.waqiti.common.events.DisputeInvestigationEvent;
import com.waqiti.dispute.domain.DisputeInvestigation;
import com.waqiti.dispute.repository.DisputeInvestigationRepository;
import com.waqiti.dispute.service.DisputeInvestigationService;
import com.waqiti.dispute.service.ForensicAnalysisService;
import com.waqiti.dispute.service.EvidenceCollectionService;
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
public class DisputeInvestigationsConsumer {

    private final DisputeInvestigationRepository investigationRepository;
    private final DisputeInvestigationService investigationService;
    private final ForensicAnalysisService forensicAnalysisService;
    private final EvidenceCollectionService evidenceCollectionService;
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
        successCounter = Counter.builder("dispute_investigations_processed_total")
            .description("Total number of successfully processed dispute investigation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("dispute_investigations_errors_total")
            .description("Total number of dispute investigation processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("dispute_investigations_processing_duration")
            .description("Time taken to process dispute investigation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"dispute-investigations"},
        groupId = "dispute-investigations-service-group",
        containerFactory = "criticalDisputeKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dispute-investigations", fallbackMethod = "handleInvestigationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
    public void handleInvestigationEvent(
            @Payload DisputeInvestigationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("investigation-%s-p%d-o%d", event.getDisputeId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDisputeId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing dispute investigation: disputeId={}, eventType={}, investigationType={}",
                event.getDisputeId(), event.getEventType(), event.getInvestigationType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case INVESTIGATION_INITIATED:
                    processInvestigationInitiated(event, correlationId);
                    break;

                case EVIDENCE_COLLECTION_STARTED:
                    processEvidenceCollectionStarted(event, correlationId);
                    break;

                case EVIDENCE_COLLECTION_COMPLETED:
                    processEvidenceCollectionCompleted(event, correlationId);
                    break;

                case FORENSIC_ANALYSIS_STARTED:
                    processForensicAnalysisStarted(event, correlationId);
                    break;

                case FORENSIC_ANALYSIS_COMPLETED:
                    processForensicAnalysisCompleted(event, correlationId);
                    break;

                case INVESTIGATION_FINDINGS_REPORTED:
                    processInvestigationFindingsReported(event, correlationId);
                    break;

                case INVESTIGATION_ESCALATED:
                    processInvestigationEscalated(event, correlationId);
                    break;

                case INVESTIGATION_COMPLETED:
                    processInvestigationCompleted(event, correlationId);
                    break;

                case INVESTIGATION_SUSPENDED:
                    processInvestigationSuspended(event, correlationId);
                    break;

                default:
                    log.warn("Unknown dispute investigation event type: {}", event.getEventType());
                    processUnknownInvestigationEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("DISPUTE_INVESTIGATION_EVENT_PROCESSED", event.getDisputeId(),
                Map.of("eventType", event.getEventType(), "investigationType", event.getInvestigationType(),
                    "investigationId", event.getInvestigationId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process dispute investigation event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("dispute-investigations-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInvestigationEventFallback(
            DisputeInvestigationEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("investigation-fallback-%s-p%d-o%d", event.getDisputeId(), partition, offset);

        log.error("Circuit breaker fallback triggered for dispute investigation: disputeId={}, error={}",
            event.getDisputeId(), ex.getMessage());

        // Create incident for circuit breaker
        investigationService.createInvestigationIncident(
            "DISPUTE_INVESTIGATION_CIRCUIT_BREAKER",
            String.format("Dispute investigation circuit breaker triggered for dispute %s", event.getDisputeId()),
            "HIGH",
            Map.of("disputeId", event.getDisputeId(), "eventType", event.getEventType(),
                "investigationId", event.getInvestigationId(), "error", ex.getMessage(),
                "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("dispute-investigations-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send alert
        try {
            notificationService.sendOperationalAlert(
                "Dispute Investigation Circuit Breaker",
                String.format("Dispute %s investigation processing failed: %s",
                    event.getDisputeId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInvestigationEvent(
            @Payload DisputeInvestigationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-investigation-%s-%d", event.getDisputeId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Dispute investigation permanently failed: disputeId={}, topic={}, error={}",
            event.getDisputeId(), topic, exceptionMessage);

        // Create critical incident
        investigationService.createInvestigationIncident(
            "DISPUTE_INVESTIGATION_DLT_EVENT",
            String.format("Dispute investigation sent to DLT for dispute %s", event.getDisputeId()),
            "CRITICAL",
            Map.of("disputeId", event.getDisputeId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "investigationId", event.getInvestigationId(),
                "correlationId", correlationId, "requiresManualIntervention", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("DISPUTE_INVESTIGATION_DLT_EVENT", event.getDisputeId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Dispute Investigation Dead Letter Event",
                String.format("Dispute %s investigation sent to DLT: %s",
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

    private void processInvestigationInitiated(DisputeInvestigationEvent event, String correlationId) {
        // Create investigation record
        DisputeInvestigation investigation = DisputeInvestigation.builder()
            .investigationId(event.getInvestigationId())
            .disputeId(event.getDisputeId())
            .investigationType(event.getInvestigationType())
            .priority(event.getPriority())
            .assignedTo(event.getAssignedTo())
            .status("INITIATED")
            .initiatedAt(LocalDateTime.now())
            .initiatedBy(event.getInitiatedBy())
            .targetCompletionDate(event.getTargetCompletionDate())
            .correlationId(correlationId)
            .build();

        investigationRepository.save(investigation);

        // Setup investigation workflow
        investigationService.setupInvestigationWorkflow(event.getInvestigationId(),
            event.getInvestigationType(), event.getPriority());

        // Assign investigation team
        investigationService.assignInvestigationTeam(event.getInvestigationId(),
            event.getAssignedTo(), event.getInvestigationType());

        // Send initiation notification
        notificationService.sendDisputeNotification(
            event.getAssignedTo(),
            "Dispute Investigation Initiated",
            String.format("Investigation %s has been initiated for dispute %s",
                event.getInvestigationId(), event.getDisputeId()),
            correlationId
        );

        // Start evidence collection
        kafkaTemplate.send("dispute-investigations", Map.of(
            "disputeId", event.getDisputeId(),
            "investigationId", event.getInvestigationId(),
            "eventType", "EVIDENCE_COLLECTION_STARTED",
            "investigationType", event.getInvestigationType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordInvestigationInitiated(event.getInvestigationType());

        log.info("Dispute investigation initiated: disputeId={}, investigationId={}, type={}",
            event.getDisputeId(), event.getInvestigationId(), event.getInvestigationType());
    }

    private void processEvidenceCollectionStarted(DisputeInvestigationEvent event, String correlationId) {
        // Update investigation status
        DisputeInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));

        investigation.setStatus("EVIDENCE_COLLECTION");
        investigation.setEvidenceCollectionStartedAt(LocalDateTime.now());
        investigationRepository.save(investigation);

        // Start evidence collection process
        evidenceCollectionService.startEvidenceCollection(event.getInvestigationId(),
            event.getDisputeId(), event.getEvidenceTypes());

        // Set collection deadlines
        evidenceCollectionService.setCollectionDeadlines(event.getInvestigationId(),
            event.getEvidenceDeadlines());

        metricsService.recordEvidenceCollectionStarted(event.getInvestigationType());

        log.info("Evidence collection started: investigationId={}, evidenceTypes={}",
            event.getInvestigationId(), event.getEvidenceTypes());
    }

    private void processEvidenceCollectionCompleted(DisputeInvestigationEvent event, String correlationId) {
        // Update investigation status
        DisputeInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));

        investigation.setStatus("EVIDENCE_COLLECTION_COMPLETED");
        investigation.setEvidenceCollectionCompletedAt(LocalDateTime.now());
        investigation.setCollectedEvidence(event.getCollectedEvidence());
        investigationRepository.save(investigation);

        // Validate collected evidence
        boolean evidenceValid = evidenceCollectionService.validateCollectedEvidence(
            event.getInvestigationId(), event.getCollectedEvidence());

        if (evidenceValid) {
            // Start forensic analysis
            kafkaTemplate.send("dispute-investigations", Map.of(
                "disputeId", event.getDisputeId(),
                "investigationId", event.getInvestigationId(),
                "eventType", "FORENSIC_ANALYSIS_STARTED",
                "investigationType", event.getInvestigationType(),
                "collectedEvidence", event.getCollectedEvidence(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Request additional evidence
            evidenceCollectionService.requestAdditionalEvidence(event.getInvestigationId(),
                event.getMissingEvidence());
        }

        metricsService.recordEvidenceCollectionCompleted(event.getInvestigationType(),
            event.getCollectedEvidence().size());

        log.info("Evidence collection completed: investigationId={}, evidenceCount={}, valid={}",
            event.getInvestigationId(), event.getCollectedEvidence().size(), evidenceValid);
    }

    private void processForensicAnalysisStarted(DisputeInvestigationEvent event, String correlationId) {
        // Update investigation status
        DisputeInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));

        investigation.setStatus("FORENSIC_ANALYSIS");
        investigation.setForensicAnalysisStartedAt(LocalDateTime.now());
        investigationRepository.save(investigation);

        // Start forensic analysis
        forensicAnalysisService.startForensicAnalysis(event.getInvestigationId(),
            event.getCollectedEvidence(), event.getAnalysisScope());

        // Assign forensic experts
        forensicAnalysisService.assignForensicExperts(event.getInvestigationId(),
            event.getInvestigationType());

        metricsService.recordForensicAnalysisStarted(event.getInvestigationType());

        log.info("Forensic analysis started: investigationId={}, evidenceCount={}",
            event.getInvestigationId(), event.getCollectedEvidence().size());
    }

    private void processForensicAnalysisCompleted(DisputeInvestigationEvent event, String correlationId) {
        // Update investigation status
        DisputeInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));

        investigation.setStatus("FORENSIC_ANALYSIS_COMPLETED");
        investigation.setForensicAnalysisCompletedAt(LocalDateTime.now());
        investigation.setForensicFindings(event.getForensicFindings());
        investigationRepository.save(investigation);

        // Generate investigation report
        String reportId = investigationService.generateInvestigationReport(event.getInvestigationId(),
            event.getForensicFindings());

        // Send findings report event
        kafkaTemplate.send("dispute-investigations", Map.of(
            "disputeId", event.getDisputeId(),
            "investigationId", event.getInvestigationId(),
            "eventType", "INVESTIGATION_FINDINGS_REPORTED",
            "investigationType", event.getInvestigationType(),
            "forensicFindings", event.getForensicFindings(),
            "reportId", reportId,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordForensicAnalysisCompleted(event.getInvestigationType());

        log.info("Forensic analysis completed: investigationId={}, reportId={}",
            event.getInvestigationId(), reportId);
    }

    private void processInvestigationFindingsReported(DisputeInvestigationEvent event, String correlationId) {
        // Update investigation status
        DisputeInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));

        investigation.setStatus("FINDINGS_REPORTED");
        investigation.setFindingsReportedAt(LocalDateTime.now());
        investigation.setReportId(event.getReportId());
        investigationRepository.save(investigation);

        // Submit findings to decision makers
        investigationService.submitFindingsForDecision(event.getInvestigationId(),
            event.getReportId(), event.getRecommendation());

        // Notify stakeholders
        notificationService.sendDisputeNotification(
            event.getStakeholders(),
            "Investigation Findings Available",
            String.format("Investigation findings for dispute %s are now available (Report: %s)",
                event.getDisputeId(), event.getReportId()),
            correlationId
        );

        // Check if investigation should be completed or escalated
        if (event.getRecommendation().equals("ESCALATE")) {
            kafkaTemplate.send("dispute-investigations", Map.of(
                "disputeId", event.getDisputeId(),
                "investigationId", event.getInvestigationId(),
                "eventType", "INVESTIGATION_ESCALATED",
                "investigationType", event.getInvestigationType(),
                "escalationReason", event.getEscalationReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("dispute-investigations", Map.of(
                "disputeId", event.getDisputeId(),
                "investigationId", event.getInvestigationId(),
                "eventType", "INVESTIGATION_COMPLETED",
                "investigationType", event.getInvestigationType(),
                "recommendation", event.getRecommendation(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInvestigationFindingsReported(event.getInvestigationType());

        log.info("Investigation findings reported: investigationId={}, reportId={}, recommendation={}",
            event.getInvestigationId(), event.getReportId(), event.getRecommendation());
    }

    private void processInvestigationEscalated(DisputeInvestigationEvent event, String correlationId) {
        // Update investigation status
        DisputeInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));

        investigation.setStatus("ESCALATED");
        investigation.setEscalatedAt(LocalDateTime.now());
        investigation.setEscalationReason(event.getEscalationReason());
        investigation.setEscalatedTo(event.getEscalatedTo());
        investigationRepository.save(investigation);

        // Escalate to higher authority
        investigationService.escalateInvestigation(event.getInvestigationId(),
            event.getEscalatedTo(), event.getEscalationReason());

        // Send escalation notification
        notificationService.sendEscalationAlert(
            "Investigation Escalated",
            String.format("Investigation %s for dispute %s has been escalated: %s",
                event.getInvestigationId(), event.getDisputeId(), event.getEscalationReason()),
            "HIGH"
        );

        metricsService.recordInvestigationEscalated(event.getInvestigationType());

        log.warn("Investigation escalated: investigationId={}, reason={}, escalatedTo={}",
            event.getInvestigationId(), event.getEscalationReason(), event.getEscalatedTo());
    }

    private void processInvestigationCompleted(DisputeInvestigationEvent event, String correlationId) {
        // Update investigation status
        DisputeInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));

        investigation.setStatus("COMPLETED");
        investigation.setCompletedAt(LocalDateTime.now());
        investigation.setFinalRecommendation(event.getRecommendation());
        investigation.setCompletionNotes(event.getCompletionNotes());
        investigationRepository.save(investigation);

        // Close investigation workflow
        investigationService.closeInvestigationWorkflow(event.getInvestigationId());

        // Update dispute status based on recommendation
        kafkaTemplate.send("dispute-status-updates", Map.of(
            "disputeId", event.getDisputeId(),
            "status", "INVESTIGATION_COMPLETED",
            "recommendation", event.getRecommendation(),
            "investigationId", event.getInvestigationId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send completion notification
        notificationService.sendDisputeNotification(
            event.getStakeholders(),
            "Investigation Completed",
            String.format("Investigation %s for dispute %s has been completed with recommendation: %s",
                event.getInvestigationId(), event.getDisputeId(), event.getRecommendation()),
            correlationId
        );

        metricsService.recordInvestigationCompleted(event.getInvestigationType(),
            investigation.getInitiatedAt(), LocalDateTime.now());

        log.info("Investigation completed: investigationId={}, recommendation={}, duration={}",
            event.getInvestigationId(), event.getRecommendation(),
            java.time.Duration.between(investigation.getInitiatedAt(), LocalDateTime.now()).toHours());
    }

    private void processInvestigationSuspended(DisputeInvestigationEvent event, String correlationId) {
        // Update investigation status
        DisputeInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));

        investigation.setStatus("SUSPENDED");
        investigation.setSuspendedAt(LocalDateTime.now());
        investigation.setSuspensionReason(event.getSuspensionReason());
        investigationRepository.save(investigation);

        // Suspend investigation workflow
        investigationService.suspendInvestigationWorkflow(event.getInvestigationId(), event.getSuspensionReason());

        // Send suspension notification
        notificationService.sendDisputeNotification(
            event.getStakeholders(),
            "Investigation Suspended",
            String.format("Investigation %s for dispute %s has been suspended: %s",
                event.getInvestigationId(), event.getDisputeId(), event.getSuspensionReason()),
            correlationId
        );

        metricsService.recordInvestigationSuspended(event.getInvestigationType());

        log.warn("Investigation suspended: investigationId={}, reason={}",
            event.getInvestigationId(), event.getSuspensionReason());
    }

    private void processUnknownInvestigationEvent(DisputeInvestigationEvent event, String correlationId) {
        // Create incident for unknown event type
        investigationService.createInvestigationIncident(
            "UNKNOWN_DISPUTE_INVESTIGATION_EVENT",
            String.format("Unknown dispute investigation event type %s for investigation %s",
                event.getEventType(), event.getInvestigationId()),
            "MEDIUM",
            Map.of("disputeId", event.getDisputeId(), "investigationId", event.getInvestigationId(),
                "unknownEventType", event.getEventType(), "correlationId", correlationId)
        );

        log.warn("Unknown dispute investigation event: disputeId={}, investigationId={}, eventType={}",
            event.getDisputeId(), event.getInvestigationId(), event.getEventType());
    }
}