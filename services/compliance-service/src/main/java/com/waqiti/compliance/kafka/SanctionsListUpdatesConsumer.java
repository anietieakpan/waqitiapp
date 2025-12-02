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
 * Production-grade Kafka consumer for sanctions list updates
 * Handles updates to OFAC, UN, EU and other sanctions lists with immediate
 * database synchronization and customer rescreening
 *
 * Critical for: Sanctions compliance, list management, regulatory updates
 * SLA: Must process sanctions list updates within 5 minutes for regulatory requirements
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SanctionsListUpdatesConsumer {

    private final SanctionsListService sanctionsListService;
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
    private final Counter sanctionsUpdatesCounter = Counter.builder("sanctions_list_updates_processed_total")
            .description("Total number of sanctions list updates processed")
            .register(metricsService.getMeterRegistry());

    private final Counter newSanctionsCounter = Counter.builder("new_sanctions_entries_total")
            .description("Total number of new sanctions entries added")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("sanctions_list_processing_duration")
            .description("Time taken to process sanctions list updates")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"sanctions-list-updates"},
        groupId = "compliance-service-sanctions-processor",
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
    @CircuitBreaker(name = "sanctions-list-processor", fallbackMethod = "handleSanctionsListFailure")
    @Retry(name = "sanctions-list-processor")
    public void processSanctionsListUpdate(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.info("COMPLIANCE: Processing sanctions list update: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Sanctions list update {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate sanctions list data
            SanctionsListUpdateData updateData = extractSanctionsListData(event.getPayload());
            validateSanctionsListData(updateData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process sanctions list update
            processSanctionsListUpdate(updateData, event);

            // Record successful processing metrics
            sanctionsUpdatesCounter.increment();

            if ("ADD".equals(updateData.getUpdateType())) {
                newSanctionsCounter.increment();
            }

            // Audit the update processing
            auditSanctionsListProcessing(updateData, event, "SUCCESS");

            log.info("COMPLIANCE: Successfully processed sanctions list update: {} for list: {} - type: {} entries: {}",
                    eventId, updateData.getListName(), updateData.getUpdateType(), updateData.getEntriesCount());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("COMPLIANCE: Invalid sanctions list data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process sanctions list update: {}", eventId, e);
            auditSanctionsListProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("Sanctions list update processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private SanctionsListUpdateData extractSanctionsListData(Map<String, Object> payload) {
        return SanctionsListUpdateData.builder()
                .updateId(extractString(payload, "updateId"))
                .listName(extractString(payload, "listName"))
                .listSource(extractString(payload, "listSource"))
                .updateType(extractString(payload, "updateType"))
                .updateDate(extractInstant(payload, "updateDate"))
                .effectiveDate(extractInstant(payload, "effectiveDate"))
                .entriesCount(extractInteger(payload, "entriesCount"))
                .sanctionsEntries(extractList(payload, "sanctionsEntries"))
                .removedEntries(extractList(payload, "removedEntries"))
                .modifiedEntries(extractList(payload, "modifiedEntries"))
                .version(extractString(payload, "version"))
                .checksum(extractString(payload, "checksum"))
                .priority(extractString(payload, "priority"))
                .complianceNotes(extractString(payload, "complianceNotes"))
                .build();
    }

    private void validateSanctionsListData(SanctionsListUpdateData updateData) {
        if (updateData.getUpdateId() == null || updateData.getUpdateId().trim().isEmpty()) {
            throw new IllegalArgumentException("Update ID is required");
        }

        if (updateData.getListName() == null || updateData.getListName().trim().isEmpty()) {
            throw new IllegalArgumentException("List name is required");
        }

        if (updateData.getUpdateType() == null || updateData.getUpdateType().trim().isEmpty()) {
            throw new IllegalArgumentException("Update type is required");
        }

        List<String> validTypes = List.of("ADD", "REMOVE", "MODIFY", "FULL_REFRESH");
        if (!validTypes.contains(updateData.getUpdateType())) {
            throw new IllegalArgumentException("Invalid update type: " + updateData.getUpdateType());
        }

        if (updateData.getUpdateDate() == null) {
            throw new IllegalArgumentException("Update date is required");
        }
    }

    private void processSanctionsListUpdate(SanctionsListUpdateData updateData, GenericKafkaEvent event) {
        log.info("COMPLIANCE: Processing sanctions list update - List: {}, Type: {}, Entries: {}, Priority: {}",
                updateData.getListName(), updateData.getUpdateType(),
                updateData.getEntriesCount(), updateData.getPriority());

        try {
            // Validate update integrity
            validateUpdateIntegrity(updateData);

            // Process update by type
            switch (updateData.getUpdateType()) {
                case "ADD":
                    handleAddEntries(updateData);
                    break;
                case "REMOVE":
                    handleRemoveEntries(updateData);
                    break;
                case "MODIFY":
                    handleModifyEntries(updateData);
                    break;
                case "FULL_REFRESH":
                    handleFullRefresh(updateData);
                    break;
                default:
                    log.warn("Unknown sanctions list update type: {}", updateData.getUpdateType());
            }

            // Update sanctions database
            sanctionsListService.applySanctionsUpdate(updateData);

            // Trigger customer rescreening if needed
            triggerCustomerRescreening(updateData);

            // Update compliance monitoring
            updateComplianceMonitoring(updateData);

            // Generate compliance notifications
            generateComplianceNotifications(updateData);

            log.info("COMPLIANCE: Sanctions list update processed - List: {}, Applied: {}, Rescreening: {}",
                    updateData.getListName(), updateData.getEntriesCount(),
                    shouldTriggerRescreening(updateData));

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process sanctions list update for: {}", updateData.getListName(), e);
            executeEmergencySanctionsProcedures(updateData, e);
            throw new RuntimeException("Sanctions list update processing failed", e);
        }
    }

    private void validateUpdateIntegrity(SanctionsListUpdateData updateData) {
        // Validate checksum if provided
        if (updateData.getChecksum() != null) {
            String calculatedChecksum = sanctionsListService.calculateChecksum(updateData.getSanctionsEntries());
            if (!calculatedChecksum.equals(updateData.getChecksum())) {
                throw new RuntimeException("Sanctions list update checksum validation failed");
            }
        }

        // Validate version sequencing
        String currentVersion = sanctionsListService.getCurrentVersion(updateData.getListName());
        if (!sanctionsListService.isValidVersionSequence(currentVersion, updateData.getVersion())) {
            throw new RuntimeException("Invalid sanctions list version sequence");
        }
    }

    private void handleAddEntries(SanctionsListUpdateData updateData) {
        log.info("Adding {} new sanctions entries to list: {}",
                updateData.getEntriesCount(), updateData.getListName());

        // Add new sanctions entries
        sanctionsListService.addSanctionsEntries(
                updateData.getListName(),
                updateData.getSanctionsEntries()
        );

        // Notify compliance team of significant additions
        if (updateData.getEntriesCount() > 100) {
            legalNotificationService.notifyComplianceTeam(
                    "Large Sanctions List Addition",
                    String.format("Added %d new entries to %s sanctions list",
                            updateData.getEntriesCount(), updateData.getListName())
            );
        }
    }

    private void handleRemoveEntries(SanctionsListUpdateData updateData) {
        log.info("Removing {} sanctions entries from list: {}",
                updateData.getRemovedEntries().size(), updateData.getListName());

        // Remove sanctions entries
        sanctionsListService.removeSanctionsEntries(
                updateData.getListName(),
                updateData.getRemovedEntries()
        );

        // Clear any related holds
        for (Map<String, Object> entry : updateData.getRemovedEntries()) {
            String entityId = extractString(entry, "entityId");
            if (entityId != null) {
                sanctionsComplianceService.clearSanctionsHold(
                        entityId,
                        "SANCTIONS_ENTRY_REMOVED"
                );
            }
        }
    }

    private void handleModifyEntries(SanctionsListUpdateData updateData) {
        log.info("Modifying {} sanctions entries in list: {}",
                updateData.getModifiedEntries().size(), updateData.getListName());

        // Update modified entries
        sanctionsListService.updateSanctionsEntries(
                updateData.getListName(),
                updateData.getModifiedEntries()
        );

        // Rescreen affected entities
        for (Map<String, Object> entry : updateData.getModifiedEntries()) {
            String entityId = extractString(entry, "entityId");
            if (entityId != null) {
                ofacScreeningService.rescreenEntity(
                        entityId,
                        "SANCTIONS_ENTRY_MODIFIED"
                );
            }
        }
    }

    private void handleFullRefresh(SanctionsListUpdateData updateData) {
        log.info("Performing full refresh of sanctions list: {} with {} entries",
                updateData.getListName(), updateData.getEntriesCount());

        // Backup current list
        sanctionsListService.backupSanctionsList(
                updateData.getListName(),
                updateData.getVersion()
        );

        // Replace entire list
        sanctionsListService.replaceSanctionsList(
                updateData.getListName(),
                updateData.getSanctionsEntries(),
                updateData.getVersion()
        );

        // Schedule full customer rescreening
        complianceWorkflowService.scheduleFullCustomerRescreening(
                updateData.getListName(),
                "SANCTIONS_LIST_REFRESH"
        );

        // Critical notification for full refresh
        legalNotificationService.sendCriticalComplianceAlert(
                "Sanctions List Full Refresh Completed",
                String.format("Full refresh of %s sanctions list completed with %d entries",
                        updateData.getListName(), updateData.getEntriesCount()),
                "COMPLIANCE_TEAM"
        );
    }

    private void triggerCustomerRescreening(SanctionsListUpdateData updateData) {
        if (shouldTriggerRescreening(updateData)) {
            log.info("Triggering customer rescreening for sanctions list update: {}",
                    updateData.getListName());

            if ("HIGH".equals(updateData.getPriority())) {
                // Immediate rescreening for high priority updates
                complianceWorkflowService.initiateImmediateRescreening(
                        updateData.getListName(),
                        updateData.getSanctionsEntries()
                );
            } else {
                // Scheduled rescreening for normal priority
                complianceWorkflowService.scheduleCustomerRescreening(
                        updateData.getListName(),
                        updateData.getEffectiveDate()
                );
            }
        }
    }

    private boolean shouldTriggerRescreening(SanctionsListUpdateData updateData) {
        return "ADD".equals(updateData.getUpdateType()) ||
               "MODIFY".equals(updateData.getUpdateType()) ||
               "FULL_REFRESH".equals(updateData.getUpdateType());
    }

    private void updateComplianceMonitoring(SanctionsListUpdateData updateData) {
        // Update sanctions compliance metrics
        complianceAuditService.updateSanctionsListMetrics(
                updateData.getListName(),
                updateData.getUpdateType(),
                updateData.getEntriesCount()
        );

        // Update sanctions monitoring dashboard
        sanctionsComplianceService.updateSanctionsListDashboard(
                updateData.getListName(),
                updateData.getVersion(),
                updateData.getUpdateDate()
        );
    }

    private void generateComplianceNotifications(SanctionsListUpdateData updateData) {
        // High priority notifications
        if ("HIGH".equals(updateData.getPriority())) {
            legalNotificationService.sendUrgentComplianceAlert(
                    "High Priority Sanctions List Update",
                    String.format("High priority update to %s sanctions list - immediate attention required",
                            updateData.getListName()),
                    "COMPLIANCE_TEAM"
            );
        }

        // Large update notifications
        if (updateData.getEntriesCount() > 1000) {
            legalNotificationService.notifyComplianceTeam(
                    "Large Sanctions List Update",
                    String.format("Large update to %s sanctions list with %d entries",
                            updateData.getListName(), updateData.getEntriesCount())
            );
        }
    }

    private void executeEmergencySanctionsProcedures(SanctionsListUpdateData updateData, Exception error) {
        log.error("EMERGENCY: Executing emergency sanctions procedures due to processing failure");

        try {
            // Rollback if possible
            sanctionsListService.rollbackUpdate(
                    updateData.getListName(),
                    updateData.getUpdateId()
            );

            // Emergency notification
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: Sanctions List Update Failed",
                    String.format("Failed to process sanctions list update for %s: %s",
                            updateData.getListName(), error.getMessage())
            );

            // Manual intervention alert
            legalNotificationService.escalateToManualIntervention(
                    updateData.getUpdateId(),
                    "SANCTIONS_LIST_UPDATE_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency sanctions procedures also failed", e);
        }
    }

    // Helper methods
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
        log.error("COMPLIANCE: Sanctions list validation failed for event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "SANCTIONS_LIST_VALIDATION_ERROR",
                null,
                "Sanctions list validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditSanctionsListProcessing(SanctionsListUpdateData updateData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "SANCTIONS_LIST_PROCESSED",
                    null,
                    String.format("Sanctions list processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "updateId", updateData != null ? updateData.getUpdateId() : "unknown",
                            "listName", updateData != null ? updateData.getListName() : "unknown",
                            "updateType", updateData != null ? updateData.getUpdateType() : "unknown",
                            "entriesCount", updateData != null ? updateData.getEntriesCount() : 0,
                            "priority", updateData != null ? updateData.getPriority() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit sanctions list processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: Sanctions list update event sent to DLT - EventId: {}", event.getEventId());

        try {
            SanctionsListUpdateData updateData = extractSanctionsListData(event.getPayload());

            // Emergency rollback
            sanctionsListService.rollbackUpdate(
                    updateData.getListName(),
                    updateData.getUpdateId()
            );

            // Critical alert
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: Sanctions List Update in DLT",
                    "Sanctions list update could not be processed - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle sanctions list update DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleSanctionsListFailure(GenericKafkaEvent event, String topic, int partition,
                                         long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for sanctions list processing - EventId: {}",
                event.getEventId(), e);

        try {
            SanctionsListUpdateData updateData = extractSanctionsListData(event.getPayload());

            // Emergency protection
            sanctionsListService.rollbackUpdate(
                    updateData.getListName(),
                    updateData.getUpdateId()
            );

            // Emergency alert
            legalNotificationService.sendEmergencySystemAlert(
                    "Sanctions List Processing Circuit Breaker Open",
                    "Sanctions list processing is failing - sanctions compliance at risk"
            );

        } catch (Exception ex) {
            log.error("Failed to handle sanctions list circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return List.of();
    }

    // Data class
    @lombok.Data
    @lombok.Builder
    public static class SanctionsListUpdateData {
        private String updateId;
        private String listName;
        private String listSource;
        private String updateType;
        private Instant updateDate;
        private Instant effectiveDate;
        private Integer entriesCount;
        private List<Map<String, Object>> sanctionsEntries;
        private List<Map<String, Object>> removedEntries;
        private List<Map<String, Object>> modifiedEntries;
        private String version;
        private String checksum;
        private String priority;
        private String complianceNotes;
    }
}