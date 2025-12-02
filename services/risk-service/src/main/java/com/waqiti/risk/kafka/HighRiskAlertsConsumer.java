package com.waqiti.risk.kafka;

import com.waqiti.common.events.HighRiskAlertEvent;
import com.waqiti.risk.domain.HighRiskAlert;
import com.waqiti.risk.repository.HighRiskAlertRepository;
import com.waqiti.risk.service.HighRiskDetectionService;
import com.waqiti.risk.service.RiskScoringService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class HighRiskAlertsConsumer {

    private final HighRiskAlertRepository highRiskAlertRepository;
    private final HighRiskDetectionService highRiskDetectionService;
    private final RiskScoringService riskScoringService;
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
        successCounter = Counter.builder("high_risk_alerts_processed_total")
            .description("Total number of successfully processed high risk alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("high_risk_alerts_errors_total")
            .description("Total number of high risk alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("high_risk_alerts_processing_duration")
            .description("Time taken to process high risk alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"high-risk-alerts"},
        groupId = "high-risk-alerts-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6" // Higher concurrency for high-risk alerts
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "high-risk-alerts", fallbackMethod = "handleHighRiskAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleHighRiskAlertEvent(
            @Payload HighRiskAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("high-risk-%s-p%d-o%d", event.getEntityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEntityId(), event.getRiskType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing high risk alert: entityId={}, riskType={}, severity={}, riskScore={}",
                event.getEntityId(), event.getRiskType(), event.getSeverity(), event.getRiskScore());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getRiskType()) {
                case SUSPICIOUS_ACTIVITY:
                    processSuspiciousActivityAlert(event, correlationId);
                    break;

                case FRAUD_INDICATOR:
                    processFraudIndicatorAlert(event, correlationId);
                    break;

                case AML_VIOLATION:
                    processAmlViolationAlert(event, correlationId);
                    break;

                case REGULATORY_BREACH:
                    processRegulatoryBreachAlert(event, correlationId);
                    break;

                case CREDIT_RISK_SPIKE:
                    processCreditRiskSpikeAlert(event, correlationId);
                    break;

                case OPERATIONAL_RISK_BREACH:
                    processOperationalRiskBreachAlert(event, correlationId);
                    break;

                case CONCENTRATION_RISK:
                    processConcentrationRiskAlert(event, correlationId);
                    break;

                case LIQUIDITY_RISK_WARNING:
                    processLiquidityRiskWarningAlert(event, correlationId);
                    break;

                case CYBER_SECURITY_THREAT:
                    processCyberSecurityThreatAlert(event, correlationId);
                    break;

                case MODEL_PERFORMANCE_DEGRADATION:
                    processModelPerformanceDegradationAlert(event, correlationId);
                    break;

                default:
                    log.warn("Unknown high risk alert type: {}", event.getRiskType());
                    processGenericHighRiskAlert(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("HIGH_RISK_ALERT_PROCESSED", event.getEntityId(),
                Map.of("riskType", event.getRiskType(), "severity", event.getSeverity(),
                    "riskScore", event.getRiskScore(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process high risk alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("high-risk-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleHighRiskAlertEventFallback(
            HighRiskAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("high-risk-fallback-%s-p%d-o%d", event.getEntityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for high risk alert: entityId={}, error={}",
            event.getEntityId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("high-risk-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification for high risk alert failures
        try {
            notificationService.sendCriticalAlert(
                "High Risk Alert Circuit Breaker Triggered",
                String.format("CRITICAL: High risk alert processing failed for entity %s: %s", event.getEntityId(), ex.getMessage()),
                Map.of("entityId", event.getEntityId(), "riskType", event.getRiskType(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltHighRiskAlertEvent(
            @Payload HighRiskAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-high-risk-%s-%d", event.getEntityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - High risk alert permanently failed: entityId={}, topic={}, error={}",
            event.getEntityId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logRiskEvent("HIGH_RISK_ALERT_DLT_EVENT", event.getEntityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "riskType", event.getRiskType(), "severity", event.getSeverity(),
                "correlationId", correlationId, "requiresImmediateIntervention", true,
                "timestamp", Instant.now()));

        // Send emergency alert for high risk DLT events
        try {
            notificationService.sendEmergencyAlert(
                "High Risk Alert Dead Letter Event",
                String.format("EMERGENCY: High risk alert for entity %s sent to DLT: %s", event.getEntityId(), exceptionMessage),
                Map.of("entityId", event.getEntityId(), "riskType", event.getRiskType(),
                       "severity", event.getSeverity(), "topic", topic, "correlationId", correlationId)
            );

            // Also trigger PagerDuty for immediate response
            notificationService.sendPagerDutyAlert(
                "High Risk Alert DLT - Immediate Response Required",
                String.format("Entity: %s, Risk: %s, Error: %s", event.getEntityId(), event.getRiskType(), exceptionMessage),
                correlationId
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

    private void processSuspiciousActivityAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setSuspiciousActivities(event.getSuspiciousActivities());
        alert.setBehaviorPatterns(event.getBehaviorPatterns());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processSuspiciousActivity(event.getEntityId(), event.getSuspiciousActivities());

        // Immediate review for suspicious activity
        kafkaTemplate.send("high-risk-review-queue", Map.of(
            "entityId", event.getEntityId(),
            "reviewType", "SUSPICIOUS_ACTIVITY",
            "priority", "URGENT",
            "suspiciousActivities", event.getSuspiciousActivities(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Alert fraud team
        kafkaTemplate.send("fraud-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "SUSPICIOUS_ACTIVITY_DETECTED",
            "activities", event.getSuspiciousActivities(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendHighPriorityNotification("suspicious-activity", event, correlationId);
        metricsService.recordHighRiskAlert("SUSPICIOUS_ACTIVITY", event.getSeverity());

        log.warn("Suspicious activity alert processed: entityId={}, activities={}",
            event.getEntityId(), event.getSuspiciousActivities());
    }

    private void processFraudIndicatorAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setFraudIndicators(event.getFraudIndicators());
        alert.setFraudScore(event.getFraudScore());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processFraudIndicators(event.getEntityId(), event.getFraudIndicators());

        // Critical - potential fraud
        kafkaTemplate.send("fraud-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "FRAUD_INDICATORS_DETECTED",
            "fraudIndicators", event.getFraudIndicators(),
            "fraudScore", event.getFraudScore(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Block entity if fraud score is very high
        if (event.getFraudScore() != null && event.getFraudScore() >= 90) {
            kafkaTemplate.send("entity-blocking-events", Map.of(
                "entityId", event.getEntityId(),
                "blockReason", "HIGH_FRAUD_SCORE",
                "fraudScore", event.getFraudScore(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        sendCriticalNotification("fraud-indicator", event, correlationId);
        metricsService.recordHighRiskAlert("FRAUD_INDICATOR", "CRITICAL");

        log.error("Fraud indicator alert processed: entityId={}, fraudScore={}, indicators={}",
            event.getEntityId(), event.getFraudScore(), event.getFraudIndicators());
    }

    private void processAmlViolationAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setAmlViolations(event.getAmlViolations());
        alert.setComplianceScore(event.getComplianceScore());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processAmlViolation(event.getEntityId(), event.getAmlViolations());

        // Send to compliance team immediately
        kafkaTemplate.send("compliance-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "AML_VIOLATION",
            "violations", event.getAmlViolations(),
            "complianceScore", event.getComplianceScore(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send to regulatory reporting
        kafkaTemplate.send("regulatory-reporting", Map.of(
            "entityId", event.getEntityId(),
            "reportType", "AML_VIOLATION",
            "violationDetails", event.getAmlViolations(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendCriticalNotification("aml-violation", event, correlationId);
        metricsService.recordHighRiskAlert("AML_VIOLATION", "CRITICAL");

        log.error("AML violation alert processed: entityId={}, violations={}, complianceScore={}",
            event.getEntityId(), event.getAmlViolations(), event.getComplianceScore());
    }

    private void processRegulatoryBreachAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setRegulatoryBreaches(event.getRegulatoryBreaches());
        alert.setRegulatoryRequirements(event.getRegulatoryRequirements());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processRegulatoryBreach(event.getEntityId(), event.getRegulatoryBreaches());

        // Critical regulatory issue - immediate attention
        kafkaTemplate.send("regulatory-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "REGULATORY_BREACH",
            "breaches", event.getRegulatoryBreaches(),
            "requirements", event.getRegulatoryRequirements(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send to legal team
        kafkaTemplate.send("legal-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "REGULATORY_BREACH",
            "breachDetails", event.getRegulatoryBreaches(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendEmergencyNotification("regulatory-breach", event, correlationId);
        metricsService.recordHighRiskAlert("REGULATORY_BREACH", "CRITICAL");

        log.error("Regulatory breach alert processed: entityId={}, breaches={}",
            event.getEntityId(), event.getRegulatoryBreaches());
    }

    private void processCreditRiskSpikeAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setCreditMetrics(event.getCreditMetrics());
        alert.setProbabilityOfDefault(event.getProbabilityOfDefault());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processCreditRiskSpike(event.getEntityId(), event.getCreditMetrics());

        // Send to credit risk management
        kafkaTemplate.send("credit-risk-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "CREDIT_RISK_SPIKE",
            "creditMetrics", event.getCreditMetrics(),
            "probabilityOfDefault", event.getProbabilityOfDefault(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendHighPriorityNotification("credit-risk-spike", event, correlationId);
        metricsService.recordHighRiskAlert("CREDIT_RISK_SPIKE", event.getSeverity());

        log.warn("Credit risk spike alert processed: entityId={}, pod={}",
            event.getEntityId(), event.getProbabilityOfDefault());
    }

    private void processOperationalRiskBreachAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setOperationalRisks(event.getOperationalRisks());
        alert.setOperationalLosses(event.getOperationalLosses());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processOperationalRiskBreach(event.getEntityId(), event.getOperationalRisks());

        // Send to operational risk team
        kafkaTemplate.send("operational-risk-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "OPERATIONAL_RISK_BREACH",
            "operationalRisks", event.getOperationalRisks(),
            "operationalLosses", event.getOperationalLosses(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendHighPriorityNotification("operational-risk-breach", event, correlationId);
        metricsService.recordHighRiskAlert("OPERATIONAL_RISK_BREACH", event.getSeverity());

        log.warn("Operational risk breach alert processed: entityId={}, risks={}",
            event.getEntityId(), event.getOperationalRisks());
    }

    private void processConcentrationRiskAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setConcentrationMetrics(event.getConcentrationMetrics());
        alert.setExposureLimits(event.getExposureLimits());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processConcentrationRisk(event.getEntityId(), event.getConcentrationMetrics());

        // Send to risk management for limit review
        kafkaTemplate.send("risk-management-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "CONCENTRATION_RISK",
            "concentrationMetrics", event.getConcentrationMetrics(),
            "exposureLimits", event.getExposureLimits(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendHighPriorityNotification("concentration-risk", event, correlationId);
        metricsService.recordHighRiskAlert("CONCENTRATION_RISK", event.getSeverity());

        log.warn("Concentration risk alert processed: entityId={}, metrics={}",
            event.getEntityId(), event.getConcentrationMetrics());
    }

    private void processLiquidityRiskWarningAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setLiquidityMetrics(event.getLiquidityMetrics());
        alert.setLiquidityRatio(event.getLiquidityRatio());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processLiquidityRiskWarning(event.getEntityId(), event.getLiquidityMetrics());

        // Send to treasury and liquidity management
        kafkaTemplate.send("liquidity-risk-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "LIQUIDITY_RISK_WARNING",
            "liquidityMetrics", event.getLiquidityMetrics(),
            "liquidityRatio", event.getLiquidityRatio(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendHighPriorityNotification("liquidity-risk-warning", event, correlationId);
        metricsService.recordHighRiskAlert("LIQUIDITY_RISK_WARNING", event.getSeverity());

        log.warn("Liquidity risk warning alert processed: entityId={}, ratio={}",
            event.getEntityId(), event.getLiquidityRatio());
    }

    private void processCyberSecurityThreatAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setSecurityThreats(event.getSecurityThreats());
        alert.setThreatLevel(event.getThreatLevel());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processCyberSecurityThreat(event.getEntityId(), event.getSecurityThreats());

        // Critical security threat - immediate action
        kafkaTemplate.send("security-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "CYBER_SECURITY_THREAT",
            "threats", event.getSecurityThreats(),
            "threatLevel", event.getThreatLevel(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // If threat level is critical, trigger security response
        if ("CRITICAL".equals(event.getThreatLevel())) {
            kafkaTemplate.send("security-incident-response", Map.of(
                "entityId", event.getEntityId(),
                "incidentType", "CYBER_THREAT",
                "threats", event.getSecurityThreats(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        sendEmergencyNotification("cyber-security-threat", event, correlationId);
        metricsService.recordHighRiskAlert("CYBER_SECURITY_THREAT", "CRITICAL");

        log.error("Cyber security threat alert processed: entityId={}, threats={}, level={}",
            event.getEntityId(), event.getSecurityThreats(), event.getThreatLevel());
    }

    private void processModelPerformanceDegradationAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        alert.setModelMetrics(event.getModelMetrics());
        alert.setPerformanceThresholds(event.getPerformanceThresholds());
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processModelPerformanceDegradation(event.getEntityId(), event.getModelMetrics());

        // Send to model risk management
        kafkaTemplate.send("model-risk-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "MODEL_PERFORMANCE_DEGRADATION",
            "modelMetrics", event.getModelMetrics(),
            "performanceThresholds", event.getPerformanceThresholds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendHighPriorityNotification("model-performance-degradation", event, correlationId);
        metricsService.recordHighRiskAlert("MODEL_PERFORMANCE_DEGRADATION", event.getSeverity());

        log.warn("Model performance degradation alert processed: entityId={}, metrics={}",
            event.getEntityId(), event.getModelMetrics());
    }

    private void processGenericHighRiskAlert(HighRiskAlertEvent event, String correlationId) {
        HighRiskAlert alert = createHighRiskAlert(event, correlationId);
        highRiskAlertRepository.save(alert);

        highRiskDetectionService.processGenericHighRiskEvent(event.getEntityId(), event.getRiskType());

        kafkaTemplate.send("high-risk-review-queue", Map.of(
            "entityId", event.getEntityId(),
            "reviewType", "GENERIC_HIGH_RISK",
            "riskType", event.getRiskType(),
            "priority", event.getSeverity(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        sendHighPriorityNotification("generic-high-risk", event, correlationId);
        metricsService.recordHighRiskAlert("GENERIC", event.getSeverity());

        log.warn("Generic high risk alert processed: entityId={}, riskType={}",
            event.getEntityId(), event.getRiskType());
    }

    private HighRiskAlert createHighRiskAlert(HighRiskAlertEvent event, String correlationId) {
        return HighRiskAlert.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .riskType(event.getRiskType())
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .detectedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .riskFactors(event.getRiskFactors())
            .confidence(event.getConfidence())
            .build();
    }

    private void sendHighPriorityNotification(String alertType, HighRiskAlertEvent event, String correlationId) {
        try {
            notificationService.sendHighPriorityNotification("high-risk-team",
                String.format("High Risk Alert: %s", alertType),
                String.format("High risk detected for entity %s: %s (Score: %d)",
                    event.getEntityId(), event.getRiskType(), event.getRiskScore()),
                correlationId);
        } catch (Exception e) {
            log.error("Failed to send high priority notification: {}", e.getMessage());
        }
    }

    private void sendCriticalNotification(String alertType, HighRiskAlertEvent event, String correlationId) {
        try {
            notificationService.sendCriticalAlert(
                String.format("Critical High Risk Alert: %s", alertType),
                String.format("CRITICAL: High risk detected for entity %s: %s (Score: %d)",
                    event.getEntityId(), event.getRiskType(), event.getRiskScore()),
                Map.of("entityId", event.getEntityId(), "riskType", event.getRiskType(),
                       "riskScore", event.getRiskScore(), "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to send critical notification: {}", e.getMessage());
        }
    }

    private void sendEmergencyNotification(String alertType, HighRiskAlertEvent event, String correlationId) {
        try {
            notificationService.sendEmergencyAlert(
                String.format("Emergency High Risk Alert: %s", alertType),
                String.format("EMERGENCY: High risk detected for entity %s: %s (Score: %d)",
                    event.getEntityId(), event.getRiskType(), event.getRiskScore()),
                Map.of("entityId", event.getEntityId(), "riskType", event.getRiskType(),
                       "riskScore", event.getRiskScore(), "correlationId", correlationId, "severity", "EMERGENCY")
            );

            // Also trigger PagerDuty for emergency cases
            notificationService.sendPagerDutyAlert(
                String.format("High Risk Emergency: %s", event.getRiskType()),
                String.format("Entity: %s, Risk: %s, Score: %d", event.getEntityId(), event.getRiskType(), event.getRiskScore()),
                correlationId
            );
        } catch (Exception e) {
            log.error("Failed to send emergency notification: {}", e.getMessage());
        }
    }
}