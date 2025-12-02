package com.waqiti.compliance.kafka;

import com.waqiti.compliance.domain.ComplianceAlert;
import com.waqiti.compliance.domain.SuspiciousActivity;
import com.waqiti.compliance.repository.ComplianceAlertRepository;
import com.waqiti.compliance.repository.SuspiciousActivityRepository;
import com.waqiti.compliance.service.AmlService;
import com.waqiti.compliance.service.ComplianceMetricsService;
import com.waqiti.compliance.service.AuditService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.compliance.service.SARProcessingService;
import com.waqiti.compliance.service.AMLTransactionMonitoringService;
import com.waqiti.compliance.service.SanctionsScreeningService;
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
public class AMLAlertsConsumer {

    private final ComplianceAlertRepository alertRepository;
    private final SuspiciousActivityRepository suspiciousActivityRepository;
    private final AmlService amlService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final ComplianceNotificationService notificationService;
    private final SARProcessingService sarProcessingService;
    private final AMLTransactionMonitoringService transactionMonitoringService;
    private final SanctionsScreeningService sanctionsScreeningService;
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
        successCounter = Counter.builder("aml_alerts_processed_total")
            .description("Total number of successfully processed AML alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("aml_alerts_errors_total")
            .description("Total number of AML alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("aml_alerts_processing_duration")
            .description("Time taken to process AML alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"aml-alerts", "aml-suspicious-activity", "aml-investigation-required"},
        groupId = "aml-alerts-compliance-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "aml-alerts", fallbackMethod = "handleAMLAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAMLAlertEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String entityId = String.valueOf(event.get("entityId"));
        String alertType = String.valueOf(event.get("alertType"));
        String correlationId = String.format("aml-alert-%s-p%d-o%d", entityId, partition, offset);
        String eventKey = String.format("%s-%s-%s", entityId, alertType, event.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("AML alert event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing AML alert: entityId={}, alertType={}, severity={}",
                entityId, alertType, event.get("severity"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (alertType) {
                case "SUSPICIOUS_ACTIVITY_DETECTED":
                    processSuspiciousActivityAlert(event, correlationId);
                    break;

                case "TRANSACTION_PATTERN_ANOMALY":
                    processTransactionPatternAlert(event, correlationId);
                    break;

                case "STRUCTURING_DETECTED":
                    processStructuringAlert(event, correlationId);
                    break;

                case "VELOCITY_THRESHOLD_EXCEEDED":
                    processVelocityAlert(event, correlationId);
                    break;

                case "GEOGRAPHIC_ANOMALY":
                    processGeographicAlert(event, correlationId);
                    break;

                case "HIGH_RISK_CUSTOMER_ACTIVITY":
                    processHighRiskCustomerAlert(event, correlationId);
                    break;

                case "SANCTIONS_POTENTIAL_MATCH":
                    processSanctionsAlert(event, correlationId);
                    break;

                case "PEP_EXPOSURE_DETECTED":
                    processPEPAlert(event, correlationId);
                    break;

                case "MONEY_LAUNDERING_INDICATORS":
                    processMoneyLaunderingAlert(event, correlationId);
                    break;

                case "TERRORIST_FINANCING_INDICATORS":
                    processTerroristFinancingAlert(event, correlationId);
                    break;

                default:
                    log.warn("Unknown AML alert type: {}", alertType);
                    processGenericAMLAlert(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCriticalComplianceEvent("AML_ALERT_PROCESSED", entityId,
                Map.of("alertType", alertType, "severity", event.get("severity"),
                    "correlationId", correlationId, "timestamp", Instant.now(),
                    "riskScore", event.get("riskScore"), "requiresInvestigation", true));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process AML alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("aml-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3, "alertType", alertType));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAMLAlertEventFallback(
            Map<String, Object> event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String entityId = String.valueOf(event.get("entityId"));
        String correlationId = String.format("aml-alert-fallback-%s-p%d-o%d", entityId, partition, offset);

        log.error("Circuit breaker fallback triggered for AML alert: entityId={}, error={}",
            entityId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("aml-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification to compliance team
        try {
            notificationService.sendCriticalComplianceAlert(
                "AML Alert Processing Circuit Breaker Triggered",
                String.format("AML alert processing failed for entity %s: %s", entityId, ex.getMessage()),
                "CRITICAL",
                Map.of("entityId", entityId, "alertType", event.get("alertType"), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send AML alert notification: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAMLAlertEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String entityId = String.valueOf(event.get("entityId"));
        String correlationId = String.format("dlt-aml-alert-%s-%d", entityId, System.currentTimeMillis());

        log.error("Dead letter topic handler - AML alert permanently failed: entityId={}, topic={}, error={}",
            entityId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCriticalComplianceEvent("AML_ALERT_DLT_EVENT", entityId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.get("alertType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "regulatoryImplication", true,
                "timestamp", Instant.now()));

        // Send emergency alert to compliance leadership
        try {
            notificationService.sendEmergencyComplianceAlert(
                "CRITICAL: AML Alert Dead Letter Event",
                String.format("AML alert for entity %s sent to DLT - Manual intervention required: %s",
                    entityId, exceptionMessage),
                Map.of("entityId", entityId, "topic", topic, "correlationId", correlationId,
                    "regulatoryRisk", "HIGH", "immediateActionRequired", true)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency AML DLT alert: {}", ex.getMessage());
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

    private void processSuspiciousActivityAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String activityType = String.valueOf(event.get("activityType"));

        SuspiciousActivity suspiciousActivity = SuspiciousActivity.builder()
            .entityId(entityId)
            .activityType(activityType)
            .description(String.valueOf(event.get("description")))
            .severity(String.valueOf(event.get("severity")))
            .riskScore(Double.valueOf(String.valueOf(event.get("riskScore"))))
            .detectedAt(LocalDateTime.now())
            .status("UNDER_INVESTIGATION")
            .correlationId(correlationId)
            .requiresSAR(Boolean.TRUE.equals(event.get("requiresSAR")))
            .build();
        suspiciousActivityRepository.save(suspiciousActivity);

        // Trigger AML investigation
        amlService.processAmlAlert(entityId, activityType, event);

        // Check if SAR filing is required
        if (Boolean.TRUE.equals(event.get("requiresSAR"))) {
            sarProcessingService.initiateSARFiling(entityId, activityType,
                String.valueOf(event.get("description")), correlationId);
        }

        // Send notification to compliance team
        notificationService.sendComplianceAlert(
            "Suspicious Activity Detected",
            String.format("Suspicious activity detected for entity %s: %s", entityId, activityType),
            "HIGH",
            correlationId
        );

        log.info("Suspicious activity alert processed: entityId={}, activityType={}, requiresSAR={}",
            entityId, activityType, event.get("requiresSAR"));
    }

    private void processTransactionPatternAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String patternType = String.valueOf(event.get("patternType"));

        // Analyze transaction patterns
        transactionMonitoringService.analyzeTransactionPattern(entityId, patternType, event);

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType("TRANSACTION_PATTERN")
            .description(String.format("Unusual transaction pattern detected: %s", patternType))
            .severity(String.valueOf(event.get("severity")))
            .status("OPEN")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        log.info("Transaction pattern alert processed: entityId={}, patternType={}", entityId, patternType);
    }

    private void processStructuringAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));

        // Structuring is a serious AML violation requiring immediate attention
        SuspiciousActivity structuringActivity = SuspiciousActivity.builder()
            .entityId(entityId)
            .activityType("STRUCTURING")
            .description("Potential structuring activity detected - transactions designed to avoid reporting thresholds")
            .severity("CRITICAL")
            .riskScore(Double.valueOf(String.valueOf(event.getOrDefault("riskScore", 90.0))))
            .detectedAt(LocalDateTime.now())
            .status("URGENT_REVIEW_REQUIRED")
            .correlationId(correlationId)
            .requiresSAR(true)
            .build();
        suspiciousActivityRepository.save(structuringActivity);

        // Immediately initiate SAR filing for structuring
        sarProcessingService.initiateSARFiling(entityId, "STRUCTURING",
            "Potential structuring activity detected", correlationId);

        // Send urgent notification
        notificationService.sendUrgentComplianceAlert(
            "CRITICAL: Potential Structuring Detected",
            String.format("Potential structuring activity detected for entity %s - SAR filing initiated", entityId),
            Map.of("entityId", entityId, "correlationId", correlationId, "sarRequired", true)
        );

        log.warn("Structuring alert processed - SAR initiated: entityId={}", entityId);
    }

    private void processVelocityAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));

        transactionMonitoringService.evaluateVelocityThresholds(entityId, event);

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType("VELOCITY_THRESHOLD")
            .description("Transaction velocity threshold exceeded")
            .severity(String.valueOf(event.get("severity")))
            .status("UNDER_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        log.info("Velocity alert processed: entityId={}", entityId);
    }

    private void processGeographicAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String location = String.valueOf(event.get("location"));

        // Check for high-risk jurisdictions
        transactionMonitoringService.evaluateGeographicRisk(entityId, location, event);

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType("GEOGRAPHIC_RISK")
            .description(String.format("Transaction from high-risk jurisdiction: %s", location))
            .severity(String.valueOf(event.get("severity")))
            .status("UNDER_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        log.info("Geographic alert processed: entityId={}, location={}", entityId, location);
    }

    private void processHighRiskCustomerAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));

        // Enhanced monitoring for high-risk customers
        amlService.processAmlAlert(entityId, "HIGH_RISK_CUSTOMER", event);

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType("HIGH_RISK_CUSTOMER")
            .description("Activity from high-risk customer requiring enhanced monitoring")
            .severity("HIGH")
            .status("ENHANCED_MONITORING")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        log.info("High-risk customer alert processed: entityId={}", entityId);
    }

    private void processSanctionsAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String matchType = String.valueOf(event.get("matchType"));

        // Perform enhanced sanctions screening
        sanctionsScreeningService.processSanctionsMatch(entityId, matchType, event);

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType("SANCTIONS_MATCH")
            .description(String.format("Potential sanctions match detected: %s", matchType))
            .severity("CRITICAL")
            .status("IMMEDIATE_REVIEW_REQUIRED")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        // Send immediate notification for sanctions matches
        notificationService.sendEmergencyComplianceAlert(
            "URGENT: Potential Sanctions Match",
            String.format("Potential sanctions match for entity %s - Immediate review required", entityId),
            Map.of("entityId", entityId, "matchType", matchType, "correlationId", correlationId)
        );

        log.warn("Sanctions alert processed: entityId={}, matchType={}", entityId, matchType);
    }

    private void processPEPAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String pepLevel = String.valueOf(event.get("pepLevel"));

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType("PEP_EXPOSURE")
            .description(String.format("Politically Exposed Person detected: %s", pepLevel))
            .severity("HIGH")
            .status("ENHANCED_DUE_DILIGENCE_REQUIRED")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        log.info("PEP alert processed: entityId={}, pepLevel={}", entityId, pepLevel);
    }

    private void processMoneyLaunderingAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));

        SuspiciousActivity mlActivity = SuspiciousActivity.builder()
            .entityId(entityId)
            .activityType("MONEY_LAUNDERING")
            .description("Money laundering indicators detected")
            .severity("CRITICAL")
            .riskScore(Double.valueOf(String.valueOf(event.getOrDefault("riskScore", 95.0))))
            .detectedAt(LocalDateTime.now())
            .status("SAR_FILING_REQUIRED")
            .correlationId(correlationId)
            .requiresSAR(true)
            .build();
        suspiciousActivityRepository.save(mlActivity);

        // Automatically initiate SAR filing for money laundering indicators
        sarProcessingService.initiateSARFiling(entityId, "MONEY_LAUNDERING",
            "Money laundering indicators detected", correlationId);

        log.error("Money laundering alert processed - SAR initiated: entityId={}", entityId);
    }

    private void processTerroristFinancingAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));

        SuspiciousActivity tfActivity = SuspiciousActivity.builder()
            .entityId(entityId)
            .activityType("TERRORIST_FINANCING")
            .description("Terrorist financing indicators detected")
            .severity("CRITICAL")
            .riskScore(100.0) // Maximum risk score for terrorist financing
            .detectedAt(LocalDateTime.now())
            .status("IMMEDIATE_SAR_FILING_REQUIRED")
            .correlationId(correlationId)
            .requiresSAR(true)
            .build();
        suspiciousActivityRepository.save(tfActivity);

        // Immediately initiate SAR filing for terrorist financing
        sarProcessingService.initiateSARFiling(entityId, "TERRORIST_FINANCING",
            "Terrorist financing indicators detected", correlationId);

        // Send emergency notification for terrorist financing
        notificationService.sendEmergencyComplianceAlert(
            "EMERGENCY: Terrorist Financing Indicators",
            String.format("Terrorist financing indicators detected for entity %s - Immediate SAR filing initiated", entityId),
            Map.of("entityId", entityId, "correlationId", correlationId, "lawEnforcementNotification", true)
        );

        log.error("Terrorist financing alert processed - Emergency SAR initiated: entityId={}", entityId);
    }

    private void processGenericAMLAlert(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String alertType = String.valueOf(event.get("alertType"));

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType(alertType)
            .description(String.valueOf(event.get("description")))
            .severity(String.valueOf(event.getOrDefault("severity", "MEDIUM")))
            .status("UNDER_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        log.info("Generic AML alert processed: entityId={}, alertType={}", entityId, alertType);
    }
}