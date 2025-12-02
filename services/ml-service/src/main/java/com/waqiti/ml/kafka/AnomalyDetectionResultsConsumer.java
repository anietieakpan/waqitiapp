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
 * Production-grade Kafka consumer for anomaly detection results
 * Processes ML model anomaly detection results with model evaluation,
 * performance tracking, and result validation
 * 
 * Critical for: ML result processing, model performance tracking, result validation
 * SLA: Must process results within 10 seconds for model feedback loops
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnomalyDetectionResultsConsumer {

    private final MLResultProcessingService mlResultProcessingService;
    private final ModelPerformanceService modelPerformanceService;
    private final ResultValidationService resultValidationService;
    private final MLNotificationService mlNotificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter resultsProcessedCounter = Counter.builder("ml_results_processed_total")
            .description("Total number of ML anomaly results processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("ml_result_processing_duration")
            .description("Time taken to process ML anomaly results")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"anomaly-detection-results"},
        groupId = "ml-service-anomaly-results-processor",
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
    @CircuitBreaker(name = "ml-results-processor", fallbackMethod = "handleMLResultsProcessingFailure")
    @Retry(name = "ml-results-processor")
    public void processAnomalyDetectionResult(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.info("Processing ML anomaly result: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("ML result event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate result data
            AnomalyDetectionResultData resultData = extractResultData(event.getPayload());
            validateResultData(resultData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process ML result
            processMLResult(resultData, event);

            // Record successful processing metrics
            resultsProcessedCounter.increment();
            
            // Audit the result processing
            auditMLResultProcessing(resultData, event, "SUCCESS");

            log.info("Successfully processed ML result: {} for model: {} - accuracy: {}", 
                    eventId, resultData.getModelName(), resultData.getAccuracyScore());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid ML result event data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process ML result event: {}", eventId, e);
            auditMLResultProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("ML result event processing failed", e);

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

    private AnomalyDetectionResultData extractResultData(Map<String, Object> payload) throws JsonProcessingException {
        return AnomalyDetectionResultData.builder()
                .resultId(extractString(payload, "resultId"))
                .modelName(extractString(payload, "modelName"))
                .modelVersion(extractString(payload, "modelVersion"))
                .detectionResults(extractMap(payload, "detectionResults"))
                .anomaliesDetected(extractInteger(payload, "anomaliesDetected"))
                .totalSamples(extractInteger(payload, "totalSamples"))
                .accuracyScore(extractDouble(payload, "accuracyScore"))
                .precisionScore(extractDouble(payload, "precisionScore"))
                .recallScore(extractDouble(payload, "recallScore"))
                .f1Score(extractDouble(payload, "f1Score"))
                .falsePositiveRate(extractDouble(payload, "falsePositiveRate"))
                .truePositiveRate(extractDouble(payload, "truePositiveRate"))
                .performanceMetrics(extractMap(payload, "performanceMetrics"))
                .modelDiagnostics(extractMap(payload, "modelDiagnostics"))
                .processingTimeMs(extractLong(payload, "processingTimeMs"))
                .resultTimestamp(extractInstant(payload, "resultTimestamp"))
                .batchId(extractString(payload, "batchId"))
                .build();
    }

    private void validateResultData(AnomalyDetectionResultData resultData) {
        if (resultData.getResultId() == null || resultData.getResultId().trim().isEmpty()) {
            throw new IllegalArgumentException("Result ID is required");
        }
        
        if (resultData.getModelName() == null || resultData.getModelName().trim().isEmpty()) {
            throw new IllegalArgumentException("Model name is required");
        }
        
        if (resultData.getDetectionResults() == null || resultData.getDetectionResults().isEmpty()) {
            throw new IllegalArgumentException("Detection results are required");
        }
        
        if (resultData.getResultTimestamp() == null) {
            throw new IllegalArgumentException("Result timestamp is required");
        }
    }

    private void processMLResult(AnomalyDetectionResultData resultData, GenericKafkaEvent event) {
        log.info("Processing ML result - Model: {}, Anomalies: {}/{}, Accuracy: {}", 
                resultData.getModelName(), resultData.getAnomaliesDetected(),
                resultData.getTotalSamples(), resultData.getAccuracyScore());

        try {
            // Process detection results
            ResultProcessingOutcome outcome = mlResultProcessingService.processDetectionResults(resultData);

            // Update model performance metrics
            updateModelPerformance(resultData, outcome);

            // Validate result quality
            ResultValidationOutcome validation = validateResultQuality(resultData);

            // Send notifications if needed
            sendResultNotifications(resultData, validation);

            log.info("ML result processed - ResultId: {}, Quality: {}, Performance Updated: {}", 
                    resultData.getResultId(), validation.getQualityScore(), outcome.isPerformanceUpdated());

        } catch (Exception e) {
            log.error("Failed to process ML result for model: {}", resultData.getModelName(), e);
            throw new RuntimeException("ML result processing failed", e);
        }
    }

    private void updateModelPerformance(AnomalyDetectionResultData resultData, ResultProcessingOutcome outcome) {
        modelPerformanceService.updatePerformanceMetrics(
                resultData.getModelName(),
                resultData.getModelVersion(),
                resultData.getAccuracyScore(),
                resultData.getPrecisionScore(),
                resultData.getRecallScore(),
                resultData.getF1Score()
        );
    }

    private ResultValidationOutcome validateResultQuality(AnomalyDetectionResultData resultData) {
        return resultValidationService.validateResultQuality(
                resultData.getModelName(),
                resultData.getDetectionResults(),
                resultData.getPerformanceMetrics()
        );
    }

    private void sendResultNotifications(AnomalyDetectionResultData resultData, ResultValidationOutcome validation) {
        if (validation.getQualityScore() < 0.7) {
            mlNotificationService.sendLowQualityResultAlert(
                    "Low Quality ML Results Detected",
                    resultData,
                    validation
            );
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("ML result validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "ML_RESULT_VALIDATION_ERROR",
                null,
                "ML result validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditMLResultProcessing(AnomalyDetectionResultData resultData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "ML_RESULT_PROCESSED",
                    null,
                    String.format("ML result processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "resultId", resultData != null ? resultData.getResultId() : "unknown",
                            "modelName", resultData != null ? resultData.getModelName() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit ML result processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("ML result event sent to DLT - EventId: {}", event.getEventId());
    }

    public void handleMLResultsProcessingFailure(GenericKafkaEvent event, String topic, int partition,
                                               long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for ML results processing - EventId: {}", event.getEventId(), e);
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

    private Long extractLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
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
    public static class AnomalyDetectionResultData {
        private String resultId;
        private String modelName;
        private String modelVersion;
        private Map<String, Object> detectionResults;
        private Integer anomaliesDetected;
        private Integer totalSamples;
        private Double accuracyScore;
        private Double precisionScore;
        private Double recallScore;
        private Double f1Score;
        private Double falsePositiveRate;
        private Double truePositiveRate;
        private Map<String, Object> performanceMetrics;
        private Map<String, Object> modelDiagnostics;
        private Long processingTimeMs;
        private Instant resultTimestamp;
        private String batchId;
    }

    @lombok.Data
    @lombok.Builder
    public static class ResultProcessingOutcome {
        private boolean performanceUpdated;
        private String processingStatus;
    }

    @lombok.Data
    @lombok.Builder
    public static class ResultValidationOutcome {
        private Double qualityScore;
        private boolean valid;
        private List<String> validationIssues;
    }
}