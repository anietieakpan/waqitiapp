package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.ServiceHealthEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.analytics.service.ServiceMonitoringService;
import com.waqiti.analytics.service.AlertService;
import com.waqiti.analytics.model.ServiceHealthMetric;
import com.waqiti.analytics.repository.ServiceHealthRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumer for processing service health events.
 * Monitors service availability, health checks, and dependency status.
 */
@Slf4j
@Component
public class ServiceHealthConsumer extends BaseKafkaConsumer<ServiceHealthEvent> {

    private static final String TOPIC = "service-health-events";

    private final ServiceMonitoringService serviceMonitoringService;
    private final AlertService alertService;
    private final ServiceHealthRepository serviceHealthRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter serviceDownCounter;
    private final Counter healthCheckFailCounter;
    private final Timer processingTimer;

    @Autowired
    public ServiceHealthConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            ServiceMonitoringService serviceMonitoringService,
            AlertService alertService,
            ServiceHealthRepository serviceHealthRepository) {
        super(objectMapper, TOPIC);
        this.serviceMonitoringService = serviceMonitoringService;
        this.alertService = alertService;
        this.serviceHealthRepository = serviceHealthRepository;

        this.processedCounter = Counter.builder("service_health_processed_total")
                .description("Total service health events processed")
                .register(meterRegistry);
        this.serviceDownCounter = Counter.builder("service_down_total")
                .description("Total service down incidents")
                .register(meterRegistry);
        this.healthCheckFailCounter = Counter.builder("health_check_fail_total")
                .description("Total health check failures")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("service_health_processing_duration")
                .description("Time taken to process service health events")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "analytics-service-health-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing service health event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            ServiceHealthEvent event = deserializeEvent(record.value(), ServiceHealthEvent.class);

            // Validate required fields
            validateEvent(event);

            // Process the service health event
            processServiceHealthEvent(event);

            processedCounter.increment();
            log.info("Successfully processed service health event: {} - {}",
                    event.getServiceId(), event.getHealthStatus());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing service health event: {}", record.value(), e);
            throw new RuntimeException("Failed to process service health event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processServiceHealthEvent(ServiceHealthEvent event) {
        try {
            // Create service health metric record
            ServiceHealthMetric metric = createServiceHealthMetric(event);

            // Check service status
            if (isServiceDown(event)) {
                handleServiceDown(event, metric);
                serviceDownCounter.increment();
            }

            // Check health check failures
            if (hasHealthCheckFailures(event)) {
                handleHealthCheckFailures(event, metric);
                healthCheckFailCounter.increment();
            }

            // Update service dependencies
            updateServiceDependencies(event, metric);

            // Calculate service availability
            calculateServiceAvailability(event, metric);

            // Save the metric
            serviceHealthRepository.save(metric);

            // Update service dashboard
            updateServiceDashboard(event, metric);

            log.info("Processed service health metric: {} - Status: {} (Availability: {}%)",
                    event.getServiceId(), event.getHealthStatus(), metric.getAvailabilityPercent());

        } catch (Exception e) {
            log.error("Error processing service health event: {}", event.getServiceId(), e);
            throw new RuntimeException("Failed to process service health event", e);
        }
    }

    private ServiceHealthMetric createServiceHealthMetric(ServiceHealthEvent event) {
        return ServiceHealthMetric.builder()
                .serviceId(event.getServiceId())
                .serviceName(event.getServiceName())
                .serviceVersion(event.getServiceVersion())
                .healthStatus(event.getHealthStatus())
                .responseTime(event.getResponseTime())
                .uptime(event.getUptime())
                .lastRestartTime(event.getLastRestartTime())
                .healthCheckResults(event.getHealthCheckResults())
                .dependencies(event.getDependencies())
                .errorRate(event.getErrorRate())
                .memoryUsage(event.getMemoryUsage())
                .cpuUsage(event.getCpuUsage())
                .diskUsage(event.getDiskUsage())
                .timestamp(event.getTimestamp())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean isServiceDown(ServiceHealthEvent event) {
        return "DOWN".equals(event.getHealthStatus()) || "CRITICAL".equals(event.getHealthStatus());
    }

    private void handleServiceDown(ServiceHealthEvent event, ServiceHealthMetric metric) {
        try {
            metric.setServiceDown(true);

            // Create service down alert
            alertService.createServiceDownAlert(
                event.getServiceId(),
                event.getServiceName(),
                event.getHealthStatus()
            );

            // Check for cascading failures
            var dependentServices = serviceMonitoringService.getDependentServices(event.getServiceId());
            if (!dependentServices.isEmpty()) {
                alertService.createCascadingFailureAlert(event.getServiceId(), dependentServices);
                metric.setPotentialCascadingFailure(true);
            }

            // Trigger incident response
            serviceMonitoringService.triggerIncidentResponse(event.getServiceId(), event.getHealthStatus());

            log.error("Service down detected: {} - {} ({})",
                    event.getServiceId(), event.getServiceName(), event.getHealthStatus());

        } catch (Exception e) {
            log.error("Error handling service down: {}", event.getServiceId(), e);
            throw new RuntimeException("Failed to handle service down", e);
        }
    }

    private boolean hasHealthCheckFailures(ServiceHealthEvent event) {
        return event.getHealthCheckResults() != null &&
               event.getHealthCheckResults().values().stream().anyMatch(result -> !"PASS".equals(result));
    }

    private void handleHealthCheckFailures(ServiceHealthEvent event, ServiceHealthMetric metric) {
        try {
            metric.setHealthCheckFailures(true);

            // Analyze failed health checks
            var failedChecks = event.getHealthCheckResults().entrySet().stream()
                .filter(entry -> !"PASS".equals(entry.getValue()))
                .toList();

            metric.setFailedHealthChecks(failedChecks.size());

            // Create health check failure alerts
            for (var failedCheck : failedChecks) {
                alertService.createHealthCheckFailureAlert(
                    event.getServiceId(),
                    failedCheck.getKey(),
                    failedCheck.getValue()
                );
            }

            log.warn("Health check failures detected: {} - Failed checks: {}",
                    event.getServiceId(), failedChecks.size());

        } catch (Exception e) {
            log.error("Error handling health check failures: {}", event.getServiceId(), e);
            throw new RuntimeException("Failed to handle health check failures", e);
        }
    }

    private void updateServiceDependencies(ServiceHealthEvent event, ServiceHealthMetric metric) {
        try {
            if (event.getDependencies() != null && !event.getDependencies().isEmpty()) {
                // Check dependency health
                var unhealthyDependencies = serviceMonitoringService.checkDependencyHealth(
                    event.getDependencies()
                );

                if (!unhealthyDependencies.isEmpty()) {
                    metric.setUnhealthyDependencies(unhealthyDependencies.size());

                    // Create dependency health alerts
                    alertService.createDependencyHealthAlert(
                        event.getServiceId(), unhealthyDependencies
                    );
                }

                // Update service dependency graph
                serviceMonitoringService.updateServiceDependencyGraph(
                    event.getServiceId(), event.getDependencies()
                );
            }

        } catch (Exception e) {
            log.error("Error updating service dependencies: {}", event.getServiceId(), e);
            // Don't fail the processing for dependency update errors
        }
    }

    private void calculateServiceAvailability(ServiceHealthEvent event, ServiceHealthMetric metric) {
        try {
            // Calculate availability percentage over time window
            double availability = serviceMonitoringService.calculateServiceAvailability(
                event.getServiceId(), event.getUptime()
            );

            metric.setAvailabilityPercent(availability);

            // Check SLA compliance
            double slaTarget = serviceMonitoringService.getServiceSlaTarget(event.getServiceId());
            boolean slaCompliant = availability >= slaTarget;

            metric.setSlaCompliant(slaCompliant);

            if (!slaCompliant) {
                alertService.createSlaBreachAlert(
                    event.getServiceId(), availability, slaTarget
                );
            }

        } catch (Exception e) {
            log.error("Error calculating service availability: {}", event.getServiceId(), e);
            // Don't fail the processing for availability calculation errors
        }
    }

    private void updateServiceDashboard(ServiceHealthEvent event, ServiceHealthMetric metric) {
        try {
            // Update real-time service dashboard
            serviceMonitoringService.updateServiceDashboard(event.getServiceId(), metric);

            // Update service status page
            serviceMonitoringService.updateServiceStatusPage(event.getServiceId(), event.getHealthStatus());

        } catch (Exception e) {
            log.error("Error updating service dashboard: {}", event.getServiceId(), e);
            // Don't fail the processing for dashboard update errors
        }
    }

    private void validateEvent(ServiceHealthEvent event) {
        if (event.getServiceId() == null || event.getServiceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Service ID cannot be null or empty");
        }
        if (event.getHealthStatus() == null || event.getHealthStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("Health status cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Service health processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed service health event - Key: {}, Time: {}ms", key, processingTime);
    }
}