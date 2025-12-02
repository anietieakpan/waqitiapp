package com.waqiti.reconciliation.kafka;

import com.waqiti.common.events.ReconciliationMismatchEvent;
import com.waqiti.reconciliation.domain.Reconciliation;
import com.waqiti.reconciliation.domain.ReconciliationMismatch;
import com.waqiti.reconciliation.repository.ReconciliationRepository;
import com.waqiti.reconciliation.repository.ReconciliationMismatchRepository;
import com.waqiti.reconciliation.service.MismatchAnalysisService;
import com.waqiti.reconciliation.service.MismatchResolutionService;
import com.waqiti.reconciliation.service.EscalationService;
import com.waqiti.reconciliation.service.InvestigationService;
import com.waqiti.reconciliation.metrics.ReconciliationMetricsService;
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
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReconciliationMismatchEventsConsumer {

    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationMismatchRepository mismatchRepository;
    private final MismatchAnalysisService mismatchAnalysisService;
    private final MismatchResolutionService resolutionService;
    private final EscalationService escalationService;
    private final InvestigationService investigationService;
    private final ReconciliationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Critical thresholds
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal CRITICAL_VALUE_THRESHOLD = new BigDecimal("50000.00");

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Counter highValueMismatchCounter;
    private Counter criticalMismatchCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("reconciliation_mismatch_events_processed_total")
            .description("Total number of successfully processed reconciliation mismatch events")
            .register(meterRegistry);
        errorCounter = Counter.builder("reconciliation_mismatch_events_errors_total")
            .description("Total number of reconciliation mismatch events processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("reconciliation_mismatch_events_processing_duration")
            .description("Time taken to process reconciliation mismatch events")
            .register(meterRegistry);
        highValueMismatchCounter = Counter.builder("reconciliation_high_value_mismatches_total")
            .description("Total number of high value reconciliation mismatches")
            .register(meterRegistry);
        criticalMismatchCounter = Counter.builder("reconciliation_critical_mismatches_total")
            .description("Total number of critical reconciliation mismatches")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"reconciliation-mismatch-events", "reconciliation-discrepancy-events", "mismatch-detection-events"},
        groupId = "reconciliation-mismatch-events-service-group",
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
    @CircuitBreaker(name = "reconciliation-mismatch-events", fallbackMethod = "handleReconciliationMismatchEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleReconciliationMismatchEvent(
            @Payload ReconciliationMismatchEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("mismatch-%s-p%d-o%d", event.getMismatchId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getMismatchId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing reconciliation mismatch: mismatchId={}, eventType={}, amount={}",
                event.getMismatchId(), event.getEventType(), event.getMismatchAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Assess mismatch severity
            String severity = assessMismatchSeverity(event.getMismatchAmount());

            switch (event.getEventType()) {
                case MISMATCH_DETECTED:
                    processMismatchDetected(event, correlationId, severity);
                    break;

                case MISMATCH_ANALYZED:
                    processMismatchAnalyzed(event, correlationId);
                    break;

                case MISMATCH_CATEGORIZED:
                    processMismatchCategorized(event, correlationId);
                    break;

                case INVESTIGATION_INITIATED:
                    processInvestigationInitiated(event, correlationId);
                    break;

                case MISMATCH_RESOLVED:
                    processMismatchResolved(event, correlationId);
                    break;

                case MISMATCH_ESCALATED:
                    processMismatchEscalated(event, correlationId);
                    break;

                case MANUAL_REVIEW_REQUIRED:
                    processManualReviewRequired(event, correlationId);
                    break;

                case SYSTEMATIC_ISSUE_DETECTED:
                    processSystematicIssueDetected(event, correlationId);
                    break;

                case FALSE_POSITIVE_IDENTIFIED:
                    processFalsePositiveIdentified(event, correlationId);
                    break;

                default:
                    log.warn("Unknown reconciliation mismatch event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logReconciliationEvent("RECONCILIATION_MISMATCH_EVENT_PROCESSED", event.getMismatchId(),
                Map.of("eventType", event.getEventType(), "mismatchAmount", event.getMismatchAmount(),
                    "severity", severity, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process reconciliation mismatch event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("reconciliation-mismatch-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleReconciliationMismatchEventFallback(
            ReconciliationMismatchEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("mismatch-fallback-%s-p%d-o%d", event.getMismatchId(), partition, offset);

        log.error("Circuit breaker fallback triggered for reconciliation mismatch: mismatchId={}, error={}",
            event.getMismatchId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("reconciliation-mismatch-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high value mismatches
        if (event.getMismatchAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            try {
                notificationService.sendCriticalAlert(
                    "High Value Reconciliation Mismatch Circuit Breaker",
                    String.format("High value mismatch %s processing failed: %s", event.getMismatchId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send critical alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltReconciliationMismatchEvent(
            @Payload ReconciliationMismatchEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-mismatch-%s-%d", event.getMismatchId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Reconciliation mismatch permanently failed: mismatchId={}, topic={}, error={}",
            event.getMismatchId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logReconciliationEvent("RECONCILIATION_MISMATCH_DLT_EVENT", event.getMismatchId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "mismatchAmount", event.getMismatchAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send emergency alert for critical mismatches
        try {
            String alertLevel = event.getMismatchAmount().compareTo(CRITICAL_VALUE_THRESHOLD) >= 0 ? "EMERGENCY" : "CRITICAL";
            notificationService.sendEmergencyAlert(
                "Reconciliation Mismatch Dead Letter Event",
                String.format("Critical: Mismatch %s (amount: %s) sent to DLT: %s",
                    event.getMismatchId(), event.getMismatchAmount(), exceptionMessage),
                Map.of("mismatchId", event.getMismatchId(), "amount", event.getMismatchAmount(),
                       "topic", topic, "correlationId", correlationId, "alertLevel", alertLevel)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private String assessMismatchSeverity(BigDecimal amount) {
        if (amount.compareTo(CRITICAL_VALUE_THRESHOLD) >= 0) {
            return "CRITICAL";
        } else if (amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            return "HIGH";
        } else if (amount.compareTo(new BigDecimal("1000.00")) >= 0) {
            return "MEDIUM";
        } else {
            return "LOW";
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

    private void processMismatchDetected(ReconciliationMismatchEvent event, String correlationId, String severity) {
        ReconciliationMismatch mismatch = ReconciliationMismatch.builder()
            .mismatchId(event.getMismatchId())
            .reconciliationId(event.getReconciliationId())
            .mismatchType(event.getMismatchType())
            .mismatchAmount(event.getMismatchAmount())
            .sourceAmount(event.getSourceAmount())
            .targetAmount(event.getTargetAmount())
            .severity(severity)
            .status("DETECTED")
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        mismatchRepository.save(mismatch);

        // Update reconciliation status
        updateReconciliationMismatchCount(event.getReconciliationId());

        // Initiate automatic analysis
        mismatchAnalysisService.initiateAnalysis(event.getMismatchId());

        // Send for immediate resolution if low value
        if ("LOW".equals(severity)) {
            kafkaTemplate.send("mismatch-auto-resolution-queue", Map.of(
                "mismatchId", event.getMismatchId(),
                "priority", "LOW",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Track high and critical value mismatches
        if ("HIGH".equals(severity)) {
            highValueMismatchCounter.increment();
            notificationService.sendOperationalAlert(
                "High Value Reconciliation Mismatch",
                String.format("High value mismatch detected: %s (Amount: %s)",
                    event.getMismatchId(), event.getMismatchAmount()),
                "HIGH"
            );
        } else if ("CRITICAL".equals(severity)) {
            criticalMismatchCounter.increment();
            notificationService.sendCriticalAlert(
                "Critical Value Reconciliation Mismatch",
                String.format("Critical mismatch detected: %s (Amount: %s)",
                    event.getMismatchId(), event.getMismatchAmount()),
                Map.of("mismatchId", event.getMismatchId(), "amount", event.getMismatchAmount())
            );
        }

        metricsService.recordMismatchDetected(event.getMismatchType(), event.getMismatchAmount(), severity);

        log.warn("Mismatch detected: mismatchId={}, type={}, amount={}, severity={}",
            event.getMismatchId(), event.getMismatchType(), event.getMismatchAmount(), severity);
    }

    private void processMismatchAnalyzed(ReconciliationMismatchEvent event, String correlationId) {
        ReconciliationMismatch mismatch = mismatchRepository.findByMismatchId(event.getMismatchId())
            .orElseThrow(() -> new RuntimeException("Mismatch not found"));

        mismatch.setStatus("ANALYZED");
        mismatch.setAnalyzedAt(LocalDateTime.now());
        mismatch.setAnalysisResults(event.getAnalysisResults());
        mismatch.setRootCause(event.getRootCause());
        mismatchRepository.save(mismatch);

        // Determine next action based on analysis
        String nextAction = determineNextAction(event.getRootCause(), event.getAnalysisResults());

        kafkaTemplate.send("mismatch-workflow", Map.of(
            "mismatchId", event.getMismatchId(),
            "nextAction", nextAction,
            "rootCause", event.getRootCause(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordMismatchAnalyzed(event.getRootCause());

        log.info("Mismatch analyzed: mismatchId={}, rootCause={}, nextAction={}",
            event.getMismatchId(), event.getRootCause(), nextAction);
    }

    private void processMismatchCategorized(ReconciliationMismatchEvent event, String correlationId) {
        ReconciliationMismatch mismatch = mismatchRepository.findByMismatchId(event.getMismatchId())
            .orElseThrow(() -> new RuntimeException("Mismatch not found"));

        mismatch.setCategory(event.getCategory());
        mismatch.setSubcategory(event.getSubcategory());
        mismatch.setCategorizedAt(LocalDateTime.now());
        mismatchRepository.save(mismatch);

        // Route to appropriate resolution queue based on category
        String resolutionQueue = getResolutionQueue(event.getCategory());

        kafkaTemplate.send(resolutionQueue, Map.of(
            "mismatchId", event.getMismatchId(),
            "category", event.getCategory(),
            "subcategory", event.getSubcategory(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordMismatchCategorized(event.getCategory(), event.getSubcategory());

        log.info("Mismatch categorized: mismatchId={}, category={}, subcategory={}",
            event.getMismatchId(), event.getCategory(), event.getSubcategory());
    }

    private void processInvestigationInitiated(ReconciliationMismatchEvent event, String correlationId) {
        ReconciliationMismatch mismatch = mismatchRepository.findByMismatchId(event.getMismatchId())
            .orElseThrow(() -> new RuntimeException("Mismatch not found"));

        mismatch.setStatus("UNDER_INVESTIGATION");
        mismatch.setInvestigationStartedAt(LocalDateTime.now());
        mismatch.setInvestigatedBy(event.getInvestigatedBy());
        mismatchRepository.save(mismatch);

        investigationService.initiateInvestigation(event.getMismatchId(), event.getInvestigationPriority());

        notificationService.sendNotification(event.getInvestigatedBy(), "Mismatch Investigation Assigned",
            String.format("You have been assigned to investigate mismatch %s (Amount: %s)",
                event.getMismatchId(), event.getMismatchAmount()),
            correlationId);

        metricsService.recordInvestigationInitiated();

        log.info("Investigation initiated: mismatchId={}, investigatedBy={}, priority={}",
            event.getMismatchId(), event.getInvestigatedBy(), event.getInvestigationPriority());
    }

    private void processMismatchResolved(ReconciliationMismatchEvent event, String correlationId) {
        ReconciliationMismatch mismatch = mismatchRepository.findByMismatchId(event.getMismatchId())
            .orElseThrow(() -> new RuntimeException("Mismatch not found"));

        mismatch.setStatus("RESOLVED");
        mismatch.setResolvedAt(LocalDateTime.now());
        mismatch.setResolvedBy(event.getResolvedBy());
        mismatch.setResolutionMethod(event.getResolutionMethod());
        mismatch.setResolutionNotes(event.getResolutionNotes());
        mismatchRepository.save(mismatch);

        resolutionService.applyResolution(event.getMismatchId(), event.getResolutionMethod());

        kafkaTemplate.send("reconciliation-updates", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "eventType", "MISMATCH_RESOLVED",
            "mismatchId", event.getMismatchId(),
            "resolutionMethod", event.getResolutionMethod(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification("FINANCE_TEAM", "Mismatch Resolved",
            String.format("Mismatch %s resolved via %s by %s",
                event.getMismatchId(), event.getResolutionMethod(), event.getResolvedBy()),
            correlationId);

        metricsService.recordMismatchResolved(event.getResolutionMethod(), event.getMismatchAmount());

        log.info("Mismatch resolved: mismatchId={}, method={}, resolvedBy={}",
            event.getMismatchId(), event.getResolutionMethod(), event.getResolvedBy());
    }

    private void processMismatchEscalated(ReconciliationMismatchEvent event, String correlationId) {
        ReconciliationMismatch mismatch = mismatchRepository.findByMismatchId(event.getMismatchId())
            .orElseThrow(() -> new RuntimeException("Mismatch not found"));

        mismatch.setStatus("ESCALATED");
        mismatch.setEscalatedAt(LocalDateTime.now());
        mismatch.setEscalatedBy(event.getEscalatedBy());
        mismatch.setEscalationReason(event.getEscalationReason());
        mismatch.setEscalationLevel(event.getEscalationLevel());
        mismatchRepository.save(mismatch);

        escalationService.escalateMismatch(event.getMismatchId(), event.getEscalationLevel(), event.getEscalationReason());

        String alertLevel = "CRITICAL".equals(event.getEscalationLevel()) ? "CRITICAL" : "HIGH";
        notificationService.sendOperationalAlert(
            "Reconciliation Mismatch Escalated",
            String.format("Mismatch %s escalated to %s level: %s",
                event.getMismatchId(), event.getEscalationLevel(), event.getEscalationReason()),
            alertLevel
        );

        metricsService.recordMismatchEscalated(event.getEscalationLevel());

        log.warn("Mismatch escalated: mismatchId={}, level={}, reason={}",
            event.getMismatchId(), event.getEscalationLevel(), event.getEscalationReason());
    }

    private void processManualReviewRequired(ReconciliationMismatchEvent event, String correlationId) {
        ReconciliationMismatch mismatch = mismatchRepository.findByMismatchId(event.getMismatchId())
            .orElseThrow(() -> new RuntimeException("Mismatch not found"));

        mismatch.setStatus("MANUAL_REVIEW_REQUIRED");
        mismatch.setRequiresManualReview(true);
        mismatch.setManualReviewRequestedAt(LocalDateTime.now());
        mismatchRepository.save(mismatch);

        kafkaTemplate.send("manual-review-queue", Map.of(
            "mismatchId", event.getMismatchId(),
            "priority", event.getReviewPriority(),
            "assignedTo", "RECONCILIATION_SPECIALIST",
            "reviewReason", event.getReviewReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification("RECONCILIATION_SPECIALIST", "Manual Review Required",
            String.format("Mismatch %s requires manual review: %s",
                event.getMismatchId(), event.getReviewReason()),
            correlationId);

        metricsService.recordManualReviewRequired();

        log.warn("Manual review required: mismatchId={}, reason={}",
            event.getMismatchId(), event.getReviewReason());
    }

    private void processSystematicIssueDetected(ReconciliationMismatchEvent event, String correlationId) {
        ReconciliationMismatch mismatch = mismatchRepository.findByMismatchId(event.getMismatchId())
            .orElseThrow(() -> new RuntimeException("Mismatch not found"));

        mismatch.setSystematicIssue(true);
        mismatch.setSystematicIssueType(event.getSystematicIssueType());
        mismatch.setSystematicIssueDetectedAt(LocalDateTime.now());
        mismatchRepository.save(mismatch);

        kafkaTemplate.send("systematic-issue-alerts", Map.of(
            "mismatchId", event.getMismatchId(),
            "issueType", event.getSystematicIssueType(),
            "affectedCount", event.getAffectedReconciliationCount(),
            "totalImpact", event.getTotalImpactAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert(
            "Systematic Reconciliation Issue Detected",
            String.format("Systematic issue detected in mismatch %s: %s (Affected: %d reconciliations, Impact: %s)",
                event.getMismatchId(), event.getSystematicIssueType(),
                event.getAffectedReconciliationCount(), event.getTotalImpactAmount()),
            Map.of("mismatchId", event.getMismatchId(), "issueType", event.getSystematicIssueType(),
                   "affectedCount", event.getAffectedReconciliationCount())
        );

        metricsService.recordSystematicIssueDetected(event.getSystematicIssueType(), event.getAffectedReconciliationCount());

        log.error("Systematic issue detected: mismatchId={}, issueType={}, affectedCount={}",
            event.getMismatchId(), event.getSystematicIssueType(), event.getAffectedReconciliationCount());
    }

    private void processFalsePositiveIdentified(ReconciliationMismatchEvent event, String correlationId) {
        ReconciliationMismatch mismatch = mismatchRepository.findByMismatchId(event.getMismatchId())
            .orElseThrow(() -> new RuntimeException("Mismatch not found"));

        mismatch.setStatus("FALSE_POSITIVE");
        mismatch.setFalsePositive(true);
        mismatch.setFalsePositiveReason(event.getFalsePositiveReason());
        mismatch.setFalsePositiveIdentifiedAt(LocalDateTime.now());
        mismatchRepository.save(mismatch);

        // Improve detection algorithms
        mismatchAnalysisService.updateDetectionAlgorithm(event.getFalsePositiveReason(), event.getMismatchType());

        kafkaTemplate.send("reconciliation-updates", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "eventType", "FALSE_POSITIVE_REMOVED",
            "mismatchId", event.getMismatchId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordFalsePositiveIdentified(event.getMismatchType());

        log.info("False positive identified: mismatchId={}, reason={}",
            event.getMismatchId(), event.getFalsePositiveReason());
    }

    private void updateReconciliationMismatchCount(String reconciliationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(reconciliationId).orElse(null);
        if (reconciliation != null) {
            long mismatchCount = mismatchRepository.countByReconciliationIdAndStatusNot(reconciliationId, "RESOLVED");
            reconciliation.setActiveMismatchCount((int) mismatchCount);
            reconciliationRepository.save(reconciliation);
        }
    }

    private String determineNextAction(String rootCause, String analysisResults) {
        if ("TIMING_DIFFERENCE".equals(rootCause)) {
            return "AUTO_RESOLVE";
        } else if ("DATA_ENTRY_ERROR".equals(rootCause)) {
            return "MANUAL_CORRECTION";
        } else if ("SYSTEM_ERROR".equals(rootCause)) {
            return "TECHNICAL_INVESTIGATION";
        } else {
            return "MANUAL_REVIEW";
        }
    }

    private String getResolutionQueue(String category) {
        switch (category) {
            case "TIMING":
                return "mismatch-timing-resolution-queue";
            case "AMOUNT":
                return "mismatch-amount-resolution-queue";
            case "REFERENCE":
                return "mismatch-reference-resolution-queue";
            case "STATUS":
                return "mismatch-status-resolution-queue";
            default:
                return "mismatch-general-resolution-queue";
        }
    }
}