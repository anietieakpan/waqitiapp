package com.waqiti.risk.kafka;

import com.waqiti.common.events.InfrastructureEvent;
import com.waqiti.risk.domain.InfrastructureRiskEvent;
import com.waqiti.risk.repository.InfrastructureRiskEventRepository;
import com.waqiti.risk.service.InfrastructureRiskService;
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
public class InfrastructureEventsConsumer {

    private final InfrastructureRiskEventRepository infrastructureRiskEventRepository;
    private final InfrastructureRiskService infrastructureRiskService;
    private final RiskMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("infrastructure_events_processed_total")
            .description("Total number of successfully processed infrastructure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("infrastructure_events_errors_total")
            .description("Total number of infrastructure event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("infrastructure_events_processing_duration")
            .description("Time taken to process infrastructure events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"infrastructure-events"},
        groupId = "infrastructure-events-service-group",
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
    @CircuitBreaker(name = "infrastructure-events", fallbackMethod = "handleInfrastructureEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInfrastructureEvent(
            @Payload InfrastructureEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("infra-event-%s-p%d-o%d", event.getComponentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getComponentId(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing infrastructure event: componentId={}, eventType={}, severity={}",
                event.getComponentId(), event.getEventType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case SYSTEM_FAILURE:
                    processSystemFailure(event, correlationId);
                    break;
                case PERFORMANCE_DEGRADATION:
                    processPerformanceDegradation(event, correlationId);
                    break;
                case CAPACITY_THRESHOLD_BREACH:
                    processCapacityThresholdBreach(event, correlationId);
                    break;
                case NETWORK_OUTAGE:
                    processNetworkOutage(event, correlationId);
                    break;
                case DATA_CENTER_ISSUE:
                    processDataCenterIssue(event, correlationId);
                    break;
                case SECURITY_BREACH:
                    processSecurityBreach(event, correlationId);
                    break;
                case COMPLIANCE_VIOLATION:
                    processComplianceViolation(event, correlationId);
                    break;
                case HARDWARE_FAILURE:
                    processHardwareFailure(event, correlationId);
                    break;
                case SOFTWARE_BUG:
                    processSoftwareBug(event, correlationId);
                    break;
                case CONFIGURATION_ERROR:
                    processConfigurationError(event, correlationId);
                    break;
                default:
                    log.warn("Unknown infrastructure event type: {}", event.getEventType());
                    processGenericInfrastructureEvent(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("INFRASTRUCTURE_EVENT_PROCESSED", event.getComponentId(),
                Map.of("eventType", event.getEventType(), "severity", event.getSeverity(),
                    "component", event.getComponent(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process infrastructure event: {}", e.getMessage(), e);

            kafkaTemplate.send("infrastructure-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInfrastructureEventFallback(
            InfrastructureEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("infra-event-fallback-%s-p%d-o%d", event.getComponentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for infrastructure event: componentId={}, error={}",
            event.getComponentId(), ex.getMessage());

        kafkaTemplate.send("infrastructure-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Infrastructure Event Circuit Breaker Triggered",
                String.format("Infrastructure event processing failed for component %s: %s", event.getComponentId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInfrastructureEvent(
            @Payload InfrastructureEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-infra-event-%s-%d", event.getComponentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Infrastructure event permanently failed: componentId={}, topic={}, error={}",
            event.getComponentId(), topic, exceptionMessage);

        auditService.logRiskEvent("INFRASTRUCTURE_EVENT_DLT", event.getComponentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Infrastructure Event Dead Letter Event",
                String.format("Infrastructure event for component %s sent to DLT: %s", event.getComponentId(), exceptionMessage),
                Map.of("componentId", event.getComponentId(), "topic", topic, "correlationId", correlationId)
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

    private void processSystemFailure(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setFailureType("SYSTEM_FAILURE");
        riskEvent.setImpactLevel("CRITICAL");
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processSystemFailure(event.getComponentId(), event.getFailureDetails());

        // Critical system failure - immediate alert
        kafkaTemplate.send("infrastructure-alerts", Map.of(
            "componentId", event.getComponentId(),
            "alertType", "CRITICAL_SYSTEM_FAILURE",
            "severity", "CRITICAL",
            "failureDetails", event.getFailureDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Trigger incident response
        kafkaTemplate.send("incident-response", Map.of(
            "incidentType", "SYSTEM_FAILURE",
            "componentId", event.getComponentId(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert(
            "Critical System Failure Detected",
            String.format("CRITICAL: System failure in component %s - %s", event.getComponentId(), event.getFailureDetails()),
            Map.of("componentId", event.getComponentId(), "correlationId", correlationId)
        );

        metricsService.recordInfrastructureEvent("SYSTEM_FAILURE", "CRITICAL");
        log.error("System failure processed: componentId={}, details={}", event.getComponentId(), event.getFailureDetails());
    }

    private void processPerformanceDegradation(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setPerformanceMetrics(event.getPerformanceMetrics());
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processPerformanceDegradation(event.getComponentId(), event.getPerformanceMetrics());

        if ("HIGH".equals(event.getSeverity()) || "CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("infrastructure-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "PERFORMANCE_DEGRADATION",
                "severity", event.getSeverity(),
                "performanceMetrics", event.getPerformanceMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureEvent("PERFORMANCE_DEGRADATION", event.getSeverity());
        log.warn("Performance degradation processed: componentId={}, metrics={}", event.getComponentId(), event.getPerformanceMetrics());
    }

    private void processCapacityThresholdBreach(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setCapacityMetrics(event.getCapacityMetrics());
        riskEvent.setThresholdBreach(true);
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processCapacityThresholdBreach(event.getComponentId(), event.getCapacityMetrics());

        // Send to capacity planning team
        kafkaTemplate.send("capacity-planning-alerts", Map.of(
            "componentId", event.getComponentId(),
            "alertType", "CAPACITY_THRESHOLD_BREACH",
            "capacityMetrics", event.getCapacityMetrics(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendHighPriorityNotification("capacity-planning-team",
            "Capacity Threshold Breach",
            String.format("Capacity threshold breach in component %s", event.getComponentId()),
            correlationId);

        metricsService.recordInfrastructureEvent("CAPACITY_THRESHOLD_BREACH", event.getSeverity());
        log.warn("Capacity threshold breach processed: componentId={}, metrics={}", event.getComponentId(), event.getCapacityMetrics());
    }

    private void processNetworkOutage(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setNetworkDetails(event.getNetworkDetails());
        riskEvent.setOutageDuration(event.getOutageDuration());
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processNetworkOutage(event.getComponentId(), event.getNetworkDetails());

        // Network outages are critical for financial operations
        kafkaTemplate.send("infrastructure-alerts", Map.of(
            "componentId", event.getComponentId(),
            "alertType", "NETWORK_OUTAGE",
            "severity", "CRITICAL",
            "networkDetails", event.getNetworkDetails(),
            "outageDuration", event.getOutageDuration(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert(
            "Network Outage Detected",
            String.format("CRITICAL: Network outage in component %s - Duration: %s", event.getComponentId(), event.getOutageDuration()),
            Map.of("componentId", event.getComponentId(), "outageDuration", event.getOutageDuration(), "correlationId", correlationId)
        );

        metricsService.recordInfrastructureEvent("NETWORK_OUTAGE", "CRITICAL");
        log.error("Network outage processed: componentId={}, duration={}", event.getComponentId(), event.getOutageDuration());
    }

    private void processDataCenterIssue(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setDataCenterDetails(event.getDataCenterDetails());
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processDataCenterIssue(event.getComponentId(), event.getDataCenterDetails());

        // Data center issues are always critical
        kafkaTemplate.send("infrastructure-alerts", Map.of(
            "componentId", event.getComponentId(),
            "alertType", "DATA_CENTER_ISSUE",
            "severity", "CRITICAL",
            "dataCenterDetails", event.getDataCenterDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Data Center Issue Detected",
            String.format("EMERGENCY: Data center issue in component %s - %s", event.getComponentId(), event.getDataCenterDetails()),
            Map.of("componentId", event.getComponentId(), "dataCenterDetails", event.getDataCenterDetails(), "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordInfrastructureEvent("DATA_CENTER_ISSUE", "CRITICAL");
        log.error("Data center issue processed: componentId={}, details={}", event.getComponentId(), event.getDataCenterDetails());
    }

    private void processSecurityBreach(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setSecurityDetails(event.getSecurityDetails());
        riskEvent.setBreachType(event.getBreachType());
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processSecurityBreach(event.getComponentId(), event.getSecurityDetails());

        // Security breaches require immediate response
        kafkaTemplate.send("security-alerts", Map.of(
            "componentId", event.getComponentId(),
            "alertType", "INFRASTRUCTURE_SECURITY_BREACH",
            "severity", "CRITICAL",
            "securityDetails", event.getSecurityDetails(),
            "breachType", event.getBreachType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Trigger security incident response
        kafkaTemplate.send("security-incident-response", Map.of(
            "componentId", event.getComponentId(),
            "incidentType", "INFRASTRUCTURE_BREACH",
            "breachType", event.getBreachType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Infrastructure Security Breach",
            String.format("EMERGENCY: Security breach in component %s - %s", event.getComponentId(), event.getBreachType()),
            Map.of("componentId", event.getComponentId(), "breachType", event.getBreachType(), "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordInfrastructureEvent("SECURITY_BREACH", "CRITICAL");
        log.error("Security breach processed: componentId={}, breachType={}", event.getComponentId(), event.getBreachType());
    }

    private void processComplianceViolation(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setComplianceDetails(event.getComplianceDetails());
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processComplianceViolation(event.getComponentId(), event.getComplianceDetails());

        // Send to compliance team
        kafkaTemplate.send("compliance-alerts", Map.of(
            "componentId", event.getComponentId(),
            "alertType", "INFRASTRUCTURE_COMPLIANCE_VIOLATION",
            "severity", event.getSeverity(),
            "complianceDetails", event.getComplianceDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendHighPriorityNotification("compliance-team",
            "Infrastructure Compliance Violation",
            String.format("Compliance violation in component %s - %s", event.getComponentId(), event.getComplianceDetails()),
            correlationId);

        metricsService.recordInfrastructureEvent("COMPLIANCE_VIOLATION", event.getSeverity());
        log.warn("Compliance violation processed: componentId={}, details={}", event.getComponentId(), event.getComplianceDetails());
    }

    private void processHardwareFailure(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setHardwareDetails(event.getHardwareDetails());
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processHardwareFailure(event.getComponentId(), event.getHardwareDetails());

        // Hardware failures can be critical
        if ("CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("infrastructure-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "HARDWARE_FAILURE",
                "severity", "CRITICAL",
                "hardwareDetails", event.getHardwareDetails(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureEvent("HARDWARE_FAILURE", event.getSeverity());
        log.warn("Hardware failure processed: componentId={}, details={}", event.getComponentId(), event.getHardwareDetails());
    }

    private void processSoftwareBug(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setBugDetails(event.getBugDetails());
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processSoftwareBug(event.getComponentId(), event.getBugDetails());

        // Send to development team for high severity bugs
        if ("HIGH".equals(event.getSeverity()) || "CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("development-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "SOFTWARE_BUG",
                "severity", event.getSeverity(),
                "bugDetails", event.getBugDetails(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureEvent("SOFTWARE_BUG", event.getSeverity());
        log.info("Software bug processed: componentId={}, details={}", event.getComponentId(), event.getBugDetails());
    }

    private void processConfigurationError(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        riskEvent.setConfigurationDetails(event.getConfigurationDetails());
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processConfigurationError(event.getComponentId(), event.getConfigurationDetails());

        metricsService.recordInfrastructureEvent("CONFIGURATION_ERROR", event.getSeverity());
        log.info("Configuration error processed: componentId={}, details={}", event.getComponentId(), event.getConfigurationDetails());
    }

    private void processGenericInfrastructureEvent(InfrastructureEvent event, String correlationId) {
        InfrastructureRiskEvent riskEvent = createInfrastructureRiskEvent(event, correlationId);
        infrastructureRiskEventRepository.save(riskEvent);

        infrastructureRiskService.processGenericInfrastructureEvent(event.getComponentId(), event.getEventType());

        metricsService.recordInfrastructureEvent("GENERIC", event.getSeverity());
        log.info("Generic infrastructure event processed: componentId={}, eventType={}", event.getComponentId(), event.getEventType());
    }

    private InfrastructureRiskEvent createInfrastructureRiskEvent(InfrastructureEvent event, String correlationId) {
        return InfrastructureRiskEvent.builder()
            .componentId(event.getComponentId())
            .component(event.getComponent())
            .eventType(event.getEventType())
            .severity(event.getSeverity())
            .detectedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .description(event.getDescription())
            .riskImpact(determineRiskImpact(event.getSeverity()))
            .build();
    }

    private String determineRiskImpact(String severity) {
        switch (severity.toUpperCase()) {
            case "CRITICAL": return "HIGH";
            case "HIGH": return "MEDIUM";
            case "MEDIUM": return "LOW";
            default: return "MINIMAL";
        }
    }
}