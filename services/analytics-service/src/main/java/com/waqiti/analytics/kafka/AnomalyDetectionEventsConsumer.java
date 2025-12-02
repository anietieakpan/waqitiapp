package com.waqiti.analytics.kafka;

import com.waqiti.analytics.domain.AnomalyDetection;
import com.waqiti.analytics.repository.AnomalyDetectionRepository;
import com.waqiti.analytics.service.AnomalyDetectionAnalyticsService;
import com.waqiti.analytics.service.MachineLearningAnalyticsService;
import com.waqiti.analytics.service.AnomalyPatternService;
import com.waqiti.analytics.service.BehaviorAnalyticsService;
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
public class AnomalyDetectionEventsConsumer {

    private final AnomalyDetectionRepository anomalyDetectionRepository;
    private final AnomalyDetectionAnalyticsService anomalyDetectionAnalyticsService;
    private final MachineLearningAnalyticsService mlAnalyticsService;
    private final AnomalyPatternService anomalyPatternService;
    private final BehaviorAnalyticsService behaviorAnalyticsService;
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
        successCounter = Counter.builder("anomaly_detection_events_processed_total")
            .description("Total number of successfully processed anomaly detection events")
            .register(meterRegistry);
        errorCounter = Counter.builder("anomaly_detection_events_errors_total")
            .description("Total number of anomaly detection event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("anomaly_detection_events_processing_duration")
            .description("Time taken to process anomaly detection events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"anomaly-detection-events", "ml-anomaly-detection", "behavioral-anomaly-detection", "pattern-anomaly-detection"},
        groupId = "analytics-anomaly-detection-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "anomaly-detection-events", fallbackMethod = "handleAnomalyDetectionEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAnomalyDetectionEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String detectionId = String.valueOf(event.get("detectionId"));
        String correlationId = String.format("anomaly-detection-%s-p%d-o%d", detectionId, partition, offset);
        String eventKey = String.format("%s-%s-%s", detectionId, event.get("detectionType"), event.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing anomaly detection: detectionId={}, type={}, confidence={}",
                detectionId, event.get("detectionType"), event.get("confidence"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String detectionType = String.valueOf(event.get("detectionType"));
            switch (detectionType) {
                case "ML_DETECTION":
                    processMachineLearningDetection(event, correlationId);
                    break;

                case "BEHAVIORAL_DETECTION":
                    processBehavioralDetection(event, correlationId);
                    break;

                case "PATTERN_DETECTION":
                    processPatternDetection(event, correlationId);
                    break;

                case "STATISTICAL_DETECTION":
                    processStatisticalDetection(event, correlationId);
                    break;

                case "RULE_BASED_DETECTION":
                    processRuleBasedDetection(event, correlationId);
                    break;

                case "ENSEMBLE_DETECTION":
                    processEnsembleDetection(event, correlationId);
                    break;

                default:
                    processGenericDetection(event, correlationId);
                    break;
            }

            // Analyze detection effectiveness
            analyzeDetectionEffectiveness(event, correlationId);

            // Update detection models
            updateDetectionModels(event, correlationId);

            // Generate detection insights
            generateDetectionInsights(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ANOMALY_DETECTION_EVENT_PROCESSED", detectionId,
                Map.of("detectionType", detectionType, "confidence", event.get("confidence"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process anomaly detection event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("anomaly-detection-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAnomalyDetectionEventFallback(
            Map<String, Object> event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String detectionId = String.valueOf(event.get("detectionId"));
        String correlationId = String.format("anomaly-detection-fallback-%s-p%d-o%d", detectionId, partition, offset);

        log.error("Circuit breaker fallback triggered for anomaly detection: detectionId={}, error={}",
            detectionId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("anomaly-detection-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Anomaly Detection Events Circuit Breaker Triggered",
                String.format("Detection %s analytics processing failed: %s", detectionId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAnomalyDetectionEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String detectionId = String.valueOf(event.get("detectionId"));
        String correlationId = String.format("dlt-anomaly-detection-%s-%d", detectionId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Anomaly detection permanently failed: detectionId={}, topic={}, error={}",
            detectionId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ANOMALY_DETECTION_DLT_EVENT", detectionId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.get("detectionType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Anomaly Detection Dead Letter Event",
                String.format("Detection %s analytics sent to DLT: %s", detectionId, exceptionMessage),
                Map.of("detectionId", detectionId, "topic", topic, "correlationId", correlationId)
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

    private void processMachineLearningDetection(Map<String, Object> event, String correlationId) {
        String modelName = String.valueOf(event.get("modelName"));
        String modelVersion = String.valueOf(event.get("modelVersion"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;
        Map<String, Object> features = (Map<String, Object>) event.get("features");

        // Record ML detection analytics
        saveDetectionEvent(event, "ML_DETECTION", correlationId);

        // Update ML model analytics
        mlAnalyticsService.recordDetection(modelName, modelVersion, confidence, features);

        // Analyze feature importance
        Map<String, Double> featureImportance = mlAnalyticsService.analyzeFeatureImportance(modelName, features);

        // Track model drift
        double driftScore = mlAnalyticsService.calculateModelDrift(modelName, features);
        if (driftScore > 0.3) {
            kafkaTemplate.send("ml-alerts", Map.of(
                "alertType", "MODEL_DRIFT_DETECTED",
                "modelName", modelName,
                "driftScore", driftScore,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update model performance
        mlAnalyticsService.updateModelPerformance(modelName, confidence);

        log.info("ML detection processed: model={}, confidence={}, drift={}", modelName, confidence, driftScore);
    }

    private void processBehavioralDetection(Map<String, Object> event, String correlationId) {
        String userId = String.valueOf(event.get("userId"));
        String behaviorType = String.valueOf(event.get("behaviorType"));
        String deviation = String.valueOf(event.get("deviation"));
        Double anomalyScore = event.get("anomalyScore") != null ? ((Number) event.get("anomalyScore")).doubleValue() : 0.0;

        // Record behavioral detection
        saveDetectionEvent(event, "BEHAVIORAL_DETECTION", correlationId);

        // Update user behavior profile
        behaviorAnalyticsService.updateBehaviorProfile(userId, behaviorType, deviation, anomalyScore);

        // Analyze behavior patterns
        Map<String, Object> behaviorInsights = behaviorAnalyticsService.analyzeBehaviorPatterns(userId, behaviorType);

        // Check for behavior escalation
        double escalationRisk = behaviorAnalyticsService.calculateEscalationRisk(userId, behaviorType);
        if (escalationRisk > 0.7) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "BEHAVIOR_ESCALATION_RISK",
                "userId", userId,
                "behaviorType", behaviorType,
                "escalationRisk", escalationRisk,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Behavioral detection processed: user={}, type={}, score={}", userId, behaviorType, anomalyScore);
    }

    private void processPatternDetection(Map<String, Object> event, String correlationId) {
        String patternType = String.valueOf(event.get("patternType"));
        String patternId = String.valueOf(event.get("patternId"));
        Double matchScore = event.get("matchScore") != null ? ((Number) event.get("matchScore")).doubleValue() : 0.0;
        List<String> entities = (List<String>) event.getOrDefault("entities", new ArrayList<>());

        // Record pattern detection
        saveDetectionEvent(event, "PATTERN_DETECTION", correlationId);

        // Update pattern analytics
        anomalyPatternService.recordPatternMatch(patternType, patternId, matchScore, entities);

        // Analyze pattern evolution
        Map<String, Object> patternEvolution = anomalyPatternService.analyzePatternEvolution(patternType);

        // Detect pattern chains
        List<String> patternChain = anomalyPatternService.detectPatternChains(patternId, entities);
        if (!patternChain.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "PATTERN_CHAIN_DETECTED",
                "patternType", patternType,
                "patternChain", patternChain,
                "chainComplexity", patternChain.size(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Pattern detection processed: type={}, score={}, chain={}", patternType, matchScore, patternChain.size());
    }

    private void processStatisticalDetection(Map<String, Object> event, String correlationId) {
        String statisticalMethod = String.valueOf(event.get("statisticalMethod"));
        Double zscore = event.get("zscore") != null ? ((Number) event.get("zscore")).doubleValue() : 0.0;
        Double pvalue = event.get("pvalue") != null ? ((Number) event.get("pvalue")).doubleValue() : 0.0;
        String variable = String.valueOf(event.get("variable"));

        // Record statistical detection
        saveDetectionEvent(event, "STATISTICAL_DETECTION", correlationId);

        // Update statistical analytics
        anomalyDetectionAnalyticsService.recordStatisticalDetection(statisticalMethod, zscore, pvalue, variable);

        // Analyze statistical trends
        Map<String, Object> trends = anomalyDetectionAnalyticsService.analyzeStatisticalTrends(statisticalMethod, variable);

        // Check for statistical significance
        if (Math.abs(zscore) > 3.0 && pvalue < 0.001) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "STATISTICALLY_SIGNIFICANT_ANOMALY",
                "statisticalMethod", statisticalMethod,
                "variable", variable,
                "zscore", zscore,
                "pvalue", pvalue,
                "significance", "EXTREMELY_HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Statistical detection processed: method={}, zscore={}, pvalue={}", statisticalMethod, zscore, pvalue);
    }

    private void processRuleBasedDetection(Map<String, Object> event, String correlationId) {
        String ruleName = String.valueOf(event.get("ruleName"));
        String ruleVersion = String.valueOf(event.get("ruleVersion"));
        List<String> triggeredConditions = (List<String>) event.getOrDefault("triggeredConditions", new ArrayList<>());
        Map<String, Object> ruleContext = (Map<String, Object>) event.getOrDefault("ruleContext", new HashMap<>());

        // Record rule-based detection
        saveDetectionEvent(event, "RULE_BASED_DETECTION", correlationId);

        // Update rule analytics
        anomalyDetectionAnalyticsService.recordRuleDetection(ruleName, ruleVersion, triggeredConditions, ruleContext);

        // Analyze rule effectiveness
        double ruleEffectiveness = anomalyDetectionAnalyticsService.calculateRuleEffectiveness(ruleName);

        // Check for rule conflicts
        List<String> conflictingRules = anomalyDetectionAnalyticsService.detectRuleConflicts(ruleName, triggeredConditions);
        if (!conflictingRules.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "RULE_CONFLICTS_DETECTED",
                "ruleName", ruleName,
                "conflictingRules", conflictingRules,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Rule-based detection processed: rule={}, effectiveness={}, conflicts={}",
            ruleName, ruleEffectiveness, conflictingRules.size());
    }

    private void processEnsembleDetection(Map<String, Object> event, String correlationId) {
        List<Map<String, Object>> modelResults = (List<Map<String, Object>>) event.getOrDefault("modelResults", new ArrayList<>());
        String aggregationMethod = String.valueOf(event.get("aggregationMethod"));
        Double ensembleScore = event.get("ensembleScore") != null ? ((Number) event.get("ensembleScore")).doubleValue() : 0.0;
        Double consensus = event.get("consensus") != null ? ((Number) event.get("consensus")).doubleValue() : 0.0;

        // Record ensemble detection
        saveDetectionEvent(event, "ENSEMBLE_DETECTION", correlationId);

        // Update ensemble analytics
        anomalyDetectionAnalyticsService.recordEnsembleDetection(aggregationMethod, ensembleScore, consensus, modelResults);

        // Analyze model agreement
        Map<String, Object> agreementAnalysis = anomalyDetectionAnalyticsService.analyzeModelAgreement(modelResults);

        // Check for low consensus
        if (consensus < 0.6) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "LOW_ENSEMBLE_CONSENSUS",
                "aggregationMethod", aggregationMethod,
                "consensus", consensus,
                "threshold", 0.6,
                "modelResults", modelResults.size(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Ensemble detection processed: method={}, score={}, consensus={}",
            aggregationMethod, ensembleScore, consensus);
    }

    private void processGenericDetection(Map<String, Object> event, String correlationId) {
        String detectionType = String.valueOf(event.get("detectionType"));

        // Record generic detection
        saveDetectionEvent(event, detectionType, correlationId);

        // Update generic analytics
        anomalyDetectionAnalyticsService.recordGenericDetection(detectionType, event);

        log.info("Generic detection processed: type={}", detectionType);
    }

    private void saveDetectionEvent(Map<String, Object> event, String detectionType, String correlationId) {
        String detectionId = String.valueOf(event.get("detectionId"));
        String userId = String.valueOf(event.get("userId"));
        String entityType = String.valueOf(event.get("entityType"));
        String entityId = String.valueOf(event.get("entityId"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;

        AnomalyDetection detection = AnomalyDetection.builder()
            .id(UUID.randomUUID().toString())
            .detectionId(detectionId)
            .detectionType(detectionType)
            .userId(userId)
            .entityType(entityType)
            .entityId(entityId)
            .confidence(confidence)
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .features((Map<String, Object>) event.get("features"))
            .metadata((Map<String, Object>) event.get("metadata"))
            .build();

        anomalyDetectionRepository.save(detection);

        // Record metrics
        metricsService.recordAnomalyDetection(detectionType, confidence);
    }

    private void analyzeDetectionEffectiveness(Map<String, Object> event, String correlationId) {
        String detectionType = String.valueOf(event.get("detectionType"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;

        // Calculate detection effectiveness metrics
        Map<String, Object> effectiveness = anomalyDetectionAnalyticsService.calculateDetectionEffectiveness(detectionType);

        // Generate effectiveness insights
        if (!effectiveness.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "DETECTION_EFFECTIVENESS",
                "detectionType", detectionType,
                "effectiveness", effectiveness,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Detection effectiveness analyzed: type={}, confidence={}", detectionType, confidence);
    }

    private void updateDetectionModels(Map<String, Object> event, String correlationId) {
        String detectionType = String.valueOf(event.get("detectionType"));
        Map<String, Object> features = (Map<String, Object>) event.getOrDefault("features", new HashMap<>());

        // Update detection model parameters
        anomalyDetectionAnalyticsService.updateDetectionModels(detectionType, features, event);

        // Generate model update insights
        kafkaTemplate.send("ml-model-updates", Map.of(
            "updateType", "DETECTION_MODEL_UPDATE",
            "detectionType", detectionType,
            "features", features,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Detection models updated: type={}", detectionType);
    }

    private void generateDetectionInsights(Map<String, Object> event, String correlationId) {
        String detectionType = String.valueOf(event.get("detectionType"));
        String entityType = String.valueOf(event.get("entityType"));
        Double confidence = event.get("confidence") != null ? ((Number) event.get("confidence")).doubleValue() : 0.0;

        // Generate real-time detection insights
        Map<String, Object> insights = anomalyDetectionAnalyticsService.generateDetectionInsights(detectionType, entityType, confidence);

        kafkaTemplate.send("real-time-analytics", Map.of(
            "analyticsType", "DETECTION_INSIGHTS",
            "detectionId", event.get("detectionId"),
            "detectionType", detectionType,
            "entityType", entityType,
            "confidence", confidence,
            "insights", insights,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Detection insights generated: type={}, confidence={}", detectionType, confidence);
    }
}