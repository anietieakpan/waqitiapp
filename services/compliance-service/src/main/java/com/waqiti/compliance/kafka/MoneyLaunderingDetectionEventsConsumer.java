package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.MoneyLaunderingDetectionEventDto;
import com.waqiti.compliance.service.MoneyLaunderingDetectionService;
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
 * Consumer for processing money laundering detection events.
 * Handles ML-based detection alerts, pattern recognition events, and suspicious activity identification.
 * Implements comprehensive AML compliance monitoring with advanced threat detection capabilities.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class MoneyLaunderingDetectionEventsConsumer extends BaseConsumer<MoneyLaunderingDetectionEventDto> {

    private static final String TOPIC_NAME = "money-laundering-detection-events";
    private static final String CONSUMER_GROUP = "compliance-service-ml-detection";
    private static final String DLQ_TOPIC = "money-laundering-detection-events-dlq";

    private final MoneyLaunderingDetectionService detectionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter suspiciousActivitiesDetectedCounter;
    private final Counter highRiskPatternsCounter;
    private final Counter mlModelAlertsCounter;
    private final Timer processingTimer;
    private final Timer mlAnalysisTimer;

    @Autowired
    public MoneyLaunderingDetectionEventsConsumer(
            MoneyLaunderingDetectionService detectionService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.detectionService = detectionService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("money-laundering-detection", 5, Duration.ofMinutes(2));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("ml_detection_events_processed_total")
                .description("Total number of money laundering detection events processed")
                .tag("service", "compliance")
                .tag("consumer", "money-laundering-detection")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("ml_detection_events_failed_total")
                .description("Total number of failed money laundering detection events")
                .tag("service", "compliance")
                .tag("consumer", "money-laundering-detection")
                .register(meterRegistry);

        this.suspiciousActivitiesDetectedCounter = Counter.builder("suspicious_activities_detected_total")
                .description("Total number of suspicious activities detected")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.highRiskPatternsCounter = Counter.builder("high_risk_patterns_detected_total")
                .description("Total number of high-risk patterns detected")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.mlModelAlertsCounter = Counter.builder("ml_model_alerts_total")
                .description("Total number of ML model alerts generated")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("ml_detection_processing_duration")
                .description("Time taken to process money laundering detection events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.mlAnalysisTimer = Timer.builder("ml_analysis_duration")
                .description("Time taken for ML analysis")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes money laundering detection events with comprehensive ML analysis.
     *
     * @param eventJson The JSON representation of the money laundering detection event
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
    public void processMoneyLaunderingDetectionEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing money laundering detection event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            MoneyLaunderingDetectionEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processDetectionEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.info("Successfully processed money laundering detection event - CorrelationId: {}, CustomerId: {}, DetectionType: {}",
                    correlationId, event.getCustomerId(), event.getDetectionType());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the money laundering detection event with comprehensive analysis.
     */
    private void processDetectionEvent(MoneyLaunderingDetectionEventDto event, String correlationId) {
        Timer.Sample analysisTimer = Timer.start();

        try {
            log.info("Processing ML detection for customer: {} - CorrelationId: {}",
                    event.getCustomerId(), correlationId);

            // Perform ML-based analysis
            var analysisResult = detectionService.analyzeForMoneyLaundering(event, correlationId);

            // Process based on detection type
            switch (event.getDetectionType()) {
                case "PATTERN_RECOGNITION":
                    processPatternRecognition(event, analysisResult, correlationId);
                    break;
                case "BEHAVIORAL_ANALYSIS":
                    processBehavioralAnalysis(event, analysisResult, correlationId);
                    break;
                case "NETWORK_ANALYSIS":
                    processNetworkAnalysis(event, analysisResult, correlationId);
                    break;
                case "TRANSACTION_CLUSTERING":
                    processTransactionClustering(event, analysisResult, correlationId);
                    break;
                case "ANOMALY_DETECTION":
                    processAnomalyDetection(event, analysisResult, correlationId);
                    break;
                default:
                    processGenericDetection(event, analysisResult, correlationId);
            }

            // Generate compliance reports if required
            if (analysisResult.requiresReporting()) {
                detectionService.generateComplianceReport(event, analysisResult, correlationId);
            }

            // Update customer risk profile
            detectionService.updateCustomerRiskProfile(event.getCustomerId(), analysisResult, correlationId);

            // Track detection metrics
            trackDetectionMetrics(event, analysisResult);

        } finally {
            analysisTimer.stop(mlAnalysisTimer);
        }
    }

    /**
     * Processes pattern recognition detection events.
     */
    private void processPatternRecognition(MoneyLaunderingDetectionEventDto event,
                                         var analysisResult, String correlationId) {
        log.info("Processing pattern recognition detection - CorrelationId: {}", correlationId);

        // Analyze transaction patterns
        var patterns = detectionService.analyzeTransactionPatterns(event, correlationId);

        // Check for known ML patterns
        for (var pattern : patterns) {
            if (pattern.getRiskScore() > 0.8) {
                highRiskPatternsCounter.increment();

                // Generate high-priority alert
                detectionService.generateHighPriorityAlert(event, pattern, correlationId);

                // Trigger immediate investigation
                if (pattern.getRiskScore() > 0.95) {
                    detectionService.triggerImmediateInvestigation(event, pattern, correlationId);
                }
            }
        }
    }

    /**
     * Processes behavioral analysis detection events.
     */
    private void processBehavioralAnalysis(MoneyLaunderingDetectionEventDto event,
                                         var analysisResult, String correlationId) {
        log.info("Processing behavioral analysis detection - CorrelationId: {}", correlationId);

        // Analyze customer behavior deviations
        var behaviorAnalysis = detectionService.analyzeBehaviorDeviations(event, correlationId);

        if (behaviorAnalysis.hasSignificantDeviations()) {
            suspiciousActivitiesDetectedCounter.increment();

            // Enhanced due diligence if behavior is highly unusual
            if (behaviorAnalysis.getRiskLevel().equals("HIGH")) {
                detectionService.initiateEnhancedDueDiligence(event.getCustomerId(),
                        behaviorAnalysis, correlationId);
            }
        }
    }

    /**
     * Processes network analysis detection events.
     */
    private void processNetworkAnalysis(MoneyLaunderingDetectionEventDto event,
                                      var analysisResult, String correlationId) {
        log.info("Processing network analysis detection - CorrelationId: {}", correlationId);

        // Analyze transaction networks and connections
        var networkAnalysis = detectionService.analyzeTransactionNetworks(event, correlationId);

        // Check for suspicious network patterns
        if (networkAnalysis.hasSuspiciousConnections()) {
            mlModelAlertsCounter.increment();

            // Flag entire network for review
            detectionService.flagNetworkForReview(networkAnalysis, correlationId);
        }
    }

    /**
     * Processes transaction clustering detection events.
     */
    private void processTransactionClustering(MoneyLaunderingDetectionEventDto event,
                                             var analysisResult, String correlationId) {
        log.info("Processing transaction clustering detection - CorrelationId: {}", correlationId);

        // Analyze transaction clusters for layering patterns
        var clusterAnalysis = detectionService.analyzeTransactionClusters(event, correlationId);

        // Detect layering and structuring patterns
        if (clusterAnalysis.hasLayeringPatterns() || clusterAnalysis.hasStructuringPatterns()) {
            suspiciousActivitiesDetectedCounter.increment();
            highRiskPatternsCounter.increment();

            // Generate SAR if confidence is high
            if (clusterAnalysis.getConfidenceScore() > 0.9) {
                detectionService.generateSuspiciousActivityReport(event, clusterAnalysis, correlationId);
            }
        }
    }

    /**
     * Processes anomaly detection events.
     */
    private void processAnomalyDetection(MoneyLaunderingDetectionEventDto event,
                                       var analysisResult, String correlationId) {
        log.info("Processing anomaly detection - CorrelationId: {}", correlationId);

        // Advanced anomaly detection using multiple ML models
        var anomalies = detectionService.detectAnomalies(event, correlationId);

        for (var anomaly : anomalies) {
            if (anomaly.getSeverity().equals("CRITICAL")) {
                mlModelAlertsCounter.increment();

                // Immediate escalation for critical anomalies
                detectionService.escalateToCompliance(event, anomaly, correlationId);

                // Temporary transaction restrictions if warranted
                if (anomaly.requiresImmediateAction()) {
                    detectionService.applyTemporaryRestrictions(event.getCustomerId(),
                            anomaly, correlationId);
                }
            }
        }
    }

    /**
     * Processes generic detection events.
     */
    private void processGenericDetection(MoneyLaunderingDetectionEventDto event,
                                       var analysisResult, String correlationId) {
        log.info("Processing generic ML detection - CorrelationId: {}", correlationId);

        // Apply general ML models for money laundering detection
        var generalAnalysis = detectionService.applyGeneralMLModels(event, correlationId);

        if (generalAnalysis.getRiskScore() > 0.7) {
            suspiciousActivitiesDetectedCounter.increment();

            // Queue for manual review
            detectionService.queueForManualReview(event, generalAnalysis, correlationId);
        }
    }

    /**
     * Tracks detection-specific metrics.
     */
    private void trackDetectionMetrics(MoneyLaunderingDetectionEventDto event, var analysisResult) {
        // Track by detection type
        Counter.builder("ml_detection_by_type_total")
                .tag("detection_type", event.getDetectionType())
                .tag("service", "compliance")
                .register(metricsCollector.getMeterRegistry())
                .increment();

        // Track by risk level
        if (analysisResult.getRiskLevel() != null) {
            Counter.builder("ml_detection_by_risk_level_total")
                    .tag("risk_level", analysisResult.getRiskLevel())
                    .tag("service", "compliance")
                    .register(metricsCollector.getMeterRegistry())
                    .increment();
        }
    }

    /**
     * Deserializes the event JSON into a MoneyLaunderingDetectionEventDto.
     */
    private MoneyLaunderingDetectionEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, MoneyLaunderingDetectionEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize money laundering detection event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the money laundering detection event.
     */
    private void validateEvent(MoneyLaunderingDetectionEventDto event, String correlationId) {
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required for money laundering detection");
        }

        if (event.getDetectionType() == null || event.getDetectionType().trim().isEmpty()) {
            throw new IllegalArgumentException("Detection type is required");
        }

        if (event.getTransactionData() == null && event.getBehaviorData() == null) {
            throw new IllegalArgumentException("Either transaction data or behavior data is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required for detection events");
        }

        // Validate timestamp is not too old (older than 30 days)
        if (event.getTimestamp().isBefore(Instant.now().minus(Duration.ofDays(30)))) {
            log.warn("Money laundering detection event is older than 30 days - CorrelationId: {}, Timestamp: {}",
                    correlationId, event.getTimestamp());
        }
    }

    /**
     * Handles processing errors with comprehensive error management.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("Failed to process money laundering detection event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ for later processing
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "money-laundering-detection"
            ));

            log.info("Sent failed money laundering detection event to DLQ - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("Failed to send money laundering detection event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "MoneyLaunderingDetectionEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}