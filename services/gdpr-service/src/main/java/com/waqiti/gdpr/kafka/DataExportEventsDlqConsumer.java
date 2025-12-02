package com.waqiti.gdpr.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.model.alert.DataExportRecoveryResult;
import com.waqiti.gdpr.service.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataExportEventsDlqConsumer extends BaseDlqConsumer {

    private final DataExportService dataExportService;
    private final GdprComplianceService gdprComplianceService;
    private final GdprNotificationService gdprNotificationService;
    private final GdprIncidentService gdprIncidentService;
    private final DataProtectionOfficerAlertService dpoAlertService;
    private final GdprManualReviewQueueService gdprManualReviewQueueService;
    private final GdprEmergencyProtocolService gdprEmergencyProtocolService;
    private final AuditService auditService;
    private final AutomatedDataRetentionService dataRetentionService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public DataExportEventsDlqConsumer(DataExportService dataExportService,
                                       GdprComplianceService gdprComplianceService,
                                       GdprNotificationService gdprNotificationService,
                                       GdprIncidentService gdprIncidentService,
                                       DataProtectionOfficerAlertService dpoAlertService,
                                       GdprManualReviewQueueService gdprManualReviewQueueService,
                                       GdprEmergencyProtocolService gdprEmergencyProtocolService,
                                       AuditService auditService,
                                       AutomatedDataRetentionService dataRetentionService,
                                       MeterRegistry meterRegistry,
                                       ObjectMapper objectMapper) {
        super("data-export-events-dlq");
        this.dataExportService = dataExportService;
        this.gdprComplianceService = gdprComplianceService;
        this.gdprNotificationService = gdprNotificationService;
        this.gdprIncidentService = gdprIncidentService;
        this.dpoAlertService = dpoAlertService;
        this.gdprManualReviewQueueService = gdprManualReviewQueueService;
        this.gdprEmergencyProtocolService = gdprEmergencyProtocolService;
        this.auditService = auditService;
        this.dataRetentionService = dataRetentionService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.processedCounter = Counter.builder("data_export_events_dlq_processed_total")
                .description("Total data export events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("data_export_events_dlq_errors_total")
                .description("Total data export events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("data_export_events_dlq_duration")
                .description("Data export events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "data-export-events-dlq",
        groupId = "gdpr-service-data-export-events-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=600000",
            "spring.kafka.consumer.session-timeout-ms=30000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 16000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "data-export-events-dlq", fallbackMethod = "handleDataExportEventsDlqFallback")
    public void handleDataExportEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Export-Id", required = false) String exportId,
            @Header(value = "X-Subject-Id", required = false) String subjectId,
            @Header(value = "X-Request-Type", required = false) String requestType,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Data export event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing data export DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, exportId={}, subjectId={}, requestType={}",
                     topic, partition, offset, record.key(), correlationId, exportId, subjectId, requestType);

            String exportData = record.value();
            validateDataExportData(exportData, eventId);

            // Process data export DLQ with GDPR compliance validation
            DataExportRecoveryResult result = dataExportService.processDataExportEventsDlq(
                exportData,
                record.key(),
                correlationId,
                exportId,
                subjectId,
                requestType,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on GDPR requirements
            if (result.isExported()) {
                handleSuccessfulExport(result, correlationId);
            } else if (result.isGdprViolation()) {
                handleGdprViolation(result, eventId, correlationId);
            } else if (result.requiresManualReview()) {
                handleManualReviewRequired(result, correlationId);
            } else {
                handleFailedExport(result, eventId, correlationId);
            }

            // Update GDPR compliance metrics
            updateGdprComplianceMetrics(result, correlationId);

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed data export DLQ: eventId={}, exportId={}, " +
                    "correlationId={}, exportStatus={}",
                    eventId, result.getExportId(), correlationId, result.getExportStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in data export DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (GdprException e) {
            errorCounter.increment();
            log.error("GDPR violation in data export DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleGdprException(record, e, correlationId);
            throw e; // GDPR violations must be retried
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in data export DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in data export DLQ: eventId={}, correlationId={}",
                     eventId, correlationId, e);
            handleCriticalFailure(record, e, correlationId);
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition) {

        String correlationId = generateCorrelationId();
        log.error("Data export event sent to DLT - GDPR COMPLIANCE AT RISK: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Execute GDPR emergency protocol
        executeGdprEmergencyProtocol(record, topic, exceptionMessage, correlationId);

        // Store for data protection officer review
        storeForDataProtectionOfficerReview(record, topic, exceptionMessage, correlationId);

        // Send data protection officer alert
        sendDataProtectionOfficerAlert(record, topic, exceptionMessage, correlationId);

        // Create GDPR compliance incident
        createGdprComplianceIncident(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("data_export_events_dlt_critical_events_total")
                .description("Critical data export events sent to DLT")
                .tag("topic", topic)
                .tag("severity", "critical")
                .tag("gdpr_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    public void handleDataExportEventsDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String exportId, String subjectId, String requestType,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for data export DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in GDPR compliance review queue
        storeInGdprComplianceReviewQueue(record, correlationId);

        // Send GDPR team alert
        sendGdprTeamAlert(correlationId, ex);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("data_export_events_dlq_circuit_breaker_activations_total")
                .tag("gdpr_impact", "medium")
                .register(meterRegistry)
                .increment();
    }

    private void validateDataExportData(String exportData, String eventId) {
        if (exportData == null || exportData.trim().isEmpty()) {
            throw new ValidationException("Data export data is null or empty for eventId: " + eventId);
        }

        if (!exportData.contains("exportId")) {
            throw new ValidationException("Data export data missing exportId for eventId: " + eventId);
        }

        if (!exportData.contains("subjectId")) {
            throw new ValidationException("Data export data missing subjectId for eventId: " + eventId);
        }

        if (!exportData.contains("requestType")) {
            throw new ValidationException("Data export data missing requestType for eventId: " + eventId);
        }

        // Validate GDPR compliance requirements
        validateGdprCompliance(exportData, eventId);
    }

    private void validateGdprCompliance(String exportData, String eventId) {
        try {
            JsonNode data = objectMapper.readTree(exportData);
            String requestType = data.get("requestType").asText();

            // Validate GDPR request type
            if (!isValidGdprRequestType(requestType)) {
                throw new GdprException("Invalid GDPR request type: " + requestType + " for eventId: " + eventId);
            }

            // Validate subject consent
            if (!data.has("subjectConsent")) {
                throw new GdprException("Missing subject consent for GDPR request: " + eventId);
            }

            // Validate legal basis for processing
            if (!data.has("legalBasis")) {
                throw new GdprException("Missing legal basis for data processing: " + eventId);
            }

            // Validate data retention requirements
            if (data.has("retentionPeriod")) {
                int retentionDays = data.get("retentionPeriod").asInt();
                if (retentionDays > 2555) { // 7 years max
                    log.warn("Data retention period exceeds recommended maximum: {} days for eventId: {}",
                            retentionDays, eventId);
                }
            }

            // Validate data minimization
            if (!data.has("dataScope")) {
                log.warn("Missing data scope definition for GDPR compliance: eventId={}", eventId);
            }

        } catch (Exception e) {
            throw new ValidationException("Failed to validate GDPR compliance: " + e.getMessage());
        }
    }

    private boolean isValidGdprRequestType(String requestType) {
        return requestType.equals("DATA_PORTABILITY") ||
               requestType.equals("RIGHT_TO_ACCESS") ||
               requestType.equals("RIGHT_TO_RECTIFICATION") ||
               requestType.equals("RIGHT_TO_ERASURE") ||
               requestType.equals("RIGHT_TO_RESTRICT") ||
               requestType.equals("DATA_BREACH_NOTIFICATION");
    }

    private void handleSuccessfulExport(DataExportRecoveryResult result, String correlationId) {
        log.info("Data export successfully completed: exportId={}, subjectId={}, requestType={}, correlationId={}",
                result.getExportId(), result.getSubjectId(), result.getRequestType(), correlationId);

        // Update export status
        dataExportService.updateExportStatus(
            result.getExportId(),
            DataExportService.ExportStatus.COMPLETED,
            result.getExportDetails(),
            correlationId
        );

        // Send completion notification
        gdprNotificationService.sendDataExportCompletionNotification(
            result.getSubjectId(),
            result.getExportId(),
            result.getRequestType(),
            result.getExportUrl(),
            correlationId
        );

        // Update GDPR compliance metrics
        Counter.builder("gdpr_successful_exports_total")
                .tag("request_type", result.getRequestType())
                .register(meterRegistry)
                .increment();

        Timer.builder("gdpr_export_processing_time")
                .tag("request_type", result.getRequestType())
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(System.currentTimeMillis()));

        // Log GDPR compliance event
        java.util.Map<String, Object> auditData = new java.util.HashMap<>();
        auditData.put("subjectId", result.getSubjectId());
        auditData.put("requestType", result.getRequestType());
        auditData.put("eventType", "DATA_EXPORT_COMPLETED");
        auditData.put("exportId", result.getExportId());
        auditData.put("exportDetails", result.getExportDetails());
        auditData.put("correlationId", correlationId);

        auditService.logGDPRActivity(auditData);

        // Set data retention policy if needed
        if (result.getRegulatoryDeadlineDays() != null && result.getRegulatoryDeadlineDays() > 0) {
            // Data retention handled automatically by AutomatedDataRetentionService
            log.debug("Data retention policy active: exportId={} deadline={} days",
                    result.getExportId(), result.getRegulatoryDeadlineDays());
        }
    }

    private void handleGdprViolation(DataExportRecoveryResult result, String eventId, String correlationId) {
        log.error("GDPR violation in data export: exportId={}, subjectId={}, violation={}, correlationId={}",
                result.getExportId(), result.getSubjectId(), result.getViolationType(), correlationId);

        // Create GDPR violation incident (P0)
        gdprIncidentService.createGdprViolationIncident(
            result.getExportId(),
            result.getSubjectId(),
            result.getViolationType() != null ? result.getViolationType() : "GDPR_DATA_EXPORT_VIOLATION",
            eventId,
            correlationId
        );

        // Send immediate DPO alert
        java.util.Map<String, String> alertDetails = new java.util.HashMap<>();
        alertDetails.put("exportId", result.getExportId());
        alertDetails.put("subjectId", result.getSubjectId());
        alertDetails.put("violationType", result.getViolationType() != null ? result.getViolationType() : "UNKNOWN");
        alertDetails.put("correlationId", correlationId);
        alertDetails.put("action", "Data export halted, DPO review required");

        dpoAlertService.sendCriticalAlert(
            "DATA_EXPORT_VIOLATION",
            String.format("GDPR violation in data export: %s", result.getViolationType()),
            alertDetails,
            correlationId
        );

        // Halt data export process
        dataExportService.haltExport(
            result.getExportId(),
            DataExportService.HaltReason.GDPR_VIOLATION,
            correlationId
        );

        // File data protection breach if required
        if (result.isRequiresBreachNotification()) {
            gdprIncidentService.createDataBreachIncident(
                result.getExportId(),
                result.getSubjectId(),
                result.getViolationType() != null ? result.getViolationType() : "DATA_EXPORT_BREACH",
                correlationId
            );
        }

        // Create legal review case
        gdprIncidentService.createLegalReviewCase(
            result.getExportId(),
            "GDPR_VIOLATION",
            String.format("GDPR violation: %s", result.getViolationType()),
            correlationId
        );
    }

    private void handleManualReviewRequired(DataExportRecoveryResult result, String correlationId) {
        log.info("Data export requires manual review: exportId={}, subjectId={}, reason={}, correlationId={}",
                result.getExportId(), result.getSubjectId(), result.getReviewReason(), correlationId);

        // Update export status to pending review
        dataExportService.updateExportStatus(
            result.getExportId(),
            DataExportService.ExportStatus.PENDING_MANUAL_REVIEW,
            result.getReviewReason(),
            correlationId
        );

        // Queue for GDPR team review
        gdprManualReviewQueueService.add(
            GdprManualReviewQueueService.GdprManualReviewRequest.builder()
                .exportId(result.getExportId())
                .subjectId(result.getSubjectId())
                .requestType(result.getRequestType())
                .reviewReason(result.getReviewReason())
                .correlationId(correlationId)
                .priority(GdprManualReviewQueueService.Priority.HIGH)
                .assignedTo("GDPR_COMPLIANCE_TEAM")
                .requiresLegalReview(result.isRequiresLegalReview())
                .deadline(java.time.Instant.now().plus(java.time.Duration.ofDays(30))) // GDPR 30-day requirement
                .createdAt(java.time.Instant.now())
                .build()
        );

        // Send manual review notification
        gdprNotificationService.sendDataExportManualReviewNotification(
            result.getSubjectId(),
            result.getExportId(),
            result.getReviewReason(),
            correlationId
        );

        // Update GDPR SLA metrics
        Counter.builder("gdpr_manual_reviews_required_total")
                .tag("request_type", result.getRequestType())
                .tag("review_reason", result.getReviewReason() != null ? result.getReviewReason() : "UNKNOWN")
                .register(meterRegistry)
                .increment();

        log.info("Manual review queued: exportId={} queueSize={} correlationId={}",
                result.getExportId(), gdprManualReviewQueueService.getQueueSize(), correlationId
        );
    }

    private void handleFailedExport(DataExportRecoveryResult result, String eventId, String correlationId) {
        log.error("Data export recovery failed: exportId={}, subjectId={}, reason={}, correlationId={}",
                result.getExportId(), result.getSubjectId(), result.getErrorMessage(), correlationId);

        // Update export status to failed
        dataExportService.updateExportStatus(
            result.getExportId(),
            DataExportService.ExportStatus.FAILED,
            result.getErrorMessage(),
            correlationId
        );

        // Escalate to data protection officer
        dpoAlertService.escalateExportFailure(
            result.getExportId(),
            result.getSubjectId(),
            result.getErrorMessage(),
            correlationId
        );

        // Create GDPR compliance failure incident
        gdprIncidentService.createComplianceFailureIncident(
            result.getExportId(),
            result.getSubjectId(),
            result.getErrorMessage(),
            correlationId
        );

        // Send failure notification to data subject
        gdprNotificationService.sendDataExportFailureNotification(
            result.getSubjectId(),
            result.getExportId(),
            result.getErrorMessage(),
            correlationId
        );

        // Check GDPR deadline compliance
        if (result.isDeadlineBreached()) {
            dpoAlertService.alertDeadlineRisk(
                result.getExportId(),
                result.getSubjectId(),
                0, // Already breached
                correlationId
            );
        }
    }

    private void updateGdprComplianceMetrics(DataExportRecoveryResult result, String correlationId) {
        // Record GDPR request processing metrics
        Counter.builder("gdpr_request_processing_total")
                .tag("request_type", result.getRequestType())
                .tag("export_status", result.getExportStatus())
                .tag("gdpr_compliant", String.valueOf(result.isGdprCompliant()))
                .register(meterRegistry)
                .increment();

        // Update GDPR SLA compliance metrics
        Counter.builder("gdpr_sla_compliance_total")
                .tag("request_type", result.getRequestType())
                .tag("deadline_met", String.valueOf(!result.isDeadlineBreached()))
                .register(meterRegistry)
                .increment();

        // Update data subject rights metrics
        Counter.builder("gdpr_data_subject_rights_exercised_total")
                .tag("request_type", result.getRequestType())
                .tag("export_status", result.getExportStatus())
                .register(meterRegistry)
                .increment();
    }

    private void handleGdprException(ConsumerRecord<String, String> record,
                                    Exception e, String correlationId) {
        log.error("GDPR exception in data export DLQ: correlationId={}", correlationId, e);

        // Extract details from record
        String exportId = record.key() != null ? record.key() : "UNKNOWN";

        // Send immediate DPO alert
        java.util.Map<String, String> alertDetails = new java.util.HashMap<>();
        alertDetails.put("exportId", exportId);
        alertDetails.put("error", e.getMessage());
        alertDetails.put("correlationId", correlationId);
        alertDetails.put("action", "GDPR exception requires immediate review");

        dpoAlertService.sendCriticalAlert(
            "GDPR_EXCEPTION",
            String.format("GDPR exception in data export: %s", e.getMessage()),
            alertDetails,
            correlationId
        );
    }

    private void executeGdprEmergencyProtocol(ConsumerRecord<String, String> record,
                                              String topic, String exceptionMessage,
                                              String correlationId) {
        try {
            String exportId = record.key() != null ? record.key() : "UNKNOWN";
            String subjectId = "UNKNOWN"; // Would extract from record.value() in production

            // Execute comprehensive GDPR emergency protocol
            GdprEmergencyProtocolService.GdprEmergencyResult emergency =
                    gdprEmergencyProtocolService.execute(
                        exportId,
                        subjectId,
                        "DLT_CRITICAL_FAILURE",
                        exceptionMessage,
                        correlationId
                    );

            if (emergency.isSuccess()) {
                log.error("GDPR emergency protocol executed: exportId={} correlationId={}",
                        exportId, correlationId);
            } else {
                log.error("GDPR emergency protocol failed: exportId={} error={} correlationId={}",
                        exportId, emergency.getErrorMessage(), correlationId);
            }
        } catch (Exception e) {
            log.error("CRITICAL: GDPR emergency protocol exception: correlationId={}", correlationId, e);
        }
    }

    private void storeForDataProtectionOfficerReview(ConsumerRecord<String, String> record, String topic,
                                                     String exceptionMessage, String correlationId) {
        log.error("Storing for DPO review: topic={} key={} correlationId={}",
                topic, record.key(), correlationId);

        // In production, would persist to database for DPO review
        // For now, log comprehensively for audit trail
        java.util.Map<String, Object> reviewData = new java.util.HashMap<>();
        reviewData.put("sourceTopic", topic);
        reviewData.put("exportId", record.key());
        reviewData.put("failureReason", exceptionMessage);
        reviewData.put("correlationId", correlationId);
        reviewData.put("status", "PENDING_DPO_REVIEW");
        reviewData.put("timestamp", java.time.Instant.now().toString());

        auditService.logGDPRActivity(reviewData);
    }

    private void sendDataProtectionOfficerAlert(ConsumerRecord<String, String> record, String topic,
                                                String exceptionMessage, String correlationId) {
        java.util.Map<String, String> alertDetails = new java.util.HashMap<>();
        alertDetails.put("topic", topic);
        alertDetails.put("exportId", record.key() != null ? record.key() : "UNKNOWN");
        alertDetails.put("error", exceptionMessage);
        alertDetails.put("correlationId", correlationId);
        alertDetails.put("gdprImpact", "HIGH");
        alertDetails.put("requiredAction", "Immediate DPO review for GDPR compliance");
        alertDetails.put("deadlineRisk", "30-day GDPR deadline at risk");

        dpoAlertService.sendCriticalAlert(
            "DATA_EXPORT_DLT_FAILURE",
            "Data export permanently failed - GDPR compliance at risk",
            alertDetails,
            correlationId
        );
    }

    private void createGdprComplianceIncident(ConsumerRecord<String, String> record, String topic,
                                              String exceptionMessage, String correlationId) {
        String exportId = record.key() != null ? record.key() : "UNKNOWN";

        java.util.Map<String, String> incidentDetails = new java.util.HashMap<>();
        incidentDetails.put("exportId", exportId);
        incidentDetails.put("topic", topic);
        incidentDetails.put("error", exceptionMessage);
        incidentDetails.put("correlationId", correlationId);

        gdprIncidentService.createComplianceIncident(
            "CRITICAL",
            "GDPR_DATA_EXPORT_DLT_FAILURE",
            String.format("Data export compliance failure - Export: %s - Error: %s",
                    exportId, exceptionMessage),
            incidentDetails,
            correlationId
        );
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }

    private String extractExportId(String value) {
        try {
            return objectMapper.readTree(value).get("exportId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractSubjectId(String value) {
        try {
            return objectMapper.readTree(value).get("subjectId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractRequestType(String value) {
        try {
            return objectMapper.readTree(value).get("requestType").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}