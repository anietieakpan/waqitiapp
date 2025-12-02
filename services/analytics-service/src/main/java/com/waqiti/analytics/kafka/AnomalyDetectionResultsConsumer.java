package com.waqiti.analytics.kafka;

import com.waqiti.analytics.domain.AnomalyDetectionResult;
import com.waqiti.analytics.repository.AnomalyDetectionResultRepository;
import com.waqiti.analytics.service.AnomalyResultAnalyticsService;
import com.waqiti.analytics.service.ModelValidationService;
import com.waqiti.analytics.service.AnomalyFeedbackService;
import com.waqiti.analytics.service.AnomalyClassificationService;
import com.waqiti.analytics.metrics.AnalyticsMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AnomalyDetectionResultsConsumer {

    private final AnomalyDetectionResultRepository anomalyDetectionResultRepository;
    private final AnomalyResultAnalyticsService anomalyResultAnalyticsService;
    private final ModelValidationService modelValidationService;
    private final AnomalyFeedbackService anomalyFeedbackService;
    private final AnomalyClassificationService anomalyClassificationService;
    private final AnalyticsMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("anomaly_detection_results_processed_total")
            .description("Total number of successfully processed anomaly detection result events")
            .register(meterRegistry);
        errorCounter = Counter.builder("anomaly_detection_results_errors_total")
            .description("Total number of anomaly detection result processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("anomaly_detection_results_processing_duration")
            .description("Time taken to process anomaly detection result events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"anomaly-detection-results", "ml-anomaly-results", "anomaly-classification-results", "anomaly-validation-results"},
        groupId = "analytics-anomaly-results-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "anomaly-detection-results", fallbackMethod = "handleAnomalyDetectionResultEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAnomalyDetectionResultEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String resultId = String.valueOf(event.get("resultId"));
        String correlationId = String.format("anomaly-result-%s-p%d-o%d", resultId, partition, offset);
        String eventKey = String.format("%s-%s-%s", resultId, event.get("resultType"), event.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing anomaly detection result: resultId={}, type={}, classification={}",
                resultId, event.get("resultType"), event.get("classification"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String resultType = String.valueOf(event.get("resultType"));
            switch (resultType) {
                case "CLASSIFICATION_RESULT":
                    processClassificationResult(event, correlationId);
                    break;

                case "VALIDATION_RESULT":
                    processValidationResult(event, correlationId);
                    break;

                case "FEEDBACK_RESULT":
                    processFeedbackResult(event, correlationId);
                    break;

                case "PERFORMANCE_RESULT":
                    processPerformanceResult(event, correlationId);
                    break;

                case "COMPARISON_RESULT":
                    processComparisonResult(event, correlationId);
                    break;

                case "AGGREGATION_RESULT":
                    processAggregationResult(event, correlationId);
                    break;

                default:
                    processGenericResult(event, correlationId);
                    break;
            }

            // Analyze result quality
            analyzeResultQuality(event, correlationId);

            // Update model performance metrics
            updateModelPerformance(event, correlationId);

            // Generate result insights
            generateResultInsights(event, correlationId);

            // Check for result anomalies
            checkResultAnomalies(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ANOMALY_DETECTION_RESULT_PROCESSED", resultId,
                Map.of("resultType", resultType, "classification", event.get("classification"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process anomaly detection result event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("anomaly-detection-results-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAnomalyDetectionResultEventFallback(
            Map<String, Object> event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String resultId = String.valueOf(event.get("resultId"));
        String correlationId = String.format("anomaly-result-fallback-%s-p%d-o%d", resultId, partition, offset);

        log.error("Circuit breaker fallback triggered for anomaly detection result: resultId={}, error={}",
            resultId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("anomaly-detection-results-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Anomaly Detection Results Circuit Breaker Triggered",
                String.format("Result %s analytics processing failed: %s", resultId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAnomalyDetectionResultEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String resultId = String.valueOf(event.get("resultId"));
        String correlationId = String.format("dlt-anomaly-result-%s-%d", resultId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Anomaly detection result permanently failed: resultId={}, topic={}, error={}",
            resultId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ANOMALY_DETECTION_RESULT_DLT_EVENT", resultId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.get("resultType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Anomaly Detection Result Dead Letter Event",
                String.format("Result %s analytics sent to DLT: %s", resultId, exceptionMessage),
                Map.of("resultId", resultId, "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processClassificationResult(Map<String, Object> event, String correlationId) {
        String resultId = String.valueOf(event.get("resultId"));
        String detectionId = String.valueOf(event.get("detectionId"));
        String classification = String.valueOf(event.get("classification"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;
        String classificationMethod = String.valueOf(event.get("classificationMethod"));
        Map<String, Double> classificationScores = (Map<String, Double>) event.getOrDefault("classificationScores", new HashMap<>());

        // Save classification result
        saveDetectionResult(event, "CLASSIFICATION_RESULT", correlationId);

        // Update classification analytics
        anomalyClassificationService.recordClassification(detectionId, classification, confidence, classificationMethod);

        // Analyze classification accuracy
        double accuracy = anomalyClassificationService.calculateClassificationAccuracy(classificationMethod);

        // Update classification model metrics
        anomalyClassificationService.updateClassificationMetrics(classificationMethod, classificationScores);

        // Generate classification insights
        Map<String, Object> insights = anomalyClassificationService.generateClassificationInsights(classification, confidence);
        if (!insights.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "CLASSIFICATION_INSIGHTS",
                "detectionId", detectionId,
                "classification", classification,
                "confidence", confidence,
                "accuracy", accuracy,
                "insights", insights,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Classification result processed: detection={}, class={}, confidence={}, accuracy={}",
            detectionId, classification, confidence, accuracy);
    }

    private void processValidationResult(Map<String, Object> event, String correlationId) {
        String resultId = String.valueOf(event.get("resultId"));
        String detectionId = String.valueOf(event.get("detectionId"));
        String validationStatus = String.valueOf(event.get("validationStatus"));
        String validationMethod = String.valueOf(event.get("validationMethod"));
        Map<String, Object> validationMetrics = (Map<String, Object>) event.getOrDefault("validationMetrics", new HashMap<>());
        List<String> validationErrors = (List<String>) event.getOrDefault("validationErrors", new ArrayList<>());

        // Save validation result
        saveDetectionResult(event, "VALIDATION_RESULT", correlationId);

        // Update validation analytics
        modelValidationService.recordValidation(detectionId, validationStatus, validationMethod, validationMetrics);

        // Analyze validation patterns
        Map<String, Object> validationPatterns = modelValidationService.analyzeValidationPatterns(validationMethod);

        // Check validation quality
        double validationQuality = modelValidationService.calculateValidationQuality(validationStatus, validationMetrics);

        // Handle validation failures
        if ("FAILED".equals(validationStatus) && !validationErrors.isEmpty()) {
            kafkaTemplate.send("ml-alerts", Map.of(
                "alertType", "VALIDATION_FAILURE",
                "detectionId", detectionId,
                "validationMethod", validationMethod,
                "validationErrors", validationErrors,
                "validationQuality", validationQuality,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Validation result processed: detection={}, status={}, quality={}",
            detectionId, validationStatus, validationQuality);
    }

    private void processFeedbackResult(Map<String, Object> event, String correlationId) {
        String resultId = String.valueOf(event.get("resultId"));
        String detectionId = String.valueOf(event.get("detectionId"));
        String feedbackType = String.valueOf(event.get("feedbackType"));
        String feedbackValue = String.valueOf(event.get("feedbackValue"));
        String feedbackSource = String.valueOf(event.get("feedbackSource"));
        Double feedbackConfidence = event.get("feedbackConfidence") != null ? ((Number) event.get("feedbackConfidence")).doubleValue() : 0.0;

        // Save feedback result
        saveDetectionResult(event, "FEEDBACK_RESULT", correlationId);

        // Process feedback for model improvement
        anomalyFeedbackService.processFeedback(detectionId, feedbackType, feedbackValue, feedbackSource, feedbackConfidence);

        // Update feedback analytics
        Map<String, Object> feedbackAnalytics = anomalyFeedbackService.analyzeFeedbackTrends(feedbackType, feedbackSource);

        // Generate model improvement suggestions
        List<String> improvements = anomalyFeedbackService.generateModelImprovements(feedbackType, feedbackValue);

        // Send feedback to ML pipeline
        if (!improvements.isEmpty()) {
            kafkaTemplate.send("ml-model-updates", Map.of(
                "updateType", "FEEDBACK_BASED_IMPROVEMENT",
                "detectionId", detectionId,
                "feedbackType", feedbackType,
                "improvements", improvements,
                "feedbackConfidence", feedbackConfidence,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Feedback result processed: detection={}, type={}, value={}, improvements={}",
            detectionId, feedbackType, feedbackValue, improvements.size());
    }

    private void processPerformanceResult(Map<String, Object> event, String correlationId) {
        String resultId = String.valueOf(event.get("resultId"));
        String modelName = String.valueOf(event.get("modelName"));
        String modelVersion = String.valueOf(event.get("modelVersion"));
        Map<String, Double> performanceMetrics = (Map<String, Double>) event.getOrDefault("performanceMetrics", new HashMap<>());
        String performancePeriod = String.valueOf(event.get("performancePeriod"));

        // Save performance result
        saveDetectionResult(event, "PERFORMANCE_RESULT", correlationId);

        // Update model performance tracking
        anomalyResultAnalyticsService.updateModelPerformance(modelName, modelVersion, performanceMetrics, performancePeriod);

        // Analyze performance trends
        Map<String, Object> performanceTrends = anomalyResultAnalyticsService.analyzePerformanceTrends(modelName, performancePeriod);

        // Check for performance degradation
        double degradationScore = anomalyResultAnalyticsService.calculatePerformanceDegradation(modelName, performanceMetrics);
        if (degradationScore > 0.3) {
            kafkaTemplate.send("ml-alerts", Map.of(
                "alertType", "MODEL_PERFORMANCE_DEGRADATION",
                "modelName", modelName,
                "modelVersion", modelVersion,
                "degradationScore", degradationScore,
                "performanceMetrics", performanceMetrics,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Performance result processed: model={}, version={}, degradation={}",
            modelName, modelVersion, degradationScore);
    }

    private void processComparisonResult(Map<String, Object> event, String correlationId) {
        String resultId = String.valueOf(event.get("resultId"));
        List<String> comparedModels = (List<String>) event.getOrDefault("comparedModels", new ArrayList<>());
        Map<String, Map<String, Double>> comparisonMetrics = (Map<String, Map<String, Double>>) event.getOrDefault("comparisonMetrics", new HashMap<>());
        String bestPerformingModel = String.valueOf(event.get("bestPerformingModel"));
        String comparisonCriteria = String.valueOf(event.get("comparisonCriteria"));

        // Save comparison result
        saveDetectionResult(event, "COMPARISON_RESULT", correlationId);

        // Update model comparison analytics
        anomalyResultAnalyticsService.updateModelComparison(comparedModels, comparisonMetrics, bestPerformingModel, comparisonCriteria);

        // Generate model ranking
        List<Map<String, Object>> modelRanking = anomalyResultAnalyticsService.generateModelRanking(comparedModels, comparisonMetrics);

        // Send model selection recommendations
        kafkaTemplate.send("ml-recommendations", Map.of(
            "recommendationType", "MODEL_SELECTION",
            "comparedModels", comparedModels,
            "bestPerformingModel", bestPerformingModel,
            "modelRanking", modelRanking,
            "comparisonCriteria", comparisonCriteria,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Comparison result processed: models={}, best={}, criteria={}",
            comparedModels.size(), bestPerformingModel, comparisonCriteria);
    }

    private void processAggregationResult(Map<String, Object> event, String correlationId) {
        String resultId = String.valueOf(event.get("resultId"));
        String aggregationType = String.valueOf(event.get("aggregationType"));
        String timeWindow = String.valueOf(event.get("timeWindow"));
        Map<String, Object> aggregatedMetrics = (Map<String, Object>) event.getOrDefault("aggregatedMetrics", new HashMap<>());
        Integer sampleCount = (Integer) event.getOrDefault("sampleCount", 0);

        // Save aggregation result
        saveDetectionResult(event, "AGGREGATION_RESULT", correlationId);

        // Update aggregation analytics
        anomalyResultAnalyticsService.updateAggregationAnalytics(aggregationType, timeWindow, aggregatedMetrics, sampleCount);

        // Analyze aggregation patterns
        Map<String, Object> aggregationInsights = anomalyResultAnalyticsService.analyzeAggregationPatterns(aggregationType, timeWindow);

        // Generate aggregation insights
        if (!aggregationInsights.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "AGGREGATION_INSIGHTS",
                "aggregationType", aggregationType,
                "timeWindow", timeWindow,
                "sampleCount", sampleCount,
                "insights", aggregationInsights,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Aggregation result processed: type={}, window={}, samples={}",
            aggregationType, timeWindow, sampleCount);
    }

    private void processGenericResult(Map<String, Object> event, String correlationId) {
        String resultType = String.valueOf(event.get("resultType"));

        // Save generic result
        saveDetectionResult(event, resultType, correlationId);

        // Update generic result analytics
        anomalyResultAnalyticsService.updateGenericResultAnalytics(resultType, event);

        log.info("Generic result processed: type={}", resultType);
    }

    private void saveDetectionResult(Map<String, Object> event, String resultType, String correlationId) {
        String resultId = String.valueOf(event.get("resultId"));
        String detectionId = String.valueOf(event.get("detectionId"));
        String userId = String.valueOf(event.get("userId"));
        String status = String.valueOf(event.getOrDefault("status", "PROCESSED"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;

        AnomalyDetectionResult result = AnomalyDetectionResult.builder()
            .id(UUID.randomUUID().toString())
            .resultId(resultId)
            .detectionId(detectionId)
            .resultType(resultType)
            .userId(userId)
            .status(status)
            .confidence(confidence)
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .resultData((Map<String, Object>) event.get("resultData"))
            .metadata((Map<String, Object>) event.get("metadata"))
            .build();

        anomalyDetectionResultRepository.save(result);

        // Record metrics
        metricsService.recordAnomalyDetectionResult(resultType, status, confidence);
    }

    private void analyzeResultQuality(Map<String, Object> event, String correlationId) {
        String resultType = String.valueOf(event.get("resultType"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;
        String status = String.valueOf(event.getOrDefault("status", "PROCESSED"));

        // Calculate result quality score
        double qualityScore = anomalyResultAnalyticsService.calculateResultQuality(resultType, confidence, status, event);

        // Generate quality insights
        if (qualityScore < 0.7) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "LOW_RESULT_QUALITY",
                "resultType", resultType,
                "qualityScore", qualityScore,
                "confidence", confidence,
                "status", status,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Result quality analyzed: type={}, quality={}, confidence={}", resultType, qualityScore, confidence);
    }

    private void updateModelPerformance(Map<String, Object> event, String correlationId) {
        String resultType = String.valueOf(event.get("resultType"));
        String modelName = String.valueOf(event.getOrDefault("modelName", "unknown"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;

        // Update model performance metrics
        anomalyResultAnalyticsService.updateModelPerformanceFromResult(modelName, resultType, confidence, event);

        log.info("Model performance updated: model={}, type={}, confidence={}", modelName, resultType, confidence);
    }

    private void generateResultInsights(Map<String, Object> event, String correlationId) {
        String resultType = String.valueOf(event.get("resultType"));
        String detectionId = String.valueOf(event.get("detectionId"));

        // Generate comprehensive result insights
        Map<String, Object> insights = anomalyResultAnalyticsService.generateResultInsights(resultType, event);

        kafkaTemplate.send("real-time-analytics", Map.of(
            "analyticsType", "RESULT_INSIGHTS",
            "resultId", event.get("resultId"),
            "detectionId", detectionId,
            "resultType", resultType,
            "insights", insights,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Result insights generated: result={}, type={}", event.get("resultId"), resultType);
    }

    private void checkResultAnomalies(Map<String, Object> event, String correlationId) {
        String resultType = String.valueOf(event.get("resultType"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;

        // Check for unusual result patterns
        boolean isAnomalous = anomalyResultAnalyticsService.detectResultAnomalies(resultType, confidence, event);

        if (isAnomalous) {
            kafkaTemplate.send("analytics-alerts", Map.of(
                "alertType", "ANOMALOUS_DETECTION_RESULT",
                "resultType", resultType,
                "confidence", confidence,
                "resultId", event.get("resultId"),
                "severity", "MEDIUM",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Result anomalies checked: type={}, anomalous={}", resultType, isAnomalous);
    }
}