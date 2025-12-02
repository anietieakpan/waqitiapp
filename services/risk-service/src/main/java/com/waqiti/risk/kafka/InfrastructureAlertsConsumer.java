package com.waqiti.risk.kafka;

import com.waqiti.common.events.InfrastructureAlertEvent;
import com.waqiti.risk.domain.InfrastructureAlert;
import com.waqiti.risk.repository.InfrastructureAlertRepository;
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
public class InfrastructureAlertsConsumer {

    private final InfrastructureAlertRepository infrastructureAlertRepository;
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
        successCounter = Counter.builder("infrastructure_alerts_processed_total")
            .description("Total number of successfully processed infrastructure alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("infrastructure_alerts_errors_total")
            .description("Total number of infrastructure alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("infrastructure_alerts_processing_duration")
            .description("Time taken to process infrastructure alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"infrastructure-alerts"},
        groupId = "infrastructure-alerts-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "infrastructure-alerts", fallbackMethod = "handleInfrastructureAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInfrastructureAlertEvent(
            @Payload InfrastructureAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("infra-alert-%s-p%d-o%d", event.getComponentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getComponentId(), event.getAlertType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing infrastructure alert: componentId={}, alertType={}, severity={}",
                event.getComponentId(), event.getAlertType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getAlertType()) {
                case CRITICAL_SYSTEM_FAILURE:
                    processCriticalSystemFailureAlert(event, correlationId);
                    break;
                case PERFORMANCE_DEGRADATION:
                    processPerformanceDegradationAlert(event, correlationId);
                    break;
                case CAPACITY_THRESHOLD_BREACH:
                    processCapacityThresholdBreachAlert(event, correlationId);
                    break;
                case NETWORK_OUTAGE:
                    processNetworkOutageAlert(event, correlationId);
                    break;
                case DATA_CENTER_ISSUE:
                    processDataCenterIssueAlert(event, correlationId);
                    break;
                case INFRASTRUCTURE_SECURITY_BREACH:
                    processInfrastructureSecurityBreachAlert(event, correlationId);
                    break;
                case INFRASTRUCTURE_COMPLIANCE_VIOLATION:
                    processInfrastructureComplianceViolationAlert(event, correlationId);
                    break;
                case HARDWARE_FAILURE:
                    processHardwareFailureAlert(event, correlationId);
                    break;
                case SERVICE_UNAVAILABILITY:
                    processServiceUnavailabilityAlert(event, correlationId);
                    break;
                case DISASTER_RECOVERY_ACTIVATION:
                    processDisasterRecoveryActivationAlert(event, correlationId);
                    break;
                default:
                    log.warn("Unknown infrastructure alert type: {}", event.getAlertType());
                    processGenericInfrastructureAlert(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("INFRASTRUCTURE_ALERT_PROCESSED", event.getComponentId(),
                Map.of("alertType", event.getAlertType(), "severity", event.getSeverity(),
                    "component", event.getComponent(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process infrastructure alert event: {}", e.getMessage(), e);

            kafkaTemplate.send("infrastructure-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInfrastructureAlertEventFallback(
            InfrastructureAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("infra-alert-fallback-%s-p%d-o%d", event.getComponentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for infrastructure alert: componentId={}, error={}",
            event.getComponentId(), ex.getMessage());

        kafkaTemplate.send("infrastructure-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Infrastructure Alert Circuit Breaker Triggered",
                String.format("CRITICAL: Infrastructure alert processing failed for component %s: %s",
                    event.getComponentId(), ex.getMessage()),
                Map.of("componentId", event.getComponentId(), "alertType", event.getAlertType(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInfrastructureAlertEvent(
            @Payload InfrastructureAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-infra-alert-%s-%d", event.getComponentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Infrastructure alert permanently failed: componentId={}, topic={}, error={}",
            event.getComponentId(), topic, exceptionMessage);

        auditService.logRiskEvent("INFRASTRUCTURE_ALERT_DLT_EVENT", event.getComponentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "correlationId", correlationId,
                "requiresEmergencyIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "Infrastructure Alert Dead Letter Event",
                String.format("EMERGENCY: Infrastructure alert for component %s sent to DLT: %s",
                    event.getComponentId(), exceptionMessage),
                Map.of("componentId", event.getComponentId(), "alertType", event.getAlertType(),
                       "topic", topic, "correlationId", correlationId, "severity", "EMERGENCY")
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) return false;
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

    private void processCriticalSystemFailureAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setSeverity("CRITICAL");
        alert.setRequiresImmediateAction(true);
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processCriticalSystemFailure(event.getComponentId(), event.getFailureDetails());

        // Emergency response for critical system failures
        kafkaTemplate.send("emergency-response", Map.of(
            "emergencyType", "CRITICAL_INFRASTRUCTURE_FAILURE",
            "componentId", event.getComponentId(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Critical Infrastructure System Failure",
            String.format("EMERGENCY: Critical system failure in component %s", event.getComponentId()),
            Map.of("componentId", event.getComponentId(), "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordInfrastructureAlert("CRITICAL_SYSTEM_FAILURE", "CRITICAL");
        log.error("Critical system failure alert processed: componentId={}", event.getComponentId());
    }

    private void processPerformanceDegradationAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setPerformanceMetrics(event.getPerformanceMetrics());
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processPerformanceDegradationAlert(event.getComponentId(), event.getPerformanceMetrics());

        notificationService.sendHighPriorityNotification("infrastructure-team",
            "Performance Degradation Alert",
            String.format("Performance degradation detected in component %s", event.getComponentId()),
            correlationId);

        metricsService.recordInfrastructureAlert("PERFORMANCE_DEGRADATION", event.getSeverity());
        log.warn("Performance degradation alert processed: componentId={}", event.getComponentId());
    }

    private void processCapacityThresholdBreachAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setCapacityMetrics(event.getCapacityMetrics());
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processCapacityThresholdBreach(event.getComponentId(), event.getCapacityMetrics());

        kafkaTemplate.send("capacity-scaling-events", Map.of(
            "componentId", event.getComponentId(),
            "scalingAction", "EVALUATE_SCALING",
            "capacityMetrics", event.getCapacityMetrics(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordInfrastructureAlert("CAPACITY_THRESHOLD_BREACH", event.getSeverity());
        log.warn("Capacity threshold breach alert processed: componentId={}", event.getComponentId());
    }

    private void processNetworkOutageAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setNetworkDetails(event.getNetworkDetails());
        alert.setSeverity("CRITICAL");
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processNetworkOutage(event.getComponentId(), event.getNetworkDetails());

        notificationService.sendCriticalAlert(
            "Network Outage Alert",
            String.format("CRITICAL: Network outage in component %s", event.getComponentId()),
            Map.of("componentId", event.getComponentId(), "networkDetails", event.getNetworkDetails(), "correlationId", correlationId)
        );

        metricsService.recordInfrastructureAlert("NETWORK_OUTAGE", "CRITICAL");
        log.error("Network outage alert processed: componentId={}", event.getComponentId());
    }

    private void processDataCenterIssueAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setDataCenterDetails(event.getDataCenterDetails());
        alert.setSeverity("CRITICAL");
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processDataCenterIssue(event.getComponentId(), event.getDataCenterDetails());

        // Data center issues trigger DR planning
        kafkaTemplate.send("disaster-recovery-assessment", Map.of(
            "componentId", event.getComponentId(),
            "issueType", "DATA_CENTER_ISSUE",
            "dataCenterDetails", event.getDataCenterDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Data Center Issue Alert",
            String.format("EMERGENCY: Data center issue in component %s", event.getComponentId()),
            Map.of("componentId", event.getComponentId(), "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordInfrastructureAlert("DATA_CENTER_ISSUE", "CRITICAL");
        log.error("Data center issue alert processed: componentId={}", event.getComponentId());
    }

    private void processInfrastructureSecurityBreachAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setSecurityDetails(event.getSecurityDetails());
        alert.setSeverity("CRITICAL");
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processSecurityBreach(event.getComponentId(), event.getSecurityDetails());

        kafkaTemplate.send("security-incident-response", Map.of(
            "componentId", event.getComponentId(),
            "incidentType", "INFRASTRUCTURE_SECURITY_BREACH",
            "securityDetails", event.getSecurityDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Infrastructure Security Breach",
            String.format("EMERGENCY: Security breach in infrastructure component %s", event.getComponentId()),
            Map.of("componentId", event.getComponentId(), "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordInfrastructureAlert("SECURITY_BREACH", "CRITICAL");
        log.error("Infrastructure security breach alert processed: componentId={}", event.getComponentId());
    }

    private void processInfrastructureComplianceViolationAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setComplianceDetails(event.getComplianceDetails());
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processComplianceViolation(event.getComponentId(), event.getComplianceDetails());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "componentId", event.getComponentId(),
            "alertType", "INFRASTRUCTURE_COMPLIANCE_VIOLATION",
            "complianceDetails", event.getComplianceDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordInfrastructureAlert("COMPLIANCE_VIOLATION", event.getSeverity());
        log.warn("Infrastructure compliance violation alert processed: componentId={}", event.getComponentId());
    }

    private void processHardwareFailureAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setHardwareDetails(event.getHardwareDetails());
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processHardwareFailure(event.getComponentId(), event.getHardwareDetails());

        metricsService.recordInfrastructureAlert("HARDWARE_FAILURE", event.getSeverity());
        log.warn("Hardware failure alert processed: componentId={}", event.getComponentId());
    }

    private void processServiceUnavailabilityAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setServiceDetails(event.getServiceDetails());
        alert.setSeverity("HIGH");
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processServiceUnavailability(event.getComponentId(), event.getServiceDetails());

        kafkaTemplate.send("service-recovery-events", Map.of(
            "componentId", event.getComponentId(),
            "recoveryAction", "INITIATE_SERVICE_RECOVERY",
            "serviceDetails", event.getServiceDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordInfrastructureAlert("SERVICE_UNAVAILABILITY", "HIGH");
        log.warn("Service unavailability alert processed: componentId={}", event.getComponentId());
    }

    private void processDisasterRecoveryActivationAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        alert.setDisasterRecoveryDetails(event.getDisasterRecoveryDetails());
        alert.setSeverity("CRITICAL");
        alert.setRequiresImmediateAction(true);
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processDisasterRecoveryActivation(event.getComponentId(), event.getDisasterRecoveryDetails());

        notificationService.sendEmergencyAlert(
            "Disaster Recovery Activation",
            String.format("EMERGENCY: Disaster recovery activated for component %s", event.getComponentId()),
            Map.of("componentId", event.getComponentId(), "drDetails", event.getDisasterRecoveryDetails(),
                   "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordInfrastructureAlert("DISASTER_RECOVERY_ACTIVATION", "CRITICAL");
        log.error("Disaster recovery activation alert processed: componentId={}", event.getComponentId());
    }

    private void processGenericInfrastructureAlert(InfrastructureAlertEvent event, String correlationId) {
        InfrastructureAlert alert = createInfrastructureAlert(event, correlationId);
        infrastructureAlertRepository.save(alert);

        infrastructureRiskService.processGenericInfrastructureAlert(event.getComponentId(), event.getAlertType());

        metricsService.recordInfrastructureAlert("GENERIC", event.getSeverity());
        log.info("Generic infrastructure alert processed: componentId={}, alertType={}",
            event.getComponentId(), event.getAlertType());
    }

    private InfrastructureAlert createInfrastructureAlert(InfrastructureAlertEvent event, String correlationId) {
        return InfrastructureAlert.builder()
            .componentId(event.getComponentId())
            .component(event.getComponent())
            .alertType(event.getAlertType())
            .severity(event.getSeverity())
            .raisedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .description(event.getDescription())
            .impact(determineImpact(event.getSeverity()))
            .build();
    }

    private String determineImpact(String severity) {
        switch (severity.toUpperCase()) {
            case "CRITICAL": return "BUSINESS_CRITICAL";
            case "HIGH": return "HIGH_IMPACT";
            case "MEDIUM": return "MEDIUM_IMPACT";
            default: return "LOW_IMPACT";
        }
    }
}