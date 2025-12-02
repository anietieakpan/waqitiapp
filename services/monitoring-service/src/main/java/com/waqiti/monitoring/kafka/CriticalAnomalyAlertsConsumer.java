package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.CriticalAnomalyAlertEvent;
import com.waqiti.monitoring.service.AnomalyDetectionService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.InfrastructureMetricsService;
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
public class CriticalAnomalyAlertsConsumer {

    private final AnomalyDetectionService anomalyService;
    private final AlertingService alertingService;
    private final InfrastructureMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("critical_anomaly_alerts_processed_total")
            .description("Total number of successfully processed critical anomaly alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("critical_anomaly_alerts_errors_total")
            .description("Total number of critical anomaly alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("critical_anomaly_alerts_processing_duration")
            .description("Time taken to process critical anomaly alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"critical-anomaly-alerts", "critical-ml-alerts", "critical-pattern-alerts"},
        groupId = "critical-anomaly-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "critical-anomaly-alerts", fallbackMethod = "handleCriticalAnomalyAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCriticalAnomalyAlertEvent(
            @Payload CriticalAnomalyAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("crit-anomaly-%s-p%d-o%d", event.getAnomalyId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAnomalyId(), event.getAnomalyType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL anomaly alert: anomalyId={}, type={}, severity={}, confidence={}",
                event.getAnomalyId(), event.getAnomalyType(), event.getSeverity(), event.getConfidenceScore());

            cleanExpiredEntries();

            switch (event.getAnomalyType()) {
                case TRAFFIC_SPIKE:
                    handleTrafficSpike(event, correlationId);
                    break;

                case FRAUD_PATTERN:
                    handleFraudPattern(event, correlationId);
                    break;

                case PERFORMANCE_DEGRADATION:
                    handlePerformanceDegradation(event, correlationId);
                    break;

                case SECURITY_BREACH_PATTERN:
                    handleSecurityBreachPattern(event, correlationId);
                    break;

                case RESOURCE_EXHAUSTION:
                    handleResourceExhaustion(event, correlationId);
                    break;

                case DATA_QUALITY_ANOMALY:
                    handleDataQualityAnomaly(event, correlationId);
                    break;

                case BEHAVIORAL_ANOMALY:
                    handleBehavioralAnomaly(event, correlationId);
                    break;

                case SYSTEM_INSTABILITY:
                    handleSystemInstability(event, correlationId);
                    break;

                default:
                    log.warn("Unknown critical anomaly type: {}", event.getAnomalyType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logCriticalEvent("CRITICAL_ANOMALY_ALERT_PROCESSED", event.getAnomalyId(),
                Map.of("anomalyType", event.getAnomalyType(), "severity", event.getSeverity(),
                    "confidence", event.getConfidenceScore(), "affectedMetrics", event.getAffectedMetrics(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process critical anomaly alert: {}", e.getMessage(), e);

            kafkaTemplate.send("critical-anomaly-alerts-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCriticalAnomalyAlertEventFallback(
            CriticalAnomalyAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("crit-anomaly-fallback-%s-p%d-o%d", event.getAnomalyId(), partition, offset);

        log.error("Circuit breaker fallback for critical anomaly: anomalyId={}, error={}",
            event.getAnomalyId(), ex.getMessage());

        kafkaTemplate.send("critical-anomaly-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "Critical Anomaly Alert Processing Failure",
                String.format("Critical anomaly %s processing failed: %s", event.getAnomalyId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send emergency alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCriticalAnomalyAlertEvent(
            @Payload CriticalAnomalyAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-crit-anomaly-%s-%d", event.getAnomalyId(), System.currentTimeMillis());

        log.error("DLT handler - Critical anomaly alert failed: anomalyId={}, topic={}, error={}",
            event.getAnomalyId(), topic, exceptionMessage);

        auditService.logCriticalEvent("CRITICAL_ANOMALY_ALERT_DLT_EVENT", event.getAnomalyId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "anomalyType", event.getAnomalyType(), "correlationId", correlationId,
                "requiresImmediateIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "Critical Anomaly Alert DLT Event",
                String.format("Critical anomaly %s sent to DLT: %s", event.getAnomalyId(), exceptionMessage),
                Map.of("anomalyId", event.getAnomalyId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }
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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void handleTrafficSpike(CriticalAnomalyAlertEvent event, String correlationId) {
        log.error("CRITICAL traffic spike: anomalyId={}, baseline={}, current={}, increase={}%",
            event.getAnomalyId(), event.getBaselineValue(), event.getCurrentValue(), event.getDeviationPercentage());

        anomalyService.handleTrafficSpike(event.getAnomalyId(), event.getCurrentValue(), event.getBaselineValue());

        alertingService.sendCriticalAlert(
            "Critical Traffic Spike Detected",
            String.format("Traffic spike: %.0f%% increase from baseline (current: %s, baseline: %s)",
                event.getDeviationPercentage(), event.getCurrentValue(), event.getBaselineValue()),
            correlationId
        );

        // Auto-scale if configured
        anomalyService.triggerAutoScaling(event.getAffectedResource(), event.getCurrentValue());

        metricsService.recordTrafficSpike(event.getAffectedResource(), event.getDeviationPercentage());
    }

    private void handleFraudPattern(CriticalAnomalyAlertEvent event, String correlationId) {
        log.error("CRITICAL fraud pattern: anomalyId={}, pattern={}, riskScore={}",
            event.getAnomalyId(), event.getFraudPattern(), event.getRiskScore());

        anomalyService.handleFraudPattern(event.getAnomalyId(), event.getFraudPattern(), event.getRiskScore());

        alertingService.sendEmergencyAlert(
            "Critical Fraud Pattern Detected",
            String.format("Fraud pattern: %s (risk score: %.2f)",
                event.getFraudPattern(), event.getRiskScore()),
            correlationId
        );

        // Freeze suspicious accounts if high risk
        if (event.getRiskScore() > 0.9) {
            anomalyService.freezeSuspiciousAccounts(event.getFraudPattern());
        }

        metricsService.recordFraudPattern(event.getFraudPattern(), event.getRiskScore());

        // Send to fraud team
        kafkaTemplate.send("fraud-critical-events", Map.of(
            "eventType", "CRITICAL_FRAUD_PATTERN",
            "anomalyId", event.getAnomalyId(),
            "pattern", event.getFraudPattern(),
            "riskScore", event.getRiskScore(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handlePerformanceDegradation(CriticalAnomalyAlertEvent event, String correlationId) {
        log.error("CRITICAL performance degradation: service={}, latency={}, errorRate={}",
            event.getAffectedService(), event.getLatencyMs(), event.getErrorRate());

        anomalyService.handlePerformanceDegradation(
            event.getAffectedService(), event.getLatencyMs(), event.getErrorRate());

        alertingService.sendCriticalAlert(
            "Critical Performance Degradation",
            String.format("Service %s degraded: latency %dms, error rate %.2f%%",
                event.getAffectedService(), event.getLatencyMs(), event.getErrorRate() * 100),
            correlationId
        );

        // Trigger circuit breakers if error rate is too high
        if (event.getErrorRate() > 0.1) {
            anomalyService.triggerCircuitBreakers(event.getAffectedService());
        }

        metricsService.recordPerformanceDegradation(event.getAffectedService(), event.getLatencyMs());
    }

    private void handleSecurityBreachPattern(CriticalAnomalyAlertEvent event, String correlationId) {
        log.error("CRITICAL security breach pattern: anomalyId={}, pattern={}, threatLevel={}",
            event.getAnomalyId(), event.getSecurityPattern(), event.getThreatLevel());

        anomalyService.handleSecurityBreachPattern(
            event.getAnomalyId(), event.getSecurityPattern(), event.getThreatLevel());

        alertingService.sendEmergencyAlert(
            "Critical Security Breach Pattern",
            String.format("Security breach pattern: %s (threat level: %s)",
                event.getSecurityPattern(), event.getThreatLevel()),
            correlationId
        );

        // Trigger security lockdown
        anomalyService.triggerSecurityLockdown(event.getSecurityPattern());

        metricsService.recordSecurityBreachPattern(event.getSecurityPattern(), event.getThreatLevel());

        // Send to security team
        kafkaTemplate.send("security-critical-events", Map.of(
            "eventType", "CRITICAL_SECURITY_BREACH_PATTERN",
            "anomalyId", event.getAnomalyId(),
            "pattern", event.getSecurityPattern(),
            "threatLevel", event.getThreatLevel(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleResourceExhaustion(CriticalAnomalyAlertEvent event, String correlationId) {
        log.error("CRITICAL resource exhaustion: resource={}, usage={}%, threshold={}%",
            event.getResourceType(), event.getUsagePercentage(), event.getThreshold());

        anomalyService.handleResourceExhaustion(
            event.getResourceType(), event.getUsagePercentage(), event.getThreshold());

        alertingService.sendCriticalAlert(
            "Critical Resource Exhaustion",
            String.format("Resource %s at %.1f%% usage (threshold: %.1f%%)",
                event.getResourceType(), event.getUsagePercentage(), event.getThreshold()),
            correlationId
        );

        // Trigger resource scaling
        anomalyService.triggerResourceScaling(event.getResourceType(), event.getUsagePercentage());

        metricsService.recordResourceExhaustion(event.getResourceType(), event.getUsagePercentage());
    }

    private void handleDataQualityAnomaly(CriticalAnomalyAlertEvent event, String correlationId) {
        log.error("CRITICAL data quality anomaly: dataset={}, qualityScore={}, issueType={}",
            event.getDataset(), event.getQualityScore(), event.getQualityIssueType());

        anomalyService.handleDataQualityAnomaly(
            event.getDataset(), event.getQualityScore(), event.getQualityIssueType());

        alertingService.sendCriticalAlert(
            "Critical Data Quality Anomaly",
            String.format("Data quality issue in %s: %s (score: %.2f)",
                event.getDataset(), event.getQualityIssueType(), event.getQualityScore()),
            correlationId
        );

        metricsService.recordDataQualityAnomaly(event.getDataset(), event.getQualityScore());
    }

    private void handleBehavioralAnomaly(CriticalAnomalyAlertEvent event, String correlationId) {
        log.error("CRITICAL behavioral anomaly: user={}, behavior={}, riskScore={}",
            event.getUserId(), event.getBehaviorType(), event.getRiskScore());

        anomalyService.handleBehavioralAnomaly(
            event.getUserId(), event.getBehaviorType(), event.getRiskScore());

        alertingService.sendCriticalAlert(
            "Critical Behavioral Anomaly",
            String.format("Behavioral anomaly: %s (user: %s, risk: %.2f)",
                event.getBehaviorType(), event.getUserId(), event.getRiskScore()),
            correlationId
        );

        // Flag user for review if high risk
        if (event.getRiskScore() > 0.8) {
            anomalyService.flagUserForReview(event.getUserId(), event.getBehaviorType());
        }

        metricsService.recordBehavioralAnomaly(event.getBehaviorType(), event.getRiskScore());
    }

    private void handleSystemInstability(CriticalAnomalyAlertEvent event, String correlationId) {
        log.error("CRITICAL system instability: system={}, instabilityType={}, severity={}",
            event.getSystemComponent(), event.getInstabilityType(), event.getSeverity());

        anomalyService.handleSystemInstability(
            event.getSystemComponent(), event.getInstabilityType(), event.getSeverity());

        alertingService.sendCriticalAlert(
            "Critical System Instability",
            String.format("System instability in %s: %s (severity: %s)",
                event.getSystemComponent(), event.getInstabilityType(), event.getSeverity()),
            correlationId
        );

        // Trigger failover procedures
        anomalyService.triggerFailoverProcedures(event.getSystemComponent());

        metricsService.recordSystemInstability(event.getSystemComponent(), event.getInstabilityType());
    }
}