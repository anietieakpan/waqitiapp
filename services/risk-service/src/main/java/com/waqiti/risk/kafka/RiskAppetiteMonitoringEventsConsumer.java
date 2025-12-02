package com.waqiti.risk.kafka;

import com.waqiti.common.events.RiskAppetiteMonitoringEvent;
import com.waqiti.risk.service.RiskAppetiteService;
import com.waqiti.risk.service.RiskMonitoringService;
import com.waqiti.risk.service.RiskMetricsService;
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
public class RiskAppetiteMonitoringEventsConsumer {

    private final RiskAppetiteService riskAppetiteService;
    private final RiskMonitoringService monitoringService;
    private final RiskMetricsService metricsService;
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
        successCounter = Counter.builder("risk_appetite_monitoring_processed_total")
            .description("Total number of successfully processed risk appetite monitoring events")
            .register(meterRegistry);
        errorCounter = Counter.builder("risk_appetite_monitoring_errors_total")
            .description("Total number of risk appetite monitoring processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("risk_appetite_monitoring_processing_duration")
            .description("Time taken to process risk appetite monitoring events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"risk-appetite-monitoring-events", "risk-appetite-alerts", "risk-limit-monitoring"},
        groupId = "risk-appetite-monitoring-service-group",
        containerFactory = "criticalRiskKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "risk-appetite-monitoring", fallbackMethod = "handleRiskAppetiteMonitoringEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleRiskAppetiteMonitoringEvent(
            @Payload RiskAppetiteMonitoringEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("risk-appetite-%s-p%d-o%d", event.getRiskMetricId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getRiskMetricId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing risk appetite monitoring event: metricId={}, type={}, riskLevel={}, threshold={}",
                event.getRiskMetricId(), event.getEventType(), event.getRiskLevel(), event.getThresholdValue());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case RISK_LIMIT_EXCEEDED:
                    processRiskLimitExceeded(event, correlationId);
                    break;

                case RISK_LIMIT_APPROACHING:
                    processRiskLimitApproaching(event, correlationId);
                    break;

                case RISK_APPETITE_BREACH:
                    processRiskAppetiteBreach(event, correlationId);
                    break;

                case RISK_APPETITE_RESTORED:
                    processRiskAppetiteRestored(event, correlationId);
                    break;

                case RISK_METRIC_UPDATED:
                    processRiskMetricUpdated(event, correlationId);
                    break;

                case RISK_THRESHOLD_ADJUSTED:
                    processRiskThresholdAdjusted(event, correlationId);
                    break;

                case CONCENTRATION_RISK_ALERT:
                    processConcentrationRiskAlert(event, correlationId);
                    break;

                case AGGREGATE_RISK_BREACH:
                    processAggregateRiskBreach(event, correlationId);
                    break;

                default:
                    log.warn("Unknown risk appetite monitoring event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("RISK_APPETITE_MONITORING_PROCESSED", event.getRiskMetricId(),
                Map.of("eventType", event.getEventType(), "riskLevel", event.getRiskLevel(),
                    "thresholdValue", event.getThresholdValue(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process risk appetite monitoring event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("risk-appetite-monitoring-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleRiskAppetiteMonitoringEventFallback(
            RiskAppetiteMonitoringEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("risk-appetite-fallback-%s-p%d-o%d", event.getRiskMetricId(), partition, offset);

        log.error("Circuit breaker fallback triggered for risk appetite monitoring: metricId={}, error={}",
            event.getRiskMetricId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("risk-appetite-monitoring-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification for high-risk events
        if ("HIGH".equals(event.getRiskLevel()) || "CRITICAL".equals(event.getRiskLevel())) {
            try {
                notificationService.sendCriticalAlert(
                    "Risk Appetite Monitoring Circuit Breaker Triggered",
                    String.format("Risk appetite monitoring for %s failed: %s", event.getRiskMetricId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send critical alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltRiskAppetiteMonitoringEvent(
            @Payload RiskAppetiteMonitoringEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-risk-appetite-%s-%d", event.getRiskMetricId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Risk appetite monitoring permanently failed: metricId={}, topic={}, error={}",
            event.getRiskMetricId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logRiskEvent("RISK_APPETITE_MONITORING_DLT_EVENT", event.getRiskMetricId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Risk Appetite Monitoring Dead Letter Event",
                String.format("Risk appetite monitoring %s sent to DLT: %s", event.getRiskMetricId(), exceptionMessage),
                Map.of("riskMetricId", event.getRiskMetricId(), "topic", topic, "correlationId", correlationId)
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

    private void processRiskLimitExceeded(RiskAppetiteMonitoringEvent event, String correlationId) {
        // Record risk limit breach
        monitoringService.recordRiskLimitBreach(
            event.getRiskMetricId(),
            event.getRiskCategory(),
            event.getCurrentValue(),
            event.getThresholdValue(),
            event.getExcessAmount(),
            event.getTimestamp(),
            correlationId
        );

        // Trigger immediate risk mitigation
        kafkaTemplate.send("risk-mitigation-actions", Map.of(
            "type", "IMMEDIATE_LIMIT_BREACH",
            "riskMetricId", event.getRiskMetricId(),
            "riskCategory", event.getRiskCategory(),
            "severity", "HIGH",
            "currentValue", event.getCurrentValue(),
            "thresholdValue", event.getThresholdValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Risk Limit Exceeded",
            String.format("CRITICAL: Risk limit exceeded for %s. Current: %s, Limit: %s, Excess: %s",
                event.getRiskMetricId(), event.getCurrentValue(), event.getThresholdValue(), event.getExcessAmount()),
            Map.of("riskMetricId", event.getRiskMetricId(), "correlationId", correlationId)
        );

        // Escalate to risk management
        kafkaTemplate.send("risk-management-escalations", Map.of(
            "type", "LIMIT_BREACH",
            "severity", "CRITICAL",
            "riskMetricId", event.getRiskMetricId(),
            "description", String.format("Risk limit exceeded by %s", event.getExcessAmount()),
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementRiskLimitBreaches(event.getRiskCategory());
        metricsService.recordRiskExcessAmount(event.getRiskCategory(), event.getExcessAmount());

        log.error("Risk limit exceeded: metricId={}, current={}, limit={}, excess={}",
            event.getRiskMetricId(), event.getCurrentValue(), event.getThresholdValue(), event.getExcessAmount());
    }

    private void processRiskLimitApproaching(RiskAppetiteMonitoringEvent event, String correlationId) {
        // Record approaching limit warning
        monitoringService.recordApproachingLimit(
            event.getRiskMetricId(),
            event.getRiskCategory(),
            event.getCurrentValue(),
            event.getThresholdValue(),
            event.getUtilizationPercentage(),
            correlationId
        );

        // Send warning notification
        notificationService.sendOperationalAlert(
            "Risk Limit Warning",
            String.format("Risk limit approaching for %s. Current: %s (%s%% of limit %s)",
                event.getRiskMetricId(), event.getCurrentValue(),
                event.getUtilizationPercentage(), event.getThresholdValue()),
            "HIGH"
        );

        // Trigger preventive measures
        kafkaTemplate.send("risk-prevention-measures", Map.of(
            "type", "APPROACHING_LIMIT",
            "riskMetricId", event.getRiskMetricId(),
            "utilizationPercentage", event.getUtilizationPercentage(),
            "recommendedAction", riskAppetiteService.getRecommendedAction(event.getRiskCategory(), event.getUtilizationPercentage()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementApproachingLimitWarnings(event.getRiskCategory());

        log.warn("Risk limit approaching: metricId={}, utilization={}%",
            event.getRiskMetricId(), event.getUtilizationPercentage());
    }

    private void processRiskAppetiteBreach(RiskAppetiteMonitoringEvent event, String correlationId) {
        // Record appetite breach
        riskAppetiteService.recordAppetiteBreach(
            event.getRiskMetricId(),
            event.getRiskCategory(),
            event.getCurrentValue(),
            event.getAppetiteThreshold(),
            event.getBreachDuration(),
            correlationId
        );

        // Trigger automatic remediation if configured
        if (riskAppetiteService.hasAutomaticRemediation(event.getRiskCategory())) {
            kafkaTemplate.send("automatic-risk-remediation", Map.of(
                "riskMetricId", event.getRiskMetricId(),
                "riskCategory", event.getRiskCategory(),
                "remediationPlan", riskAppetiteService.getRemediationPlan(event.getRiskCategory()),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Send to board reporting if significant
        if (riskAppetiteService.requiresBoardReporting(event.getRiskCategory(), event.getCurrentValue())) {
            kafkaTemplate.send("board-reporting-queue", Map.of(
                "reportType", "RISK_APPETITE_BREACH",
                "riskMetricId", event.getRiskMetricId(),
                "riskCategory", event.getRiskCategory(),
                "severity", event.getRiskLevel(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.incrementAppetiteBreaches(event.getRiskCategory());

        log.error("Risk appetite breach: metricId={}, category={}, duration={}",
            event.getRiskMetricId(), event.getRiskCategory(), event.getBreachDuration());
    }

    private void processRiskAppetiteRestored(RiskAppetiteMonitoringEvent event, String correlationId) {
        // Record appetite restoration
        riskAppetiteService.recordAppetiteRestoration(
            event.getRiskMetricId(),
            event.getRiskCategory(),
            event.getCurrentValue(),
            event.getPreviousValue(),
            event.getRestorationActions(),
            correlationId
        );

        // Send restoration notification
        notificationService.sendOperationalAlert(
            "Risk Appetite Restored",
            String.format("Risk appetite restored for %s. Current: %s, Previous: %s",
                event.getRiskMetricId(), event.getCurrentValue(), event.getPreviousValue()),
            "INFO"
        );

        // Update mitigation status
        kafkaTemplate.send("risk-mitigation-status-updates", Map.of(
            "riskMetricId", event.getRiskMetricId(),
            "status", "RESOLVED",
            "resolutionActions", event.getRestorationActions(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementAppetiteRestorations(event.getRiskCategory());

        log.info("Risk appetite restored: metricId={}, current={}, previous={}",
            event.getRiskMetricId(), event.getCurrentValue(), event.getPreviousValue());
    }

    private void processRiskMetricUpdated(RiskAppetiteMonitoringEvent event, String correlationId) {
        // Update risk metric
        monitoringService.updateRiskMetric(
            event.getRiskMetricId(),
            event.getCurrentValue(),
            event.getPreviousValue(),
            event.getChangePercentage(),
            event.getTrendDirection(),
            correlationId
        );

        // Check for significant changes
        if (event.getChangePercentage() > monitoringService.getSignificantChangeThreshold()) {
            kafkaTemplate.send("significant-risk-changes", Map.of(
                "riskMetricId", event.getRiskMetricId(),
                "changePercentage", event.getChangePercentage(),
                "trendDirection", event.getTrendDirection(),
                "requiresAnalysis", true,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update risk dashboard
        kafkaTemplate.send("risk-dashboard-updates", Map.of(
            "riskMetricId", event.getRiskMetricId(),
            "currentValue", event.getCurrentValue(),
            "changePercentage", event.getChangePercentage(),
            "trendDirection", event.getTrendDirection(),
            "lastUpdated", event.getTimestamp(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordMetricUpdate(event.getRiskCategory(), event.getChangePercentage());

        log.info("Risk metric updated: metricId={}, change={}%, trend={}",
            event.getRiskMetricId(), event.getChangePercentage(), event.getTrendDirection());
    }

    private void processRiskThresholdAdjusted(RiskAppetiteMonitoringEvent event, String correlationId) {
        // Update threshold
        riskAppetiteService.updateRiskThreshold(
            event.getRiskMetricId(),
            event.getThresholdValue(),
            event.getPreviousThreshold(),
            event.getAdjustmentReason(),
            event.getAdjustedBy(),
            correlationId
        );

        // Send threshold change notification
        notificationService.sendOperationalAlert(
            "Risk Threshold Adjusted",
            String.format("Risk threshold adjusted for %s. New: %s, Previous: %s, Reason: %s",
                event.getRiskMetricId(), event.getThresholdValue(), event.getPreviousThreshold(), event.getAdjustmentReason()),
            "MEDIUM"
        );

        // Audit threshold change
        auditService.logRiskEvent("RISK_THRESHOLD_ADJUSTED", event.getRiskMetricId(),
            Map.of("newThreshold", event.getThresholdValue(), "previousThreshold", event.getPreviousThreshold(),
                "adjustmentReason", event.getAdjustmentReason(), "adjustedBy", event.getAdjustedBy(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        metricsService.incrementThresholdAdjustments(event.getRiskCategory());

        log.info("Risk threshold adjusted: metricId={}, new={}, previous={}, reason={}",
            event.getRiskMetricId(), event.getThresholdValue(), event.getPreviousThreshold(), event.getAdjustmentReason());
    }

    private void processConcentrationRiskAlert(RiskAppetiteMonitoringEvent event, String correlationId) {
        // Record concentration risk
        monitoringService.recordConcentrationRisk(
            event.getRiskMetricId(),
            event.getConcentrationCategory(),
            event.getConcentrationLevel(),
            event.getConcentrationThreshold(),
            event.getConcentratedEntities(),
            correlationId
        );

        // Send concentration alert
        notificationService.sendCriticalAlert(
            "Concentration Risk Alert",
            String.format("High concentration detected in %s: %s%% (threshold: %s%%)",
                event.getConcentrationCategory(), event.getConcentrationLevel(), event.getConcentrationThreshold()),
            Map.of("riskMetricId", event.getRiskMetricId(), "correlationId", correlationId)
        );

        // Trigger concentration analysis
        kafkaTemplate.send("concentration-risk-analysis", Map.of(
            "riskMetricId", event.getRiskMetricId(),
            "concentrationCategory", event.getConcentrationCategory(),
            "concentrationLevel", event.getConcentrationLevel(),
            "concentratedEntities", event.getConcentratedEntities(),
            "requiresRebalancing", event.getConcentrationLevel() > event.getConcentrationThreshold() * 1.5,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementConcentrationRiskAlerts(event.getConcentrationCategory());

        log.warn("Concentration risk alert: category={}, level={}%, threshold={}%",
            event.getConcentrationCategory(), event.getConcentrationLevel(), event.getConcentrationThreshold());
    }

    private void processAggregateRiskBreach(RiskAppetiteMonitoringEvent event, String correlationId) {
        // Record aggregate risk breach
        monitoringService.recordAggregateRiskBreach(
            event.getAggregateRiskScore(),
            event.getAggregateRiskLimit(),
            event.getComponentRisks(),
            event.getBreachDuration(),
            correlationId
        );

        // Send emergency notification
        notificationService.sendEmergencyAlert(
            "Aggregate Risk Limit Breach",
            String.format("URGENT: Aggregate risk score %s exceeds limit %s. Immediate action required.",
                event.getAggregateRiskScore(), event.getAggregateRiskLimit()),
            Map.of("aggregateRiskScore", event.getAggregateRiskScore(), "correlationId", correlationId)
        );

        // Trigger emergency risk committee
        kafkaTemplate.send("emergency-risk-committee", Map.of(
            "type", "AGGREGATE_RISK_BREACH",
            "severity", "EMERGENCY",
            "aggregateRiskScore", event.getAggregateRiskScore(),
            "aggregateRiskLimit", event.getAggregateRiskLimit(),
            "componentRisks", event.getComponentRisks(),
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementAggregateRiskBreaches();
        metricsService.recordAggregateRiskScore(event.getAggregateRiskScore());

        log.error("Aggregate risk breach: score={}, limit={}, duration={}",
            event.getAggregateRiskScore(), event.getAggregateRiskLimit(), event.getBreachDuration());
    }
}