package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.TerroristFinancingDetectionEventDto;
import com.waqiti.compliance.service.TerroristFinancingDetectionService;
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
 * Consumer for processing terrorist financing detection events.
 * Handles CTF (Counter-Terrorist Financing) detection, OFAC sanctions screening,
 * and terrorism-related suspicious activity monitoring with real-time threat assessment.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class TerroristFinancingDetectionEventsConsumer extends BaseConsumer<TerroristFinancingDetectionEventDto> {

    private static final String TOPIC_NAME = "terrorist-financing-detection-events";
    private static final String CONSUMER_GROUP = "compliance-service-tf-detection";
    private static final String DLQ_TOPIC = "terrorist-financing-detection-events-dlq";

    private final TerroristFinancingDetectionService detectionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter terroristFinancingAlertsCounter;
    private final Counter ofacMatchesCounter;
    private final Counter highThreatDetectionsCounter;
    private final Counter emergencyReportsCounter;
    private final Timer processingTimer;
    private final Timer threatAssessmentTimer;

    @Autowired
    public TerroristFinancingDetectionEventsConsumer(
            TerroristFinancingDetectionService detectionService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.detectionService = detectionService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("terrorist-financing-detection", 3, Duration.ofMinutes(1));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("tf_detection_events_processed_total")
                .description("Total number of terrorist financing detection events processed")
                .tag("service", "compliance")
                .tag("consumer", "terrorist-financing-detection")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("tf_detection_events_failed_total")
                .description("Total number of failed terrorist financing detection events")
                .tag("service", "compliance")
                .tag("consumer", "terrorist-financing-detection")
                .register(meterRegistry);

        this.terroristFinancingAlertsCounter = Counter.builder("terrorist_financing_alerts_total")
                .description("Total number of terrorist financing alerts generated")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.ofacMatchesCounter = Counter.builder("ofac_matches_total")
                .description("Total number of OFAC list matches detected")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.highThreatDetectionsCounter = Counter.builder("high_threat_detections_total")
                .description("Total number of high threat detections")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.emergencyReportsCounter = Counter.builder("emergency_reports_total")
                .description("Total number of emergency reports filed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("tf_detection_processing_duration")
                .description("Time taken to process terrorist financing detection events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.threatAssessmentTimer = Timer.builder("threat_assessment_duration")
                .description("Time taken for threat assessment")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes terrorist financing detection events with real-time threat assessment.
     *
     * @param eventJson The JSON representation of the terrorist financing detection event
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
        maxAttempts = 2, // Reduced retries for security-critical events
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void processTerroristFinancingDetectionEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.warn("Processing CRITICAL terrorist financing detection event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            TerroristFinancingDetectionEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker (reduced tolerance for security events)
            circuitBreaker.executeSupplier(() -> {
                processDetectionEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.warn("Successfully processed terrorist financing detection event - CorrelationId: {}, CustomerId: {}, ThreatLevel: {}",
                    correlationId, event.getCustomerId(), event.getThreatLevel());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the terrorist financing detection event with comprehensive threat assessment.
     */
    private void processDetectionEvent(TerroristFinancingDetectionEventDto event, String correlationId) {
        Timer.Sample assessmentTimer = Timer.start();

        try {
            log.warn("Processing CTF detection for customer: {} - ThreatLevel: {} - CorrelationId: {}",
                    event.getCustomerId(), event.getThreatLevel(), correlationId);

            // Immediate threat assessment
            var threatAssessment = detectionService.assessThreatLevel(event, correlationId);

            // Process based on detection type
            switch (event.getDetectionType()) {
                case "OFAC_MATCH":
                    processOfacMatch(event, threatAssessment, correlationId);
                    break;
                case "TERRORIST_ENTITY_LINK":
                    processTerroristEntityLink(event, threatAssessment, correlationId);
                    break;
                case "SUSPICIOUS_GEOGRAPHIC_PATTERN":
                    processSuspiciousGeographicPattern(event, threatAssessment, correlationId);
                    break;
                case "HIGH_RISK_JURISDICTION":
                    processHighRiskJurisdiction(event, threatAssessment, correlationId);
                    break;
                case "SUSPICIOUS_TRANSACTION_PATTERN":
                    processSuspiciousTransactionPattern(event, threatAssessment, correlationId);
                    break;
                case "NETWORK_ANALYSIS_ALERT":
                    processNetworkAnalysisAlert(event, threatAssessment, correlationId);
                    break;
                default:
                    processGenericThreatDetection(event, threatAssessment, correlationId);
            }

            // Emergency procedures for high threats
            if ("CRITICAL".equals(event.getThreatLevel()) || threatAssessment.requiresImmediateAction()) {
                initiateEmergencyProcedures(event, threatAssessment, correlationId);
            }

            // Update threat intelligence database
            detectionService.updateThreatIntelligence(event, threatAssessment, correlationId);

            // Track threat metrics
            trackThreatMetrics(event, threatAssessment);

        } finally {
            assessmentTimer.stop(threatAssessmentTimer);
        }
    }

    /**
     * Processes OFAC sanctions list matches.
     */
    private void processOfacMatch(TerroristFinancingDetectionEventDto event,
                                 var threatAssessment, String correlationId) {
        log.error("CRITICAL: OFAC sanctions match detected - CorrelationId: {}, Customer: {}",
                correlationId, event.getCustomerId());

        ofacMatchesCounter.increment();
        terroristFinancingAlertsCounter.increment();

        // Immediate account freeze
        detectionService.freezeAccountImmediately(event.getCustomerId(), "OFAC_MATCH", correlationId);

        // Generate emergency SAR
        detectionService.generateEmergencySAR(event, "OFAC sanctions list match", correlationId);

        // Notify law enforcement if required
        if (event.getMatchConfidence() > 0.8) {
            detectionService.notifyLawEnforcement(event, threatAssessment, correlationId);
            emergencyReportsCounter.increment();
        }

        // Block all related accounts
        detectionService.blockRelatedAccounts(event.getCustomerId(), correlationId);

        log.error("Emergency procedures initiated for OFAC match - CorrelationId: {}", correlationId);
    }

    /**
     * Processes terrorist entity linkage detection.
     */
    private void processTerroristEntityLink(TerroristFinancingDetectionEventDto event,
                                          var threatAssessment, String correlationId) {
        log.error("CRITICAL: Terrorist entity linkage detected - CorrelationId: {}", correlationId);

        highThreatDetectionsCounter.increment();

        // Enhanced investigation
        detectionService.initiateEnhancedInvestigation(event, threatAssessment, correlationId);

        // Cross-reference with international databases
        var crossRefResults = detectionService.crossReferenceInternationalDatabases(event, correlationId);

        if (crossRefResults.hasMatches()) {
            // Escalate to highest priority
            detectionService.escalateToHighestPriority(event, crossRefResults, correlationId);

            // Consider asset freeze
            if (crossRefResults.getConfidenceLevel() > 0.7) {
                detectionService.initiateAssetFreezeProcess(event.getCustomerId(), correlationId);
            }
        }
    }

    /**
     * Processes suspicious geographic pattern detection.
     */
    private void processSuspiciousGeographicPattern(TerroristFinancingDetectionEventDto event,
                                                  var threatAssessment, String correlationId) {
        log.warn("Suspicious geographic pattern detected - CorrelationId: {}", correlationId);

        // Analyze geographic risk factors
        var geoAnalysis = detectionService.analyzeGeographicRiskFactors(event, correlationId);

        if (geoAnalysis.involvesHighRiskJurisdictions()) {
            terroristFinancingAlertsCounter.increment();

            // Enhanced monitoring
            detectionService.enableEnhancedMonitoring(event.getCustomerId(),
                    "Geographic pattern risk", correlationId);

            // Additional screening
            detectionService.performAdditionalScreening(event, geoAnalysis, correlationId);
        }
    }

    /**
     * Processes high-risk jurisdiction transactions.
     */
    private void processHighRiskJurisdiction(TerroristFinancingDetectionEventDto event,
                                           var threatAssessment, String correlationId) {
        log.warn("High-risk jurisdiction transaction detected - CorrelationId: {}", correlationId);

        // Evaluate jurisdiction risk level
        var jurisdictionRisk = detectionService.evaluateJurisdictionRisk(event, correlationId);

        if (jurisdictionRisk.isEmbargoed() || jurisdictionRisk.isSanctioned()) {
            terroristFinancingAlertsCounter.increment();

            // Block transaction immediately
            detectionService.blockTransaction(event.getTransactionId(), "Embargoed jurisdiction", correlationId);

            // Generate compliance alert
            detectionService.generateComplianceAlert(event, jurisdictionRisk, correlationId);
        }
    }

    /**
     * Processes suspicious transaction patterns.
     */
    private void processSuspiciousTransactionPattern(TerroristFinancingDetectionEventDto event,
                                                   var threatAssessment, String correlationId) {
        log.warn("Suspicious CTF transaction pattern detected - CorrelationId: {}", correlationId);

        // Analyze transaction patterns for CTF indicators
        var patternAnalysis = detectionService.analyzeTransactionPatternsForCTF(event, correlationId);

        if (patternAnalysis.hasCtfIndicators()) {
            terroristFinancingAlertsCounter.increment();

            // Queue for specialist review
            detectionService.queueForCtfSpecialistReview(event, patternAnalysis, correlationId);

            // Apply additional monitoring
            if (patternAnalysis.getRiskScore() > 0.8) {
                detectionService.applyStrictMonitoring(event.getCustomerId(), correlationId);
            }
        }
    }

    /**
     * Processes network analysis alerts.
     */
    private void processNetworkAnalysisAlert(TerroristFinancingDetectionEventDto event,
                                           var threatAssessment, String correlationId) {
        log.warn("CTF network analysis alert - CorrelationId: {}", correlationId);

        // Analyze network connections for terrorist financing
        var networkAnalysis = detectionService.analyzeNetworkForCtf(event, correlationId);

        if (networkAnalysis.hasCtfConnections()) {
            highThreatDetectionsCounter.increment();

            // Flag entire network for investigation
            detectionService.flagNetworkForCtfInvestigation(networkAnalysis, correlationId);

            // Prioritize high-risk connections
            if (networkAnalysis.hasCriticalConnections()) {
                detectionService.prioritizeCriticalConnections(networkAnalysis, correlationId);
            }
        }
    }

    /**
     * Processes generic terrorist financing threat detection.
     */
    private void processGenericThreatDetection(TerroristFinancingDetectionEventDto event,
                                             var threatAssessment, String correlationId) {
        log.warn("Generic CTF threat detection - CorrelationId: {}", correlationId);

        // Apply general CTF detection models
        var generalAnalysis = detectionService.applyGeneralCtfModels(event, correlationId);

        if (generalAnalysis.getThreatScore() > 0.6) {
            terroristFinancingAlertsCounter.increment();

            // Queue for manual CTF review
            detectionService.queueForManualCtfReview(event, generalAnalysis, correlationId);
        }
    }

    /**
     * Initiates emergency procedures for critical threats.
     */
    private void initiateEmergencyProcedures(TerroristFinancingDetectionEventDto event,
                                           var threatAssessment, String correlationId) {
        log.error("EMERGENCY: Initiating critical CTF response procedures - CorrelationId: {}", correlationId);

        emergencyReportsCounter.increment();

        // Immediate account suspension
        detectionService.suspendAccountImmediately(event.getCustomerId(),
                "Critical CTF threat", correlationId);

        // Generate emergency notification to authorities
        detectionService.generateEmergencyNotification(event, threatAssessment, correlationId);

        // Freeze all transactions
        detectionService.freezeAllTransactions(event.getCustomerId(), correlationId);

        // Notify senior management
        detectionService.notifySeniorManagement(event, threatAssessment, correlationId);

        // Document emergency response
        detectionService.documentEmergencyResponse(event, threatAssessment, correlationId);
    }

    /**
     * Tracks threat-specific metrics.
     */
    private void trackThreatMetrics(TerroristFinancingDetectionEventDto event, var threatAssessment) {
        // Track by detection type
        Counter.builder("ctf_detection_by_type_total")
                .tag("detection_type", event.getDetectionType())
                .tag("service", "compliance")
                .register(metricsCollector.getMeterRegistry())
                .increment();

        // Track by threat level
        Counter.builder("ctf_detection_by_threat_level_total")
                .tag("threat_level", event.getThreatLevel())
                .tag("service", "compliance")
                .register(metricsCollector.getMeterRegistry())
                .increment();

        // Track geographic risks
        if (event.getGeographicData() != null) {
            Counter.builder("ctf_geographic_risks_total")
                    .tag("region", event.getGeographicData().getRegion())
                    .tag("service", "compliance")
                    .register(metricsCollector.getMeterRegistry())
                    .increment();
        }
    }

    /**
     * Deserializes the event JSON into a TerroristFinancingDetectionEventDto.
     */
    private TerroristFinancingDetectionEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, TerroristFinancingDetectionEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize terrorist financing detection event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the terrorist financing detection event.
     */
    private void validateEvent(TerroristFinancingDetectionEventDto event, String correlationId) {
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required for CTF detection");
        }

        if (event.getDetectionType() == null || event.getDetectionType().trim().isEmpty()) {
            throw new IllegalArgumentException("Detection type is required");
        }

        if (event.getThreatLevel() == null || event.getThreatLevel().trim().isEmpty()) {
            throw new IllegalArgumentException("Threat level is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required for CTF detection events");
        }

        // Validate timestamp is recent (CTF events should be processed quickly)
        if (event.getTimestamp().isBefore(Instant.now().minus(Duration.ofHours(24)))) {
            log.warn("CTF detection event is older than 24 hours - CorrelationId: {}, Timestamp: {}",
                    correlationId, event.getTimestamp());
        }
    }

    /**
     * Handles processing errors with enhanced security logging.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("CRITICAL: Failed to process terrorist financing detection event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ with high priority
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "terrorist-financing-detection",
                "priority", "CRITICAL",
                "securityEvent", true
            ));

            log.error("Sent failed CTF detection event to DLQ - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("CRITICAL: Failed to send CTF detection event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);

            // Notify security team of processing failure
            detectionService.notifySecurityTeamOfProcessingFailure(correlationId, error, dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "TerroristFinancingDetectionEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}