package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.SystemHealthEvent;
import com.waqiti.monitoring.domain.SystemHealthRecord;
import com.waqiti.monitoring.repository.SystemHealthRecordRepository;
import com.waqiti.monitoring.service.SystemHealthService;
import com.waqiti.monitoring.service.HealthRecoveryService;
import com.waqiti.monitoring.service.AlertService;
import com.waqiti.monitoring.metrics.SystemMetricsService;
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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class SystemHealthEventsConsumer {

    private final SystemHealthRecordRepository healthRecordRepository;
    private final SystemHealthService healthService;
    private final HealthRecoveryService recoveryService;
    private final AlertService alertService;
    private final SystemMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // System recovery tracking
    private final AtomicLong criticalSystemIssues = new AtomicLong(0);
    private final AtomicLong systemRecoveryCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter criticalIssuesCounter;
    private Timer processingTimer;
    private Gauge systemHealthGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("system_health_events_processed_total")
            .description("Total number of successfully processed system health events")
            .register(meterRegistry);
        errorCounter = Counter.builder("system_health_events_errors_total")
            .description("Total number of system health event processing errors")
            .register(meterRegistry);
        criticalIssuesCounter = Counter.builder("system_health_critical_issues_total")
            .description("Total number of critical system health issues")
            .register(meterRegistry);
        processingTimer = Timer.builder("system_health_events_processing_duration")
            .description("Time taken to process system health events")
            .register(meterRegistry);
        systemHealthGauge = Gauge.builder("system_health_critical_issues_active")
            .description("Number of active critical system health issues")
            .register(meterRegistry, criticalSystemIssues, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"system-health-events", "component-health-alerts", "service-availability-events"},
        groupId = "system-health-events-service-group",
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
    @CircuitBreaker(name = "system-health-events", fallbackMethod = "handleSystemHealthEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleSystemHealthEvent(
            @Payload SystemHealthEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("health-%s-p%d-o%d", event.getComponentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getComponentId(), event.getHealthStatus(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing system health event: componentId={}, healthStatus={}, severity={}, serviceName={}",
                event.getComponentId(), event.getHealthStatus(), event.getSeverity(), event.getServiceName());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // System recovery impact assessment
            assessSystemRecoveryImpact(event, correlationId);

            switch (event.getHealthStatus()) {
                case HEALTHY:
                    handleHealthyStatus(event, correlationId);
                    break;

                case DEGRADED:
                    handleDegradedStatus(event, correlationId);
                    break;

                case UNHEALTHY:
                    handleUnhealthyStatus(event, correlationId);
                    break;

                case CRITICAL:
                    handleCriticalStatus(event, correlationId);
                    break;

                case RECOVERING:
                    handleRecoveringStatus(event, correlationId);
                    break;

                case MAINTENANCE:
                    handleMaintenanceStatus(event, correlationId);
                    break;

                case UNKNOWN:
                    handleUnknownStatus(event, correlationId);
                    break;

                default:
                    log.warn("Unknown system health status: {}", event.getHealthStatus());
                    handleGenericHealthEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSystemHealthEvent("SYSTEM_HEALTH_EVENT_PROCESSED", event.getComponentId(),
                Map.of("healthStatus", event.getHealthStatus(), "severity", event.getSeverity(),
                    "serviceName", event.getServiceName(), "healthMetrics", event.getHealthMetrics(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process system health event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("system-health-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleSystemHealthEventFallback(
            SystemHealthEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("health-fallback-%s-p%d-o%d", event.getComponentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for system health event: componentId={}, error={}",
            event.getComponentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("system-health-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for critical components
        if ("CRITICAL".equals(event.getSeverity())) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical System Health Event - Circuit Breaker Triggered",
                    String.format("Critical system component %s health monitoring failed: %s",
                        event.getComponentId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltSystemHealthEvent(
            @Payload SystemHealthEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-health-%s-%d", event.getComponentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - System health event permanently failed: componentId={}, topic={}, error={}",
            event.getComponentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSystemHealthEvent("SYSTEM_HEALTH_DLT_EVENT", event.getComponentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "healthStatus", event.getHealthStatus(), "severity", event.getSeverity(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "System Health Event Dead Letter Event",
                String.format("System health monitoring for %s sent to DLT: %s",
                    event.getComponentId(), exceptionMessage),
                Map.of("componentId", event.getComponentId(), "topic", topic,
                    "correlationId", correlationId, "severity", event.getSeverity())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessSystemRecoveryImpact(SystemHealthEvent event, String correlationId) {
        if ("CRITICAL".equals(event.getSeverity()) &&
            ("UNHEALTHY".equals(event.getHealthStatus()) || "CRITICAL".equals(event.getHealthStatus()))) {
            criticalSystemIssues.incrementAndGet();
            criticalIssuesCounter.increment();

            // Alert if too many critical issues
            if (criticalSystemIssues.get() > 5) {
                try {
                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Multiple System Health Issues",
                        String.format("Critical system issues count: %d. Emergency response required.",
                            criticalSystemIssues.get()),
                        "CRITICAL"
                    );
                } catch (Exception ex) {
                    log.error("Failed to send system recovery impact alert: {}", ex.getMessage());
                }
            }
        }

        if ("HEALTHY".equals(event.getHealthStatus()) || "RECOVERING".equals(event.getHealthStatus())) {
            long currentIssues = criticalSystemIssues.get();
            if (currentIssues > 0) {
                criticalSystemIssues.decrementAndGet();
                systemRecoveryCount.incrementAndGet();
            }
        }
    }

    private void handleHealthyStatus(SystemHealthEvent event, String correlationId) {
        createHealthRecord(event, "HEALTHY", correlationId);

        // Clear any existing alerts for this component
        kafkaTemplate.send("alert-resolution-requests", Map.of(
            "componentId", event.getComponentId(),
            "resolutionType", "HEALTH_RESTORED",
            "healthStatus", "HEALTHY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update component health status
        healthService.updateComponentHealth(event.getComponentId(), "HEALTHY", event.getHealthMetrics());

        // Cancel any ongoing recovery actions
        kafkaTemplate.send("recovery-action-cancellation", Map.of(
            "componentId", event.getComponentId(),
            "cancellationType", "HEALTH_RESTORED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("System component {} restored to healthy status", event.getComponentId());
        metricsService.recordHealthStatusChange("HEALTHY", event.getComponentId());
    }

    private void handleDegradedStatus(SystemHealthEvent event, String correlationId) {
        createHealthRecord(event, "DEGRADED", correlationId);

        // Trigger performance optimization
        kafkaTemplate.send("performance-optimization-requests", Map.of(
            "componentId", event.getComponentId(),
            "optimizationType", "DEGRADED_PERFORMANCE",
            "healthMetrics", event.getHealthMetrics(),
            "priority", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Monitor for escalation
        kafkaTemplate.send("health-monitoring-escalation", Map.of(
            "componentId", event.getComponentId(),
            "monitoringType", "DEGRADATION_TRACKING",
            "escalationThreshold", "5_MINUTES",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send alert if degradation persists
        if (healthService.getDegradationDuration(event.getComponentId()) > 300000) { // 5 minutes
            notificationService.sendOperationalAlert("System Component Degraded",
                String.format("Component %s has been degraded for over 5 minutes",
                    event.getComponentId()),
                "MEDIUM");
        }

        metricsService.recordHealthStatusChange("DEGRADED", event.getComponentId());
    }

    private void handleUnhealthyStatus(SystemHealthEvent event, String correlationId) {
        createHealthRecord(event, "UNHEALTHY", correlationId);

        // Immediate health assessment
        kafkaTemplate.send("immediate-health-assessment", Map.of(
            "componentId", event.getComponentId(),
            "assessmentType", "UNHEALTHY_DIAGNOSIS",
            "healthMetrics", event.getHealthMetrics(),
            "errorDetails", event.getErrorDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Trigger automatic recovery
        kafkaTemplate.send("automatic-recovery-requests", Map.of(
            "componentId", event.getComponentId(),
            "recoveryType", "UNHEALTHY_RECOVERY",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Failover to backup if available
        if (healthService.hasBackupComponent(event.getComponentId())) {
            kafkaTemplate.send("component-failover-requests", Map.of(
                "primaryComponent", event.getComponentId(),
                "failoverType", "UNHEALTHY_FAILOVER",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendOperationalAlert("System Component Unhealthy",
            String.format("Component %s is unhealthy: %s",
                event.getComponentId(), event.getErrorDetails()),
            "HIGH");

        metricsService.recordHealthStatusChange("UNHEALTHY", event.getComponentId());
    }

    private void handleCriticalStatus(SystemHealthEvent event, String correlationId) {
        createHealthRecord(event, "CRITICAL", correlationId);

        // Emergency response
        kafkaTemplate.send("emergency-response-requests", Map.of(
            "componentId", event.getComponentId(),
            "responseType", "CRITICAL_SYSTEM_FAILURE",
            "healthMetrics", event.getHealthMetrics(),
            "errorDetails", event.getErrorDetails(),
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Immediate failover
        kafkaTemplate.send("emergency-failover-requests", Map.of(
            "componentId", event.getComponentId(),
            "failoverType", "CRITICAL_EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Isolate component to prevent cascade failure
        kafkaTemplate.send("component-isolation-requests", Map.of(
            "componentId", event.getComponentId(),
            "isolationType", "CRITICAL_FAILURE_ISOLATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Executive escalation
        notificationService.sendExecutiveAlert("CRITICAL System Component Failure",
            String.format("CRITICAL failure in component %s: %s. Immediate intervention required.",
                event.getComponentId(), event.getErrorDetails()),
            "CRITICAL");

        metricsService.recordHealthStatusChange("CRITICAL", event.getComponentId());
    }

    private void handleRecoveringStatus(SystemHealthEvent event, String correlationId) {
        createHealthRecord(event, "RECOVERING", correlationId);

        // Monitor recovery progress
        kafkaTemplate.send("recovery-progress-monitoring", Map.of(
            "componentId", event.getComponentId(),
            "monitoringType", "RECOVERY_TRACKING",
            "recoveryMetrics", event.getRecoveryMetrics(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Gradually restore traffic if applicable
        kafkaTemplate.send("traffic-restoration-requests", Map.of(
            "componentId", event.getComponentId(),
            "restorationType", "GRADUAL_RECOVERY",
            "trafficPercentage", 25, // Start with 25%
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Set recovery timeout
        kafkaTemplate.send("recovery-timeout-scheduling", Map.of(
            "componentId", event.getComponentId(),
            "timeoutDuration", 1800000, // 30 minutes
            "timeoutAction", "ESCALATE_RECOVERY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Component {} is recovering, monitoring progress", event.getComponentId());
        metricsService.recordHealthStatusChange("RECOVERING", event.getComponentId());
    }

    private void handleMaintenanceStatus(SystemHealthEvent event, String correlationId) {
        createHealthRecord(event, "MAINTENANCE", correlationId);

        // Verify scheduled maintenance
        kafkaTemplate.send("maintenance-verification", Map.of(
            "componentId", event.getComponentId(),
            "verificationType", "SCHEDULED_MAINTENANCE",
            "maintenanceWindow", event.getMaintenanceWindow(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Redirect traffic if needed
        kafkaTemplate.send("traffic-redirection-requests", Map.of(
            "componentId", event.getComponentId(),
            "redirectionType", "MAINTENANCE_MODE",
            "estimatedDuration", event.getMaintenanceDuration(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule maintenance completion check
        kafkaTemplate.send("maintenance-completion-scheduling", Map.of(
            "componentId", event.getComponentId(),
            "scheduledCompletion", event.getScheduledCompletionTime(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordHealthStatusChange("MAINTENANCE", event.getComponentId());
    }

    private void handleUnknownStatus(SystemHealthEvent event, String correlationId) {
        createHealthRecord(event, "UNKNOWN", correlationId);

        // Diagnostic health check
        kafkaTemplate.send("diagnostic-health-checks", Map.of(
            "componentId", event.getComponentId(),
            "diagnosticType", "UNKNOWN_STATUS_INVESTIGATION",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Attempt status determination
        kafkaTemplate.send("status-determination-requests", Map.of(
            "componentId", event.getComponentId(),
            "determinationType", "COMPREHENSIVE_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Unknown System Component Status",
            String.format("Component %s has unknown status, investigating",
                event.getComponentId()),
            "MEDIUM");

        metricsService.recordHealthStatusChange("UNKNOWN", event.getComponentId());
    }

    private void handleGenericHealthEvent(SystemHealthEvent event, String correlationId) {
        createHealthRecord(event, "GENERIC", correlationId);

        // Log for investigation
        auditService.logSystemHealthEvent("UNKNOWN_HEALTH_STATUS", event.getComponentId(),
            Map.of("healthStatus", event.getHealthStatus(), "severity", event.getSeverity(),
                "healthMetrics", event.getHealthMetrics(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown System Health Status",
            String.format("Unknown health status for component %s: %s",
                event.getComponentId(), event.getHealthStatus()),
            "MEDIUM");

        metricsService.recordHealthStatusChange("GENERIC", event.getComponentId());
    }

    private void createHealthRecord(SystemHealthEvent event, String status, String correlationId) {
        try {
            SystemHealthRecord record = SystemHealthRecord.builder()
                .componentId(event.getComponentId())
                .serviceName(event.getServiceName())
                .healthStatus(status)
                .severity(event.getSeverity())
                .healthMetrics(event.getHealthMetrics())
                .errorDetails(event.getErrorDetails())
                .checkTime(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

            healthRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to create system health record: {}", e.getMessage());
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
}