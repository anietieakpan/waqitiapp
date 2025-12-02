package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.compliance.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for OFAC screening results
 * Handles Office of Foreign Assets Control screening results with immediate
 * action on sanctions hits and compliance workflow management
 *
 * Critical for: Sanctions compliance, OFAC screening, AML requirements
 * SLA: Must process OFAC screening results within 3 seconds for regulatory requirements
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OfacScreeningResultsConsumer {

    private final OfacScreeningService ofacScreeningService;
    private final SanctionsComplianceService sanctionsComplianceService;
    private final ComplianceWorkflowService complianceWorkflowService;
    private final LegalNotificationService legalNotificationService;
    private final ComplianceAuditService complianceAuditService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter ofacScreeningCounter = Counter.builder("ofac_screening_results_processed_total")
            .description("Total number of OFAC screening results processed")
            .register(metricsService.getMeterRegistry());

    private final Counter sanctionsHitCounter = Counter.builder("sanctions_hits_detected_total")
            .description("Total number of sanctions hits detected")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("ofac_screening_processing_duration")
            .description("Time taken to process OFAC screening results")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"ofac-screening-results"},
        groupId = "compliance-service-ofac-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "ofac-screening-processor", fallbackMethod = "handleOfacScreeningFailure")
    @Retry(name = "ofac-screening-processor")
    public void processOfacScreeningResult(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.info("COMPLIANCE: Processing OFAC screening result: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("OFAC screening result {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate OFAC screening data
            OfacScreeningData screeningData = extractOfacScreeningData(event.getPayload());
            validateOfacScreeningData(screeningData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process OFAC screening result
            processOfacScreening(screeningData, event);

            // Record successful processing metrics
            ofacScreeningCounter.increment();

            if ("HIT".equals(screeningData.getScreeningResult())) {
                sanctionsHitCounter.increment();
            }

            // Audit the screening processing
            auditOfacScreeningProcessing(screeningData, event, "SUCCESS");

            log.info("COMPLIANCE: Successfully processed OFAC screening: {} for entity: {} - result: {} confidence: {}",
                    eventId, screeningData.getEntityId(), screeningData.getScreeningResult(), screeningData.getConfidenceScore());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("COMPLIANCE: Invalid OFAC screening data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process OFAC screening: {}", eventId, e);
            auditOfacScreeningProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("OFAC screening processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private OfacScreeningData extractOfacScreeningData(Map<String, Object> payload) {
        return OfacScreeningData.builder()
                .screeningId(extractString(payload, "screeningId"))
                .entityId(extractString(payload, "entityId"))
                .entityType(extractString(payload, "entityType"))
                .screeningResult(extractString(payload, "screeningResult"))
                .confidenceScore(extractDouble(payload, "confidenceScore"))
                .matchDetails(extractMap(payload, "matchDetails"))
                .sanctionsList(extractString(payload, "sanctionsList"))
                .sanctionsProgram(extractString(payload, "sanctionsProgram"))
                .riskLevel(extractString(payload, "riskLevel"))
                .screeningDate(extractInstant(payload, "screeningDate"))
                .screeningSource(extractString(payload, "screeningSource"))
                .reviewRequired(extractBoolean(payload, "reviewRequired"))
                .falsePositiveFlag(extractBoolean(payload, "falsePositiveFlag"))
                .complianceNotes(extractString(payload, "complianceNotes"))
                .build();
    }

    private void validateOfacScreeningData(OfacScreeningData screeningData) {
        if (screeningData.getScreeningId() == null || screeningData.getScreeningId().trim().isEmpty()) {
            throw new IllegalArgumentException("Screening ID is required");
        }

        if (screeningData.getEntityId() == null || screeningData.getEntityId().trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID is required");
        }

        if (screeningData.getScreeningResult() == null || screeningData.getScreeningResult().trim().isEmpty()) {
            throw new IllegalArgumentException("Screening result is required");
        }

        List<String> validResults = List.of("HIT", "NO_HIT", "FALSE_POSITIVE", "PENDING_REVIEW");
        if (!validResults.contains(screeningData.getScreeningResult())) {
            throw new IllegalArgumentException("Invalid screening result: " + screeningData.getScreeningResult());
        }

        if (screeningData.getScreeningDate() == null) {
            throw new IllegalArgumentException("Screening date is required");
        }
    }

    private void processOfacScreening(OfacScreeningData screeningData, GenericKafkaEvent event) {
        log.info("COMPLIANCE: Processing OFAC screening - Entity: {}, Result: {}, Confidence: {}, Risk: {}",
                screeningData.getEntityId(), screeningData.getScreeningResult(),
                screeningData.getConfidenceScore(), screeningData.getRiskLevel());

        try {
            // Process screening result
            switch (screeningData.getScreeningResult()) {
                case "HIT":
                    handleSanctionsHit(screeningData);
                    break;
                case "NO_HIT":
                    handleNoHit(screeningData);
                    break;
                case "FALSE_POSITIVE":
                    handleFalsePositive(screeningData);
                    break;
                case "PENDING_REVIEW":
                    handlePendingReview(screeningData);
                    break;
                default:
                    log.warn("Unknown OFAC screening result: {}", screeningData.getScreeningResult());
            }

            // Update screening database
            ofacScreeningService.updateScreeningResult(screeningData);

            // Update compliance monitoring
            updateComplianceMonitoring(screeningData);

            log.info("COMPLIANCE: OFAC screening processed - Entity: {}, Result: {}, Action: {}",
                    screeningData.getEntityId(), screeningData.getScreeningResult(),
                    getScreeningAction(screeningData.getScreeningResult()));

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process OFAC screening for: {}", screeningData.getEntityId(), e);
            executeEmergencyOfacProcedures(screeningData, e);
            throw new RuntimeException("OFAC screening processing failed", e);
        }
    }

    private void handleSanctionsHit(OfacScreeningData screeningData) {
        log.error("SANCTIONS HIT DETECTED: Entity {} matched sanctions list {}",
                screeningData.getEntityId(), screeningData.getSanctionsList());

        // Immediate actions for sanctions hit
        sanctionsComplianceService.freezeEntity(screeningData.getEntityId(), "OFAC_SANCTIONS_HIT");

        // Critical alert
        legalNotificationService.sendCriticalComplianceAlert(
                "SANCTIONS HIT DETECTED - IMMEDIATE ACTION REQUIRED",
                String.format("Entity %s matched OFAC sanctions list %s with confidence %.2f",
                        screeningData.getEntityId(), screeningData.getSanctionsList(), screeningData.getConfidenceScore()),
                "COMPLIANCE_TEAM"
        );

        // Initiate sanctions workflow
        complianceWorkflowService.initiateSanctionsWorkflow(
                screeningData.getEntityId(),
                screeningData.getSanctionsList(),
                screeningData.getMatchDetails()
        );

        // Generate regulatory report
        complianceWorkflowService.generateOfacReport(
                screeningData.getScreeningId(),
                "SANCTIONS_HIT",
                screeningData.getMatchDetails()
        );

        // Executive notification for high-confidence hits
        if (screeningData.getConfidenceScore() >= 0.95) {
            legalNotificationService.sendExecutiveComplianceAlert(
                    "High-Confidence OFAC Sanctions Hit",
                    screeningData,
                    "OFAC_SANCTIONS_HIT"
            );
        }
    }

    private void handleNoHit(OfacScreeningData screeningData) {
        // Clear entity for processing
        sanctionsComplianceService.clearEntityForProcessing(
                screeningData.getEntityId(),
                "OFAC_SCREENING_CLEAR"
        );

        // Update entity risk profile
        ofacScreeningService.updateEntityRiskProfile(
                screeningData.getEntityId(),
                "CLEAR",
                screeningData.getScreeningDate()
        );
    }

    private void handleFalsePositive(OfacScreeningData screeningData) {
        // Record false positive
        ofacScreeningService.recordFalsePositive(
                screeningData.getScreeningId(),
                screeningData.getEntityId(),
                screeningData.getComplianceNotes()
        );

        // Clear entity
        sanctionsComplianceService.clearEntityForProcessing(
                screeningData.getEntityId(),
                "FALSE_POSITIVE_CONFIRMED"
        );

        // Update screening algorithms if needed
        ofacScreeningService.updateScreeningAlgorithms(
                screeningData.getMatchDetails(),
                "FALSE_POSITIVE"
        );
    }

    private void handlePendingReview(OfacScreeningData screeningData) {
        // Queue for manual review
        complianceWorkflowService.queueForManualReview(
                screeningData.getScreeningId(),
                screeningData.getEntityId(),
                "OFAC_MANUAL_REVIEW",
                screeningData.getMatchDetails()
        );

        // Notify compliance team
        legalNotificationService.notifyComplianceTeam(
                "OFAC Screening Requires Manual Review",
                String.format("Entity %s requires manual review with confidence %.2f",
                        screeningData.getEntityId(), screeningData.getConfidenceScore())
        );

        // Temporary hold if high confidence
        if (screeningData.getConfidenceScore() >= 0.75) {
            sanctionsComplianceService.temporaryHold(
                    screeningData.getEntityId(),
                    "PENDING_OFAC_REVIEW"
            );
        }
    }

    private void updateComplianceMonitoring(OfacScreeningData screeningData) {
        // Update OFAC compliance metrics
        complianceAuditService.updateOfacComplianceMetrics(
                screeningData.getScreeningResult(),
                screeningData.getConfidenceScore(),
                screeningData.getRiskLevel()
        );

        // Update sanctions monitoring dashboard
        sanctionsComplianceService.updateSanctionsMonitoringDashboard(
                screeningData.getEntityId(),
                screeningData.getScreeningResult(),
                screeningData.getSanctionsList()
        );
    }

    private String getScreeningAction(String screeningResult) {
        return switch (screeningResult) {
            case "HIT" -> "ENTITY_FROZEN";
            case "NO_HIT" -> "ENTITY_CLEARED";
            case "FALSE_POSITIVE" -> "FALSE_POSITIVE_RECORDED";
            case "PENDING_REVIEW" -> "QUEUED_FOR_REVIEW";
            default -> "UNKNOWN_ACTION";
        };
    }

    private void executeEmergencyOfacProcedures(OfacScreeningData screeningData, Exception error) {
        log.error("EMERGENCY: Executing emergency OFAC procedures due to processing failure");

        try {
            // Emergency freeze if potential hit
            if ("HIT".equals(screeningData.getScreeningResult()) ||
                (screeningData.getConfidenceScore() != null && screeningData.getConfidenceScore() >= 0.75)) {
                sanctionsComplianceService.emergencyFreeze(
                        screeningData.getEntityId(),
                        "EMERGENCY_OFAC_FREEZE"
                );
            }

            // Emergency notification
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: OFAC Screening Processing Failed",
                    String.format("Failed to process OFAC screening for %s: %s",
                            screeningData.getEntityId(), error.getMessage())
            );

            // Manual intervention alert
            legalNotificationService.escalateToManualIntervention(
                    screeningData.getScreeningId(),
                    "OFAC_SCREENING_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency OFAC procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("COMPLIANCE: OFAC screening validation failed for event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "OFAC_SCREENING_VALIDATION_ERROR",
                null,
                "OFAC screening validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditOfacScreeningProcessing(OfacScreeningData screeningData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "OFAC_SCREENING_PROCESSED",
                    screeningData != null ? screeningData.getEntityId() : null,
                    String.format("OFAC screening processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "screeningId", screeningData != null ? screeningData.getScreeningId() : "unknown",
                            "entityId", screeningData != null ? screeningData.getEntityId() : "unknown",
                            "screeningResult", screeningData != null ? screeningData.getScreeningResult() : "unknown",
                            "confidenceScore", screeningData != null ? screeningData.getConfidenceScore() : 0.0,
                            "sanctionsList", screeningData != null ? screeningData.getSanctionsList() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit OFAC screening processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: OFAC screening event sent to DLT - EventId: {}", event.getEventId());

        try {
            OfacScreeningData screeningData = extractOfacScreeningData(event.getPayload());

            // Emergency procedures for DLT events
            if ("HIT".equals(screeningData.getScreeningResult())) {
                sanctionsComplianceService.emergencyFreeze(
                        screeningData.getEntityId(),
                        "DLT_SANCTIONS_HIT"
                );
            }

            // Critical alert
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: OFAC Screening in DLT",
                    "OFAC screening could not be processed - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle OFAC screening DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleOfacScreeningFailure(GenericKafkaEvent event, String topic, int partition,
                                         long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for OFAC screening processing - EventId: {}",
                event.getEventId(), e);

        try {
            OfacScreeningData screeningData = extractOfacScreeningData(event.getPayload());

            // Emergency protection
            if ("HIT".equals(screeningData.getScreeningResult())) {
                sanctionsComplianceService.emergencyFreeze(
                        screeningData.getEntityId(),
                        "CIRCUIT_BREAKER_OFAC_HIT"
                );
            }

            // Emergency alert
            legalNotificationService.sendEmergencySystemAlert(
                    "OFAC Screening Circuit Breaker Open",
                    "OFAC screening processing is failing - sanctions compliance at risk"
            );

        } catch (Exception ex) {
            log.error("Failed to handle OFAC screening circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private Boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data class
    @lombok.Data
    @lombok.Builder
    public static class OfacScreeningData {
        private String screeningId;
        private String entityId;
        private String entityType;
        private String screeningResult;
        private Double confidenceScore;
        private Map<String, Object> matchDetails;
        private String sanctionsList;
        private String sanctionsProgram;
        private String riskLevel;
        private Instant screeningDate;
        private String screeningSource;
        private Boolean reviewRequired;
        private Boolean falsePositiveFlag;
        private String complianceNotes;
    }
}