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
 * Production-grade Kafka consumer for PEP screening results
 * Handles Politically Exposed Person screening results with enhanced
 * due diligence workflows and risk assessment procedures
 *
 * Critical for: PEP compliance, enhanced due diligence, AML requirements
 * SLA: Must process PEP screening results within 5 seconds for regulatory requirements
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PepScreeningResultsConsumer {

    private final PepScreeningService pepScreeningService;
    private final EnhancedDueDiligenceService enhancedDueDiligenceService;
    private final RiskAssessmentService riskAssessmentService;
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
    private final Counter pepScreeningCounter = Counter.builder("pep_screening_results_processed_total")
            .description("Total number of PEP screening results processed")
            .register(metricsService.getMeterRegistry());

    private final Counter pepHitCounter = Counter.builder("pep_hits_detected_total")
            .description("Total number of PEP hits detected")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("pep_screening_processing_duration")
            .description("Time taken to process PEP screening results")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"pep-screening-results"},
        groupId = "compliance-service-pep-processor",
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
    @CircuitBreaker(name = "pep-screening-processor", fallbackMethod = "handlePepScreeningFailure")
    @Retry(name = "pep-screening-processor")
    public void processPepScreeningResult(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.info("COMPLIANCE: Processing PEP screening result: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            if (isEventAlreadyProcessed(eventId)) {
                log.info("PEP screening result {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            PepScreeningData screeningData = extractPepScreeningData(event.getPayload());
            validatePepScreeningData(screeningData);

            markEventAsProcessed(eventId);
            processPepScreening(screeningData, event);

            pepScreeningCounter.increment();
            if ("HIT".equals(screeningData.getScreeningResult())) {
                pepHitCounter.increment();
            }

            auditPepScreeningProcessing(screeningData, event, "SUCCESS");

            log.info("COMPLIANCE: Successfully processed PEP screening: {} for customer: {} - result: {} risk: {}",
                    eventId, screeningData.getCustomerId(), screeningData.getScreeningResult(), screeningData.getRiskLevel());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("COMPLIANCE: Invalid PEP screening data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process PEP screening: {}", eventId, e);
            auditPepScreeningProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("PEP screening processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private PepScreeningData extractPepScreeningData(Map<String, Object> payload) {
        return PepScreeningData.builder()
                .screeningId(extractString(payload, "screeningId"))
                .customerId(extractString(payload, "customerId"))
                .accountId(extractString(payload, "accountId"))
                .screeningResult(extractString(payload, "screeningResult"))
                .confidenceScore(extractDouble(payload, "confidenceScore"))
                .pepCategory(extractString(payload, "pepCategory"))
                .politicalPosition(extractString(payload, "politicalPosition"))
                .jurisdiction(extractString(payload, "jurisdiction"))
                .riskLevel(extractString(payload, "riskLevel"))
                .relationshipType(extractString(payload, "relationshipType"))
                .screeningDate(extractInstant(payload, "screeningDate"))
                .lastUpdateDate(extractInstant(payload, "lastUpdateDate"))
                .matchDetails(extractMap(payload, "matchDetails"))
                .enhancedDueDiligenceRequired(extractBoolean(payload, "enhancedDueDiligenceRequired"))
                .seniorManagementApprovalRequired(extractBoolean(payload, "seniorManagementApprovalRequired"))
                .complianceNotes(extractString(payload, "complianceNotes"))
                .build();
    }

    private void validatePepScreeningData(PepScreeningData screeningData) {
        if (screeningData.getScreeningId() == null || screeningData.getScreeningId().trim().isEmpty()) {
            throw new IllegalArgumentException("Screening ID is required");
        }

        if (screeningData.getCustomerId() == null || screeningData.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        List<String> validResults = List.of("HIT", "NO_HIT", "POTENTIAL_MATCH", "PENDING_REVIEW");
        if (!validResults.contains(screeningData.getScreeningResult())) {
            throw new IllegalArgumentException("Invalid screening result: " + screeningData.getScreeningResult());
        }

        if (screeningData.getScreeningDate() == null) {
            throw new IllegalArgumentException("Screening date is required");
        }
    }

    private void processPepScreening(PepScreeningData screeningData, GenericKafkaEvent event) {
        log.info("COMPLIANCE: Processing PEP screening - Customer: {}, Result: {}, Category: {}, Risk: {}",
                screeningData.getCustomerId(), screeningData.getScreeningResult(),
                screeningData.getPepCategory(), screeningData.getRiskLevel());

        try {
            switch (screeningData.getScreeningResult()) {
                case "HIT":
                    handlePepHit(screeningData);
                    break;
                case "NO_HIT":
                    handleNoPepHit(screeningData);
                    break;
                case "POTENTIAL_MATCH":
                    handlePotentialPepMatch(screeningData);
                    break;
                case "PENDING_REVIEW":
                    handlePendingPepReview(screeningData);
                    break;
                default:
                    log.warn("Unknown PEP screening result: {}", screeningData.getScreeningResult());
            }

            pepScreeningService.updatePepScreeningResult(screeningData);
            updateComplianceMonitoring(screeningData);

            log.info("COMPLIANCE: PEP screening processed - Customer: {}, Action: {}, EDD: {}",
                    screeningData.getCustomerId(),
                    getPepAction(screeningData.getScreeningResult()),
                    screeningData.getEnhancedDueDiligenceRequired());

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process PEP screening for: {}", screeningData.getCustomerId(), e);
            executeEmergencyPepProcedures(screeningData, e);
            throw new RuntimeException("PEP screening processing failed", e);
        }
    }

    private void handlePepHit(PepScreeningData screeningData) {
        log.warn("PEP HIT DETECTED: Customer {} identified as PEP category {}",
                screeningData.getCustomerId(), screeningData.getPepCategory());

        // Update customer risk rating
        riskAssessmentService.updateCustomerRiskRating(
                screeningData.getCustomerId(),
                "PEP_IDENTIFIED",
                screeningData.getRiskLevel()
        );

        // Initiate enhanced due diligence
        enhancedDueDiligenceService.initiateEdd(
                screeningData.getCustomerId(),
                "PEP_SCREENING_HIT",
                screeningData.getMatchDetails()
        );

        // Senior management approval required
        if (screeningData.getSeniorManagementApprovalRequired()) {
            complianceWorkflowService.requestSeniorManagementApproval(
                    screeningData.getCustomerId(),
                    "PEP_RELATIONSHIP_APPROVAL",
                    screeningData.getPoliticalPosition()
            );
        }

        // Compliance notifications
        legalNotificationService.sendUrgentComplianceAlert(
                "PEP Screening Hit Detected",
                String.format("Customer %s identified as %s PEP in %s",
                        screeningData.getCustomerId(),
                        screeningData.getPepCategory(),
                        screeningData.getJurisdiction()),
                "COMPLIANCE_TEAM"
        );

        // Enhanced monitoring
        complianceWorkflowService.enableEnhancedMonitoring(
                screeningData.getCustomerId(),
                "PEP_ENHANCED_MONITORING"
        );
    }

    private void handleNoPepHit(PepScreeningData screeningData) {
        // Clear any previous PEP flags
        pepScreeningService.clearPepFlags(
                screeningData.getCustomerId(),
                "NO_PEP_DETECTED"
        );

        // Update risk assessment
        riskAssessmentService.updatePepRiskAssessment(
                screeningData.getCustomerId(),
                "CLEAR",
                screeningData.getScreeningDate()
        );
    }

    private void handlePotentialPepMatch(PepScreeningData screeningData) {
        // Queue for manual review
        complianceWorkflowService.queueForManualReview(
                screeningData.getScreeningId(),
                screeningData.getCustomerId(),
                "PEP_POTENTIAL_MATCH",
                screeningData.getMatchDetails()
        );

        // Temporary enhanced monitoring
        complianceWorkflowService.enableTemporaryEnhancedMonitoring(
                screeningData.getCustomerId(),
                "POTENTIAL_PEP_MATCH",
                30 // days
        );

        // Notify compliance team
        legalNotificationService.notifyComplianceTeam(
                "Potential PEP Match Requires Review",
                String.format("Customer %s has potential PEP match with confidence %.2f",
                        screeningData.getCustomerId(), screeningData.getConfidenceScore())
        );
    }

    private void handlePendingPepReview(PepScreeningData screeningData) {
        // Schedule manual review
        complianceWorkflowService.scheduleManualPepReview(
                screeningData.getScreeningId(),
                screeningData.getCustomerId(),
                screeningData.getMatchDetails()
        );

        // Precautionary measures for high confidence matches
        if (screeningData.getConfidenceScore() >= 0.75) {
            enhancedDueDiligenceService.initiatePrecautionaryEdd(
                    screeningData.getCustomerId(),
                    "PENDING_PEP_REVIEW"
            );
        }
    }

    private void updateComplianceMonitoring(PepScreeningData screeningData) {
        complianceAuditService.updatePepComplianceMetrics(
                screeningData.getScreeningResult(),
                screeningData.getPepCategory(),
                screeningData.getRiskLevel()
        );

        pepScreeningService.updatePepMonitoringDashboard(
                screeningData.getCustomerId(),
                screeningData.getScreeningResult(),
                screeningData.getPoliticalPosition()
        );
    }

    private String getPepAction(String screeningResult) {
        return switch (screeningResult) {
            case "HIT" -> "EDD_INITIATED";
            case "NO_HIT" -> "CLEARED";
            case "POTENTIAL_MATCH" -> "MANUAL_REVIEW";
            case "PENDING_REVIEW" -> "SCHEDULED_REVIEW";
            default -> "UNKNOWN_ACTION";
        };
    }

    private void executeEmergencyPepProcedures(PepScreeningData screeningData, Exception error) {
        log.error("EMERGENCY: Executing emergency PEP procedures due to processing failure");

        try {
            if ("HIT".equals(screeningData.getScreeningResult()) ||
                (screeningData.getConfidenceScore() != null && screeningData.getConfidenceScore() >= 0.80)) {
                enhancedDueDiligenceService.initiateEmergencyEdd(
                        screeningData.getCustomerId(),
                        "EMERGENCY_PEP_PROCEDURES"
                );
            }

            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: PEP Screening Processing Failed",
                    String.format("Failed to process PEP screening for %s: %s",
                            screeningData.getCustomerId(), error.getMessage())
            );

            legalNotificationService.escalateToManualIntervention(
                    screeningData.getScreeningId(),
                    "PEP_SCREENING_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency PEP procedures also failed", e);
        }
    }

    // Helper and audit methods
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

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("COMPLIANCE: PEP screening validation failed for event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "PEP_SCREENING_VALIDATION_ERROR",
                null,
                "PEP screening validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditPepScreeningProcessing(PepScreeningData screeningData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "PEP_SCREENING_PROCESSED",
                    screeningData != null ? screeningData.getCustomerId() : null,
                    String.format("PEP screening processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "screeningId", screeningData != null ? screeningData.getScreeningId() : "unknown",
                            "customerId", screeningData != null ? screeningData.getCustomerId() : "unknown",
                            "screeningResult", screeningData != null ? screeningData.getScreeningResult() : "unknown",
                            "pepCategory", screeningData != null ? screeningData.getPepCategory() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit PEP screening processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: PEP screening event sent to DLT - EventId: {}", event.getEventId());

        try {
            PepScreeningData screeningData = extractPepScreeningData(event.getPayload());

            if ("HIT".equals(screeningData.getScreeningResult())) {
                enhancedDueDiligenceService.initiateEmergencyEdd(
                        screeningData.getCustomerId(),
                        "DLT_PEP_HIT"
                );
            }

            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: PEP Screening in DLT",
                    "PEP screening could not be processed - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle PEP screening DLT event: {}", event.getEventId(), e);
        }
    }

    public void handlePepScreeningFailure(GenericKafkaEvent event, String topic, int partition,
                                        long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for PEP screening processing - EventId: {}",
                event.getEventId(), e);

        try {
            PepScreeningData screeningData = extractPepScreeningData(event.getPayload());

            if ("HIT".equals(screeningData.getScreeningResult())) {
                enhancedDueDiligenceService.initiateEmergencyEdd(
                        screeningData.getCustomerId(),
                        "CIRCUIT_BREAKER_PEP_HIT"
                );
            }

            legalNotificationService.sendEmergencySystemAlert(
                    "PEP Screening Circuit Breaker Open",
                    "PEP screening processing is failing - compliance at risk"
            );

        } catch (Exception ex) {
            log.error("Failed to handle PEP screening circuit breaker fallback", ex);
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
    public static class PepScreeningData {
        private String screeningId;
        private String customerId;
        private String accountId;
        private String screeningResult;
        private Double confidenceScore;
        private String pepCategory;
        private String politicalPosition;
        private String jurisdiction;
        private String riskLevel;
        private String relationshipType;
        private Instant screeningDate;
        private Instant lastUpdateDate;
        private Map<String, Object> matchDetails;
        private Boolean enhancedDueDiligenceRequired;
        private Boolean seniorManagementApprovalRequired;
        private String complianceNotes;
    }
}