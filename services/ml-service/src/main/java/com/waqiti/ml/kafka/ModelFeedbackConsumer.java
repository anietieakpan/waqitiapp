package com.waqiti.ml.kafka;

import com.waqiti.common.events.ModelFeedbackEvent;
import com.waqiti.ml.service.ModelFeedbackService;
import com.waqiti.ml.service.ModelLearningService;
import com.waqiti.ml.service.ModelMetricsService;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModelFeedbackConsumer {

    private final ModelFeedbackService feedbackService;
    private final ModelLearningService learningService;
    private final ModelMetricsService metricsService;
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
        successCounter = Counter.builder("model_feedback_processed_total")
            .description("Total number of successfully processed model feedback events")
            .register(meterRegistry);
        errorCounter = Counter.builder("model_feedback_errors_total")
            .description("Total number of model feedback processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("model_feedback_processing_duration")
            .description("Time taken to process model feedback events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"model-feedback", "model-corrections", "model-validation-feedback"},
        groupId = "model-feedback-service-group",
        containerFactory = "mlKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "model-feedback", fallbackMethod = "handleModelFeedbackEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleModelFeedbackEvent(
            @Payload ModelFeedbackEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("feedback-%s-p%d-o%d", event.getModelId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getModelId(), event.getPredictionId(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing model feedback: modelId={}, predictionId={}, feedbackType={}, correct={}",
                event.getModelId(), event.getPredictionId(), event.getFeedbackType(), event.isCorrectPrediction());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getFeedbackType()) {
                case PREDICTION_CORRECTION:
                    processPredictionCorrection(event, correlationId);
                    break;

                case PREDICTION_VALIDATION:
                    processPredictionValidation(event, correlationId);
                    break;

                case PERFORMANCE_FEEDBACK:
                    processPerformanceFeedback(event, correlationId);
                    break;

                case USER_SATISFACTION:
                    processUserSatisfactionFeedback(event, correlationId);
                    break;

                case BUSINESS_OUTCOME:
                    processBusinessOutcomeFeedback(event, correlationId);
                    break;

                case MODEL_BIAS_REPORT:
                    processModelBiasReport(event, correlationId);
                    break;

                case FEATURE_IMPORTANCE:
                    processFeatureImportanceFeedback(event, correlationId);
                    break;

                case DRIFT_DETECTION:
                    processDriftDetectionFeedback(event, correlationId);
                    break;

                default:
                    log.warn("Unknown model feedback type: {}", event.getFeedbackType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logMlEvent("MODEL_FEEDBACK_PROCESSED", event.getModelId(),
                Map.of("predictionId", event.getPredictionId(), "feedbackType", event.getFeedbackType(),
                    "correctPrediction", event.isCorrectPrediction(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process model feedback event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("model-feedback-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleModelFeedbackEventFallback(
            ModelFeedbackEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("feedback-fallback-%s-p%d-o%d", event.getModelId(), partition, offset);

        log.error("Circuit breaker fallback triggered for model feedback: modelId={}, error={}",
            event.getModelId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("model-feedback-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to ML team
        try {
            notificationService.sendOperationalAlert(
                "Model Feedback Circuit Breaker Triggered",
                String.format("Model %s feedback processing failed: %s", event.getModelId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltModelFeedbackEvent(
            @Payload ModelFeedbackEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-feedback-%s-%d", event.getModelId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Model feedback permanently failed: modelId={}, topic={}, error={}",
            event.getModelId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logMlEvent("MODEL_FEEDBACK_DLT_EVENT", event.getModelId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "predictionId", event.getPredictionId(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Model Feedback Dead Letter Event",
                String.format("Model %s feedback sent to DLT: %s", event.getModelId(), exceptionMessage),
                Map.of("modelId", event.getModelId(), "topic", topic, "correlationId", correlationId)
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

    private void processPredictionCorrection(ModelFeedbackEvent event, String correlationId) {
        // Record prediction correction
        feedbackService.recordPredictionCorrection(
            event.getModelId(),
            event.getPredictionId(),
            event.getOriginalPrediction(),
            event.getCorrectedPrediction(),
            event.getCorrectionReason(),
            event.getCorrectedBy(),
            correlationId
        );

        // Update model accuracy metrics
        metricsService.updateAccuracyMetrics(
            event.getModelId(),
            event.isCorrectPrediction(),
            event.getConfidenceScore()
        );

        // Add to retraining dataset if significant error
        if (feedbackService.isSignificantError(event.getOriginalPrediction(), event.getCorrectedPrediction())) {
            learningService.addToRetrainingDataset(
                event.getModelId(),
                event.getInputFeatures(),
                event.getCorrectedPrediction(),
                "HIGH_ERROR_CORRECTION",
                correlationId
            );
        }

        // Check if retraining threshold reached
        if (feedbackService.shouldTriggerRetraining(event.getModelId())) {
            kafkaTemplate.send("model-retraining-triggers", Map.of(
                "modelId", event.getModelId(),
                "reason", "ACCUMULATED_CORRECTIONS",
                "priority", "MEDIUM",
                "correctionCount", feedbackService.getCorrectionCount(event.getModelId()),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.incrementPredictionCorrections(event.getModelId());

        log.info("Prediction correction processed: modelId={}, predictionId={}, significantError={}",
            event.getModelId(), event.getPredictionId(),
            feedbackService.isSignificantError(event.getOriginalPrediction(), event.getCorrectedPrediction()));
    }

    private void processPredictionValidation(ModelFeedbackEvent event, String correlationId) {
        // Record validation feedback
        feedbackService.recordPredictionValidation(
            event.getModelId(),
            event.getPredictionId(),
            event.isCorrectPrediction(),
            event.getValidationSource(),
            event.getValidationConfidence(),
            correlationId
        );

        // Update precision/recall metrics
        metricsService.updatePrecisionRecallMetrics(
            event.getModelId(),
            event.getPredictedClass(),
            event.getActualClass(),
            event.isCorrectPrediction()
        );

        // Check for systematic prediction errors
        var errorPattern = feedbackService.analyzeErrorPattern(
            event.getModelId(),
            event.getPredictedClass(),
            event.getActualClass()
        );

        if (errorPattern.isSystematic()) {
            kafkaTemplate.send("model-bias-detection", Map.of(
                "modelId", event.getModelId(),
                "errorPattern", errorPattern.getPattern(),
                "affectedClasses", errorPattern.getAffectedClasses(),
                "severity", errorPattern.getSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update online learning if enabled
        if (learningService.isOnlineLearningEnabled(event.getModelId())) {
            learningService.updateModelOnline(
                event.getModelId(),
                event.getInputFeatures(),
                event.getActualClass(),
                event.getValidationConfidence(),
                correlationId
            );
        }

        metricsService.incrementValidationFeedback(event.getModelId(), event.isCorrectPrediction());

        log.info("Prediction validation processed: modelId={}, predictionId={}, correct={}, systematicError={}",
            event.getModelId(), event.getPredictionId(), event.isCorrectPrediction(), errorPattern.isSystematic());
    }

    private void processPerformanceFeedback(ModelFeedbackEvent event, String correlationId) {
        // Record performance metrics
        metricsService.recordPerformanceFeedback(
            event.getModelId(),
            event.getPerformanceMetrics(),
            event.getPerformanceScore(),
            event.getBenchmarkComparison(),
            correlationId
        );

        // Check for performance degradation
        if (metricsService.isPerformanceDegraded(event.getModelId(), event.getPerformanceScore())) {
            kafkaTemplate.send("model-performance-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "PERFORMANCE_DEGRADATION",
                "currentScore", event.getPerformanceScore(),
                "benchmarkScore", event.getBenchmarkComparison(),
                "severity", metricsService.calculateDegradationSeverity(event.getModelId(), event.getPerformanceScore()),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update model monitoring dashboard
        kafkaTemplate.send("model-dashboard-updates", Map.of(
            "modelId", event.getModelId(),
            "updateType", "PERFORMANCE_METRICS",
            "performanceScore", event.getPerformanceScore(),
            "performanceMetrics", event.getPerformanceMetrics(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPerformanceUpdate(event.getModelId(), event.getPerformanceScore());

        log.info("Performance feedback processed: modelId={}, score={}, degraded={}",
            event.getModelId(), event.getPerformanceScore(),
            metricsService.isPerformanceDegraded(event.getModelId(), event.getPerformanceScore()));
    }

    private void processUserSatisfactionFeedback(ModelFeedbackEvent event, String correlationId) {
        // Record user satisfaction
        feedbackService.recordUserSatisfaction(
            event.getModelId(),
            event.getPredictionId(),
            event.getUserSatisfactionScore(),
            event.getUserFeedbackText(),
            event.getUserId(),
            correlationId
        );

        // Analyze satisfaction trends
        var satisfactionTrend = feedbackService.analyzeSatisfactionTrend(event.getModelId());

        if (satisfactionTrend.isDecreasing()) {
            kafkaTemplate.send("model-satisfaction-alerts", Map.of(
                "modelId", event.getModelId(),
                "trend", "DECREASING",
                "currentScore", satisfactionTrend.getCurrentScore(),
                "previousScore", satisfactionTrend.getPreviousScore(),
                "userCount", satisfactionTrend.getUserCount(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Extract insights from feedback text
        if (event.getUserFeedbackText() != null) {
            kafkaTemplate.send("feedback-text-analysis", Map.of(
                "modelId", event.getModelId(),
                "feedbackText", event.getUserFeedbackText(),
                "sentiment", feedbackService.analyzeSentiment(event.getUserFeedbackText()),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordUserSatisfaction(event.getModelId(), event.getUserSatisfactionScore());

        log.info("User satisfaction feedback processed: modelId={}, score={}, trend={}",
            event.getModelId(), event.getUserSatisfactionScore(), satisfactionTrend.getDirection());
    }

    private void processBusinessOutcomeFeedback(ModelFeedbackEvent event, String correlationId) {
        // Record business outcome
        feedbackService.recordBusinessOutcome(
            event.getModelId(),
            event.getPredictionId(),
            event.getBusinessMetric(),
            event.getBusinessValue(),
            event.getOutcomeTimestamp(),
            correlationId
        );

        // Calculate ROI and business impact
        var businessImpact = feedbackService.calculateBusinessImpact(
            event.getModelId(),
            event.getBusinessMetric(),
            event.getBusinessValue()
        );

        // Update business metrics dashboard
        kafkaTemplate.send("business-metrics-updates", Map.of(
            "modelId", event.getModelId(),
            "businessMetric", event.getBusinessMetric(),
            "businessValue", event.getBusinessValue(),
            "businessImpact", businessImpact,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check if business objectives are being met
        if (feedbackService.areBusinessObjectivesMet(event.getModelId())) {
            kafkaTemplate.send("model-success-notifications", Map.of(
                "modelId", event.getModelId(),
                "achievement", "BUSINESS_OBJECTIVES_MET",
                "businessImpact", businessImpact,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordBusinessOutcome(event.getModelId(), event.getBusinessMetric(), event.getBusinessValue());

        log.info("Business outcome feedback processed: modelId={}, metric={}, value={}, impact={}",
            event.getModelId(), event.getBusinessMetric(), event.getBusinessValue(), businessImpact);
    }

    private void processModelBiasReport(ModelFeedbackEvent event, String correlationId) {
        // Record bias report
        feedbackService.recordBiasReport(
            event.getModelId(),
            event.getBiasType(),
            event.getAffectedGroups(),
            event.getBiasMetrics(),
            event.getBiasSeverity(),
            event.getReportedBy(),
            correlationId
        );

        // Send bias alert
        notificationService.sendCriticalAlert(
            "Model Bias Detected",
            String.format("Bias detected in model %s. Type: %s, Severity: %s, Affected Groups: %s",
                event.getModelId(), event.getBiasType(), event.getBiasSeverity(), event.getAffectedGroups()),
            Map.of("modelId", event.getModelId(), "correlationId", correlationId)
        );

        // Trigger bias mitigation
        kafkaTemplate.send("bias-mitigation-actions", Map.of(
            "modelId", event.getModelId(),
            "biasType", event.getBiasType(),
            "affectedGroups", event.getAffectedGroups(),
            "severity", event.getBiasSeverity(),
            "mitigationPlan", feedbackService.getBiasMitigationPlan(event.getBiasType()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule fairness testing
        kafkaTemplate.send("fairness-testing-schedule", Map.of(
            "modelId", event.getModelId(),
            "testType", "COMPREHENSIVE_FAIRNESS",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementBiasReports(event.getModelId(), event.getBiasType());

        log.warn("Model bias report processed: modelId={}, biasType={}, severity={}, affectedGroups={}",
            event.getModelId(), event.getBiasType(), event.getBiasSeverity(), event.getAffectedGroups());
    }

    private void processFeatureImportanceFeedback(ModelFeedbackEvent event, String correlationId) {
        // Update feature importance scores
        feedbackService.updateFeatureImportance(
            event.getModelId(),
            event.getFeatureImportanceScores(),
            event.getFeatureContributions(),
            correlationId
        );

        // Analyze feature drift
        var featureDrift = feedbackService.analyzeFeatureDrift(
            event.getModelId(),
            event.getFeatureImportanceScores()
        );

        if (featureDrift.isDriftDetected()) {
            kafkaTemplate.send("feature-drift-alerts", Map.of(
                "modelId", event.getModelId(),
                "driftedFeatures", featureDrift.getDriftedFeatures(),
                "driftMagnitude", featureDrift.getDriftMagnitude(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update feature monitoring
        kafkaTemplate.send("feature-monitoring-updates", Map.of(
            "modelId", event.getModelId(),
            "featureImportanceScores", event.getFeatureImportanceScores(),
            "featureDrift", featureDrift,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordFeatureImportanceUpdate(event.getModelId(), event.getFeatureImportanceScores());

        log.info("Feature importance feedback processed: modelId={}, driftDetected={}, driftedFeatures={}",
            event.getModelId(), featureDrift.isDriftDetected(), featureDrift.getDriftedFeatures().size());
    }

    private void processDriftDetectionFeedback(ModelFeedbackEvent event, String correlationId) {
        // Record drift detection results
        feedbackService.recordDriftDetection(
            event.getModelId(),
            event.getDriftType(),
            event.getDriftMagnitude(),
            event.getDriftedFeatures(),
            event.getDriftConfidence(),
            correlationId
        );

        // Send drift alert based on severity
        String alertLevel = event.getDriftMagnitude() > 0.7 ? "CRITICAL" : "HIGH";
        notificationService.sendAlert(
            "Model Drift Detected",
            String.format("Drift detected in model %s. Type: %s, Magnitude: %.2f, Confidence: %.2f",
                event.getModelId(), event.getDriftType(), event.getDriftMagnitude(), event.getDriftConfidence()),
            alertLevel
        );

        // Trigger automatic remediation if configured
        if (feedbackService.hasAutomaticDriftRemediation(event.getModelId())) {
            kafkaTemplate.send("drift-remediation-actions", Map.of(
                "modelId", event.getModelId(),
                "driftType", event.getDriftType(),
                "driftMagnitude", event.getDriftMagnitude(),
                "remediationPlan", feedbackService.getDriftRemediationPlan(event.getDriftType()),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Consider model retraining for significant drift
        if (event.getDriftMagnitude() > 0.5) {
            kafkaTemplate.send("model-retraining-triggers", Map.of(
                "modelId", event.getModelId(),
                "reason", "SIGNIFICANT_DRIFT_DETECTED",
                "priority", "HIGH",
                "driftMagnitude", event.getDriftMagnitude(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordDriftDetection(event.getModelId(), event.getDriftType(), event.getDriftMagnitude());

        log.warn("Drift detection feedback processed: modelId={}, type={}, magnitude={}, confidence={}",
            event.getModelId(), event.getDriftType(), event.getDriftMagnitude(), event.getDriftConfidence());
    }
}