package com.waqiti.risk.kafka;

import com.waqiti.common.events.InfrastructureStatusEvent;
import com.waqiti.risk.domain.InfrastructureStatusLog;
import com.waqiti.risk.repository.InfrastructureStatusLogRepository;
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
public class InfrastructureStatusEventsConsumer {

    private final InfrastructureStatusLogRepository statusLogRepository;
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
        successCounter = Counter.builder("infrastructure_status_events_processed_total")
            .description("Total number of successfully processed infrastructure status events")
            .register(meterRegistry);
        errorCounter = Counter.builder("infrastructure_status_events_errors_total")
            .description("Total number of infrastructure status event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("infrastructure_status_events_processing_duration")
            .description("Time taken to process infrastructure status events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"infrastructure-status-events"},
        groupId = "infrastructure-status-events-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "infrastructure-status-events", fallbackMethod = "handleInfrastructureStatusEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInfrastructureStatusEvent(
            @Payload InfrastructureStatusEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("infra-status-%s-p%d-o%d", event.getComponentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getComponentId(), event.getStatusType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.debug("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing infrastructure status event: componentId={}, statusType={}, status={}",
                event.getComponentId(), event.getStatusType(), event.getStatus());

            cleanExpiredEntries();

            switch (event.getStatusType()) {
                case HEALTH_CHECK:
                    processHealthCheckStatus(event, correlationId);
                    break;
                case PERFORMANCE_METRICS:
                    processPerformanceMetricsStatus(event, correlationId);
                    break;
                case CAPACITY_UTILIZATION:
                    processCapacityUtilizationStatus(event, correlationId);
                    break;
                case AVAILABILITY_STATUS:
                    processAvailabilityStatus(event, correlationId);
                    break;
                case RESOURCE_UTILIZATION:
                    processResourceUtilizationStatus(event, correlationId);
                    break;
                case SECURITY_STATUS:
                    processSecurityStatus(event, correlationId);
                    break;
                case BACKUP_STATUS:
                    processBackupStatus(event, correlationId);
                    break;
                case MAINTENANCE_STATUS:
                    processMaintenanceStatus(event, correlationId);
                    break;
                case COMPLIANCE_STATUS:
                    processComplianceStatus(event, correlationId);
                    break;
                case DISASTER_RECOVERY_STATUS:
                    processDisasterRecoveryStatus(event, correlationId);
                    break;
                default:
                    log.debug("Unknown infrastructure status type: {}", event.getStatusType());
                    processGenericStatusEvent(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("INFRASTRUCTURE_STATUS_PROCESSED", event.getComponentId(),
                Map.of("statusType", event.getStatusType(), "status", event.getStatus(),
                    "component", event.getComponent(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process infrastructure status event: {}", e.getMessage(), e);

            kafkaTemplate.send("infrastructure-status-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInfrastructureStatusEventFallback(
            InfrastructureStatusEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("infra-status-fallback-%s-p%d-o%d", event.getComponentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for infrastructure status event: componentId={}, error={}",
            event.getComponentId(), ex.getMessage());

        kafkaTemplate.send("infrastructure-status-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Infrastructure Status Event Circuit Breaker Triggered",
                String.format("Infrastructure status event processing failed for component %s: %s",
                    event.getComponentId(), ex.getMessage()),
                "MEDIUM"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInfrastructureStatusEvent(
            @Payload InfrastructureStatusEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-infra-status-%s-%d", event.getComponentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Infrastructure status event permanently failed: componentId={}, topic={}, error={}",
            event.getComponentId(), topic, exceptionMessage);

        auditService.logRiskEvent("INFRASTRUCTURE_STATUS_DLT_EVENT", event.getComponentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "statusType", event.getStatusType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendHighPriorityNotification("infrastructure-ops-team",
                "Infrastructure Status Event Dead Letter Event",
                String.format("Infrastructure status event for component %s sent to DLT: %s",
                    event.getComponentId(), exceptionMessage),
                correlationId
            );
        } catch (Exception ex) {
            log.error("Failed to send high priority DLT alert: {}", ex.getMessage());
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

    private void processHealthCheckStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setHealthStatus(event.getStatus());
        statusLog.setHealthMetrics(event.getHealthMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateHealthStatus(event.getComponentId(), event.getStatus(), event.getHealthMetrics());

        if ("UNHEALTHY".equals(event.getStatus()) || "DEGRADED".equals(event.getStatus())) {
            kafkaTemplate.send("infrastructure-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "HEALTH_CHECK_FAILURE",
                "severity", "UNHEALTHY".equals(event.getStatus()) ? "HIGH" : "MEDIUM",
                "healthMetrics", event.getHealthMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureStatus("HEALTH_CHECK", event.getStatus());
        log.info("Health check status processed: componentId={}, status={}", event.getComponentId(), event.getStatus());
    }

    private void processPerformanceMetricsStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setPerformanceMetrics(event.getPerformanceMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updatePerformanceMetrics(event.getComponentId(), event.getPerformanceMetrics());

        // Check for performance degradation thresholds
        if (isPerformanceDegraded(event.getPerformanceMetrics())) {
            kafkaTemplate.send("infrastructure-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "PERFORMANCE_DEGRADATION",
                "severity", "MEDIUM",
                "performanceMetrics", event.getPerformanceMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureStatus("PERFORMANCE_METRICS", "UPDATED");
        log.debug("Performance metrics status processed: componentId={}", event.getComponentId());
    }

    private void processCapacityUtilizationStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setCapacityMetrics(event.getCapacityMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateCapacityUtilization(event.getComponentId(), event.getCapacityMetrics());

        // Check capacity thresholds
        if (isCapacityThresholdBreached(event.getCapacityMetrics())) {
            kafkaTemplate.send("infrastructure-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "CAPACITY_THRESHOLD_BREACH",
                "severity", "HIGH",
                "capacityMetrics", event.getCapacityMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureStatus("CAPACITY_UTILIZATION", "UPDATED");
        log.debug("Capacity utilization status processed: componentId={}", event.getComponentId());
    }

    private void processAvailabilityStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setAvailabilityStatus(event.getStatus());
        statusLog.setAvailabilityMetrics(event.getAvailabilityMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateAvailabilityStatus(event.getComponentId(), event.getStatus(), event.getAvailabilityMetrics());

        if ("DOWN".equals(event.getStatus()) || "PARTIAL".equals(event.getStatus())) {
            kafkaTemplate.send("infrastructure-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "SERVICE_UNAVAILABILITY",
                "severity", "DOWN".equals(event.getStatus()) ? "CRITICAL" : "HIGH",
                "availabilityMetrics", event.getAvailabilityMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureStatus("AVAILABILITY_STATUS", event.getStatus());
        log.info("Availability status processed: componentId={}, status={}", event.getComponentId(), event.getStatus());
    }

    private void processResourceUtilizationStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setResourceMetrics(event.getResourceMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateResourceUtilization(event.getComponentId(), event.getResourceMetrics());

        metricsService.recordInfrastructureStatus("RESOURCE_UTILIZATION", "UPDATED");
        log.debug("Resource utilization status processed: componentId={}", event.getComponentId());
    }

    private void processSecurityStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setSecurityStatus(event.getStatus());
        statusLog.setSecurityMetrics(event.getSecurityMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateSecurityStatus(event.getComponentId(), event.getStatus(), event.getSecurityMetrics());

        if ("COMPROMISED".equals(event.getStatus()) || "VULNERABLE".equals(event.getStatus())) {
            kafkaTemplate.send("security-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "INFRASTRUCTURE_SECURITY_STATUS",
                "severity", "COMPROMISED".equals(event.getStatus()) ? "CRITICAL" : "HIGH",
                "securityMetrics", event.getSecurityMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureStatus("SECURITY_STATUS", event.getStatus());
        log.info("Security status processed: componentId={}, status={}", event.getComponentId(), event.getStatus());
    }

    private void processBackupStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setBackupStatus(event.getStatus());
        statusLog.setBackupMetrics(event.getBackupMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateBackupStatus(event.getComponentId(), event.getStatus(), event.getBackupMetrics());

        if ("FAILED".equals(event.getStatus()) || "PARTIAL".equals(event.getStatus())) {
            kafkaTemplate.send("backup-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "BACKUP_STATUS_ISSUE",
                "severity", "FAILED".equals(event.getStatus()) ? "HIGH" : "MEDIUM",
                "backupMetrics", event.getBackupMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureStatus("BACKUP_STATUS", event.getStatus());
        log.info("Backup status processed: componentId={}, status={}", event.getComponentId(), event.getStatus());
    }

    private void processMaintenanceStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setMaintenanceStatus(event.getStatus());
        statusLog.setMaintenanceDetails(event.getMaintenanceDetails());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateMaintenanceStatus(event.getComponentId(), event.getStatus(), event.getMaintenanceDetails());

        metricsService.recordInfrastructureStatus("MAINTENANCE_STATUS", event.getStatus());
        log.info("Maintenance status processed: componentId={}, status={}", event.getComponentId(), event.getStatus());
    }

    private void processComplianceStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setComplianceStatus(event.getStatus());
        statusLog.setComplianceMetrics(event.getComplianceMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateComplianceStatus(event.getComponentId(), event.getStatus(), event.getComplianceMetrics());

        if ("NON_COMPLIANT".equals(event.getStatus()) || "PARTIAL_COMPLIANCE".equals(event.getStatus())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "INFRASTRUCTURE_COMPLIANCE_STATUS",
                "severity", "NON_COMPLIANT".equals(event.getStatus()) ? "HIGH" : "MEDIUM",
                "complianceMetrics", event.getComplianceMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureStatus("COMPLIANCE_STATUS", event.getStatus());
        log.info("Compliance status processed: componentId={}, status={}", event.getComponentId(), event.getStatus());
    }

    private void processDisasterRecoveryStatus(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLog.setDisasterRecoveryStatus(event.getStatus());
        statusLog.setDisasterRecoveryMetrics(event.getDisasterRecoveryMetrics());
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateDisasterRecoveryStatus(event.getComponentId(), event.getStatus(), event.getDisasterRecoveryMetrics());

        if ("FAILED".equals(event.getStatus()) || "PARTIAL".equals(event.getStatus())) {
            kafkaTemplate.send("disaster-recovery-alerts", Map.of(
                "componentId", event.getComponentId(),
                "alertType", "DR_STATUS_ISSUE",
                "severity", "FAILED".equals(event.getStatus()) ? "CRITICAL" : "HIGH",
                "drMetrics", event.getDisasterRecoveryMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordInfrastructureStatus("DISASTER_RECOVERY_STATUS", event.getStatus());
        log.info("Disaster recovery status processed: componentId={}, status={}", event.getComponentId(), event.getStatus());
    }

    private void processGenericStatusEvent(InfrastructureStatusEvent event, String correlationId) {
        InfrastructureStatusLog statusLog = createStatusLog(event, correlationId);
        statusLogRepository.save(statusLog);

        infrastructureRiskService.updateGenericStatus(event.getComponentId(), event.getStatusType(), event.getStatus());

        metricsService.recordInfrastructureStatus("GENERIC", event.getStatus());
        log.debug("Generic status event processed: componentId={}, statusType={}, status={}",
            event.getComponentId(), event.getStatusType(), event.getStatus());
    }

    private InfrastructureStatusLog createStatusLog(InfrastructureStatusEvent event, String correlationId) {
        return InfrastructureStatusLog.builder()
            .componentId(event.getComponentId())
            .component(event.getComponent())
            .statusType(event.getStatusType())
            .status(event.getStatus())
            .recordedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .description(event.getDescription())
            .build();
    }

    private boolean isPerformanceDegraded(Map<String, Object> performanceMetrics) {
        if (performanceMetrics == null) return false;
        // Simple threshold check - could be more sophisticated
        Object cpuUsage = performanceMetrics.get("cpuUsage");
        Object responseTime = performanceMetrics.get("responseTime");

        return (cpuUsage instanceof Number && ((Number) cpuUsage).doubleValue() > 80.0) ||
               (responseTime instanceof Number && ((Number) responseTime).doubleValue() > 5000.0);
    }

    private boolean isCapacityThresholdBreached(Map<String, Object> capacityMetrics) {
        if (capacityMetrics == null) return false;
        // Simple threshold check
        Object utilization = capacityMetrics.get("utilization");
        return utilization instanceof Number && ((Number) utilization).doubleValue() > 85.0;
    }
}