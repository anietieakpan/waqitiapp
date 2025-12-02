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
 * Production-grade Kafka consumer for SAR filing deadline events
 * Handles Suspicious Activity Report filing deadline tracking and enforcement
 * with automated compliance workflow management and regulatory reporting
 *
 * Critical for: BSA compliance, SAR reporting, regulatory deadlines
 * SLA: Must process SAR filing deadlines within 2 seconds for compliance requirements
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SarFilingDeadlineConsumer {

    private final SarFilingService sarFilingService;
    private final RegulatoryReportingService regulatoryReportingService;
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
    private final Counter sarDeadlineCounter = Counter.builder("sar_filing_deadlines_processed_total")
            .description("Total number of SAR filing deadline events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter criticalDeadlineCounter = Counter.builder("critical_sar_deadlines_total")
            .description("Total number of critical SAR filing deadlines processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("sar_deadline_processing_duration")
            .description("Time taken to process SAR filing deadline events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"sar-filing-deadline-events"},
        groupId = "compliance-service-sar-deadline-processor",
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
    @CircuitBreaker(name = "sar-deadline-processor", fallbackMethod = "handleSarDeadlineFailure")
    @Retry(name = "sar-deadline-processor")
    public void processSarFilingDeadline(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.info("COMPLIANCE: Processing SAR filing deadline: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("SAR deadline event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate SAR deadline data
            SarDeadlineData deadlineData = extractSarDeadlineData(event.getPayload());
            validateSarDeadlineData(deadlineData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process SAR filing deadline
            processSarDeadline(deadlineData, event);

            // Record successful processing metrics
            sarDeadlineCounter.increment();

            if (deadlineData.getDaysUntilDeadline() <= 5) {
                criticalDeadlineCounter.increment();
            }

            // Audit the deadline processing
            auditSarDeadlineProcessing(deadlineData, event, "SUCCESS");

            log.info("COMPLIANCE: Successfully processed SAR deadline: {} for SAR: {} - status: {} days: {}",
                    eventId, deadlineData.getSarId(), deadlineData.getDeadlineStatus(), deadlineData.getDaysUntilDeadline());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("COMPLIANCE: Invalid SAR deadline data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process SAR deadline: {}", eventId, e);
            auditSarDeadlineProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("SAR deadline processing failed", e);

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

    private SarDeadlineData extractSarDeadlineData(Map<String, Object> payload) {
        return SarDeadlineData.builder()
                .sarId(extractString(payload, "sarId"))
                .accountId(extractString(payload, "accountId"))
                .customerId(extractString(payload, "customerId"))
                .suspiciousActivity(extractString(payload, "suspiciousActivity"))
                .filingDeadline(extractInstant(payload, "filingDeadline"))
                .daysUntilDeadline(extractInteger(payload, "daysUntilDeadline"))
                .deadlineStatus(extractString(payload, "deadlineStatus"))
                .reportingInstitution(extractString(payload, "reportingInstitution"))
                .reportingOfficer(extractString(payload, "reportingOfficer"))
                .transactionAmount(extractDouble(payload, "transactionAmount"))
                .transactionDate(extractInstant(payload, "transactionDate"))
                .suspicionLevel(extractString(payload, "suspicionLevel"))
                .regulatoryRequirements(extractStringList(payload, "regulatoryRequirements"))
                .complianceNotes(extractString(payload, "complianceNotes"))
                .filingStatus(extractString(payload, "filingStatus"))
                .extensionRequested(extractBoolean(payload, "extensionRequested"))
                .extensionReason(extractString(payload, "extensionReason"))
                .priorityLevel(extractString(payload, "priorityLevel"))
                .relatedReports(extractStringList(payload, "relatedReports"))
                .build();
    }

    private void validateSarDeadlineData(SarDeadlineData deadlineData) {
        if (deadlineData.getSarId() == null || deadlineData.getSarId().trim().isEmpty()) {
            throw new IllegalArgumentException("SAR ID is required");
        }

        if (deadlineData.getAccountId() == null || deadlineData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }

        if (deadlineData.getFilingDeadline() == null) {
            throw new IllegalArgumentException("Filing deadline is required");
        }

        if (deadlineData.getDaysUntilDeadline() == null) {
            throw new IllegalArgumentException("Days until deadline is required");
        }

        List<String> validStatuses = List.of("UPCOMING", "DUE_SOON", "OVERDUE", "FILED", "EXTENDED");
        if (!validStatuses.contains(deadlineData.getDeadlineStatus())) {
            throw new IllegalArgumentException("Invalid deadline status: " + deadlineData.getDeadlineStatus());
        }

        if (deadlineData.getFilingDeadline().isBefore(Instant.now().minus(365, ChronoUnit.DAYS))) {
            throw new IllegalArgumentException("Filing deadline cannot be more than 1 year old");
        }
    }

    private void processSarDeadline(SarDeadlineData deadlineData, GenericKafkaEvent event) {
        log.info("COMPLIANCE: Processing SAR deadline - SAR: {}, Status: {}, Days: {}, Priority: {}",
                deadlineData.getSarId(), deadlineData.getDeadlineStatus(),
                deadlineData.getDaysUntilDeadline(), deadlineData.getPriorityLevel());

        try {
            // Update SAR filing status tracking
            updateSarFilingTracking(deadlineData);

            // Process deadline based on status
            processDeadlineByStatus(deadlineData);

            // Generate compliance notifications
            generateComplianceNotifications(deadlineData);

            // Update regulatory monitoring
            updateRegulatoryMonitoring(deadlineData);

            // Schedule automated actions
            scheduleAutomatedActions(deadlineData);

            // Check for extension requirements
            processExtensionRequirements(deadlineData);

            log.info("COMPLIANCE: SAR deadline processed - SAR: {}, Action: {}, NextReview: {}",
                    deadlineData.getSarId(),
                    getDeadlineAction(deadlineData.getDeadlineStatus()),
                    calculateNextReviewDate(deadlineData));

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process SAR deadline for: {}", deadlineData.getSarId(), e);

            // Emergency compliance procedures
            executeEmergencySarProcedures(deadlineData, e);

            throw new RuntimeException("SAR deadline processing failed", e);
        }
    }

    private void updateSarFilingTracking(SarDeadlineData deadlineData) {
        sarFilingService.updateSarStatus(
                deadlineData.getSarId(),
                deadlineData.getDeadlineStatus(),
                deadlineData.getDaysUntilDeadline(),
                deadlineData.getFilingStatus()
        );

        // Update compliance dashboard
        sarFilingService.updateSarDashboard(
                deadlineData.getSarId(),
                deadlineData.getDeadlineStatus(),
                deadlineData.getDaysUntilDeadline(),
                deadlineData.getPriorityLevel()
        );
    }

    private void processDeadlineByStatus(SarDeadlineData deadlineData) {
        switch (deadlineData.getDeadlineStatus()) {
            case "UPCOMING":
                handleUpcomingDeadline(deadlineData);
                break;

            case "DUE_SOON":
                handleDueSoonDeadline(deadlineData);
                break;

            case "OVERDUE":
                handleOverdueDeadline(deadlineData);
                break;

            case "FILED":
                handleFiledSar(deadlineData);
                break;

            case "EXTENDED":
                handleExtendedDeadline(deadlineData);
                break;

            default:
                log.warn("Unknown SAR deadline status: {}", deadlineData.getDeadlineStatus());
        }
    }

    private void handleUpcomingDeadline(SarDeadlineData deadlineData) {
        // Schedule reminder notifications
        complianceWorkflowService.scheduleDeadlineReminders(
                deadlineData.getSarId(),
                deadlineData.getFilingDeadline(),
                deadlineData.getReportingOfficer()
        );

        // Prepare filing documentation
        sarFilingService.prepareFilingDocumentation(deadlineData.getSarId());
    }

    private void handleDueSoonDeadline(SarDeadlineData deadlineData) {
        // Urgent notifications to compliance team
        legalNotificationService.sendUrgentComplianceAlert(
                "SAR Filing Due Soon",
                String.format("SAR %s is due in %d days",
                        deadlineData.getSarId(), deadlineData.getDaysUntilDeadline()),
                deadlineData.getReportingOfficer()
        );

        // Escalate to supervisor if less than 3 days
        if (deadlineData.getDaysUntilDeadline() <= 3) {
            legalNotificationService.escalateToComplianceSupervisor(
                    deadlineData.getSarId(),
                    "SAR_FILING_CRITICAL",
                    deadlineData.getDaysUntilDeadline()
            );
        }

        // Auto-prepare filing if possible
        if ("HIGH".equals(deadlineData.getPriorityLevel())) {
            sarFilingService.initializeAutoFiling(deadlineData.getSarId());
        }
    }

    private void handleOverdueDeadline(SarDeadlineData deadlineData) {
        // Critical compliance breach - immediate escalation
        legalNotificationService.sendCriticalComplianceAlert(
                "OVERDUE SAR FILING - REGULATORY VIOLATION",
                String.format("SAR %s is overdue by %d days - immediate action required",
                        deadlineData.getSarId(), Math.abs(deadlineData.getDaysUntilDeadline())),
                deadlineData.getReportingOfficer()
        );

        // Executive notifications
        legalNotificationService.sendExecutiveComplianceAlert(
                "Critical SAR Filing Violation",
                deadlineData,
                "OVERDUE_SAR_FILING"
        );

        // Initiate breach procedures
        complianceWorkflowService.initiateRegulatoryBreachProcedures(
                deadlineData.getSarId(),
                "SAR_FILING_OVERDUE",
                Math.abs(deadlineData.getDaysUntilDeadline())
        );

        // Emergency filing workflow
        sarFilingService.initiateEmergencyFiling(deadlineData.getSarId());
    }

    private void handleFiledSar(SarDeadlineData deadlineData) {
        // Confirm filing completion
        sarFilingService.confirmSarFiling(deadlineData.getSarId());

        // Update compliance records
        complianceAuditService.recordSuccessfulSarFiling(
                deadlineData.getSarId(),
                deadlineData.getFilingDeadline()
        );

        // Generate filing confirmation report
        regulatoryReportingService.generateSarFilingConfirmation(deadlineData.getSarId());
    }

    private void handleExtendedDeadline(SarDeadlineData deadlineData) {
        // Track extension usage
        sarFilingService.trackDeadlineExtension(
                deadlineData.getSarId(),
                deadlineData.getExtensionReason()
        );

        // Update monitoring with new deadline
        complianceWorkflowService.updateDeadlineMonitoring(
                deadlineData.getSarId(),
                deadlineData.getFilingDeadline()
        );
    }

    private void generateComplianceNotifications(SarDeadlineData deadlineData) {
        // Daily notifications for critical SARs
        if ("HIGH".equals(deadlineData.getPriorityLevel()) && deadlineData.getDaysUntilDeadline() <= 7) {
            legalNotificationService.scheduleDailyDeadlineReminders(
                    deadlineData.getSarId(),
                    deadlineData.getReportingOfficer(),
                    deadlineData.getDaysUntilDeadline()
            );
        }

        // Supervisor notifications
        if (deadlineData.getDaysUntilDeadline() <= 5) {
            legalNotificationService.notifyComplianceSupervisor(
                    "SAR Filing Deadline Alert",
                    deadlineData
            );
        }
    }

    private void updateRegulatoryMonitoring(SarDeadlineData deadlineData) {
        // Update regulatory compliance metrics
        regulatoryReportingService.updateSarComplianceMetrics(
                deadlineData.getDeadlineStatus(),
                deadlineData.getDaysUntilDeadline(),
                deadlineData.getPriorityLevel()
        );

        // Update BSA compliance dashboard
        regulatoryReportingService.updateBsaComplianceDashboard(
                deadlineData.getSarId(),
                deadlineData.getDeadlineStatus()
        );
    }

    private void scheduleAutomatedActions(SarDeadlineData deadlineData) {
        // Schedule auto-filing for routine SARs
        if ("ROUTINE".equals(deadlineData.getPriorityLevel()) &&
            deadlineData.getDaysUntilDeadline() == 2) {
            sarFilingService.scheduleAutoFiling(
                    deadlineData.getSarId(),
                    deadlineData.getFilingDeadline().minus(1, ChronoUnit.DAYS)
            );
        }

        // Schedule compliance checks
        complianceAuditService.scheduleDeadlineComplianceCheck(
                deadlineData.getSarId(),
                deadlineData.getFilingDeadline()
        );
    }

    private void processExtensionRequirements(SarDeadlineData deadlineData) {
        if (deadlineData.getExtensionRequested() &&
            !"EXTENDED".equals(deadlineData.getDeadlineStatus())) {

            // Process extension request
            sarFilingService.processExtensionRequest(
                    deadlineData.getSarId(),
                    deadlineData.getExtensionReason()
            );

            // Notify regulators if required
            if (deadlineData.getDaysUntilDeadline() <= 0) {
                regulatoryReportingService.notifyRegulatorsOfExtension(
                        deadlineData.getSarId(),
                        deadlineData.getExtensionReason()
                );
            }
        }
    }

    private String getDeadlineAction(String deadlineStatus) {
        return switch (deadlineStatus) {
            case "UPCOMING" -> "MONITORING";
            case "DUE_SOON" -> "URGENT_PROCESSING";
            case "OVERDUE" -> "EMERGENCY_FILING";
            case "FILED" -> "COMPLIANCE_CONFIRMED";
            case "EXTENDED" -> "EXTENSION_TRACKING";
            default -> "UNKNOWN_ACTION";
        };
    }

    private Instant calculateNextReviewDate(SarDeadlineData deadlineData) {
        return switch (deadlineData.getDeadlineStatus()) {
            case "UPCOMING" -> Instant.now().plus(7, ChronoUnit.DAYS);
            case "DUE_SOON" -> Instant.now().plus(1, ChronoUnit.DAYS);
            case "OVERDUE" -> Instant.now().plus(4, ChronoUnit.HOURS);
            case "FILED" -> Instant.now().plus(30, ChronoUnit.DAYS);
            case "EXTENDED" -> deadlineData.getFilingDeadline().minus(5, ChronoUnit.DAYS);
            default -> Instant.now().plus(1, ChronoUnit.DAYS);
        };
    }

    private void executeEmergencySarProcedures(SarDeadlineData deadlineData, Exception error) {
        log.error("EMERGENCY: Executing emergency SAR deadline procedures due to processing failure");

        try {
            // Emergency filing preparation
            sarFilingService.prepareEmergencyFiling(deadlineData.getSarId());

            // Emergency notification
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: SAR Deadline Processing Failed",
                    String.format("Failed to process SAR deadline for %s: %s",
                            deadlineData.getSarId(), error.getMessage())
            );

            // Manual intervention alert
            legalNotificationService.escalateToManualIntervention(
                    deadlineData.getSarId(),
                    "SAR_DEADLINE_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency SAR deadline procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("COMPLIANCE: SAR deadline validation failed for event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "SAR_DEADLINE_VALIDATION_ERROR",
                null,
                "SAR deadline validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditSarDeadlineProcessing(SarDeadlineData deadlineData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "SAR_DEADLINE_PROCESSED",
                    deadlineData != null ? deadlineData.getAccountId() : null,
                    String.format("SAR deadline processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "sarId", deadlineData != null ? deadlineData.getSarId() : "unknown",
                            "deadlineStatus", deadlineData != null ? deadlineData.getDeadlineStatus() : "unknown",
                            "daysUntilDeadline", deadlineData != null ? deadlineData.getDaysUntilDeadline() : 0,
                            "priorityLevel", deadlineData != null ? deadlineData.getPriorityLevel() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit SAR deadline processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: SAR deadline event sent to DLT - EventId: {}", event.getEventId());

        try {
            SarDeadlineData deadlineData = extractSarDeadlineData(event.getPayload());

            // Emergency SAR procedures
            sarFilingService.prepareEmergencyFiling(deadlineData.getSarId());

            // Critical alert
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: SAR Deadline in DLT",
                    "SAR deadline could not be processed - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle SAR deadline DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleSarDeadlineFailure(GenericKafkaEvent event, String topic, int partition,
                                       long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for SAR deadline processing - EventId: {}",
                event.getEventId(), e);

        try {
            SarDeadlineData deadlineData = extractSarDeadlineData(event.getPayload());

            // Emergency SAR protection
            sarFilingService.prepareEmergencyFiling(deadlineData.getSarId());

            // Emergency alert
            legalNotificationService.sendEmergencySystemAlert(
                    "SAR Deadline Circuit Breaker Open",
                    "SAR deadline processing is failing - regulatory compliance at risk"
            );

        } catch (Exception ex) {
            log.error("Failed to handle SAR deadline circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
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
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    // Data class
    @lombok.Data
    @lombok.Builder
    public static class SarDeadlineData {
        private String sarId;
        private String accountId;
        private String customerId;
        private String suspiciousActivity;
        private Instant filingDeadline;
        private Integer daysUntilDeadline;
        private String deadlineStatus;
        private String reportingInstitution;
        private String reportingOfficer;
        private Double transactionAmount;
        private Instant transactionDate;
        private String suspicionLevel;
        private List<String> regulatoryRequirements;
        private String complianceNotes;
        private String filingStatus;
        private Boolean extensionRequested;
        private String extensionReason;
        private String priorityLevel;
        private List<String> relatedReports;
    }
}