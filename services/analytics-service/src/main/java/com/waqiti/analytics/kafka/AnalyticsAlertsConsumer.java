package com.waqiti.analytics.kafka;

import com.waqiti.common.events.AmlAlertEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.analytics.domain.AlertAnalytics;
import com.waqiti.analytics.repository.AlertAnalyticsRepository;
import com.waqiti.analytics.service.AlertTrendService;
import com.waqiti.analytics.service.RiskScoreAnalyticsService;
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
public class AnalyticsAlertsConsumer {

    private final AlertAnalyticsRepository alertAnalyticsRepository;
    private final AlertTrendService alertTrendService;
    private final RiskScoreAnalyticsService riskScoreAnalyticsService;
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
        successCounter = Counter.builder("analytics_alerts_processed_total")
            .description("Total number of successfully processed analytics alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("analytics_alerts_errors_total")
            .description("Total number of analytics alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("analytics_alerts_processing_duration")
            .description("Time taken to process analytics alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"analytics-alerts", "fraud-alert-events", "aml-alert-events", "compliance-alerts"},
        groupId = "analytics-alerts-service-group",
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
    @CircuitBreaker(name = "analytics-alerts", fallbackMethod = "handleAnalyticsAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAnalyticsAlertEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("analytics-alert-%s-p%d-o%d", alertId, partition, offset);
        String eventKey = String.format("%s-%s-%s", alertId, event.get("alertType"), event.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing analytics alert: alertId={}, type={}, severity={}",
                alertId, event.get("alertType"), event.get("severity"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String alertType = String.valueOf(event.get("alertType"));
            String severity = String.valueOf(event.get("severity"));

            // Process the alert for analytics
            processAlertForAnalytics(event, correlationId);

            // Analyze alert patterns and trends
            analyzeAlertPatterns(event, correlationId);

            // Update risk scoring models
            updateRiskScoringModels(event, correlationId);

            // Generate real-time insights
            generateRealTimeInsights(event, correlationId);

            // Check for alert threshold breaches
            checkAlertThresholds(alertType, severity, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ANALYTICS_ALERT_PROCESSED", alertId,
                Map.of("alertType", alertType, "severity", severity,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process analytics alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("analytics-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAnalyticsAlertEventFallback(
            Map<String, Object> event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("analytics-alert-fallback-%s-p%d-o%d", alertId, partition, offset);

        log.error("Circuit breaker fallback triggered for analytics alert: alertId={}, error={}",
            alertId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("analytics-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Analytics Alerts Circuit Breaker Triggered",
                String.format("Alert %s analytics processing failed: %s", alertId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAnalyticsAlertEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("dlt-analytics-alert-%s-%d", alertId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Analytics alert permanently failed: alertId={}, topic={}, error={}",
            alertId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ANALYTICS_ALERT_DLT_EVENT", alertId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.get("alertType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Analytics Alert Dead Letter Event",
                String.format("Alert %s analytics sent to DLT: %s", alertId, exceptionMessage),
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

    private void processAlertForAnalytics(Map<String, Object> event, String correlationId) {
        String alertId = String.valueOf(event.get("alertId"));
        String alertType = String.valueOf(event.get("alertType"));
        String severity = String.valueOf(event.get("severity"));
        String userId = String.valueOf(event.get("userId"));
        String source = String.valueOf(event.get("source"));
        Double riskScore = event.get("riskScore") != null ? ((Number) event.get("riskScore")).doubleValue() : 0.0;

        AlertAnalytics analytics = AlertAnalytics.builder()
            .id(UUID.randomUUID().toString())
            .alertId(alertId)
            .alertType(alertType)
            .severity(severity)
            .userId(userId)
            .source(source)
            .riskScore(riskScore)
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .metadata((Map<String, Object>) event.get("metadata"))
            .build();

        alertAnalyticsRepository.save(analytics);

        // Record analytics metrics
        metricsService.recordAlert(alertType, severity, source);

        log.info("Alert analytics recorded: alertId={}, type={}, score={}", alertId, alertType, riskScore);
    }

    private void analyzeAlertPatterns(Map<String, Object> event, String correlationId) {
        String alertType = String.valueOf(event.get("alertType"));
        String userId = String.valueOf(event.get("userId"));
        Double riskScore = event.get("riskScore") != null ? ((Number) event.get("riskScore")).doubleValue() : 0.0;

        // Analyze temporal patterns
        alertTrendService.analyzeAlertTrends(alertType, userId, riskScore);

        // Update pattern recognition models
        patternRecognitionService.updateAlertPatterns(alertType, event);

        // Detect alert clustering
        List<String> relatedAlerts = patternRecognitionService.detectAlertClusters(userId, alertType);
        if (!relatedAlerts.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "ALERT_CLUSTER_DETECTED",
                "userId", userId,
                "alertType", alertType,
                "relatedAlerts", relatedAlerts,
                "clusterRiskScore", calculateClusterRiskScore(relatedAlerts),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Check for unusual alert frequency
        int alertFrequency = alertTrendService.getRecentAlertFrequency(userId, alertType);
        if (alertFrequency > getAlertFrequencyThreshold(alertType)) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "HIGH_ALERT_FREQUENCY",
                "userId", userId,
                "alertType", alertType,
                "frequency", alertFrequency,
                "threshold", getAlertFrequencyThreshold(alertType),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Alert patterns analyzed: type={}, frequency={}", alertType, alertFrequency);
    }

    private void updateRiskScoringModels(Map<String, Object> event, String correlationId) {
        String alertType = String.valueOf(event.get("alertType"));
        String userId = String.valueOf(event.get("userId"));
        Double riskScore = event.get("riskScore") != null ? ((Number) event.get("riskScore")).doubleValue() : 0.0;

        // Update user risk profile
        riskScoreAnalyticsService.updateUserRiskProfile(userId, alertType, riskScore);

        // Update model performance metrics
        riskScoreAnalyticsService.updateModelMetrics(alertType, riskScore);

        // Generate risk score insights
        Map<String, Object> riskInsights = riskScoreAnalyticsService.generateRiskInsights(userId, alertType);
        if (!riskInsights.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "RISK_SCORE_UPDATE",
                "userId", userId,
                "alertType", alertType,
                "riskInsights", riskInsights,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Risk scoring models updated: userId={}, type={}, score={}", userId, alertType, riskScore);
    }

    private void generateRealTimeInsights(Map<String, Object> event, String correlationId) {
        String alertType = String.valueOf(event.get("alertType"));
        String severity = String.valueOf(event.get("severity"));
        String userId = String.valueOf(event.get("userId"));

        // Generate predictive insights
        Map<String, Object> predictiveInsights = patternRecognitionService.generatePredictiveInsights(userId, alertType);

        // Calculate alert impact score
        double impactScore = calculateAlertImpactScore(event);

        // Generate recommendations
        List<String> recommendations = generateAlertRecommendations(alertType, severity, impactScore);

        kafkaTemplate.send("real-time-analytics", Map.of(
            "analyticsType", "ALERT_INSIGHTS",
            "alertId", event.get("alertId"),
            "userId", userId,
            "alertType", alertType,
            "severity", severity,
            "impactScore", impactScore,
            "predictiveInsights", predictiveInsights,
            "recommendations", recommendations,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Real-time insights generated: alertId={}, impact={}", event.get("alertId"), impactScore);
    }

    private void checkAlertThresholds(String alertType, String severity, String correlationId) {
        // Check hourly alert volume
        int hourlyVolume = alertTrendService.getHourlyAlertVolume(alertType);
        int hourlyThreshold = getHourlyThreshold(alertType);

        if (hourlyVolume > hourlyThreshold) {
            kafkaTemplate.send("analytics-alerts", Map.of(
                "alertType", "THRESHOLD_BREACH",
                "thresholdType", "HOURLY_VOLUME",
                "alertCategory", alertType,
                "currentValue", hourlyVolume,
                "threshold", hourlyThreshold,
                "severity", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Check severity distribution
        Map<String, Integer> severityDistribution = alertTrendService.getSeverityDistribution(alertType);
        if (isSeverityDistributionAnomalous(severityDistribution)) {
            kafkaTemplate.send("analytics-alerts", Map.of(
                "alertType", "ANOMALOUS_SEVERITY_DISTRIBUTION",
                "alertCategory", alertType,
                "severityDistribution", severityDistribution,
                "severity", "MEDIUM",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Alert thresholds checked: type={}, hourlyVolume={}", alertType, hourlyVolume);
    }

    private double calculateClusterRiskScore(List<String> relatedAlerts) {
        // Calculate aggregate risk score for alert cluster
        return relatedAlerts.size() * 0.1 + Math.min(relatedAlerts.size() * 0.05, 0.5);
    }

    private int getAlertFrequencyThreshold(String alertType) {
        switch (alertType.toUpperCase()) {
            case "FRAUD": return 5;
            case "AML": return 3;
            case "COMPLIANCE": return 4;
            case "SECURITY": return 6;
            default: return 10;
        }
    }

    private int getHourlyThreshold(String alertType) {
        switch (alertType.toUpperCase()) {
            case "FRAUD": return 100;
            case "AML": return 50;
            case "COMPLIANCE": return 75;
            case "SECURITY": return 120;
            default: return 200;
        }
    }

    private double calculateAlertImpactScore(Map<String, Object> event) {
        String severity = String.valueOf(event.get("severity"));
        Double riskScore = event.get("riskScore") != null ? ((Number) event.get("riskScore")).doubleValue() : 0.0;

        double impactScore = riskScore / 100.0;

        switch (severity.toUpperCase()) {
            case "CRITICAL": impactScore += 0.4; break;
            case "HIGH": impactScore += 0.3; break;
            case "MEDIUM": impactScore += 0.2; break;
            case "LOW": impactScore += 0.1; break;
        }

        return Math.min(impactScore, 1.0);
    }

    private List<String> generateAlertRecommendations(String alertType, String severity, double impactScore) {
        List<String> recommendations = new ArrayList<>();

        if (impactScore > 0.8) {
            recommendations.add("IMMEDIATE_INVESTIGATION_REQUIRED");
            recommendations.add("ESCALATE_TO_SENIOR_ANALYST");
        }

        if (impactScore > 0.6) {
            recommendations.add("PRIORITY_REVIEW");
            recommendations.add("ENHANCED_MONITORING");
        }

        if ("CRITICAL".equals(severity)) {
            recommendations.add("ALERT_STAKEHOLDERS");
            recommendations.add("PREPARE_INCIDENT_RESPONSE");
        }

        if (alertType.contains("FRAUD")) {
            recommendations.add("REVIEW_TRANSACTION_HISTORY");
            recommendations.add("CHECK_USER_BEHAVIOR_PATTERNS");
        }

        return recommendations;
    }

    private boolean isSeverityDistributionAnomalous(Map<String, Integer> distribution) {
        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();
        if (total < 10) return false;

        int criticalCount = distribution.getOrDefault("CRITICAL", 0);
        double criticalPercentage = (double) criticalCount / total;

        return criticalPercentage > 0.15; // More than 15% critical alerts is anomalous
    }
}