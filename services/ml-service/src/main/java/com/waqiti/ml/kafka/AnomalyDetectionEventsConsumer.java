package com.waqiti.ml.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.ml.service.*;
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
 * Production-grade Kafka consumer for ML-based anomaly detection events
 * Handles machine learning anomaly detection events with model processing,
 * feature analysis, and intelligent threat assessment
 * 
 * Critical for: ML anomaly detection, feature engineering, model inference
 * SLA: Must process ML events within 12 seconds for real-time detection
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnomalyDetectionEventsConsumer {

    private final MLAnomalyDetectionService mlAnomalyDetectionService;
    private final FeatureEngineeringService featureEngineeringService;
    private final ModelInferenceService modelInferenceService;
    private final MLModelManagementService mlModelManagementService;
    private final MLNotificationService mlNotificationService;
    private final MLMetricsService mlMetricsService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter mlEventsCounter = Counter.builder("ml_anomaly_events_processed_total")
            .description("Total number of ML anomaly detection events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter anomaliesDetectedCounter = Counter.builder("ml_anomalies_detected_total")
            .description("Total number of anomalies detected by ML models")
            .register(metricsService.getMeterRegistry());

    private final Counter processingFailuresCounter = Counter.builder("ml_anomaly_events_processing_failed_total")
            .description("Total number of ML anomaly events that failed processing")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("ml_anomaly_event_processing_duration")
            .description("Time taken to process ML anomaly detection events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"anomaly-detection-events"},
        groupId = "ml-service-anomaly-detection-processor",
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
    @CircuitBreaker(name = "ml-anomaly-detection-processor", fallbackMethod = "handleMLAnomalyDetectionFailure")
    @Retry(name = "ml-anomaly-detection-processor")
    public void processMLAnomalyDetectionEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.info("Processing ML anomaly detection event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("ML anomaly event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate ML anomaly event data
            MLAnomalyEventData eventData = extractMLAnomalyEventData(event.getPayload());
            validateMLAnomalyEvent(eventData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process ML anomaly detection
            processMLAnomalyDetection(eventData, event);

            // Record successful processing metrics
            mlEventsCounter.increment();
            
            // Audit the ML processing
            auditMLAnomalyProcessing(eventData, event, "SUCCESS");

            log.info("Successfully processed ML anomaly event: {} for account: {} - model: {} confidence: {}", 
                    eventId, eventData.getAccountId(), eventData.getModelName(), eventData.getConfidenceScore());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid ML anomaly event data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process ML anomaly event: {}", eventId, e);
            processingFailuresCounter.increment();
            auditMLAnomalyProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("ML anomaly event processing failed", e);

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

    private MLAnomalyEventData extractMLAnomalyEventData(Map<String, Object> payload) throws JsonProcessingException {
        return MLAnomalyEventData.builder()
                .eventId(extractString(payload, "eventId"))
                .accountId(extractString(payload, "accountId"))
                .userId(extractString(payload, "userId"))
                .modelName(extractString(payload, "modelName"))
                .modelVersion(extractString(payload, "modelVersion"))
                .dataPoints(extractMap(payload, "dataPoints"))
                .features(extractMap(payload, "features"))
                .rawFeatures(extractMap(payload, "rawFeatures"))
                .engineeredFeatures(extractMap(payload, "engineeredFeatures"))
                .anomalyScore(extractDouble(payload, "anomalyScore"))
                .confidenceScore(extractDouble(payload, "confidenceScore"))
                .threshold(extractDouble(payload, "threshold"))
                .isAnomaly(extractBoolean(payload, "isAnomaly"))
                .anomalyType(extractString(payload, "anomalyType"))
                .featureImportance(extractMap(payload, "featureImportance"))
                .shapeValues(extractMap(payload, "shapeValues"))
                .predictionExplanation(extractString(payload, "predictionExplanation"))
                .modelMetrics(extractMap(payload, "modelMetrics"))
                .dataQualityMetrics(extractMap(payload, "dataQualityMetrics"))
                .preprocessingSteps(extractStringList(payload, "preprocessingSteps"))
                .detectionTimestamp(extractInstant(payload, "detectionTimestamp"))
                .processingTimestamp(extractInstant(payload, "processingTimestamp"))
                .dataTimestamp(extractInstant(payload, "dataTimestamp"))
                .sourceSystem(extractString(payload, "sourceSystem"))
                .batchId(extractString(payload, "batchId"))
                .experimentId(extractString(payload, "experimentId"))
                .build();
    }

    private void validateMLAnomalyEvent(MLAnomalyEventData eventData) {
        if (eventData.getEventId() == null || eventData.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }
        
        if (eventData.getAccountId() == null || eventData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (eventData.getModelName() == null || eventData.getModelName().trim().isEmpty()) {
            throw new IllegalArgumentException("Model name is required");
        }
        
        if (eventData.getModelVersion() == null || eventData.getModelVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("Model version is required");
        }
        
        if (eventData.getDataPoints() == null || eventData.getDataPoints().isEmpty()) {
            throw new IllegalArgumentException("Data points are required for ML processing");
        }
        
        if (eventData.getAnomalyScore() == null) {
            throw new IllegalArgumentException("Anomaly score is required");
        }
        
        if (eventData.getConfidenceScore() == null || 
            eventData.getConfidenceScore() < 0.0 || eventData.getConfidenceScore() > 1.0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }
        
        if (eventData.getDetectionTimestamp() == null) {
            throw new IllegalArgumentException("Detection timestamp is required");
        }
    }

    private void processMLAnomalyDetection(MLAnomalyEventData eventData, GenericKafkaEvent event) {
        log.info("Processing ML anomaly detection - Model: {}, Account: {}, Score: {}, IsAnomaly: {}", 
                eventData.getModelName(), eventData.getAccountId(), 
                eventData.getAnomalyScore(), eventData.getIsAnomaly());

        try {
            // Validate model availability and version
            validateModelAvailability(eventData);

            // Perform feature engineering and validation
            FeatureProcessingResult featureResult = processFeatures(eventData);

            // Run ML model inference if needed
            ModelInferenceResult inferenceResult = performModelInference(eventData, featureResult);

            // Analyze anomaly results
            AnomalyAnalysisResult analysisResult = analyzeAnomalyResults(eventData, inferenceResult);

            // Update model performance metrics
            updateModelMetrics(eventData, inferenceResult, analysisResult);

            // Process anomaly if detected
            if (eventData.getIsAnomaly() || analysisResult.isAnomalyConfirmed()) {
                processDetectedAnomaly(eventData, analysisResult);
                anomaliesDetectedCounter.increment();
            }

            // Generate explanations and insights
            generateAnomalyExplanations(eventData, analysisResult);

            // Update model learning and adaptation
            updateModelLearning(eventData, inferenceResult);

            // Send notifications and alerts
            sendMLNotifications(eventData, analysisResult);

            log.info("ML anomaly detection completed - EventId: {}, AnomalyConfirmed: {}, ExplanationGenerated: {}", 
                    eventData.getEventId(), analysisResult.isAnomalyConfirmed(), 
                    analysisResult.hasExplanation());

        } catch (Exception e) {
            log.error("Failed to process ML anomaly detection for account: {}", 
                    eventData.getAccountId(), e);
            
            // Apply fallback ML processing
            applyFallbackMLProcessing(eventData, e);
            
            throw new RuntimeException("ML anomaly detection processing failed", e);
        }
    }

    private void validateModelAvailability(MLAnomalyEventData eventData) {
        // Check if model is available and current version is valid
        if (!mlModelManagementService.isModelAvailable(eventData.getModelName(), eventData.getModelVersion())) {
            throw new IllegalStateException("Model not available: " + eventData.getModelName() + 
                                          " version " + eventData.getModelVersion());
        }

        // Check if model is healthy and performant
        ModelHealthStatus health = mlModelManagementService.getModelHealth(eventData.getModelName());
        if (!health.isHealthy()) {
            log.warn("Model {} has degraded health: {}", eventData.getModelName(), health.getIssues());
            
            // Try to use fallback model if available
            String fallbackModel = mlModelManagementService.getFallbackModel(eventData.getModelName());
            if (fallbackModel != null) {
                log.info("Using fallback model: {} for {}", fallbackModel, eventData.getModelName());
                eventData.setModelName(fallbackModel);
            }
        }
    }

    private FeatureProcessingResult processFeatures(MLAnomalyEventData eventData) {
        // Validate feature quality
        FeatureQualityAssessment quality = featureEngineeringService.assessFeatureQuality(
                eventData.getFeatures(),
                eventData.getDataQualityMetrics()
        );

        // Perform feature engineering if needed
        Map<String, Object> engineeredFeatures = null;
        if (eventData.getEngineeredFeatures() == null || eventData.getEngineeredFeatures().isEmpty()) {
            engineeredFeatures = featureEngineeringService.engineerFeatures(
                    eventData.getRawFeatures(),
                    eventData.getModelName()
            );
        } else {
            engineeredFeatures = eventData.getEngineeredFeatures();
        }

        // Validate feature completeness
        FeatureCompletenessCheck completeness = featureEngineeringService.checkFeatureCompleteness(
                engineeredFeatures,
                eventData.getModelName()
        );

        // Apply feature transformations
        Map<String, Object> transformedFeatures = featureEngineeringService.transformFeatures(
                engineeredFeatures,
                eventData.getModelName(),
                eventData.getModelVersion()
        );

        return FeatureProcessingResult.builder()
                .originalFeatures(eventData.getFeatures())
                .engineeredFeatures(engineeredFeatures)
                .transformedFeatures(transformedFeatures)
                .qualityAssessment(quality)
                .completenessCheck(completeness)
                .processingTimestamp(Instant.now())
                .build();
    }

    private ModelInferenceResult performModelInference(MLAnomalyEventData eventData, 
                                                      FeatureProcessingResult featureResult) {
        // Perform model inference with processed features
        ModelPrediction prediction = modelInferenceService.predict(
                eventData.getModelName(),
                eventData.getModelVersion(),
                featureResult.getTransformedFeatures()
        );

        // Calculate model confidence and uncertainty
        ModelConfidenceMetrics confidence = modelInferenceService.calculateConfidence(
                prediction,
                eventData.getModelName(),
                featureResult.getTransformedFeatures()
        );

        // Generate model explanations
        ModelExplanation explanation = modelInferenceService.generateExplanation(
                prediction,
                featureResult.getTransformedFeatures(),
                eventData.getModelName()
        );

        // Validate prediction quality
        PredictionQualityAssessment quality = modelInferenceService.assessPredictionQuality(
                prediction,
                confidence,
                featureResult.getQualityAssessment()
        );

        return ModelInferenceResult.builder()
                .prediction(prediction)
                .confidence(confidence)
                .explanation(explanation)
                .qualityAssessment(quality)
                .inferenceTimestamp(Instant.now())
                .modelMetrics(mlModelManagementService.getModelMetrics(eventData.getModelName()))
                .build();
    }

    private AnomalyAnalysisResult analyzeAnomalyResults(MLAnomalyEventData eventData, 
                                                       ModelInferenceResult inferenceResult) {
        // Combine original anomaly detection with inference results
        boolean anomalyConfirmed = eventData.getIsAnomaly() && 
                                 inferenceResult.getPrediction().isAnomaly() &&
                                 inferenceResult.getConfidence().getScore() > 0.7;

        // Analyze anomaly severity
        AnomalySeverityAnalysis severity = mlAnomalyDetectionService.analyzeSeverity(
                eventData.getAnomalyScore(),
                eventData.getConfidenceScore(),
                inferenceResult.getPrediction().getScore()
        );

        // Analyze anomaly patterns
        AnomalyPatternAnalysis patterns = mlAnomalyDetectionService.analyzePatterns(
                eventData.getAccountId(),
                eventData.getAnomalyType(),
                eventData.getFeatures()
        );

        // Generate comprehensive explanation
        String explanation = generateComprehensiveExplanation(
                eventData,
                inferenceResult,
                severity,
                patterns
        );

        // Determine recommended actions
        List<String> recommendedActions = generateRecommendedActions(
                anomalyConfirmed,
                severity,
                patterns
        );

        return AnomalyAnalysisResult.builder()
                .anomalyConfirmed(anomalyConfirmed)
                .combinedScore(calculateCombinedScore(eventData, inferenceResult))
                .severityAnalysis(severity)
                .patternAnalysis(patterns)
                .explanation(explanation)
                .recommendedActions(recommendedActions)
                .analysisTimestamp(Instant.now())
                .hasExplanation(explanation != null && !explanation.isEmpty())
                .build();
    }

    private double calculateCombinedScore(MLAnomalyEventData eventData, ModelInferenceResult inferenceResult) {
        // Weighted combination of original score and inference score
        double originalWeight = 0.6;
        double inferenceWeight = 0.4;
        
        return (eventData.getAnomalyScore() * originalWeight) + 
               (inferenceResult.getPrediction().getScore() * inferenceWeight);
    }

    private String generateComprehensiveExplanation(MLAnomalyEventData eventData,
                                                  ModelInferenceResult inferenceResult,
                                                  AnomalySeverityAnalysis severity,
                                                  AnomalyPatternAnalysis patterns) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append(String.format("ML Anomaly Detection - Model: %s, Score: %.3f, Confidence: %.3f. ",
                eventData.getModelName(), eventData.getAnomalyScore(), eventData.getConfidenceScore()));
        
        if (eventData.getPredictionExplanation() != null) {
            explanation.append(eventData.getPredictionExplanation()).append(" ");
        }
        
        if (inferenceResult.getExplanation() != null) {
            explanation.append(inferenceResult.getExplanation().getExplanationText()).append(" ");
        }
        
        explanation.append(String.format("Severity: %s, Pattern Match: %s.",
                severity.getSeverityLevel(), patterns.hasKnownPattern() ? "Yes" : "No"));
        
        return explanation.toString();
    }

    private List<String> generateRecommendedActions(boolean anomalyConfirmed,
                                                   AnomalySeverityAnalysis severity,
                                                   AnomalyPatternAnalysis patterns) {
        List<String> actions = new java.util.ArrayList<>();

        if (anomalyConfirmed) {
            switch (severity.getSeverityLevel()) {
                case "CRITICAL":
                    actions.add("IMMEDIATE_INVESTIGATION");
                    actions.add("ESCALATE_TO_SECURITY_TEAM");
                    actions.add("APPLY_PROTECTIVE_MEASURES");
                    break;
                    
                case "HIGH":
                    actions.add("PRIORITY_REVIEW");
                    actions.add("ENHANCED_MONITORING");
                    actions.add("NOTIFY_RISK_TEAM");
                    break;
                    
                case "MEDIUM":
                    actions.add("SCHEDULED_REVIEW");
                    actions.add("MONITOR_TRENDS");
                    break;
                    
                default:
                    actions.add("LOG_FOR_ANALYSIS");
            }
        }

        if (patterns.hasKnownPattern()) {
            actions.add("APPLY_PATTERN_SPECIFIC_CONTROLS");
        }

        if (actions.isEmpty()) {
            actions.add("CONTINUE_MONITORING");
        }

        return actions;
    }

    private void updateModelMetrics(MLAnomalyEventData eventData,
                                  ModelInferenceResult inferenceResult,
                                  AnomalyAnalysisResult analysisResult) {
        // Update model performance metrics
        mlMetricsService.updateModelPerformanceMetrics(
                eventData.getModelName(),
                eventData.getModelVersion(),
                inferenceResult.getConfidence().getScore(),
                analysisResult.isAnomalyConfirmed()
        );

        // Update feature importance tracking
        if (eventData.getFeatureImportance() != null) {
            mlMetricsService.updateFeatureImportanceMetrics(
                    eventData.getModelName(),
                    eventData.getFeatureImportance()
            );
        }

        // Update data quality metrics
        if (eventData.getDataQualityMetrics() != null) {
            mlMetricsService.updateDataQualityMetrics(
                    eventData.getSourceSystem(),
                    eventData.getDataQualityMetrics()
            );
        }
    }

    private void processDetectedAnomaly(MLAnomalyEventData eventData, AnomalyAnalysisResult analysisResult) {
        log.warn("Anomaly detected by ML model - Account: {}, Type: {}, Score: {}, Severity: {}", 
                eventData.getAccountId(), eventData.getAnomalyType(), 
                analysisResult.getCombinedScore(), analysisResult.getSeverityAnalysis().getSeverityLevel());

        // Record anomaly detection
        String anomalyRecordId = mlAnomalyDetectionService.recordAnomalyDetection(
                eventData,
                analysisResult
        );

        // Apply automated responses based on severity
        applyAutomatedResponses(eventData, analysisResult);

        // Update anomaly detection models with feedback
        updateAnomalyDetectionModels(eventData, analysisResult);

        log.info("Anomaly processed - RecordId: {}, AutomatedResponsesApplied: {}", 
                anomalyRecordId, !analysisResult.getRecommendedActions().isEmpty());
    }

    private void applyAutomatedResponses(MLAnomalyEventData eventData, AnomalyAnalysisResult analysisResult) {
        for (String action : analysisResult.getRecommendedActions()) {
            try {
                switch (action) {
                    case "IMMEDIATE_INVESTIGATION":
                        mlAnomalyDetectionService.triggerImmediateInvestigation(
                                eventData.getAccountId(),
                                eventData.getAnomalyType(),
                                analysisResult
                        );
                        break;
                        
                    case "ENHANCED_MONITORING":
                        mlAnomalyDetectionService.enableEnhancedMonitoring(
                                eventData.getAccountId(),
                                eventData.getModelName()
                        );
                        break;
                        
                    case "APPLY_PROTECTIVE_MEASURES":
                        mlAnomalyDetectionService.applyProtectiveMeasures(
                                eventData.getAccountId(),
                                analysisResult.getSeverityAnalysis().getSeverityLevel()
                        );
                        break;
                        
                    default:
                        log.debug("Standard action: {}", action);
                }
            } catch (Exception e) {
                log.error("Failed to apply automated response: {}", action, e);
            }
        }
    }

    private void generateAnomalyExplanations(MLAnomalyEventData eventData, AnomalyAnalysisResult analysisResult) {
        // Generate detailed explanations for anomalies
        if (analysisResult.isAnomalyConfirmed()) {
            String detailedExplanation = mlAnomalyDetectionService.generateDetailedExplanation(
                    eventData,
                    analysisResult
            );

            // Store explanation for later retrieval
            mlAnomalyDetectionService.storeAnomalyExplanation(
                    eventData.getEventId(),
                    detailedExplanation
            );
        }
    }

    private void updateModelLearning(MLAnomalyEventData eventData, ModelInferenceResult inferenceResult) {
        // Update model with new data point for continuous learning
        if (mlModelManagementService.supportsOnlineLearning(eventData.getModelName())) {
            mlModelManagementService.updateModelWithNewData(
                    eventData.getModelName(),
                    eventData.getFeatures(),
                    inferenceResult.getPrediction()
            );
        }

        // Collect feedback for model improvement
        mlModelManagementService.collectFeedback(
                eventData.getModelName(),
                eventData.getEventId(),
                inferenceResult.getQualityAssessment()
        );
    }

    private void updateAnomalyDetectionModels(MLAnomalyEventData eventData, AnomalyAnalysisResult analysisResult) {
        // Update ensemble models with detection results
        mlAnomalyDetectionService.updateEnsembleModels(
                eventData.getModelName(),
                eventData.getFeatures(),
                analysisResult.isAnomalyConfirmed(),
                analysisResult.getCombinedScore()
        );

        // Update pattern recognition models
        if (analysisResult.getPatternAnalysis().hasKnownPattern()) {
            mlAnomalyDetectionService.updatePatternRecognitionModels(
                    eventData.getAnomalyType(),
                    eventData.getFeatures(),
                    analysisResult.getPatternAnalysis()
            );
        }
    }

    private void sendMLNotifications(MLAnomalyEventData eventData, AnomalyAnalysisResult analysisResult) {
        // Send notifications based on anomaly confirmation and severity
        if (analysisResult.isAnomalyConfirmed()) {
            String severity = analysisResult.getSeverityAnalysis().getSeverityLevel();
            
            if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
                mlNotificationService.sendHighPriorityAnomalyAlert(
                        "ML Anomaly Detected",
                        eventData,
                        analysisResult
                );
            } else {
                mlNotificationService.sendStandardAnomalyNotification(
                        eventData,
                        analysisResult
                );
            }
        }

        // Send model performance notifications if needed
        if (eventData.getModelMetrics() != null) {
            mlNotificationService.sendModelPerformanceUpdate(
                    eventData.getModelName(),
                    eventData.getModelMetrics()
            );
        }
    }

    private void applyFallbackMLProcessing(MLAnomalyEventData eventData, Exception error) {
        log.error("Applying fallback ML processing due to failure");
        
        try {
            // Apply conservative anomaly detection
            mlAnomalyDetectionService.applyConservativeDetection(eventData.getAccountId());
            
            // Send fallback notification
            mlNotificationService.sendFallbackProcessingAlert(
                    "ML Anomaly Processing Failed - Fallback Applied",
                    String.format("Failed to process ML anomaly for account %s: %s", 
                            eventData.getAccountId(), error.getMessage())
            );
            
        } catch (Exception e) {
            log.error("Fallback ML processing also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("ML anomaly event validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "ML_ANOMALY_EVENT_VALIDATION_ERROR",
                null,
                "ML anomaly event validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );

        // Send to validation errors topic for analysis
        mlNotificationService.sendValidationErrorAlert(event, e.getMessage());
    }

    private void auditMLAnomalyProcessing(MLAnomalyEventData eventData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "ML_ANOMALY_EVENT_PROCESSED",
                    eventData != null ? eventData.getAccountId() : null,
                    String.format("ML anomaly event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "mlEventId", eventData != null ? eventData.getEventId() : "unknown",
                            "accountId", eventData != null ? eventData.getAccountId() : "unknown",
                            "modelName", eventData != null ? eventData.getModelName() : "unknown",
                            "anomalyScore", eventData != null ? eventData.getAnomalyScore() : 0.0,
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit ML anomaly processing", e);
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic) {
        
        log.error("ML anomaly event sent to DLT - EventId: {}, OriginalTopic: {}", 
                event.getEventId(), originalTopic);

        try {
            MLAnomalyEventData eventData = extractMLAnomalyEventData(event.getPayload());
            
            // Apply conservative anomaly handling for DLT events
            mlAnomalyDetectionService.applyConservativeDetection(eventData.getAccountId());

            // Send DLT alert
            mlNotificationService.sendDLTAlert(
                    "ML Anomaly Event in DLT",
                    "ML anomaly event could not be processed - conservative detection applied"
            );

            // Audit DLT handling
            auditService.auditSecurityEvent(
                    "ML_ANOMALY_EVENT_DLT",
                    eventData.getAccountId(),
                    "ML anomaly event sent to Dead Letter Queue - conservative detection applied",
                    Map.of(
                            "eventId", event.getEventId(),
                            "mlEventId", eventData.getEventId(),
                            "accountId", eventData.getAccountId(),
                            "modelName", eventData.getModelName(),
                            "originalTopic", originalTopic
                    )
            );

        } catch (Exception e) {
            log.error("Failed to handle ML anomaly DLT event: {}", event.getEventId(), e);
        }
    }

    // Circuit breaker fallback method
    public void handleMLAnomalyDetectionFailure(GenericKafkaEvent event, String topic, int partition,
                                               long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for ML anomaly detection processing - EventId: {}", 
                event.getEventId(), e);

        try {
            MLAnomalyEventData eventData = extractMLAnomalyEventData(event.getPayload());
            
            // Apply conservative detection
            mlAnomalyDetectionService.applyConservativeDetection(eventData.getAccountId());

            // Send system alert
            mlNotificationService.sendSystemAlert(
                    "ML Anomaly Detection Circuit Breaker Open",
                    "ML anomaly detection processing is failing - ML models may be compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle ML anomaly circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper extraction methods
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
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class MLAnomalyEventData {
        private String eventId;
        private String accountId;
        private String userId;
        private String modelName;
        private String modelVersion;
        private Map<String, Object> dataPoints;
        private Map<String, Object> features;
        private Map<String, Object> rawFeatures;
        private Map<String, Object> engineeredFeatures;
        private Double anomalyScore;
        private Double confidenceScore;
        private Double threshold;
        private Boolean isAnomaly;
        private String anomalyType;
        private Map<String, Object> featureImportance;
        private Map<String, Object> shapeValues;
        private String predictionExplanation;
        private Map<String, Object> modelMetrics;
        private Map<String, Object> dataQualityMetrics;
        private List<String> preprocessingSteps;
        private Instant detectionTimestamp;
        private Instant processingTimestamp;
        private Instant dataTimestamp;
        private String sourceSystem;
        private String batchId;
        private String experimentId;
    }

    @lombok.Data
    @lombok.Builder
    public static class FeatureProcessingResult {
        private Map<String, Object> originalFeatures;
        private Map<String, Object> engineeredFeatures;
        private Map<String, Object> transformedFeatures;
        private FeatureQualityAssessment qualityAssessment;
        private FeatureCompletenessCheck completenessCheck;
        private Instant processingTimestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelInferenceResult {
        private ModelPrediction prediction;
        private ModelConfidenceMetrics confidence;
        private ModelExplanation explanation;
        private PredictionQualityAssessment qualityAssessment;
        private Instant inferenceTimestamp;
        private Map<String, Object> modelMetrics;
    }

    @lombok.Data
    @lombok.Builder
    public static class AnomalyAnalysisResult {
        private boolean anomalyConfirmed;
        private Double combinedScore;
        private AnomalySeverityAnalysis severityAnalysis;
        private AnomalyPatternAnalysis patternAnalysis;
        private String explanation;
        private List<String> recommendedActions;
        private Instant analysisTimestamp;
        private boolean hasExplanation;
    }

    // Supporting classes (simplified)
    @lombok.Data
    @lombok.Builder
    public static class ModelHealthStatus {
        private boolean healthy;
        private List<String> issues;
    }

    @lombok.Data
    @lombok.Builder
    public static class FeatureQualityAssessment {
        private Double qualityScore;
        private List<String> qualityIssues;
    }

    @lombok.Data
    @lombok.Builder
    public static class FeatureCompletenessCheck {
        private boolean complete;
        private List<String> missingFeatures;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelPrediction {
        private boolean anomaly;
        private Double score;
        private String predictionClass;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelConfidenceMetrics {
        private Double score;
        private Double uncertainty;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelExplanation {
        private String explanationText;
        private Map<String, Double> featureContributions;
    }

    @lombok.Data
    @lombok.Builder
    public static class PredictionQualityAssessment {
        private Double qualityScore;
        private boolean reliable;
    }

    @lombok.Data
    @lombok.Builder
    public static class AnomalySeverityAnalysis {
        private String severityLevel;
        private Double severityScore;
    }

    @lombok.Data
    @lombok.Builder
    public static class AnomalyPatternAnalysis {
        private boolean hasKnownPattern;
        private String patternType;
        private Double patternMatchScore;
    }
}