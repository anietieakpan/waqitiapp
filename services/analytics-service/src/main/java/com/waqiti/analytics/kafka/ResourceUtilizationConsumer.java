package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.ResourceUtilizationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.analytics.service.ResourceMonitoringService;
import com.waqiti.analytics.service.AlertService;
import com.waqiti.analytics.model.ResourceUtilizationMetric;
import com.waqiti.analytics.repository.ResourceUtilizationRepository;
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
 * Consumer for processing resource utilization events.
 * Monitors CPU, memory, disk, and network resource usage across systems.
 */
@Slf4j
@Component
public class ResourceUtilizationConsumer extends BaseKafkaConsumer<ResourceUtilizationEvent> {

    private static final String TOPIC = "resource-utilization-events";

    private final ResourceMonitoringService resourceMonitoringService;
    private final AlertService alertService;
    private final ResourceUtilizationRepository resourceUtilizationRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter highUtilizationCounter;
    private final Counter capacityWarningCounter;
    private final Timer processingTimer;

    @Autowired
    public ResourceUtilizationConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            ResourceMonitoringService resourceMonitoringService,
            AlertService alertService,
            ResourceUtilizationRepository resourceUtilizationRepository) {
        super(objectMapper, TOPIC);
        this.resourceMonitoringService = resourceMonitoringService;
        this.alertService = alertService;
        this.resourceUtilizationRepository = resourceUtilizationRepository;

        this.processedCounter = Counter.builder("resource_utilization_processed_total")
                .description("Total resource utilization events processed")
                .register(meterRegistry);
        this.highUtilizationCounter = Counter.builder("high_resource_utilization_total")
                .description("Total high resource utilization incidents")
                .register(meterRegistry);
        this.capacityWarningCounter = Counter.builder("capacity_warning_total")
                .description("Total capacity warnings triggered")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("resource_utilization_processing_duration")
                .description("Time taken to process resource utilization events")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "analytics-service-resource-utilization-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing resource utilization event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            ResourceUtilizationEvent event = deserializeEvent(record.value(), ResourceUtilizationEvent.class);

            // Validate required fields
            validateEvent(event);

            // Process the resource utilization event
            processResourceUtilizationEvent(event);

            processedCounter.increment();
            log.info("Successfully processed resource utilization event: {} - {}",
                    event.getResourceId(), event.getResourceType());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing resource utilization event: {}", record.value(), e);
            throw new RuntimeException("Failed to process resource utilization event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processResourceUtilizationEvent(ResourceUtilizationEvent event) {
        try {
            // Create resource utilization metric record
            ResourceUtilizationMetric metric = createResourceUtilizationMetric(event);

            // Check for high utilization
            if (isHighUtilization(event)) {
                handleHighUtilization(event, metric);
                highUtilizationCounter.increment();
            }

            // Check capacity warnings
            if (needsCapacityWarning(event)) {
                handleCapacityWarning(event, metric);
                capacityWarningCounter.increment();
            }

            // Analyze utilization trends
            analyzeUtilizationTrends(event, metric);

            // Update resource forecasting
            updateResourceForecasting(event, metric);

            // Save the metric
            resourceUtilizationRepository.save(metric);

            // Generate capacity recommendations
            generateCapacityRecommendations(event, metric);

            log.info("Processed resource utilization: {} - {} = {}%",
                    event.getResourceId(), event.getResourceType(), event.getUtilizationPercent());

        } catch (Exception e) {
            log.error("Error processing resource utilization event: {} - {}",
                    event.getResourceId(), event.getResourceType(), e);
            throw new RuntimeException("Failed to process resource utilization event", e);
        }
    }

    private ResourceUtilizationMetric createResourceUtilizationMetric(ResourceUtilizationEvent event) {
        return ResourceUtilizationMetric.builder()
                .resourceId(event.getResourceId())
                .resourceType(event.getResourceType())
                .hostId(event.getHostId())
                .serviceId(event.getServiceId())
                .utilizationPercent(event.getUtilizationPercent())
                .cpuUsage(event.getCpuUsage())
                .memoryUsage(event.getMemoryUsage())
                .diskUsage(event.getDiskUsage())
                .networkUsage(event.getNetworkUsage())
                .availableCapacity(event.getAvailableCapacity())
                .totalCapacity(event.getTotalCapacity())
                .timestamp(event.getTimestamp())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean isHighUtilization(ResourceUtilizationEvent event) {
        double threshold = resourceMonitoringService.getHighUtilizationThreshold(event.getResourceType());
        return event.getUtilizationPercent() > threshold;
    }

    private void handleHighUtilization(ResourceUtilizationEvent event, ResourceUtilizationMetric metric) {
        try {
            metric.setHighUtilization(true);

            String severity = determineSeverity(event);
            metric.setSeverity(severity);

            // Create high utilization alert
            alertService.createHighUtilizationAlert(
                event.getResourceId(),
                event.getResourceType(),
                event.getUtilizationPercent(),
                severity
            );

            // Check for resource exhaustion risk
            if (event.getUtilizationPercent() > 95) {
                alertService.createResourceExhaustionAlert(
                    event.getResourceId(), event.getResourceType()
                );

                // Trigger auto-scaling if enabled
                resourceMonitoringService.triggerAutoScaling(
                    event.getResourceId(), event.getResourceType()
                );
            }

            log.warn("High resource utilization: {} - {} = {}% ({})",
                    event.getResourceId(), event.getResourceType(),
                    event.getUtilizationPercent(), severity);

        } catch (Exception e) {
            log.error("Error handling high utilization: {} - {}",
                    event.getResourceId(), event.getResourceType(), e);
            throw new RuntimeException("Failed to handle high utilization", e);
        }
    }

    private boolean needsCapacityWarning(ResourceUtilizationEvent event) {
        // Predict if capacity will be exhausted within forecast window
        return resourceMonitoringService.predictCapacityExhaustion(
            event.getResourceId(), event.getUtilizationPercent()
        );
    }

    private void handleCapacityWarning(ResourceUtilizationEvent event, ResourceUtilizationMetric metric) {
        try {
            metric.setCapacityWarning(true);

            // Calculate projected exhaustion time
            var capacityForecast = resourceMonitoringService.calculateCapacityForecast(
                event.getResourceId(), event.getUtilizationPercent()
            );

            metric.setProjectedExhaustionTime(capacityForecast.getExhaustionTime());

            // Create capacity warning alert
            alertService.createCapacityWarningAlert(
                event.getResourceId(),
                event.getResourceType(),
                capacityForecast
            );

            log.warn("Capacity warning: {} - {} (Projected exhaustion: {})",
                    event.getResourceId(), event.getResourceType(),
                    capacityForecast.getExhaustionTime());

        } catch (Exception e) {
            log.error("Error handling capacity warning: {} - {}",
                    event.getResourceId(), event.getResourceType(), e);
            throw new RuntimeException("Failed to handle capacity warning", e);
        }
    }

    private void analyzeUtilizationTrends(ResourceUtilizationEvent event, ResourceUtilizationMetric metric) {
        try {
            // Analyze utilization trends over time
            var trendAnalysis = resourceMonitoringService.analyzeUtilizationTrends(
                event.getResourceId(), event.getUtilizationPercent()
            );

            metric.setTrendAnalysis(trendAnalysis);

            // Check for concerning trends
            if (trendAnalysis.isConcerningTrend()) {
                alertService.createUtilizationTrendAlert(
                    event.getResourceId(), trendAnalysis
                );
            }

        } catch (Exception e) {
            log.error("Error analyzing utilization trends: {} - {}",
                    event.getResourceId(), event.getResourceType(), e);
            // Don't fail the processing for trend analysis errors
        }
    }

    private void updateResourceForecasting(ResourceUtilizationEvent event, ResourceUtilizationMetric metric) {
        try {
            // Update resource forecasting models
            resourceMonitoringService.updateForecastingModel(
                event.getResourceId(), event.getUtilizationPercent(), event.getTimestamp()
            );

            // Generate capacity forecasts
            var forecast = resourceMonitoringService.generateCapacityForecast(
                event.getResourceId(), 30 // 30-day forecast
            );

            metric.setCapacityForecast(forecast);

        } catch (Exception e) {
            log.error("Error updating resource forecasting: {} - {}",
                    event.getResourceId(), event.getResourceType(), e);
            // Don't fail the processing for forecasting errors
        }
    }

    private void generateCapacityRecommendations(ResourceUtilizationEvent event, ResourceUtilizationMetric metric) {
        try {
            // Generate capacity planning recommendations
            var recommendations = resourceMonitoringService.generateCapacityRecommendations(
                event.getResourceId(), event.getResourceType(), metric
            );

            if (!recommendations.isEmpty()) {
                // Store recommendations for capacity planning
                resourceMonitoringService.storeCapacityRecommendations(
                    event.getResourceId(), recommendations
                );

                // Send recommendations to infrastructure team
                alertService.sendCapacityRecommendations(
                    event.getResourceId(), recommendations
                );
            }

        } catch (Exception e) {
            log.error("Error generating capacity recommendations: {} - {}",
                    event.getResourceId(), event.getResourceType(), e);
            // Don't fail the processing for recommendation generation errors
        }
    }

    private String determineSeverity(ResourceUtilizationEvent event) {
        double utilization = event.getUtilizationPercent();
        if (utilization > 95) return "CRITICAL";
        if (utilization > 85) return "WARNING";
        return "INFO";
    }

    private void validateEvent(ResourceUtilizationEvent event) {
        if (event.getResourceId() == null || event.getResourceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Resource ID cannot be null or empty");
        }
        if (event.getResourceType() == null || event.getResourceType().trim().isEmpty()) {
            throw new IllegalArgumentException("Resource type cannot be null or empty");
        }
        if (event.getUtilizationPercent() == null || event.getUtilizationPercent() < 0 || event.getUtilizationPercent() > 100) {
            throw new IllegalArgumentException("Utilization percent must be between 0 and 100");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Resource utilization processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed resource utilization event - Key: {}, Time: {}ms", key, processingTime);
    }
}