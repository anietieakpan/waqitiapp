package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.DataRetentionComplianceEventDto;
import com.waqiti.compliance.service.DataRetentionComplianceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for processing data retention compliance events.
 * Handles data lifecycle management, retention policy enforcement,
 * automated data purging, and legal hold processing with comprehensive audit trails.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class DataRetentionComplianceEventsConsumer extends BaseConsumer<DataRetentionComplianceEventDto> {

    private static final String TOPIC_NAME = "data-retention-compliance-events";
    private static final String CONSUMER_GROUP = "compliance-service-data-retention";
    private static final String DLQ_TOPIC = "data-retention-compliance-events-dlq";

    private final DataRetentionComplianceService retentionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter retentionViolationsCounter;
    private final Counter dataPurgedCounter;
    private final Counter legalHoldsAppliedCounter;
    private final Counter complianceAuditsCounter;
    private final Timer processingTimer;
    private final Timer retentionAssessmentTimer;

    @Autowired
    public DataRetentionComplianceEventsConsumer(
            DataRetentionComplianceService retentionService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.retentionService = retentionService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("data-retention-compliance", 5, Duration.ofMinutes(2));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("data_retention_events_processed_total")
                .description("Total number of data retention compliance events processed")
                .tag("service", "compliance")
                .tag("consumer", "data-retention-compliance")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("data_retention_events_failed_total")
                .description("Total number of failed data retention compliance events")
                .tag("service", "compliance")
                .tag("consumer", "data-retention-compliance")
                .register(meterRegistry);

        this.retentionViolationsCounter = Counter.builder("retention_violations_total")
                .description("Total number of data retention violations")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.dataPurgedCounter = Counter.builder("data_purged_total")
                .description("Total amount of data purged for retention compliance")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.legalHoldsAppliedCounter = Counter.builder("legal_holds_applied_total")
                .description("Total number of legal holds applied")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.complianceAuditsCounter = Counter.builder("retention_compliance_audits_total")
                .description("Total number of retention compliance audits performed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("data_retention_processing_duration")
                .description("Time taken to process data retention compliance events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.retentionAssessmentTimer = Timer.builder("retention_assessment_duration")
                .description("Time taken for retention assessment")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes data retention compliance events with automated lifecycle management.
     *
     * @param eventJson The JSON representation of the data retention compliance event
     * @param key The message key
     * @param partition The partition number
     * @param offset The message offset
     * @param timestamp The message timestamp
     * @param acknowledgment The acknowledgment for manual commit
     */
    @KafkaListener(
        topics = TOPIC_NAME,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void processDataRetentionComplianceEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing data retention compliance event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            DataRetentionComplianceEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processRetentionEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.info("Successfully processed data retention compliance event - CorrelationId: {}, EventType: {}, DataType: {}",
                    correlationId, event.getEventType(), event.getDataType());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the data retention compliance event with comprehensive lifecycle management.
     */
    private void processRetentionEvent(DataRetentionComplianceEventDto event, String correlationId) {
        Timer.Sample assessmentTimer = Timer.start();

        try {
            log.info("Processing data retention for - Type: {}, Category: {} - CorrelationId: {}",
                    event.getEventType(), event.getDataCategory(), correlationId);

            // Retention policy assessment
            var retentionAssessment = retentionService.assessRetentionCompliance(event, correlationId);

            // Process based on event type
            switch (event.getEventType()) {
                case "RETENTION_EXPIRY":
                    processRetentionExpiry(event, retentionAssessment, correlationId);
                    break;
                case "LEGAL_HOLD_REQUEST":
                    processLegalHoldRequest(event, retentionAssessment, correlationId);
                    break;
                case "LEGAL_HOLD_RELEASE":
                    processLegalHoldRelease(event, retentionAssessment, correlationId);
                    break;
                case "DATA_CLASSIFICATION_CHANGE":
                    processDataClassificationChange(event, retentionAssessment, correlationId);
                    break;
                case "RETENTION_POLICY_UPDATE":
                    processRetentionPolicyUpdate(event, retentionAssessment, correlationId);
                    break;
                case "COMPLIANCE_AUDIT":
                    processComplianceAudit(event, retentionAssessment, correlationId);
                    break;
                case "DATA_DISCOVERY":
                    processDataDiscovery(event, retentionAssessment, correlationId);
                    break;
                case "PURGE_REQUEST":
                    processPurgeRequest(event, retentionAssessment, correlationId);
                    break;
                case "ARCHIVE_REQUEST":
                    processArchiveRequest(event, retentionAssessment, correlationId);
                    break;
                default:
                    processGenericRetentionEvent(event, retentionAssessment, correlationId);
            }

            // Handle violations if detected
            if (retentionAssessment.hasViolations()) {
                handleRetentionViolations(event, retentionAssessment, correlationId);
            }

            // Update retention tracking
            retentionService.updateRetentionTracking(event, retentionAssessment, correlationId);

        } finally {
            assessmentTimer.stop(retentionAssessmentTimer);
        }
    }

    /**
     * Processes retention expiry events for automated data lifecycle management.
     */
    private void processRetentionExpiry(DataRetentionComplianceEventDto event,
                                      var retentionAssessment, String correlationId) {
        log.info("Processing retention expiry - CorrelationId: {}, DataId: {}, ExpiryDate: {}",
                correlationId, event.getDataId(), event.getRetentionExpiryDate());

        // Check for legal holds
        var legalHoldStatus = retentionService.checkLegalHoldStatus(event, correlationId);

        if (legalHoldStatus.hasActiveLegalHolds()) {
            log.warn("Data retention expiry blocked by legal hold - CorrelationId: {}, HoldId: {}",
                    correlationId, legalHoldStatus.getLegalHoldId());

            retentionService.recordLegalHoldBlock(event, legalHoldStatus, correlationId);
            return;
        }

        // Assess regulatory requirements
        var regulatoryRequirements = retentionService.assessRegulatoryRequirements(event, correlationId);

        if (regulatoryRequirements.requiresExtendedRetention()) {
            log.info("Extending retention due to regulatory requirements - CorrelationId: {}", correlationId);
            retentionService.extendRetentionPeriod(event, regulatoryRequirements, correlationId);
            return;
        }

        // Business value assessment
        var businessValueAssessment = retentionService.assessBusinessValue(event, correlationId);

        if (businessValueAssessment.hasOngoingBusinessValue()) {
            log.info("Retention extended due to ongoing business value - CorrelationId: {}", correlationId);
            retentionService.extendForBusinessValue(event, businessValueAssessment, correlationId);
            return;
        }

        // Proceed with data disposition
        processDataDisposition(event, retentionAssessment, correlationId);
    }

    /**
     * Processes data disposition based on retention policies.
     */
    private void processDataDisposition(DataRetentionComplianceEventDto event,
                                      var retentionAssessment, String correlationId) {
        // Determine disposition action
        var dispositionAction = retentionService.determineDispositionAction(event, correlationId);

        switch (dispositionAction.getAction()) {
            case "ARCHIVE":
                archiveData(event, dispositionAction, correlationId);
                break;
            case "PURGE":
                purgeData(event, dispositionAction, correlationId);
                break;
            case "SECURE_DELETE":
                secureDeleteData(event, dispositionAction, correlationId);
                break;
            case "TRANSFER_TO_COLD_STORAGE":
                transferToColdStorage(event, dispositionAction, correlationId);
                break;
            default:
                log.warn("Unknown disposition action - CorrelationId: {}, Action: {}",
                        correlationId, dispositionAction.getAction());
        }
    }

    /**
     * Archives data according to retention policies.
     */
    private void archiveData(DataRetentionComplianceEventDto event,
                           var dispositionAction, String correlationId) {
        log.info("Archiving data - CorrelationId: {}, DataId: {}", correlationId, event.getDataId());

        // Pre-archive validation
        var archiveValidation = retentionService.validateArchiveEligibility(event, correlationId);

        if (!archiveValidation.isEligible()) {
            log.warn("Data not eligible for archiving - CorrelationId: {}, Reason: {}",
                    correlationId, archiveValidation.getReason());
            return;
        }

        // Create archive package
        var archivePackage = retentionService.createArchivePackage(event, correlationId);

        // Execute archiving
        var archiveResult = retentionService.executeArchiving(archivePackage, correlationId);

        if (archiveResult.isSuccessful()) {
            log.info("Data successfully archived - CorrelationId: {}, ArchiveId: {}",
                    correlationId, archiveResult.getArchiveId());

            // Update data lifecycle status
            retentionService.updateDataLifecycleStatus(event, "ARCHIVED", correlationId);

            // Clean up original data if configured
            if (event.getRetentionPolicy().isDeleteAfterArchive()) {
                retentionService.cleanupOriginalData(event, archiveResult, correlationId);
            }
        } else {
            log.error("Archiving failed - CorrelationId: {}, Error: {}",
                    correlationId, archiveResult.getError());
            retentionService.handleArchiveFailure(event, archiveResult, correlationId);
        }
    }

    /**
     * Purges data according to retention policies.
     */
    private void purgeData(DataRetentionComplianceEventDto event,
                         var dispositionAction, String correlationId) {
        log.info("Purging data - CorrelationId: {}, DataId: {}", correlationId, event.getDataId());

        dataPurgedCounter.increment();

        // Pre-purge validation
        var purgeValidation = retentionService.validatePurgeEligibility(event, correlationId);

        if (!purgeValidation.isEligible()) {
            log.error("Data not eligible for purging - CorrelationId: {}, Reason: {}",
                    correlationId, purgeValidation.getReason());
            return;
        }

        // Create purge audit record
        var purgeAuditRecord = retentionService.createPurgeAuditRecord(event, correlationId);

        // Execute purging
        var purgeResult = retentionService.executePurging(event, correlationId);

        if (purgeResult.isSuccessful()) {
            log.info("Data successfully purged - CorrelationId: {}, PurgeId: {}",
                    correlationId, purgeResult.getPurgeId());

            // Complete audit record
            retentionService.completePurgeAuditRecord(purgeAuditRecord, purgeResult, correlationId);

            // Update data lifecycle status
            retentionService.updateDataLifecycleStatus(event, "PURGED", correlationId);
        } else {
            log.error("Purging failed - CorrelationId: {}, Error: {}",
                    correlationId, purgeResult.getError());
            retentionService.handlePurgeFailure(event, purgeResult, correlationId);
        }
    }

    /**
     * Securely deletes data with cryptographic verification.
     */
    private void secureDeleteData(DataRetentionComplianceEventDto event,
                                var dispositionAction, String correlationId) {
        log.info("Secure deleting data - CorrelationId: {}, DataId: {}", correlationId, event.getDataId());

        // Pre-deletion validation
        var deleteValidation = retentionService.validateSecureDeleteEligibility(event, correlationId);

        if (!deleteValidation.isEligible()) {
            log.error("Data not eligible for secure deletion - CorrelationId: {}, Reason: {}",
                    correlationId, deleteValidation.getReason());
            return;
        }

        // Create secure deletion record
        var deletionRecord = retentionService.createSecureDeletionRecord(event, correlationId);

        // Execute secure deletion
        var deletionResult = retentionService.executeSecureDeletion(event, correlationId);

        if (deletionResult.isSuccessful()) {
            log.info("Data securely deleted - CorrelationId: {}, VerificationHash: {}",
                    correlationId, deletionResult.getVerificationHash());

            // Complete deletion record with cryptographic proof
            retentionService.completeDeletionRecord(deletionRecord, deletionResult, correlationId);
        } else {
            log.error("Secure deletion failed - CorrelationId: {}, Error: {}",
                    correlationId, deletionResult.getError());
            retentionService.handleSecureDeletionFailure(event, deletionResult, correlationId);
        }
    }

    /**
     * Transfers data to cold storage for long-term retention.
     */
    private void transferToColdStorage(DataRetentionComplianceEventDto event,
                                     var dispositionAction, String correlationId) {
        log.info("Transferring to cold storage - CorrelationId: {}, DataId: {}", correlationId, event.getDataId());

        // Cold storage eligibility assessment
        var coldStorageEligibility = retentionService.assessColdStorageEligibility(event, correlationId);

        if (!coldStorageEligibility.isEligible()) {
            log.warn("Data not eligible for cold storage - CorrelationId: {}, Reason: {}",
                    correlationId, coldStorageEligibility.getReason());
            return;
        }

        // Prepare for cold storage transfer
        var transferPreparation = retentionService.prepareColdStorageTransfer(event, correlationId);

        // Execute transfer
        var transferResult = retentionService.executeColdStorageTransfer(transferPreparation, correlationId);

        if (transferResult.isSuccessful()) {
            log.info("Data transferred to cold storage - CorrelationId: {}, StorageId: {}",
                    correlationId, transferResult.getStorageId());

            // Update data lifecycle status
            retentionService.updateDataLifecycleStatus(event, "COLD_STORAGE", correlationId);
        } else {
            log.error("Cold storage transfer failed - CorrelationId: {}, Error: {}",
                    correlationId, transferResult.getError());
            retentionService.handleColdStorageTransferFailure(event, transferResult, correlationId);
        }
    }

    /**
     * Processes legal hold requests.
     */
    private void processLegalHoldRequest(DataRetentionComplianceEventDto event,
                                       var retentionAssessment, String correlationId) {
        log.warn("Processing legal hold request - CorrelationId: {}, HoldId: {}",
                correlationId, event.getLegalHoldId());

        legalHoldsAppliedCounter.increment();

        // Validate legal hold authority
        var legalHoldValidation = retentionService.validateLegalHoldAuthority(event, correlationId);

        if (!legalHoldValidation.isValid()) {
            log.error("Invalid legal hold authority - CorrelationId: {}, Reason: {}",
                    correlationId, legalHoldValidation.getReason());
            return;
        }

        // Apply legal hold
        var legalHoldResult = retentionService.applyLegalHold(event, correlationId);

        if (legalHoldResult.isSuccessful()) {
            log.info("Legal hold applied successfully - CorrelationId: {}, HoldId: {}",
                    correlationId, event.getLegalHoldId());

            // Suspend all retention activities
            retentionService.suspendRetentionActivities(event, correlationId);

            // Notify stakeholders
            retentionService.notifyLegalHoldStakeholders(event, correlationId);

            // Create legal hold audit trail
            retentionService.createLegalHoldAuditTrail(event, legalHoldResult, correlationId);
        } else {
            log.error("Failed to apply legal hold - CorrelationId: {}, Error: {}",
                    correlationId, legalHoldResult.getError());
            retentionService.handleLegalHoldFailure(event, legalHoldResult, correlationId);
        }
    }

    /**
     * Processes legal hold release requests.
     */
    private void processLegalHoldRelease(DataRetentionComplianceEventDto event,
                                       var retentionAssessment, String correlationId) {
        log.info("Processing legal hold release - CorrelationId: {}, HoldId: {}",
                correlationId, event.getLegalHoldId());

        // Validate release authority
        var releaseValidation = retentionService.validateLegalHoldReleaseAuthority(event, correlationId);

        if (!releaseValidation.isValid()) {
            log.error("Invalid legal hold release authority - CorrelationId: {}, Reason: {}",
                    correlationId, releaseValidation.getReason());
            return;
        }

        // Release legal hold
        var releaseResult = retentionService.releaseLegalHold(event, correlationId);

        if (releaseResult.isSuccessful()) {
            log.info("Legal hold released successfully - CorrelationId: {}, HoldId: {}",
                    correlationId, event.getLegalHoldId());

            // Resume retention activities
            retentionService.resumeRetentionActivities(event, correlationId);

            // Process any pending retention actions
            retentionService.processPendingRetentionActions(event, correlationId);

            // Create release audit trail
            retentionService.createReleaseAuditTrail(event, releaseResult, correlationId);
        } else {
            log.error("Failed to release legal hold - CorrelationId: {}, Error: {}",
                    correlationId, releaseResult.getError());
            retentionService.handleLegalHoldReleaseFailure(event, releaseResult, correlationId);
        }
    }

    /**
     * Processes data classification changes that affect retention.
     */
    private void processDataClassificationChange(DataRetentionComplianceEventDto event,
                                                var retentionAssessment, String correlationId) {
        log.info("Processing data classification change - CorrelationId: {}, NewClassification: {}",
                correlationId, event.getNewDataClassification());

        // Assess impact on retention policies
        var classificationImpact = retentionService.assessClassificationImpact(event, correlationId);

        if (classificationImpact.affectsRetentionPolicy()) {
            // Update retention policies
            retentionService.updateRetentionPolicies(event, classificationImpact, correlationId);

            // Recalculate retention schedules
            retentionService.recalculateRetentionSchedules(event, correlationId);

            // Notify stakeholders of changes
            retentionService.notifyClassificationChangeStakeholders(event, classificationImpact, correlationId);
        }

        // Update data classification metadata
        retentionService.updateDataClassificationMetadata(event, correlationId);
    }

    /**
     * Processes retention policy updates.
     */
    private void processRetentionPolicyUpdate(DataRetentionComplianceEventDto event,
                                            var retentionAssessment, String correlationId) {
        log.info("Processing retention policy update - CorrelationId: {}, PolicyId: {}",
                correlationId, event.getPolicyId());

        // Validate policy update
        var policyValidation = retentionService.validatePolicyUpdate(event, correlationId);

        if (!policyValidation.isValid()) {
            log.error("Invalid policy update - CorrelationId: {}, Errors: {}",
                    correlationId, policyValidation.getErrors());
            return;
        }

        // Apply policy update
        var updateResult = retentionService.applyPolicyUpdate(event, correlationId);

        if (updateResult.isSuccessful()) {
            // Recalculate affected data retention schedules
            retentionService.recalculateAffectedRetentionSchedules(event, correlationId);

            // Notify affected stakeholders
            retentionService.notifyPolicyUpdateStakeholders(event, updateResult, correlationId);

            // Audit policy change
            retentionService.auditPolicyChange(event, updateResult, correlationId);
        } else {
            log.error("Policy update failed - CorrelationId: {}, Error: {}",
                    correlationId, updateResult.getError());
            retentionService.handlePolicyUpdateFailure(event, updateResult, correlationId);
        }
    }

    /**
     * Processes compliance audits for data retention.
     */
    private void processComplianceAudit(DataRetentionComplianceEventDto event,
                                      var retentionAssessment, String correlationId) {
        log.info("Processing retention compliance audit - CorrelationId: {}, AuditId: {}",
                correlationId, event.getAuditId());

        complianceAuditsCounter.increment();

        // Comprehensive compliance assessment
        var complianceAssessment = retentionService.performComplianceAssessment(event, correlationId);

        // Generate compliance report
        var complianceReport = retentionService.generateComplianceReport(complianceAssessment, correlationId);

        // Identify any compliance gaps
        var complianceGaps = retentionService.identifyComplianceGaps(complianceAssessment, correlationId);

        if (!complianceGaps.isEmpty()) {
            retentionViolationsCounter.increment();

            log.warn("Compliance gaps identified - CorrelationId: {}, GapCount: {}",
                    correlationId, complianceGaps.size());

            // Create remediation plan
            retentionService.createComplianceRemediationPlan(complianceGaps, correlationId);
        }

        // Document audit results
        retentionService.documentAuditResults(event, complianceReport, correlationId);
    }

    /**
     * Processes generic retention events.
     */
    private void processGenericRetentionEvent(DataRetentionComplianceEventDto event,
                                            var retentionAssessment, String correlationId) {
        log.info("Processing generic retention event - CorrelationId: {}", correlationId);

        // Standard retention processing
        retentionService.processStandardRetentionEvent(event, retentionAssessment, correlationId);
    }

    /**
     * Handles retention violations with comprehensive remediation.
     */
    private void handleRetentionViolations(DataRetentionComplianceEventDto event,
                                         var retentionAssessment, String correlationId) {
        log.error("Handling retention violations - CorrelationId: {}, ViolationCount: {}",
                correlationId, retentionAssessment.getViolationCount());

        retentionViolationsCounter.increment();

        // Immediate containment
        retentionService.containRetentionViolation(event, correlationId);

        // Impact assessment
        var impactAssessment = retentionService.assessViolationImpact(event, retentionAssessment, correlationId);

        // Remediation planning
        var remediationPlan = retentionService.planRetentionRemediation(event, retentionAssessment, correlationId);

        // Execute remediation
        retentionService.executeRetentionRemediation(remediationPlan, correlationId);

        // Stakeholder notification
        retentionService.notifyRetentionViolationStakeholders(event, impactAssessment, correlationId);
    }

    /**
     * Deserializes the event JSON into a DataRetentionComplianceEventDto.
     */
    private DataRetentionComplianceEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, DataRetentionComplianceEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize data retention compliance event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the data retention compliance event.
     */
    private void validateEvent(DataRetentionComplianceEventDto event, String correlationId) {
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }

        if (event.getDataType() == null || event.getDataType().trim().isEmpty()) {
            throw new IllegalArgumentException("Data type is required");
        }

        if (event.getDataCategory() == null || event.getDataCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Data category is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        // Validate retention policy if present
        if (event.getRetentionPolicy() != null && event.getRetentionPolicy().getRetentionPeriod() <= 0) {
            throw new IllegalArgumentException("Retention period must be positive");
        }
    }

    /**
     * Handles processing errors with retention audit preservation.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("Failed to process data retention compliance event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ with retention audit preservation
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "data-retention-compliance",
                "auditPreservation", true
            ));

            log.info("Sent failed data retention event to DLQ - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("Failed to send data retention event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "DataRetentionComplianceEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}