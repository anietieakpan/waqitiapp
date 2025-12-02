package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.CapacityAlertEvent;
import com.waqiti.monitoring.domain.CapacityRecord;
import com.waqiti.monitoring.repository.CapacityRecordRepository;
import com.waqiti.monitoring.service.CapacityPlanningService;
import com.waqiti.monitoring.service.ResourceScalingService;
import com.waqiti.monitoring.service.CapacityForecastService;
import com.waqiti.monitoring.metrics.CapacityMetricsService;
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
import java.util.concurrent.atomic.AtomicDouble;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class CapacityAlertsConsumer {

    private final CapacityRecordRepository capacityRepository;
    private final CapacityPlanningService planningService;
    private final ResourceScalingService scalingService;
    private final CapacityForecastService forecastService;
    private final CapacityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Capacity tracking
    private final AtomicLong capacityWarningCount = new AtomicLong(0);
    private final AtomicDouble averageCapacityUtilization = new AtomicDouble(0.0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter capacityWarningCounter;
    private Timer processingTimer;
    private Gauge capacityWarningGauge;
    private Gauge avgCapacityUtilizationGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("capacity_alerts_processed_total")
            .description("Total number of successfully processed capacity alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("capacity_alerts_errors_total")
            .description("Total number of capacity alert processing errors")
            .register(meterRegistry);
        capacityWarningCounter = Counter.builder("capacity_warnings_total")
            .description("Total number of capacity warnings")
            .register(meterRegistry);
        processingTimer = Timer.builder("capacity_alerts_processing_duration")
            .description("Time taken to process capacity alert events")
            .register(meterRegistry);
        capacityWarningGauge = Gauge.builder("capacity_warnings_active")
            .description("Number of active capacity warnings")
            .register(meterRegistry, capacityWarningCount, AtomicLong::get);
        avgCapacityUtilizationGauge = Gauge.builder("average_capacity_utilization_percent")
            .description("Average capacity utilization across all monitored resources")
            .register(meterRegistry, averageCapacityUtilization, AtomicDouble::get);
    }

    @KafkaListener(
        topics = {"capacity-alerts", "resource-capacity-warnings", "scaling-threshold-events"},
        groupId = "capacity-alerts-service-group",
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
    @CircuitBreaker(name = "capacity-alerts", fallbackMethod = "handleCapacityAlertFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCapacityAlertEvent(
            @Payload CapacityAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("capacity-%s-p%d-o%d", event.getResourceId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getResourceId(), event.getAlertType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing capacity alert: resourceId={}, alertType={}, utilizationPercent={}, threshold={}",
                event.getResourceId(), event.getAlertType(), event.getUtilizationPercent(), event.getThresholdPercent());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Capacity impact assessment
            assessCapacityImpact(event, correlationId);

            switch (event.getAlertType()) {
                case HIGH_UTILIZATION:
                    handleHighUtilizationAlert(event, correlationId);
                    break;

                case CRITICAL_UTILIZATION:
                    handleCriticalUtilizationAlert(event, correlationId);
                    break;

                case CAPACITY_EXHAUSTION_WARNING:
                    handleCapacityExhaustionWarning(event, correlationId);
                    break;

                case SCALING_THRESHOLD_REACHED:
                    handleScalingThresholdReached(event, correlationId);
                    break;

                case RESOURCE_UNAVAILABLE:
                    handleResourceUnavailable(event, correlationId);
                    break;

                case FORECAST_CAPACITY_SHORTAGE:
                    handleForecastCapacityShortage(event, correlationId);
                    break;

                case BURST_CAPACITY_ACTIVATED:
                    handleBurstCapacityActivated(event, correlationId);
                    break;

                case CAPACITY_RESTORED:
                    handleCapacityRestored(event, correlationId);
                    break;

                default:
                    log.warn("Unknown capacity alert type: {}", event.getAlertType());
                    handleGenericCapacityAlert(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCapacityEvent("CAPACITY_ALERT_PROCESSED", event.getResourceId(),
                Map.of("alertType", event.getAlertType(), "utilizationPercent", event.getUtilizationPercent(),
                    "thresholdPercent", event.getThresholdPercent(), "resourceType", event.getResourceType(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process capacity alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("capacity-alert-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCapacityAlertFallback(
            CapacityAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("capacity-fallback-%s-p%d-o%d", event.getResourceId(), partition, offset);

        log.error("Circuit breaker fallback triggered for capacity alert: resourceId={}, error={}",
            event.getResourceId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("capacity-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for capacity exhaustion
        if ("CRITICAL_UTILIZATION".equals(event.getAlertType()) ||
            "CAPACITY_EXHAUSTION_WARNING".equals(event.getAlertType())) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Capacity Alert - Circuit Breaker Triggered",
                    String.format("Critical capacity monitoring for %s failed: %s",
                        event.getResourceId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCapacityAlertEvent(
            @Payload CapacityAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-capacity-%s-%d", event.getResourceId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Capacity alert permanently failed: resourceId={}, topic={}, error={}",
            event.getResourceId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCapacityEvent("CAPACITY_ALERT_DLT_EVENT", event.getResourceId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "utilizationPercent", event.getUtilizationPercent(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Capacity Alert Dead Letter Event",
                String.format("Capacity monitoring for %s sent to DLT: %s",
                    event.getResourceId(), exceptionMessage),
                Map.of("resourceId", event.getResourceId(), "topic", topic,
                    "correlationId", correlationId, "alertType", event.getAlertType())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessCapacityImpact(CapacityAlertEvent event, String correlationId) {
        if (event.getUtilizationPercent() > 80.0) {
            capacityWarningCount.incrementAndGet();
            capacityWarningCounter.increment();

            // Update average utilization
            double currentAvg = averageCapacityUtilization.get();
            double newAvg = (currentAvg + event.getUtilizationPercent()) / 2.0;
            averageCapacityUtilization.set(newAvg);

            // Alert if too many capacity warnings
            if (capacityWarningCount.get() > 5) {
                try {
                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Multiple Capacity Warnings",
                        String.format("Capacity warnings count: %d. Infrastructure scaling required.",
                            capacityWarningCount.get()),
                        "CRITICAL"
                    );
                    // Reset counter after alert
                    capacityWarningCount.set(0);
                } catch (Exception ex) {
                    log.error("Failed to send capacity impact alert: {}", ex.getMessage());
                }
            }
        }
    }

    private void handleHighUtilizationAlert(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "HIGH_UTILIZATION", correlationId);

        // Start capacity monitoring
        kafkaTemplate.send("capacity-monitoring-requests", Map.of(
            "resourceId", event.getResourceId(),
            "monitoringType", "HIGH_UTILIZATION_TRACKING",
            "currentUtilization", event.getUtilizationPercent(),
            "threshold", event.getThresholdPercent(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Plan for scaling
        kafkaTemplate.send("scaling-preparation-requests", Map.of(
            "resourceId", event.getResourceId(),
            "preparationType", "HIGH_UTILIZATION_SCALING",
            "resourceType", event.getResourceType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("High Resource Utilization",
            String.format("Resource %s utilization: %.1f%% (threshold: %.1f%%)",
                event.getResourceId(), event.getUtilizationPercent(), event.getThresholdPercent()),
            "MEDIUM");

        metricsService.recordCapacityAlert("HIGH_UTILIZATION", event.getResourceId(), event.getUtilizationPercent());
    }

    private void handleCriticalUtilizationAlert(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "CRITICAL_UTILIZATION", correlationId);

        // Immediate scaling action
        kafkaTemplate.send("immediate-scaling-requests", Map.of(
            "resourceId", event.getResourceId(),
            "scalingType", "CRITICAL_UTILIZATION_SCALING",
            "urgency", "IMMEDIATE",
            "targetUtilization", 70.0,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Load shedding if necessary
        if (event.getUtilizationPercent() > 95.0) {
            kafkaTemplate.send("load-shedding-requests", Map.of(
                "resourceId", event.getResourceId(),
                "sheddingType", "CRITICAL_CAPACITY_PROTECTION",
                "sheddingPercentage", 20.0,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendOperationalAlert("CRITICAL Resource Utilization",
            String.format("CRITICAL: Resource %s utilization: %.1f%% - Immediate scaling required",
                event.getResourceId(), event.getUtilizationPercent()),
            "CRITICAL");

        metricsService.recordCapacityAlert("CRITICAL_UTILIZATION", event.getResourceId(), event.getUtilizationPercent());
    }

    private void handleCapacityExhaustionWarning(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "CAPACITY_EXHAUSTION_WARNING", correlationId);

        // Emergency capacity provisioning
        kafkaTemplate.send("emergency-capacity-provisioning", Map.of(
            "resourceId", event.getResourceId(),
            "provisioningType", "EXHAUSTION_PREVENTION",
            "urgency", "HIGH",
            "estimatedTimeToExhaustion", event.getTimeToExhaustionMinutes(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Burst capacity activation
        kafkaTemplate.send("burst-capacity-activation", Map.of(
            "resourceId", event.getResourceId(),
            "activationType", "EXHAUSTION_WARNING",
            "burstDuration", "2_HOURS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendExecutiveAlert("Capacity Exhaustion Warning",
            String.format("Resource %s approaching exhaustion in %d minutes",
                event.getResourceId(), event.getTimeToExhaustionMinutes()),
            "HIGH");

        metricsService.recordCapacityAlert("EXHAUSTION_WARNING", event.getResourceId(), event.getUtilizationPercent());
    }

    private void handleScalingThresholdReached(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "SCALING_THRESHOLD_REACHED", correlationId);

        // Execute auto-scaling
        kafkaTemplate.send("auto-scaling-execution", Map.of(
            "resourceId", event.getResourceId(),
            "scalingDirection", "UP",
            "scalingFactor", 1.5,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Monitor scaling progress
        kafkaTemplate.send("scaling-progress-monitoring", Map.of(
            "resourceId", event.getResourceId(),
            "monitoringType", "SCALING_EXECUTION",
            "expectedCompletionTime", 600000, // 10 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Auto-scaling initiated for resource {} due to threshold breach", event.getResourceId());
        metricsService.recordCapacityAlert("SCALING_THRESHOLD", event.getResourceId(), event.getUtilizationPercent());
    }

    private void handleResourceUnavailable(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "RESOURCE_UNAVAILABLE", correlationId);

        // Immediate failover
        kafkaTemplate.send("resource-failover-requests", Map.of(
            "primaryResourceId", event.getResourceId(),
            "failoverType", "RESOURCE_UNAVAILABILITY",
            "urgency", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Provision replacement capacity
        kafkaTemplate.send("replacement-capacity-provisioning", Map.of(
            "unavailableResourceId", event.getResourceId(),
            "provisioningType", "REPLACEMENT",
            "urgency", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Resource Unavailable",
            String.format("Resource %s is unavailable, initiating failover",
                event.getResourceId()),
            "HIGH");

        metricsService.recordCapacityAlert("RESOURCE_UNAVAILABLE", event.getResourceId(), 0.0);
    }

    private void handleForecastCapacityShortage(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "FORECAST_SHORTAGE", correlationId);

        // Proactive capacity planning
        kafkaTemplate.send("proactive-capacity-planning", Map.of(
            "resourceId", event.getResourceId(),
            "planningType", "SHORTAGE_PREVENTION",
            "forecastHorizon", event.getForecastHorizonDays(),
            "predictedShortagePercent", event.getPredictedShortagePercent(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule capacity provisioning
        kafkaTemplate.send("scheduled-capacity-provisioning", Map.of(
            "resourceId", event.getResourceId(),
            "provisioningType", "FORECAST_BASED",
            "scheduledDate", event.getRecommendedProvisioningDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Forecast Capacity Shortage",
            String.format("Predicted capacity shortage for %s in %d days: %.1f%%",
                event.getResourceId(), event.getForecastHorizonDays(), event.getPredictedShortagePercent()),
            "MEDIUM");

        metricsService.recordCapacityAlert("FORECAST_SHORTAGE", event.getResourceId(), event.getUtilizationPercent());
    }

    private void handleBurstCapacityActivated(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "BURST_CAPACITY_ACTIVATED", correlationId);

        // Monitor burst capacity usage
        kafkaTemplate.send("burst-capacity-monitoring", Map.of(
            "resourceId", event.getResourceId(),
            "monitoringType", "BURST_USAGE_TRACKING",
            "burstDuration", event.getBurstDurationMinutes(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Plan for sustained capacity if needed
        if (event.getBurstDurationMinutes() > 60) {
            kafkaTemplate.send("sustained-capacity-planning", Map.of(
                "resourceId", event.getResourceId(),
                "planningType", "BURST_TO_SUSTAINED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Burst capacity activated for resource {} for {} minutes",
            event.getResourceId(), event.getBurstDurationMinutes());
        metricsService.recordCapacityAlert("BURST_ACTIVATED", event.getResourceId(), event.getUtilizationPercent());
    }

    private void handleCapacityRestored(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "CAPACITY_RESTORED", correlationId);

        // Clear capacity alerts
        kafkaTemplate.send("capacity-alert-resolution", Map.of(
            "resourceId", event.getResourceId(),
            "resolutionType", "CAPACITY_RESTORED",
            "currentUtilization", event.getUtilizationPercent(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Resume normal operations
        kafkaTemplate.send("normal-operations-resumption", Map.of(
            "resourceId", event.getResourceId(),
            "resumptionType", "CAPACITY_RESTORED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Decrement warning count
        long currentWarnings = capacityWarningCount.get();
        if (currentWarnings > 0) {
            capacityWarningCount.decrementAndGet();
        }

        log.info("Capacity restored for resource {}, utilization: %.1f%%",
            event.getResourceId(), event.getUtilizationPercent());
        metricsService.recordCapacityAlert("CAPACITY_RESTORED", event.getResourceId(), event.getUtilizationPercent());
    }

    private void handleGenericCapacityAlert(CapacityAlertEvent event, String correlationId) {
        createCapacityRecord(event, "GENERIC", correlationId);

        // Log for investigation
        auditService.logCapacityEvent("UNKNOWN_CAPACITY_ALERT", event.getResourceId(),
            Map.of("alertType", event.getAlertType(), "utilizationPercent", event.getUtilizationPercent(),
                "thresholdPercent", event.getThresholdPercent(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Capacity Alert",
            String.format("Unknown capacity alert for resource %s: %s",
                event.getResourceId(), event.getAlertType()),
            "MEDIUM");

        metricsService.recordCapacityAlert("GENERIC", event.getResourceId(), event.getUtilizationPercent());
    }

    private void createCapacityRecord(CapacityAlertEvent event, String alertType, String correlationId) {
        try {
            CapacityRecord record = CapacityRecord.builder()
                .resourceId(event.getResourceId())
                .resourceType(event.getResourceType())
                .alertType(alertType)
                .utilizationPercent(event.getUtilizationPercent())
                .thresholdPercent(event.getThresholdPercent())
                .availableCapacity(event.getAvailableCapacity())
                .totalCapacity(event.getTotalCapacity())
                .alertTime(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

            capacityRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to create capacity record: {}", e.getMessage());
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