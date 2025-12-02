package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.SystemPerformanceEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.analytics.service.SystemMonitoringService;
import com.waqiti.analytics.service.AlertService;
import com.waqiti.analytics.model.SystemPerformanceMetric;
import com.waqiti.analytics.model.PerformanceThreshold;
import com.waqiti.analytics.repository.SystemPerformanceRepository;
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
import java.util.Map;

/**
 * Consumer for processing system performance events.
 * Monitors system metrics, detects performance anomalies, and triggers alerts.
 */
@Slf4j
@Component
public class SystemPerformanceConsumer extends BaseKafkaConsumer<SystemPerformanceEvent> {

    private static final String TOPIC = "system-performance-events";

    private final SystemMonitoringService systemMonitoringService;
    private final AlertService alertService;
    private final SystemPerformanceRepository systemPerformanceRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter alertTriggeredCounter;
    private final Counter thresholdBreachCounter;
    private final Timer processingTimer;

    @Autowired
    public SystemPerformanceConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            SystemMonitoringService systemMonitoringService,
            AlertService alertService,
            SystemPerformanceRepository systemPerformanceRepository) {
        super(objectMapper, TOPIC);
        this.systemMonitoringService = systemMonitoringService;
        this.alertService = alertService;
        this.systemPerformanceRepository = systemPerformanceRepository;

        this.processedCounter = Counter.builder("system_performance_processed_total")
                .description("Total system performance events processed")
                .register(meterRegistry);
        this.alertTriggeredCounter = Counter.builder("system_performance_alert_total")
                .description("Total system performance alerts triggered")
                .register(meterRegistry);
        this.thresholdBreachCounter = Counter.builder("system_threshold_breach_total")
                .description("Total system threshold breaches")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("system_performance_processing_duration")
                .description("Time taken to process system performance events")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "analytics-service-system-performance-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing system performance event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            SystemPerformanceEvent event = deserializeEvent(record.value(), SystemPerformanceEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getSystemId(), event.getMetricName(), event.getTimestamp())) {
                log.info("System performance metric already processed: {} - {}",
                        event.getSystemId(), event.getMetricName());
                ack.acknowledge();
                return;
            }

            // Process the system performance event
            processSystemPerformanceEvent(event);

            processedCounter.increment();
            log.info("Successfully processed system performance event: {} - {}",
                    event.getSystemId(), event.getMetricName());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing system performance event: {}", record.value(), e);
            throw new RuntimeException("Failed to process system performance event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processSystemPerformanceEvent(SystemPerformanceEvent event) {
        try {
            // Create system performance metric record
            SystemPerformanceMetric metric = createSystemPerformanceMetric(event);

            // Get performance thresholds for this metric
            PerformanceThreshold thresholds = systemMonitoringService.getPerformanceThresholds(
                event.getSystemId(), event.getMetricName()
            );

            // Check for threshold breaches
            boolean thresholdBreached = checkThresholdBreach(event, thresholds);
            if (thresholdBreached) {
                handleThresholdBreach(event, metric, thresholds);
                thresholdBreachCounter.increment();
            }

            // Perform trend analysis
            performTrendAnalysis(event, metric);

            // Check for performance anomalies
            boolean anomalyDetected = detectPerformanceAnomaly(event, metric);
            if (anomalyDetected) {
                handlePerformanceAnomaly(event, metric);
            }

            // Update system health status
            updateSystemHealthStatus(event, metric);

            // Save the metric
            systemPerformanceRepository.save(metric);

            // Generate performance insights
            generatePerformanceInsights(event, metric);

            log.info("Processed system performance metric: {} - {} = {} (Threshold: {})",
                    event.getSystemId(), event.getMetricName(),
                    event.getMetricValue(), thresholdBreached ? "BREACHED" : "OK");

        } catch (Exception e) {
            log.error("Error processing system performance event: {} - {}",
                    event.getSystemId(), event.getMetricName(), e);
            throw new RuntimeException("Failed to process system performance event", e);
        }
    }

    private SystemPerformanceMetric createSystemPerformanceMetric(SystemPerformanceEvent event) {
        return SystemPerformanceMetric.builder()
                .systemId(event.getSystemId())
                .systemName(event.getSystemName())
                .serviceId(event.getServiceId())
                .serviceName(event.getServiceName())
                .metricName(event.getMetricName())
                .metricValue(event.getMetricValue())
                .metricUnit(event.getMetricUnit())
                .metricType(event.getMetricType())
                .dimensions(event.getDimensions())
                .tags(event.getTags())
                .timestamp(event.getTimestamp())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean checkThresholdBreach(SystemPerformanceEvent event, PerformanceThreshold thresholds) {
        if (thresholds == null) {
            return false;
        }

        double value = event.getMetricValue();
        String metricType = event.getMetricType();

        // Check critical thresholds
        if (thresholds.getCriticalThreshold() != null) {
            if ("HIGHER_IS_WORSE".equals(metricType) && value > thresholds.getCriticalThreshold()) {
                return true;
            }
            if ("LOWER_IS_WORSE".equals(metricType) && value < thresholds.getCriticalThreshold()) {
                return true;
            }
        }

        // Check warning thresholds
        if (thresholds.getWarningThreshold() != null) {
            if ("HIGHER_IS_WORSE".equals(metricType) && value > thresholds.getWarningThreshold()) {
                return true;
            }
            if ("LOWER_IS_WORSE".equals(metricType) && value < thresholds.getWarningThreshold()) {
                return true;
            }
        }

        return false;
    }

    private void handleThresholdBreach(SystemPerformanceEvent event,
                                     SystemPerformanceMetric metric,
                                     PerformanceThreshold thresholds) {
        try {
            metric.setThresholdBreached(true);

            // Determine severity level
            String severity = determineSeverity(event, thresholds);
            metric.setSeverity(severity);

            // Create alert
            alertService.createPerformanceAlert(
                event.getSystemId(),
                event.getServiceId(),
                event.getMetricName(),
                event.getMetricValue(),
                thresholds,
                severity
            );

            // Send notifications based on severity
            if ("CRITICAL".equals(severity)) {
                alertService.sendCriticalAlert(event.getSystemId(),
                    "CRITICAL_PERFORMANCE_THRESHOLD_BREACH", metric);

                // Trigger automatic remediation if configured
                systemMonitoringService.triggerAutomaticRemediation(
                    event.getSystemId(), event.getMetricName(), event.getMetricValue()
                );
            } else if ("WARNING".equals(severity)) {
                alertService.sendWarningAlert(event.getSystemId(),
                    "PERFORMANCE_WARNING", metric);
            }

            // Update system status
            systemMonitoringService.updateSystemStatus(event.getSystemId(), severity);

            alertTriggeredCounter.increment();

            log.warn("Performance threshold breached: {} - {} = {} (Threshold: {} - Severity: {})",
                    event.getSystemId(), event.getMetricName(), event.getMetricValue(),
                    getSeverityThreshold(thresholds, severity), severity);

        } catch (Exception e) {
            log.error("Error handling threshold breach: {} - {}",
                    event.getSystemId(), event.getMetricName(), e);
            throw new RuntimeException("Failed to handle threshold breach", e);
        }
    }

    private String determineSeverity(SystemPerformanceEvent event, PerformanceThreshold thresholds) {
        double value = event.getMetricValue();
        String metricType = event.getMetricType();

        // Check critical threshold first
        if (thresholds.getCriticalThreshold() != null) {
            if ("HIGHER_IS_WORSE".equals(metricType) && value > thresholds.getCriticalThreshold()) {
                return "CRITICAL";
            }
            if ("LOWER_IS_WORSE".equals(metricType) && value < thresholds.getCriticalThreshold()) {
                return "CRITICAL";
            }
        }

        // Check warning threshold
        if (thresholds.getWarningThreshold() != null) {
            if ("HIGHER_IS_WORSE".equals(metricType) && value > thresholds.getWarningThreshold()) {
                return "WARNING";
            }
            if ("LOWER_IS_WORSE".equals(metricType) && value < thresholds.getWarningThreshold()) {
                return "WARNING";
            }
        }

        return "INFO";
    }

    private void performTrendAnalysis(SystemPerformanceEvent event, SystemPerformanceMetric metric) {
        try {
            // Get historical data for trend analysis
            var historicalMetrics = systemMonitoringService.getHistoricalMetrics(
                event.getSystemId(), event.getMetricName(), 24 // Last 24 hours
            );

            // Calculate trend indicators
            double trendSlope = systemMonitoringService.calculateTrendSlope(historicalMetrics);
            String trendDirection = systemMonitoringService.determineTrendDirection(trendSlope);

            metric.setTrendSlope(trendSlope);
            metric.setTrendDirection(trendDirection);

            // Check for concerning trends
            if (systemMonitoringService.isConcerningTrend(trendDirection, event.getMetricType())) {
                metric.setConcerningTrend(true);

                // Create trend alert
                alertService.createTrendAlert(event.getSystemId(), event.getMetricName(),
                    trendDirection, trendSlope);
            }

        } catch (Exception e) {
            log.error("Error performing trend analysis: {} - {}",
                    event.getSystemId(), event.getMetricName(), e);
            // Don't fail the processing for trend analysis errors
        }
    }

    private boolean detectPerformanceAnomaly(SystemPerformanceEvent event, SystemPerformanceMetric metric) {
        try {
            // Use machine learning model to detect anomalies
            boolean isAnomaly = systemMonitoringService.detectAnomaly(
                event.getSystemId(),
                event.getMetricName(),
                event.getMetricValue(),
                event.getTimestamp()
            );

            metric.setAnomalyDetected(isAnomaly);

            if (isAnomaly) {
                double anomalyScore = systemMonitoringService.calculateAnomalyScore(
                    event.getSystemId(), event.getMetricName(), event.getMetricValue()
                );
                metric.setAnomalyScore(anomalyScore);
            }

            return isAnomaly;

        } catch (Exception e) {
            log.error("Error detecting performance anomaly: {} - {}",
                    event.getSystemId(), event.getMetricName(), e);
            return false;
        }
    }

    private void handlePerformanceAnomaly(SystemPerformanceEvent event, SystemPerformanceMetric metric) {
        try {
            // Create anomaly alert
            alertService.createAnomalyAlert(
                event.getSystemId(),
                event.getMetricName(),
                event.getMetricValue(),
                metric.getAnomalyScore()
            );

            // Send anomaly notification
            alertService.sendAnomalyNotification(event.getSystemId(),
                "PERFORMANCE_ANOMALY_DETECTED", metric);

            // Trigger investigation workflow
            systemMonitoringService.triggerAnomalyInvestigation(
                event.getSystemId(), event.getMetricName(), metric
            );

            log.info("Performance anomaly detected: {} - {} = {} (Score: {})",
                    event.getSystemId(), event.getMetricName(),
                    event.getMetricValue(), metric.getAnomalyScore());

        } catch (Exception e) {
            log.error("Error handling performance anomaly: {} - {}",
                    event.getSystemId(), event.getMetricName(), e);
            throw new RuntimeException("Failed to handle performance anomaly", e);
        }
    }

    private void updateSystemHealthStatus(SystemPerformanceEvent event, SystemPerformanceMetric metric) {
        try {
            // Calculate overall system health based on all metrics
            String healthStatus = systemMonitoringService.calculateSystemHealthStatus(event.getSystemId());

            // Update system health record
            systemMonitoringService.updateSystemHealth(event.getSystemId(), healthStatus, metric);

            // Check if health status changed
            String previousStatus = systemMonitoringService.getPreviousHealthStatus(event.getSystemId());
            if (!healthStatus.equals(previousStatus)) {
                handleHealthStatusChange(event, previousStatus, healthStatus);
            }

        } catch (Exception e) {
            log.error("Error updating system health status: {}", event.getSystemId(), e);
            // Don't fail the processing for health status update errors
        }
    }

    private void handleHealthStatusChange(SystemPerformanceEvent event,
                                        String previousStatus,
                                        String newStatus) {
        try {
            // Send health status change notification
            alertService.sendHealthStatusChangeAlert(event.getSystemId(),
                previousStatus, newStatus);

            // Trigger escalation procedures if health degraded significantly
            if (systemMonitoringService.isSignificantHealthDegradation(previousStatus, newStatus)) {
                systemMonitoringService.triggerEscalationProcedure(event.getSystemId(), newStatus);
            }

            log.info("System health status changed: {} - {} -> {}",
                    event.getSystemId(), previousStatus, newStatus);

        } catch (Exception e) {
            log.error("Error handling health status change: {}", event.getSystemId(), e);
            // Don't fail the processing for health status change handling errors
        }
    }

    private void generatePerformanceInsights(SystemPerformanceEvent event, SystemPerformanceMetric metric) {
        try {
            // Generate performance insights and recommendations
            var insights = systemMonitoringService.generatePerformanceInsights(
                event.getSystemId(), event.getMetricName(), metric
            );

            if (!insights.isEmpty()) {
                // Store insights for reporting
                systemMonitoringService.storePerformanceInsights(event.getSystemId(), insights);

                // Send actionable insights to operations team
                alertService.sendPerformanceInsights(event.getSystemId(), insights);
            }

        } catch (Exception e) {
            log.error("Error generating performance insights: {} - {}",
                    event.getSystemId(), event.getMetricName(), e);
            // Don't fail the processing for insight generation errors
        }
    }

    private Double getSeverityThreshold(PerformanceThreshold thresholds, String severity) {
        return switch (severity) {
            case "CRITICAL" -> thresholds.getCriticalThreshold();
            case "WARNING" -> thresholds.getWarningThreshold();
            default -> null;
        };
    }

    private boolean isAlreadyProcessed(String systemId, String metricName, LocalDateTime timestamp) {
        return systemPerformanceRepository.existsBySystemIdAndMetricNameAndTimestamp(
            systemId, metricName, timestamp
        );
    }

    private void validateEvent(SystemPerformanceEvent event) {
        if (event.getSystemId() == null || event.getSystemId().trim().isEmpty()) {
            throw new IllegalArgumentException("System ID cannot be null or empty");
        }
        if (event.getMetricName() == null || event.getMetricName().trim().isEmpty()) {
            throw new IllegalArgumentException("Metric name cannot be null or empty");
        }
        if (event.getMetricValue() == null) {
            throw new IllegalArgumentException("Metric value cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("System performance processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed system performance event - Key: {}, Time: {}ms", key, processingTime);
    }
}