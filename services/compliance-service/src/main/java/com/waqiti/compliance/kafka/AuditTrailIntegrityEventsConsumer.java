package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.AuditTrailIntegrityEventDto;
import com.waqiti.compliance.service.AuditTrailIntegrityService;
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
 * Consumer for processing audit trail integrity events.
 * Handles audit log validation, integrity verification, tampering detection,
 * and audit trail continuity monitoring with blockchain-based verification.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class AuditTrailIntegrityEventsConsumer extends BaseConsumer<AuditTrailIntegrityEventDto> {

    private static final String TOPIC_NAME = "audit-trail-integrity-events";
    private static final String CONSUMER_GROUP = "compliance-service-audit-integrity";
    private static final String DLQ_TOPIC = "audit-trail-integrity-events-dlq";

    private final AuditTrailIntegrityService integrityService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter integrityViolationsCounter;
    private final Counter tamperingDetectedCounter;
    private final Counter blockchainVerificationsCounter;
    private final Counter auditRestorationsCounter;
    private final Timer processingTimer;
    private final Timer integrityVerificationTimer;

    @Autowired
    public AuditTrailIntegrityEventsConsumer(
            AuditTrailIntegrityService integrityService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.integrityService = integrityService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("audit-trail-integrity", 5, Duration.ofMinutes(2));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("audit_integrity_events_processed_total")
                .description("Total number of audit trail integrity events processed")
                .tag("service", "compliance")
                .tag("consumer", "audit-trail-integrity")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("audit_integrity_events_failed_total")
                .description("Total number of failed audit trail integrity events")
                .tag("service", "compliance")
                .tag("consumer", "audit-trail-integrity")
                .register(meterRegistry);

        this.integrityViolationsCounter = Counter.builder("integrity_violations_total")
                .description("Total number of audit trail integrity violations")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.tamperingDetectedCounter = Counter.builder("tampering_detected_total")
                .description("Total number of audit trail tampering incidents detected")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.blockchainVerificationsCounter = Counter.builder("blockchain_verifications_total")
                .description("Total number of blockchain verifications performed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.auditRestorationsCounter = Counter.builder("audit_restorations_total")
                .description("Total number of audit trail restorations")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("audit_integrity_processing_duration")
                .description("Time taken to process audit trail integrity events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.integrityVerificationTimer = Timer.builder("integrity_verification_duration")
                .description("Time taken for integrity verification")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes audit trail integrity events with comprehensive verification and forensics.
     *
     * @param eventJson The JSON representation of the audit trail integrity event
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
    public void processAuditTrailIntegrityEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing audit trail integrity event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            AuditTrailIntegrityEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processIntegrityEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.info("Successfully processed audit trail integrity event - CorrelationId: {}, EventType: {}, Status: {}",
                    correlationId, event.getEventType(), event.getIntegrityStatus());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the audit trail integrity event with comprehensive verification.
     */
    private void processIntegrityEvent(AuditTrailIntegrityEventDto event, String correlationId) {
        Timer.Sample verificationTimer = Timer.start();

        try {
            log.info("Processing audit integrity check - Type: {}, AuditId: {} - CorrelationId: {}",
                    event.getEventType(), event.getAuditLogId(), correlationId);

            // Comprehensive integrity verification
            var integrityVerification = integrityService.verifyIntegrity(event, correlationId);

            // Process based on event type
            switch (event.getEventType()) {
                case "INTEGRITY_CHECK":
                    processIntegrityCheck(event, integrityVerification, correlationId);
                    break;
                case "TAMPERING_DETECTION":
                    processTamperingDetection(event, integrityVerification, correlationId);
                    break;
                case "HASH_VERIFICATION":
                    processHashVerification(event, integrityVerification, correlationId);
                    break;
                case "BLOCKCHAIN_VERIFICATION":
                    processBlockchainVerification(event, integrityVerification, correlationId);
                    break;
                case "CONTINUITY_CHECK":
                    processContinuityCheck(event, integrityVerification, correlationId);
                    break;
                case "AUDIT_RESTORATION":
                    processAuditRestoration(event, integrityVerification, correlationId);
                    break;
                case "FORENSIC_ANALYSIS":
                    processForensicAnalysis(event, integrityVerification, correlationId);
                    break;
                case "COMPLIANCE_VALIDATION":
                    processComplianceValidation(event, integrityVerification, correlationId);
                    break;
                default:
                    processGenericIntegrityEvent(event, integrityVerification, correlationId);
            }

            // Handle integrity violations
            if (integrityVerification.hasViolations()) {
                handleIntegrityViolations(event, integrityVerification, correlationId);
            }

            // Update integrity tracking
            integrityService.updateIntegrityTracking(event, integrityVerification, correlationId);

        } finally {
            verificationTimer.stop(integrityVerificationTimer);
        }
    }

    /**
     * Processes routine integrity checks.
     */
    private void processIntegrityCheck(AuditTrailIntegrityEventDto event,
                                     var integrityVerification, String correlationId) {
        log.info("Processing routine integrity check - CorrelationId: {}", correlationId);

        // Cryptographic hash verification
        var hashVerification = integrityService.verifyCryptographicHashes(event, correlationId);

        if (!hashVerification.isValid()) {
            integrityViolationsCounter.increment();
            log.error("Hash verification failed - CorrelationId: {}, AuditId: {}",
                    correlationId, event.getAuditLogId());

            // Immediate investigation
            integrityService.initiateIntegrityInvestigation(event, hashVerification, correlationId);
        }

        // Digital signature validation
        var signatureValidation = integrityService.validateDigitalSignatures(event, correlationId);

        if (!signatureValidation.isValid()) {
            integrityViolationsCounter.increment();
            integrityService.escalateSignatureFailure(event, signatureValidation, correlationId);
        }

        // Timestamp verification
        var timestampVerification = integrityService.verifyTimestamps(event, correlationId);

        if (timestampVerification.hasAnomalies()) {
            integrityViolationsCounter.increment();
            integrityService.investigateTimestampAnomalies(event, timestampVerification, correlationId);
        }
    }

    /**
     * Processes tampering detection events.
     */
    private void processTamperingDetection(AuditTrailIntegrityEventDto event,
                                         var integrityVerification, String correlationId) {
        log.error("CRITICAL: Audit trail tampering detected - CorrelationId: {}, AuditId: {}",
                correlationId, event.getAuditLogId());

        tamperingDetectedCounter.increment();
        integrityViolationsCounter.increment();

        // Immediate forensic preservation
        integrityService.preserveForensicEvidence(event, correlationId);

        // Isolate affected systems
        integrityService.isolateAffectedSystems(event, correlationId);

        // Chain of custody documentation
        integrityService.establishChainOfCustody(event, correlationId);

        // Security incident response
        integrityService.activateSecurityIncidentResponse(event, correlationId);

        // Law enforcement notification if warranted
        if (integrityVerification.indicatesCriminalActivity()) {
            integrityService.notifyLawEnforcement(event, integrityVerification, correlationId);
        }

        // Comprehensive forensic analysis
        integrityService.initiateComprehensiveForensics(event, correlationId);
    }

    /**
     * Processes hash verification events.
     */
    private void processHashVerification(AuditTrailIntegrityEventDto event,
                                       var integrityVerification, String correlationId) {
        log.info("Processing hash verification - CorrelationId: {}", correlationId);

        // Multi-algorithm hash verification
        var hashResults = integrityService.performMultiAlgorithmHashVerification(event, correlationId);

        if (hashResults.hasHashMismatches()) {
            integrityViolationsCounter.increment();

            // Determine hash mismatch severity
            var severity = integrityService.assessHashMismatchSeverity(hashResults, correlationId);

            if (severity.isCritical()) {
                integrityService.escalateCriticalHashMismatch(event, hashResults, correlationId);
            }

            // Hash restoration attempts
            integrityService.attemptHashRestoration(event, hashResults, correlationId);
        }

        // Hash chain validation
        var chainValidation = integrityService.validateHashChain(event, correlationId);

        if (!chainValidation.isValid()) {
            integrityViolationsCounter.increment();
            integrityService.investigateHashChainBreak(event, chainValidation, correlationId);
        }
    }

    /**
     * Processes blockchain verification events.
     */
    private void processBlockchainVerification(AuditTrailIntegrityEventDto event,
                                             var integrityVerification, String correlationId) {
        log.info("Processing blockchain verification - CorrelationId: {}", correlationId);

        blockchainVerificationsCounter.increment();

        // Blockchain integrity verification
        var blockchainVerification = integrityService.verifyBlockchainIntegrity(event, correlationId);

        if (!blockchainVerification.isValid()) {
            integrityViolationsCounter.increment();

            log.error("Blockchain verification failed - CorrelationId: {}, Block: {}",
                    correlationId, event.getBlockchainReference());

            // Blockchain forensics
            integrityService.performBlockchainForensics(event, blockchainVerification, correlationId);

            // Consensus verification
            integrityService.verifyConsensusIntegrity(event, correlationId);
        }

        // Smart contract verification
        if (event.hasSmartContractData()) {
            var contractVerification = integrityService.verifySmartContractIntegrity(event, correlationId);

            if (!contractVerification.isValid()) {
                integrityViolationsCounter.increment();
                integrityService.investigateContractTampering(event, contractVerification, correlationId);
            }
        }

        // Merkle tree validation
        var merkleValidation = integrityService.validateMerkleTree(event, correlationId);

        if (!merkleValidation.isValid()) {
            integrityViolationsCounter.increment();
            integrityService.reconstructMerkleTree(event, merkleValidation, correlationId);
        }
    }

    /**
     * Processes audit trail continuity checks.
     */
    private void processContinuityCheck(AuditTrailIntegrityEventDto event,
                                      var integrityVerification, String correlationId) {
        log.info("Processing audit trail continuity check - CorrelationId: {}", correlationId);

        // Sequence number verification
        var sequenceVerification = integrityService.verifySequenceNumbers(event, correlationId);

        if (sequenceVerification.hasGaps()) {
            integrityViolationsCounter.increment();

            log.warn("Audit trail sequence gaps detected - CorrelationId: {}, Gaps: {}",
                    correlationId, sequenceVerification.getGaps());

            // Gap analysis
            integrityService.analyzeSequenceGaps(event, sequenceVerification, correlationId);

            // Recovery attempts
            integrityService.attemptGapRecovery(event, sequenceVerification, correlationId);
        }

        // Temporal continuity verification
        var temporalVerification = integrityService.verifyTemporalContinuity(event, correlationId);

        if (temporalVerification.hasAnomalies()) {
            integrityViolationsCounter.increment();
            integrityService.investigateTemporalAnomalies(event, temporalVerification, correlationId);
        }

        // Cross-system continuity
        var crossSystemVerification = integrityService.verifyCrossSystemContinuity(event, correlationId);

        if (!crossSystemVerification.isConsistent()) {
            integrityViolationsCounter.increment();
            integrityService.reconcileCrossSystemInconsistencies(event, crossSystemVerification, correlationId);
        }
    }

    /**
     * Processes audit trail restoration events.
     */
    private void processAuditRestoration(AuditTrailIntegrityEventDto event,
                                       var integrityVerification, String correlationId) {
        log.info("Processing audit trail restoration - CorrelationId: {}", correlationId);

        auditRestorationsCounter.increment();

        // Restoration feasibility assessment
        var feasibilityAssessment = integrityService.assessRestorationFeasibility(event, correlationId);

        if (feasibilityAssessment.isFeasible()) {
            // Multi-source restoration
            var restorationResult = integrityService.performMultiSourceRestoration(event, correlationId);

            if (restorationResult.isSuccessful()) {
                log.info("Audit trail restoration successful - CorrelationId: {}", correlationId);

                // Verification of restored data
                integrityService.verifyRestoredData(event, restorationResult, correlationId);

                // Re-establish integrity controls
                integrityService.reestablishIntegrityControls(event, correlationId);
            } else {
                log.error("Audit trail restoration failed - CorrelationId: {}", correlationId);
                integrityService.escalateRestorationFailure(event, restorationResult, correlationId);
            }
        } else {
            log.error("Audit trail restoration not feasible - CorrelationId: {}", correlationId);
            integrityService.documentRestorationInfeasibility(event, feasibilityAssessment, correlationId);
        }

        // Post-restoration monitoring
        integrityService.establishPostRestorationMonitoring(event, correlationId);
    }

    /**
     * Processes forensic analysis events.
     */
    private void processForensicAnalysis(AuditTrailIntegrityEventDto event,
                                       var integrityVerification, String correlationId) {
        log.info("Processing forensic analysis - CorrelationId: {}", correlationId);

        // Digital forensics workflow
        var forensicsWorkflow = integrityService.initiateDigitalForensicsWorkflow(event, correlationId);

        // Evidence collection
        integrityService.collectDigitalEvidence(event, forensicsWorkflow, correlationId);

        // Timeline reconstruction
        var timelineReconstruction = integrityService.reconstructEventTimeline(event, correlationId);

        // Pattern analysis
        var patternAnalysis = integrityService.analyzeIntegrityPatterns(event, correlationId);

        if (patternAnalysis.indicatesSystematicTampering()) {
            integrityService.investigateSystematicTampering(event, patternAnalysis, correlationId);
        }

        // Attribution analysis
        var attributionAnalysis = integrityService.performAttributionAnalysis(event, correlationId);

        // Forensic reporting
        integrityService.generateForensicReport(event, forensicsWorkflow, correlationId);
    }

    /**
     * Processes compliance validation events.
     */
    private void processComplianceValidation(AuditTrailIntegrityEventDto event,
                                           var integrityVerification, String correlationId) {
        log.info("Processing compliance validation - CorrelationId: {}", correlationId);

        // Regulatory compliance verification
        var complianceVerification = integrityService.verifyRegulatoryCompliance(event, correlationId);

        if (!complianceVerification.isCompliant()) {
            integrityViolationsCounter.increment();

            // Compliance remediation
            integrityService.initiateComplianceRemediation(event, complianceVerification, correlationId);

            // Regulatory notification if required
            if (complianceVerification.requiresRegulatorNotification()) {
                integrityService.notifyRegulatorsOfComplianceIssue(event, complianceVerification, correlationId);
            }
        }

        // Audit standard validation
        var auditStandardValidation = integrityService.validateAuditStandards(event, correlationId);

        if (!auditStandardValidation.meetsStandards()) {
            integrityService.remediateAuditStandardViolations(event, auditStandardValidation, correlationId);
        }

        // Retention policy compliance
        var retentionCompliance = integrityService.verifyRetentionPolicyCompliance(event, correlationId);

        if (!retentionCompliance.isCompliant()) {
            integrityService.addressRetentionPolicyViolations(event, retentionCompliance, correlationId);
        }
    }

    /**
     * Processes generic integrity events.
     */
    private void processGenericIntegrityEvent(AuditTrailIntegrityEventDto event,
                                            var integrityVerification, String correlationId) {
        log.info("Processing generic integrity event - CorrelationId: {}", correlationId);

        // Standard integrity verification
        integrityService.performStandardIntegrityVerification(event, correlationId);

        // Basic violation handling
        if (integrityVerification.hasViolations()) {
            integrityService.handleBasicIntegrityViolations(event, integrityVerification, correlationId);
        }
    }

    /**
     * Handles integrity violations with comprehensive response.
     */
    private void handleIntegrityViolations(AuditTrailIntegrityEventDto event,
                                         var integrityVerification, String correlationId) {
        log.error("Handling integrity violations - CorrelationId: {}, Violations: {}",
                correlationId, integrityVerification.getViolationCount());

        integrityViolationsCounter.increment();

        // Immediate containment
        integrityService.containIntegrityViolation(event, correlationId);

        // Impact assessment
        var impactAssessment = integrityService.assessViolationImpact(event, integrityVerification, correlationId);

        // Stakeholder notification
        integrityService.notifyStakeholders(event, impactAssessment, correlationId);

        // Remediation planning
        var remediationPlan = integrityService.planIntegrityRemediation(event, integrityVerification, correlationId);

        // Execute remediation
        integrityService.executeIntegrityRemediation(remediationPlan, correlationId);

        // Post-violation monitoring
        integrityService.establishPostViolationMonitoring(event, correlationId);
    }

    /**
     * Deserializes the event JSON into an AuditTrailIntegrityEventDto.
     */
    private AuditTrailIntegrityEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, AuditTrailIntegrityEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize audit trail integrity event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the audit trail integrity event.
     */
    private void validateEvent(AuditTrailIntegrityEventDto event, String correlationId) {
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }

        if (event.getAuditLogId() == null || event.getAuditLogId().trim().isEmpty()) {
            throw new IllegalArgumentException("Audit log ID is required");
        }

        if (event.getIntegrityStatus() == null || event.getIntegrityStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("Integrity status is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        // Validate hash data if present
        if (event.getHashData() != null && event.getHashData().getExpectedHash() == null) {
            throw new IllegalArgumentException("Expected hash is required when hash data is provided");
        }
    }

    /**
     * Handles processing errors with forensic preservation.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("Failed to process audit trail integrity event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ with forensic preservation
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "audit-trail-integrity",
                "forensicPreservation", true
            ));

            log.info("Sent failed audit integrity event to DLQ with forensic preservation - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("Failed to send audit integrity event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);

            // Emergency forensic preservation
            integrityService.emergencyForensicPreservation(eventJson, correlationId, error, dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "AuditTrailIntegrityEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}