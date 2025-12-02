package com.waqiti.analytics.kafka;

import com.waqiti.analytics.domain.AnomalyAlert;
import com.waqiti.analytics.repository.AnomalyAlertRepository;
import com.waqiti.analytics.service.AnomalyAnalyticsService;
import com.waqiti.analytics.service.AnomalyTrendService;
import com.waqiti.analytics.service.AnomalyScoreAnalyticsService;
import com.waqiti.analytics.service.PatternRecognitionService;
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
public class AnomalyAlertsConsumer {

    private final AnomalyAlertRepository anomalyAlertRepository;
    private final AnomalyAnalyticsService anomalyAnalyticsService;
    private final AnomalyTrendService anomalyTrendService;
    private final AnomalyScoreAnalyticsService anomalyScoreAnalyticsService;
    private final PatternRecognitionService patternRecognitionService;
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
        successCounter = Counter.builder("anomaly_alerts_processed_total")
            .description("Total number of successfully processed anomaly alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("anomaly_alerts_errors_total")
            .description("Total number of anomaly alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("anomaly_alerts_processing_duration")
            .description("Time taken to process anomaly alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"anomaly-alerts", "ml-anomaly-alerts", "behavioral-anomaly-alerts", "transaction-anomaly-alerts"},
        groupId = "analytics-anomaly-alerts-service-group",
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
    @CircuitBreaker(name = "anomaly-alerts", fallbackMethod = "handleAnomalyAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAnomalyAlertEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("anomaly-alert-%s-p%d-o%d", alertId, partition, offset);
        String eventKey = String.format("%s-%s-%s", alertId, event.get("anomalyType"), event.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing anomaly alert: alertId={}, type={}, score={}",
                alertId, event.get("anomalyType"), event.get("anomalyScore"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process the anomaly alert for analytics
            processAnomalyAlertForAnalytics(event, correlationId);

            // Analyze anomaly patterns and trends
            analyzeAnomalyPatterns(event, correlationId);

            // Update anomaly scoring models
            updateAnomalyScoring(event, correlationId);

            // Generate anomaly insights
            generateAnomalyInsights(event, correlationId);

            // Check for anomaly threshold breaches
            checkAnomalyThresholds(event, correlationId);

            // Detect anomaly correlations
            detectAnomalyCorrelations(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ANOMALY_ALERT_PROCESSED", alertId,
                Map.of("anomalyType", event.get("anomalyType"), "anomalyScore", event.get("anomalyScore"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process anomaly alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("anomaly-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAnomalyAlertEventFallback(
            Map<String, Object> event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("anomaly-alert-fallback-%s-p%d-o%d", alertId, partition, offset);

        log.error("Circuit breaker fallback triggered for anomaly alert: alertId={}, error={}",
            alertId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("anomaly-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Anomaly Alerts Circuit Breaker Triggered",
                String.format("Anomaly alert %s analytics processing failed: %s", alertId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAnomalyAlertEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("dlt-anomaly-alert-%s-%d", alertId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Anomaly alert permanently failed: alertId={}, topic={}, error={}",
            alertId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ANOMALY_ALERT_DLT_EVENT", alertId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.get("anomalyType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Anomaly Alert Dead Letter Event",
                String.format("Anomaly alert %s analytics sent to DLT: %s", alertId, exceptionMessage),
                Map.of("alertId", alertId, "topic", topic, "correlationId", correlationId)
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

    private void processAnomalyAlertForAnalytics(Map<String, Object> event, String correlationId) {
        String alertId = String.valueOf(event.get("alertId"));
        String anomalyType = String.valueOf(event.get("anomalyType"));
        String userId = String.valueOf(event.get("userId"));
        String entityType = String.valueOf(event.get("entityType"));
        String entityId = String.valueOf(event.get("entityId"));
        String detectionMethod = String.valueOf(event.get("detectionMethod"));
        Double anomalyScore = event.get("anomalyScore") != null ? ((Number) event.get("anomalyScore")).doubleValue() : 0.0;
        String severity = determineSeverity(anomalyScore);

        AnomalyAlert anomalyAlert = AnomalyAlert.builder()
            .id(UUID.randomUUID().toString())
            .alertId(alertId)
            .anomalyType(anomalyType)
            .userId(userId)
            .entityType(entityType)
            .entityId(entityId)
            .detectionMethod(detectionMethod)
            .anomalyScore(anomalyScore)
            .severity(severity)
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .features((Map<String, Object>) event.get("features"))
            .metadata((Map<String, Object>) event.get("metadata"))
            .build();

        anomalyAlertRepository.save(anomalyAlert);

        // Record analytics metrics
        metricsService.recordAnomalyAlert(anomalyType, severity, detectionMethod);

        log.info("Anomaly alert analytics recorded: alertId={}, type={}, score={}", alertId, anomalyType, anomalyScore);
    }

    private void analyzeAnomalyPatterns(Map<String, Object> event, String correlationId) {
        String anomalyType = String.valueOf(event.get("anomalyType"));
        String userId = String.valueOf(event.get("userId"));
        String entityType = String.valueOf(event.get("entityType"));
        Double anomalyScore = event.get("anomalyScore") != null ? ((Number) event.get("anomalyScore")).doubleValue() : 0.0;

        // Analyze temporal patterns
        anomalyTrendService.analyzeAnomalyTrends(anomalyType, userId, anomalyScore);

        // Update pattern recognition for anomalies
        patternRecognitionService.updateAnomalyPatterns(anomalyType, event);

        // Detect anomaly clusters
        List<String> relatedAnomalies = patternRecognitionService.detectAnomalyClusters(userId, entityType);
        if (!relatedAnomalies.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "ANOMALY_CLUSTER_DETECTED",
                "userId", userId,
                "entityType", entityType,
                "anomalyType", anomalyType,
                "relatedAnomalies", relatedAnomalies,
                "clusterRiskScore", calculateClusterRiskScore(relatedAnomalies, anomalyScore),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Check for escalating anomaly patterns
        double escalationScore = anomalyTrendService.calculateEscalationScore(userId, anomalyType);
        if (escalationScore > 0.7) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "ESCALATING_ANOMALY_PATTERN",
                "userId", userId,
                "anomalyType", anomalyType,
                "escalationScore", escalationScore,
                "currentScore", anomalyScore,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Anomaly patterns analyzed: type={}, escalationScore={}", anomalyType, escalationScore);
    }

    private void updateAnomalyScoring(Map<String, Object> event, String correlationId) {
        String anomalyType = String.valueOf(event.get("anomalyType"));
        String userId = String.valueOf(event.get("userId"));
        String detectionMethod = String.valueOf(event.get("detectionMethod"));
        Double anomalyScore = event.get("anomalyScore") != null ? ((Number) event.get("anomalyScore")).doubleValue() : 0.0;

        // Update anomaly score analytics
        anomalyScoreAnalyticsService.updateScoreDistribution(anomalyType, anomalyScore);

        // Update user anomaly profile
        anomalyScoreAnalyticsService.updateUserAnomalyProfile(userId, anomalyType, anomalyScore);

        // Update detection method effectiveness
        anomalyScoreAnalyticsService.updateDetectionMethodMetrics(detectionMethod, anomalyScore);

        // Generate score-based insights
        Map<String, Object> scoreInsights = anomalyScoreAnalyticsService.generateScoreInsights(anomalyType, anomalyScore);
        if (!scoreInsights.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "ANOMALY_SCORE_INSIGHTS",
                "anomalyType", anomalyType,
                "detectionMethod", detectionMethod,
                "scoreInsights", scoreInsights,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Anomaly scoring updated: type={}, method={}, score={}", anomalyType, detectionMethod, anomalyScore);
    }

    private void generateAnomalyInsights(Map<String, Object> event, String correlationId) {
        String anomalyType = String.valueOf(event.get("anomalyType"));
        String userId = String.valueOf(event.get("userId"));
        String entityType = String.valueOf(event.get("entityType"));
        Double anomalyScore = event.get("anomalyScore") != null ? ((Number) event.get("anomalyScore")).doubleValue() : 0.0;

        // Generate behavioral insights
        Map<String, Object> behavioralInsights = anomalyAnalyticsService.generateBehavioralInsights(userId, anomalyType);

        // Calculate anomaly impact
        double impactScore = calculateAnomalyImpact(event);

        // Generate remediation recommendations
        List<String> recommendations = generateAnomalyRecommendations(anomalyType, anomalyScore, impactScore);

        // Generate predictive insights
        Map<String, Object> predictiveInsights = patternRecognitionService.generateAnomalyPredictions(userId, entityType);

        kafkaTemplate.send("real-time-analytics", Map.of(
            "analyticsType", "ANOMALY_INSIGHTS",
            "alertId", event.get("alertId"),
            "userId", userId,
            "entityType", entityType,
            "anomalyType", anomalyType,
            "anomalyScore", anomalyScore,
            "impactScore", impactScore,
            "behavioralInsights", behavioralInsights,
            "predictiveInsights", predictiveInsights,
            "recommendations", recommendations,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Anomaly insights generated: alertId={}, impact={}", event.get("alertId"), impactScore);
    }

    private void checkAnomalyThresholds(Map<String, Object> event, String correlationId) {
        String anomalyType = String.valueOf(event.get("anomalyType"));
        String userId = String.valueOf(event.get("userId"));
        Double anomalyScore = event.get("anomalyScore") != null ? ((Number) event.get("anomalyScore")).doubleValue() : 0.0;

        // Check score threshold breaches
        double criticalThreshold = getCriticalThreshold(anomalyType);
        if (anomalyScore > criticalThreshold) {
            kafkaTemplate.send("analytics-alerts", Map.of(
                "alertType", "CRITICAL_ANOMALY_THRESHOLD_BREACH",
                "anomalyType", anomalyType,
                "userId", userId,
                "currentScore", anomalyScore,
                "threshold", criticalThreshold,
                "severity", "CRITICAL",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Check frequency thresholds
        int anomalyFrequency = anomalyTrendService.getRecentAnomalyFrequency(userId, anomalyType);
        int frequencyThreshold = getFrequencyThreshold(anomalyType);
        if (anomalyFrequency > frequencyThreshold) {
            kafkaTemplate.send("analytics-alerts", Map.of(
                "alertType", "HIGH_ANOMALY_FREQUENCY",
                "anomalyType", anomalyType,
                "userId", userId,
                "frequency", anomalyFrequency,
                "threshold", frequencyThreshold,
                "severity", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Anomaly thresholds checked: type={}, score={}, frequency={}", anomalyType, anomalyScore, anomalyFrequency);
    }

    private void detectAnomalyCorrelations(Map<String, Object> event, String correlationId) {
        String anomalyType = String.valueOf(event.get("anomalyType"));
        String userId = String.valueOf(event.get("userId"));
        String entityType = String.valueOf(event.get("entityType"));

        // Detect cross-entity correlations
        Map<String, Object> correlations = anomalyAnalyticsService.detectCrossEntityCorrelations(userId, entityType, anomalyType);
        if (!correlations.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "ANOMALY_CORRELATIONS",
                "userId", userId,
                "entityType", entityType,
                "anomalyType", anomalyType,
                "correlations", correlations,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Detect temporal correlations
        List<String> temporalCorrelations = anomalyAnalyticsService.detectTemporalCorrelations(anomalyType);
        if (!temporalCorrelations.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "TEMPORAL_ANOMALY_CORRELATIONS",
                "anomalyType", anomalyType,
                "temporalCorrelations", temporalCorrelations,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Anomaly correlations detected: type={}, crossEntity={}, temporal={}",
            anomalyType, !correlations.isEmpty(), !temporalCorrelations.isEmpty());
    }

    private String determineSeverity(Double anomalyScore) {
        if (anomalyScore >= 0.9) return "CRITICAL";
        if (anomalyScore >= 0.7) return "HIGH";
        if (anomalyScore >= 0.5) return "MEDIUM";
        if (anomalyScore >= 0.3) return "LOW";
        return "INFO";
    }

    private double calculateClusterRiskScore(List<String> relatedAnomalies, Double currentScore) {
        return Math.min(currentScore + (relatedAnomalies.size() * 0.1), 1.0);
    }

    private double calculateAnomalyImpact(Map<String, Object> event) {
        Double anomalyScore = event.get("anomalyScore") != null ? ((Number) event.get("anomalyScore")).doubleValue() : 0.0;
        String entityType = String.valueOf(event.get("entityType"));

        double impact = anomalyScore;

        // Adjust impact based on entity type
        switch (entityType.toUpperCase()) {
            case "TRANSACTION": impact *= 1.2; break;
            case "ACCOUNT": impact *= 1.1; break;
            case "USER": impact *= 1.3; break;
            case "PAYMENT": impact *= 1.25; break;
        }

        return Math.min(impact, 1.0);
    }

    private List<String> generateAnomalyRecommendations(String anomalyType, Double anomalyScore, double impactScore) {
        List<String> recommendations = new ArrayList<>();

        if (anomalyScore > 0.8) {
            recommendations.add("IMMEDIATE_INVESTIGATION_REQUIRED");
            recommendations.add("ESCALATE_TO_FRAUD_TEAM");
        }

        if (impactScore > 0.7) {
            recommendations.add("ENHANCED_MONITORING");
            recommendations.add("ALERT_RISK_MANAGEMENT");
        }

        if (anomalyType.contains("TRANSACTION")) {
            recommendations.add("REVIEW_TRANSACTION_PATTERNS");
            recommendations.add("CHECK_ACCOUNT_VELOCITY");
        }

        if (anomalyType.contains("BEHAVIORAL")) {
            recommendations.add("ANALYZE_USER_BEHAVIOR");
            recommendations.add("UPDATE_RISK_PROFILE");
        }

        return recommendations;
    }

    private double getCriticalThreshold(String anomalyType) {
        switch (anomalyType.toUpperCase()) {
            case "TRANSACTION_ANOMALY": return 0.85;
            case "BEHAVIORAL_ANOMALY": return 0.8;
            case "PATTERN_ANOMALY": return 0.9;
            case "VELOCITY_ANOMALY": return 0.75;
            default: return 0.8;
        }
    }

    private int getFrequencyThreshold(String anomalyType) {
        switch (anomalyType.toUpperCase()) {
            case "TRANSACTION_ANOMALY": return 5;
            case "BEHAVIORAL_ANOMALY": return 3;
            case "PATTERN_ANOMALY": return 4;
            case "VELOCITY_ANOMALY": return 6;
            default: return 5;
        }
    }
}