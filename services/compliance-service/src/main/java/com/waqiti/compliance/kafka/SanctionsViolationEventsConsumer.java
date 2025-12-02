package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.SanctionsViolationEventDto;
import com.waqiti.compliance.service.SanctionsViolationService;
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
 * Consumer for processing sanctions violation events.
 * Handles OFAC, EU, UN sanctions list violations, transaction blocking,
 * and regulatory reporting with immediate enforcement actions.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class SanctionsViolationEventsConsumer extends BaseConsumer<SanctionsViolationEventDto> {

    private static final String TOPIC_NAME = "sanctions-violation-events";
    private static final String CONSUMER_GROUP = "compliance-service-sanctions-violation";
    private static final String DLQ_TOPIC = "sanctions-violation-events-dlq";

    private final SanctionsViolationService violationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter sanctionsViolationsCounter;
    private final Counter immediateBlocksCounter;
    private final Counter assetFreezesCounter;
    private final Counter regulatoryReportsCounter;
    private final Timer processingTimer;
    private final Timer violationAssessmentTimer;

    @Autowired
    public SanctionsViolationEventsConsumer(
            SanctionsViolationService violationService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.violationService = violationService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("sanctions-violation", 3, Duration.ofMinutes(1));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("sanctions_violation_events_processed_total")
                .description("Total number of sanctions violation events processed")
                .tag("service", "compliance")
                .tag("consumer", "sanctions-violation")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("sanctions_violation_events_failed_total")
                .description("Total number of failed sanctions violation events")
                .tag("service", "compliance")
                .tag("consumer", "sanctions-violation")
                .register(meterRegistry);

        this.sanctionsViolationsCounter = Counter.builder("sanctions_violations_total")
                .description("Total number of sanctions violations detected")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.immediateBlocksCounter = Counter.builder("immediate_blocks_total")
                .description("Total number of immediate transaction blocks")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.assetFreezesCounter = Counter.builder("asset_freezes_total")
                .description("Total number of asset freezes executed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.regulatoryReportsCounter = Counter.builder("regulatory_reports_total")
                .description("Total number of regulatory reports filed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("sanctions_violation_processing_duration")
                .description("Time taken to process sanctions violation events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.violationAssessmentTimer = Timer.builder("violation_assessment_duration")
                .description("Time taken for violation assessment")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes sanctions violation events with immediate enforcement actions.
     *
     * @param eventJson The JSON representation of the sanctions violation event
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
        maxAttempts = 2, // Reduced retries for immediate enforcement
        backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void processSanctionsViolationEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.error("Processing CRITICAL sanctions violation event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            SanctionsViolationEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processViolationEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.error("Successfully processed sanctions violation event - CorrelationId: {}, Entity: {}, ViolationType: {}",
                    correlationId, event.getEntityId(), event.getViolationType());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the sanctions violation event with immediate enforcement actions.
     */
    private void processViolationEvent(SanctionsViolationEventDto event, String correlationId) {
        Timer.Sample assessmentTimer = Timer.start();

        try {
            log.error("Processing sanctions violation for entity: {} - ViolationType: {} - CorrelationId: {}",
                    event.getEntityId(), event.getViolationType(), correlationId);

            sanctionsViolationsCounter.increment();

            // Immediate violation assessment
            var violationAssessment = violationService.assessViolation(event, correlationId);

            // Process based on violation type
            switch (event.getViolationType()) {
                case "OFAC_SDN_MATCH":
                    processOfacSdnMatch(event, violationAssessment, correlationId);
                    break;
                case "EU_SANCTIONS_MATCH":
                    processEuSanctionsMatch(event, violationAssessment, correlationId);
                    break;
                case "UN_SANCTIONS_MATCH":
                    processUnSanctionsMatch(event, violationAssessment, correlationId);
                    break;
                case "SECTOR_SANCTION_VIOLATION":
                    processSectorSanctionViolation(event, violationAssessment, correlationId);
                    break;
                case "GEOGRAPHIC_SANCTION_VIOLATION":
                    processGeographicSanctionViolation(event, violationAssessment, correlationId);
                    break;
                case "TRANSACTION_PROHIBITION":
                    processTransactionProhibition(event, violationAssessment, correlationId);
                    break;
                case "ASSET_FREEZE_VIOLATION":
                    processAssetFreezeViolation(event, violationAssessment, correlationId);
                    break;
                default:
                    processGenericSanctionViolation(event, violationAssessment, correlationId);
            }

            // Execute immediate enforcement actions
            executeEnforcementActions(event, violationAssessment, correlationId);

            // Generate mandatory regulatory reports
            generateRegulatoryReports(event, violationAssessment, correlationId);

            // Update compliance records
            violationService.updateComplianceRecords(event, violationAssessment, correlationId);

        } finally {
            assessmentTimer.stop(violationAssessmentTimer);
        }
    }

    /**
     * Processes OFAC SDN list matches.
     */
    private void processOfacSdnMatch(SanctionsViolationEventDto event,
                                   var violationAssessment, String correlationId) {
        log.error("CRITICAL: OFAC SDN list match detected - CorrelationId: {}, Entity: {}",
                correlationId, event.getEntityId());

        // Immediate transaction block
        violationService.blockAllTransactions(event.getEntityId(), "OFAC SDN Match", correlationId);
        immediateBlocksCounter.increment();

        // Asset freeze
        violationService.freezeAllAssets(event.getEntityId(), "OFAC SDN Match", correlationId);
        assetFreezesCounter.increment();

        // OFAC blocking report within 10 business days
        violationService.generateOfacBlockingReport(event, violationAssessment, correlationId);

        // Reject transaction if in progress
        if (event.getTransactionId() != null) {
            violationService.rejectTransaction(event.getTransactionId(),
                    "OFAC SDN list violation", correlationId);
        }

        log.error("OFAC enforcement actions completed - CorrelationId: {}", correlationId);
    }

    /**
     * Processes EU sanctions list matches.
     */
    private void processEuSanctionsMatch(SanctionsViolationEventDto event,
                                       var violationAssessment, String correlationId) {
        log.error("CRITICAL: EU sanctions list match detected - CorrelationId: {}", correlationId);

        // Block EU-related transactions
        violationService.blockEuTransactions(event.getEntityId(), correlationId);
        immediateBlocksCounter.increment();

        // EU sanctions reporting
        violationService.generateEuSanctionsReport(event, violationAssessment, correlationId);

        // Asset restrictions
        if (violationAssessment.requiresAssetFreeze()) {
            violationService.freezeEuAssets(event.getEntityId(), correlationId);
            assetFreezesCounter.increment();
        }
    }

    /**
     * Processes UN sanctions list matches.
     */
    private void processUnSanctionsMatch(SanctionsViolationEventDto event,
                                       var violationAssessment, String correlationId) {
        log.error("CRITICAL: UN sanctions list match detected - CorrelationId: {}", correlationId);

        // Global transaction restrictions
        violationService.applyGlobalTransactionRestrictions(event.getEntityId(), correlationId);
        immediateBlocksCounter.increment();

        // UN sanctions compliance reporting
        violationService.generateUnSanctionsReport(event, violationAssessment, correlationId);

        // Asset freeze if required
        if (violationAssessment.requiresGlobalAssetFreeze()) {
            violationService.freezeAllAssets(event.getEntityId(), "UN Sanctions", correlationId);
            assetFreezesCounter.increment();
        }
    }

    /**
     * Processes sector sanction violations.
     */
    private void processSectorSanctionViolation(SanctionsViolationEventDto event,
                                              var violationAssessment, String correlationId) {
        log.warn("Sector sanction violation detected - CorrelationId: {}, Sector: {}",
                correlationId, event.getSectorInformation());

        // Block sector-specific transactions
        violationService.blockSectorTransactions(event.getEntityId(),
                event.getSectorInformation(), correlationId);

        // Enhanced due diligence
        violationService.initiateEnhancedDueDiligence(event.getEntityId(),
                "Sector sanctions concern", correlationId);

        // Sector sanctions reporting
        violationService.generateSectorSanctionsReport(event, violationAssessment, correlationId);
    }

    /**
     * Processes geographic sanction violations.
     */
    private void processGeographicSanctionViolation(SanctionsViolationEventDto event,
                                                  var violationAssessment, String correlationId) {
        log.warn("Geographic sanction violation detected - CorrelationId: {}, Geography: {}",
                correlationId, event.getGeographicInformation());

        // Block geographic transactions
        violationService.blockGeographicTransactions(event.getEntityId(),
                event.getGeographicInformation(), correlationId);

        // Geographic sanctions analysis
        var geoAnalysis = violationService.analyzeGeographicSanctions(event, correlationId);

        if (geoAnalysis.requiresCompleteBlock()) {
            violationService.blockAllTransactions(event.getEntityId(),
                    "Geographic sanctions", correlationId);
            immediateBlocksCounter.increment();
        }

        // Geographic sanctions reporting
        violationService.generateGeographicSanctionsReport(event, geoAnalysis, correlationId);
    }

    /**
     * Processes transaction prohibition violations.
     */
    private void processTransactionProhibition(SanctionsViolationEventDto event,
                                             var violationAssessment, String correlationId) {
        log.error("Transaction prohibition violation - CorrelationId: {}", correlationId);

        // Immediate transaction rejection
        if (event.getTransactionId() != null) {
            violationService.rejectTransaction(event.getTransactionId(),
                    "Transaction prohibition violation", correlationId);
        }

        // Block similar future transactions
        violationService.createTransactionProhibitionRule(event, correlationId);

        // Prohibition compliance reporting
        violationService.generateProhibitionReport(event, violationAssessment, correlationId);
    }

    /**
     * Processes asset freeze violations.
     */
    private void processAssetFreezeViolation(SanctionsViolationEventDto event,
                                           var violationAssessment, String correlationId) {
        log.error("Asset freeze violation attempt - CorrelationId: {}", correlationId);

        // Reinforce asset freeze
        violationService.reinforceAssetFreeze(event.getEntityId(), correlationId);

        // Block attempted unfreezing
        violationService.blockUnfreezingAttempts(event.getEntityId(), correlationId);

        // Asset freeze violation reporting
        violationService.generateAssetFreezeViolationReport(event, violationAssessment, correlationId);

        // Law enforcement notification if warranted
        if (violationAssessment.isIntentionalViolation()) {
            violationService.notifyLawEnforcement(event, violationAssessment, correlationId);
        }
    }

    /**
     * Processes generic sanction violations.
     */
    private void processGenericSanctionViolation(SanctionsViolationEventDto event,
                                                var violationAssessment, String correlationId) {
        log.warn("Generic sanction violation detected - CorrelationId: {}", correlationId);

        // Apply cautious restrictions
        violationService.applyCautiousRestrictions(event.getEntityId(), correlationId);

        // Enhanced monitoring
        violationService.enableEnhancedMonitoring(event.getEntityId(),
                "Generic sanctions concern", correlationId);

        // Generic sanctions reporting
        violationService.generateGenericSanctionsReport(event, violationAssessment, correlationId);
    }

    /**
     * Executes immediate enforcement actions based on violation severity.
     */
    private void executeEnforcementActions(SanctionsViolationEventDto event,
                                         var violationAssessment, String correlationId) {
        log.error("Executing enforcement actions - CorrelationId: {}, Severity: {}",
                correlationId, violationAssessment.getSeverity());

        switch (violationAssessment.getSeverity()) {
            case "CRITICAL":
                executeCriticalEnforcement(event, violationAssessment, correlationId);
                break;
            case "HIGH":
                executeHighEnforcement(event, violationAssessment, correlationId);
                break;
            case "MEDIUM":
                executeMediumEnforcement(event, violationAssessment, correlationId);
                break;
            default:
                executeStandardEnforcement(event, violationAssessment, correlationId);
        }
    }

    /**
     * Executes critical enforcement actions.
     */
    private void executeCriticalEnforcement(SanctionsViolationEventDto event,
                                          var violationAssessment, String correlationId) {
        // Complete account suspension
        violationService.suspendAccountCompletely(event.getEntityId(), correlationId);

        // Asset freeze
        violationService.freezeAllAssets(event.getEntityId(), "Critical violation", correlationId);
        assetFreezesCounter.increment();

        // Immediate regulatory notification
        violationService.notifyRegulatorsImmediately(event, violationAssessment, correlationId);

        // Law enforcement referral
        violationService.referToLawEnforcement(event, violationAssessment, correlationId);
    }

    /**
     * Executes high-level enforcement actions.
     */
    private void executeHighEnforcement(SanctionsViolationEventDto event,
                                      var violationAssessment, String correlationId) {
        // Transaction blocking
        violationService.blockHighRiskTransactions(event.getEntityId(), correlationId);
        immediateBlocksCounter.increment();

        // Enhanced monitoring
        violationService.enableContinuousMonitoring(event.getEntityId(), correlationId);

        // Regulatory notification within 24 hours
        violationService.scheduleRegulatoryNotification(event, violationAssessment, correlationId);
    }

    /**
     * Executes medium-level enforcement actions.
     */
    private void executeMediumEnforcement(SanctionsViolationEventDto event,
                                        var violationAssessment, String correlationId) {
        // Transaction review queue
        violationService.queueTransactionsForReview(event.getEntityId(), correlationId);

        // Enhanced due diligence
        violationService.initiateEnhancedDueDiligence(event.getEntityId(),
                "Medium sanctions risk", correlationId);
    }

    /**
     * Executes standard enforcement actions.
     */
    private void executeStandardEnforcement(SanctionsViolationEventDto event,
                                          var violationAssessment, String correlationId) {
        // Standard monitoring
        violationService.enableStandardMonitoring(event.getEntityId(), correlationId);

        // Documentation
        violationService.documentSanctionsIncident(event, violationAssessment, correlationId);
    }

    /**
     * Generates mandatory regulatory reports.
     */
    private void generateRegulatoryReports(SanctionsViolationEventDto event,
                                         var violationAssessment, String correlationId) {
        log.info("Generating regulatory reports - CorrelationId: {}", correlationId);

        // OFAC reporting if applicable
        if (violationAssessment.requiresOfacReporting()) {
            violationService.generateOfacReport(event, violationAssessment, correlationId);
            regulatoryReportsCounter.increment();
        }

        // FinCEN reporting if applicable
        if (violationAssessment.requiresFinCenReporting()) {
            violationService.generateFinCenReport(event, violationAssessment, correlationId);
            regulatoryReportsCounter.increment();
        }

        // International reporting if applicable
        if (violationAssessment.requiresInternationalReporting()) {
            violationService.generateInternationalReports(event, violationAssessment, correlationId);
            regulatoryReportsCounter.increment();
        }
    }

    /**
     * Deserializes the event JSON into a SanctionsViolationEventDto.
     */
    private SanctionsViolationEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, SanctionsViolationEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize sanctions violation event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the sanctions violation event.
     */
    private void validateEvent(SanctionsViolationEventDto event, String correlationId) {
        if (event.getEntityId() == null || event.getEntityId().trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID is required for sanctions violation");
        }

        if (event.getViolationType() == null || event.getViolationType().trim().isEmpty()) {
            throw new IllegalArgumentException("Violation type is required");
        }

        if (event.getSanctionsList() == null || event.getSanctionsList().trim().isEmpty()) {
            throw new IllegalArgumentException("Sanctions list information is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required for sanctions violations");
        }

        // Validate timestamp is recent (sanctions violations must be processed immediately)
        if (event.getTimestamp().isBefore(Instant.now().minus(Duration.ofMinutes(30)))) {
            log.warn("Sanctions violation event is older than 30 minutes - CorrelationId: {}, Timestamp: {}",
                    correlationId, event.getTimestamp());
        }
    }

    /**
     * Handles processing errors with enhanced compliance logging.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("CRITICAL: Failed to process sanctions violation event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ with critical priority
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "sanctions-violation",
                "priority", "CRITICAL",
                "complianceEvent", true
            ));

            log.error("Sent failed sanctions violation event to DLQ - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("CRITICAL: Failed to send sanctions violation event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);

            // Emergency notification to compliance team
            violationService.notifyComplianceTeamOfProcessingFailure(correlationId, error, dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "SanctionsViolationEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}