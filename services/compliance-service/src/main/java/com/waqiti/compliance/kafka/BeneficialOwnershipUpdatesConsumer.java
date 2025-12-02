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
 * Production-grade Kafka consumer for beneficial ownership updates
 * Handles updates to beneficial ownership information with compliance
 * validation and regulatory reporting requirements
 *
 * Critical for: CDD compliance, beneficial ownership tracking, regulatory reporting
 * SLA: Must process beneficial ownership updates within 10 seconds
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BeneficialOwnershipUpdatesConsumer {

    private final BeneficialOwnershipService beneficialOwnershipService;
    private final CustomerDueDiligenceService customerDueDiligenceService;
    private final RiskAssessmentService riskAssessmentService;
    private final ComplianceWorkflowService complianceWorkflowService;
    private final LegalNotificationService legalNotificationService;
    private final ComplianceAuditService complianceAuditService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final Counter ownershipUpdatesCounter = Counter.builder("beneficial_ownership_updates_processed_total")
            .description("Total number of beneficial ownership updates processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("beneficial_ownership_processing_duration")
            .description("Time taken to process beneficial ownership updates")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"beneficial-ownership-updates"},
        groupId = "compliance-service-ownership-processor",
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
    @CircuitBreaker(name = "ownership-processor", fallbackMethod = "handleOwnershipFailure")
    @Retry(name = "ownership-processor")
    public void processBeneficialOwnershipUpdate(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.info("COMPLIANCE: Processing beneficial ownership update: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Beneficial ownership update {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            BeneficialOwnershipData ownershipData = extractBeneficialOwnershipData(event.getPayload());
            validateBeneficialOwnershipData(ownershipData);

            markEventAsProcessed(eventId);
            processBeneficialOwnership(ownershipData, event);

            ownershipUpdatesCounter.increment();
            auditBeneficialOwnershipProcessing(ownershipData, event, "SUCCESS");

            log.info("COMPLIANCE: Successfully processed beneficial ownership update: {} for entity: {} - type: {}",
                    eventId, ownershipData.getEntityId(), ownershipData.getUpdateType());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("COMPLIANCE: Invalid beneficial ownership data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process beneficial ownership update: {}", eventId, e);
            auditBeneficialOwnershipProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("Beneficial ownership processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private BeneficialOwnershipData extractBeneficialOwnershipData(Map<String, Object> payload) {
        return BeneficialOwnershipData.builder()
                .updateId(extractString(payload, "updateId"))
                .entityId(extractString(payload, "entityId"))
                .customerId(extractString(payload, "customerId"))
                .updateType(extractString(payload, "updateType"))
                .ownershipPercentage(extractDouble(payload, "ownershipPercentage"))
                .ownershipType(extractString(payload, "ownershipType"))
                .beneficialOwner(extractMap(payload, "beneficialOwner"))
                .effectiveDate(extractInstant(payload, "effectiveDate"))
                .updateDate(extractInstant(payload, "updateDate"))
                .verificationStatus(extractString(payload, "verificationStatus"))
                .documentationProvided(extractBoolean(payload, "documentationProvided"))
                .riskAssessmentRequired(extractBoolean(payload, "riskAssessmentRequired"))
                .complianceNotes(extractString(payload, "complianceNotes"))
                .build();
    }

    private void validateBeneficialOwnershipData(BeneficialOwnershipData ownershipData) {
        if (ownershipData.getUpdateId() == null || ownershipData.getUpdateId().trim().isEmpty()) {
            throw new IllegalArgumentException("Update ID is required");
        }

        if (ownershipData.getEntityId() == null || ownershipData.getEntityId().trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID is required");
        }

        List<String> validTypes = List.of("ADD", "MODIFY", "REMOVE", "VERIFY");
        if (!validTypes.contains(ownershipData.getUpdateType())) {
            throw new IllegalArgumentException("Invalid update type: " + ownershipData.getUpdateType());
        }

        if (ownershipData.getOwnershipPercentage() != null && 
            (ownershipData.getOwnershipPercentage() < 0 || ownershipData.getOwnershipPercentage() > 100)) {
            throw new IllegalArgumentException("Invalid ownership percentage");
        }
    }

    private void processBeneficialOwnership(BeneficialOwnershipData ownershipData, GenericKafkaEvent event) {
        log.info("COMPLIANCE: Processing beneficial ownership - Entity: {}, Type: {}, Percentage: {}",
                ownershipData.getEntityId(), ownershipData.getUpdateType(), ownershipData.getOwnershipPercentage());

        try {
            switch (ownershipData.getUpdateType()) {
                case "ADD":
                    handleAddBeneficialOwner(ownershipData);
                    break;
                case "MODIFY":
                    handleModifyBeneficialOwner(ownershipData);
                    break;
                case "REMOVE":
                    handleRemoveBeneficialOwner(ownershipData);
                    break;
                case "VERIFY":
                    handleVerifyBeneficialOwner(ownershipData);
                    break;
                default:
                    log.warn("Unknown beneficial ownership update type: {}", ownershipData.getUpdateType());
            }

            beneficialOwnershipService.updateBeneficialOwnership(ownershipData);
            updateRiskAssessment(ownershipData);
            updateComplianceMonitoring(ownershipData);

            log.info("COMPLIANCE: Beneficial ownership processed - Entity: {}, Owner: {}, Action: {}",
                    ownershipData.getEntityId(),
                    extractString(ownershipData.getBeneficialOwner(), "name"),
                    getOwnershipAction(ownershipData.getUpdateType()));

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process beneficial ownership for: {}", ownershipData.getEntityId(), e);
            executeEmergencyOwnershipProcedures(ownershipData, e);
            throw new RuntimeException("Beneficial ownership processing failed", e);
        }
    }

    private void handleAddBeneficialOwner(BeneficialOwnershipData ownershipData) {
        beneficialOwnershipService.addBeneficialOwner(
                ownershipData.getEntityId(),
                ownershipData.getBeneficialOwner(),
                ownershipData.getOwnershipPercentage()
        );

        if (ownershipData.getOwnershipPercentage() >= 25.0) {
            customerDueDiligenceService.initiateEnhancedDueDiligence(
                    ownershipData.getEntityId(),
                    "SIGNIFICANT_BENEFICIAL_OWNER_ADDED"
            );
        }

        legalNotificationService.notifyComplianceTeam(
                "New Beneficial Owner Added",
                String.format("Entity %s added beneficial owner with %.2f%% ownership",
                        ownershipData.getEntityId(), ownershipData.getOwnershipPercentage())
        );
    }

    private void handleModifyBeneficialOwner(BeneficialOwnershipData ownershipData) {
        beneficialOwnershipService.modifyBeneficialOwner(
                ownershipData.getEntityId(),
                ownershipData.getBeneficialOwner()
        );

        if (ownershipData.getRiskAssessmentRequired()) {
            riskAssessmentService.scheduleRiskReassessment(
                    ownershipData.getEntityId(),
                    "BENEFICIAL_OWNERSHIP_CHANGE"
            );
        }
    }

    private void handleRemoveBeneficialOwner(BeneficialOwnershipData ownershipData) {
        beneficialOwnershipService.removeBeneficialOwner(
                ownershipData.getEntityId(),
                extractString(ownershipData.getBeneficialOwner(), "id")
        );

        complianceWorkflowService.validateOwnershipStructure(
                ownershipData.getEntityId(),
                "POST_OWNER_REMOVAL"
        );
    }

    private void handleVerifyBeneficialOwner(BeneficialOwnershipData ownershipData) {
        beneficialOwnershipService.verifyBeneficialOwner(
                ownershipData.getEntityId(),
                extractString(ownershipData.getBeneficialOwner(), "id"),
                ownershipData.getVerificationStatus()
        );

        if ("VERIFIED".equals(ownershipData.getVerificationStatus())) {
            complianceAuditService.recordOwnershipVerification(
                    ownershipData.getEntityId(),
                    ownershipData.getBeneficialOwner()
            );
        }
    }

    private void updateRiskAssessment(BeneficialOwnershipData ownershipData) {
        if (ownershipData.getRiskAssessmentRequired()) {
            riskAssessmentService.updateEntityRiskProfile(
                    ownershipData.getEntityId(),
                    "BENEFICIAL_OWNERSHIP_UPDATE",
                    ownershipData.getBeneficialOwner()
            );
        }
    }

    private void updateComplianceMonitoring(BeneficialOwnershipData ownershipData) {
        complianceAuditService.updateOwnershipComplianceMetrics(
                ownershipData.getUpdateType(),
                ownershipData.getOwnershipPercentage(),
                ownershipData.getVerificationStatus()
        );

        beneficialOwnershipService.updateOwnershipDashboard(
                ownershipData.getEntityId(),
                ownershipData.getUpdateType()
        );
    }

    private String getOwnershipAction(String updateType) {
        return switch (updateType) {
            case "ADD" -> "OWNER_ADDED";
            case "MODIFY" -> "OWNER_MODIFIED";
            case "REMOVE" -> "OWNER_REMOVED";
            case "VERIFY" -> "OWNER_VERIFIED";
            default -> "UNKNOWN_ACTION";
        };
    }

    private void executeEmergencyOwnershipProcedures(BeneficialOwnershipData ownershipData, Exception error) {
        log.error("EMERGENCY: Executing emergency beneficial ownership procedures");

        try {
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: Beneficial Ownership Processing Failed",
                    String.format("Failed to process beneficial ownership for %s: %s",
                            ownershipData.getEntityId(), error.getMessage())
            );

            legalNotificationService.escalateToManualIntervention(
                    ownershipData.getUpdateId(),
                    "BENEFICIAL_OWNERSHIP_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency beneficial ownership procedures also failed", e);
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
        log.error("COMPLIANCE: Beneficial ownership validation failed for event: {}", event.getEventId(), e);
    }

    private void auditBeneficialOwnershipProcessing(BeneficialOwnershipData ownershipData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "BENEFICIAL_OWNERSHIP_PROCESSED",
                    ownershipData != null ? ownershipData.getEntityId() : null,
                    String.format("Beneficial ownership processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "updateId", ownershipData != null ? ownershipData.getUpdateId() : "unknown",
                            "entityId", ownershipData != null ? ownershipData.getEntityId() : "unknown",
                            "updateType", ownershipData != null ? ownershipData.getUpdateType() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit beneficial ownership processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: Beneficial ownership event sent to DLT - EventId: {}", event.getEventId());

        try {
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: Beneficial Ownership in DLT",
                    "Beneficial ownership update could not be processed - immediate manual intervention required"
            );
        } catch (Exception e) {
            log.error("Failed to handle beneficial ownership DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleOwnershipFailure(GenericKafkaEvent event, String topic, int partition,
                                     long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for beneficial ownership processing - EventId: {}",
                event.getEventId(), e);

        legalNotificationService.sendEmergencySystemAlert(
                "Beneficial Ownership Circuit Breaker Open",
                "Beneficial ownership processing is failing - compliance at risk"
        );

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
    public static class BeneficialOwnershipData {
        private String updateId;
        private String entityId;
        private String customerId;
        private String updateType;
        private Double ownershipPercentage;
        private String ownershipType;
        private Map<String, Object> beneficialOwner;
        private Instant effectiveDate;
        private Instant updateDate;
        private String verificationStatus;
        private Boolean documentationProvided;
        private Boolean riskAssessmentRequired;
        private String complianceNotes;
    }
}